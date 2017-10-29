/*
 * Copyright 2015-2017 Red Hat, Inc. and/or its affiliates
 * and other contributors as indicated by the @author tags.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.hawkular.agent.monitor.protocol;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

import org.hawkular.agent.monitor.api.InventoryEvent;
import org.hawkular.agent.monitor.api.InventoryListener;
import org.hawkular.agent.monitor.api.SamplingService;
import org.hawkular.agent.monitor.config.AgentCoreEngineConfiguration.AbstractEndpointConfiguration.WaitFor;
import org.hawkular.agent.monitor.config.AgentCoreEngineConfiguration.EndpointConfiguration;
import org.hawkular.agent.monitor.diagnostics.ProtocolDiagnostics;
import org.hawkular.agent.monitor.inventory.ID;
import org.hawkular.agent.monitor.inventory.MeasurementInstance;
import org.hawkular.agent.monitor.inventory.MeasurementType;
import org.hawkular.agent.monitor.inventory.MetricType;
import org.hawkular.agent.monitor.inventory.MonitoredEndpoint;
import org.hawkular.agent.monitor.inventory.NodeLocation;
import org.hawkular.agent.monitor.inventory.Resource;
import org.hawkular.agent.monitor.inventory.ResourceManager;
import org.hawkular.agent.monitor.inventory.ResourceManager.AddResult;
import org.hawkular.agent.monitor.inventory.ResourceType;
import org.hawkular.agent.monitor.inventory.ResourceTypeManager;
import org.hawkular.agent.monitor.log.AgentLoggers;
import org.hawkular.agent.monitor.log.MsgLogger;
import org.hawkular.agent.monitor.service.ServiceStatus;
import org.hawkular.agent.monitor.util.Consumer;
import org.hawkular.agent.monitor.util.ThreadFactoryGenerator;
import org.hawkular.inventory.api.model.MetricUnit;

import com.codahale.metrics.Timer.Context;

/**
 * A service to discover and sample resources from a single {@link MonitoredEndpoint}. This service also owns the single
 * {@link ResourceManager} associated with the given {@link MonitoredEndpoint}.
 *
 * @author <a href="https://github.com/ppalaga">Peter Palaga</a>
 *
 * @param <L> the type of the protocol specific location, typically a subclass of {@link NodeLocation}
 * @param <S> the protocol specific {@link Session}
 */
public abstract class EndpointService<L, S extends Session<L>> implements SamplingService<L> {
    private static final MsgLogger LOG = AgentLoggers.getLogger(EndpointService.class);

    private class InventoryListenerSupport {
        private final List<InventoryListener> inventoryListeners = new ArrayList<>();
        private final ReadWriteLock inventoryListenerRWLock = new ReentrantReadWriteLock();

        public void fireResourcesRemoved(List<Resource<L>> resources) {
            if (!resources.isEmpty()) {
                inventoryListenerRWLock.readLock().lock();
                try {
                    LOG.debugf("Firing inventory event for [%d] removed resources", resources.size());
                    InventoryEvent<L> event = InventoryEvent.removed(
                            EndpointService.this,
                            getResourceManager(),
                            resources);
                    for (InventoryListener inventoryListener : inventoryListeners) {
                        inventoryListener.receivedEvent(event);
                    }
                } finally {
                    inventoryListenerRWLock.readLock().unlock();
                }
            }
        }

        public void fireDiscoveryComplete(List<Resource<L>> addedOrModified, List<Resource<L>> removed) {
            inventoryListenerRWLock.readLock().lock();
            try {
                LOG.debugf("Firing inventory event for discovery complete");
                InventoryEvent<L> event = InventoryEvent.discovery(
                        EndpointService.this,
                        getResourceManager(),
                        getResourceTypeManager(),
                        addedOrModified,
                        removed);
                for (InventoryListener inventoryListener : inventoryListeners) {
                    inventoryListener.receivedEvent(event);
                }
            } finally {
                inventoryListenerRWLock.readLock().unlock();
            }
        }
    }

    private class DiscoveryResults {
        private final List<Resource<L>> newOrModifiedResources = new ArrayList<>();
        private final List<ID> discoveredResourceIds = new ArrayList<>(); // to save space, just store the IDs
        private final List<Throwable> errors = new ArrayList<>();

        public DiscoveryResults() {
        }

        public void error(Throwable t) {
            errors.add(t);
        }

        public void added(Resource<L> resource) {
            discoveredResourceIds.add(resource.getID());
            newOrModifiedResources.add(resource);
        }

        public void modified(Resource<L> resource) {
            discoveredResourceIds.add(resource.getID());
            newOrModifiedResources.add(resource);
        }

        public void unchanged(Resource<L> resource) {
            discoveredResourceIds.add(resource.getID());
        }

        public void discoveryFinished() {
            // Discovery is complete so the resource manager has all known resources (including all previously
            // discovered resources that may not have been discovered this last time around). removedResources will be
            // those resources that were not added, modified, or discovered-but-unchanged but still found in the
            // resource manager - we need to remove them internally and fire the removed event.
            List<Resource<L>> removedResources = getResourceManager().getAllResources(discoveredResourceIds);

            // remove them from the resource manager itself
            removedResources.forEach(r -> getResourceManager().removeResource(r));

            // do not fire a discovery complete event if errors occurred since we might be missing resources
            // that really do exist - we don't want to permanently delete those during an inventory sync
            if (errors.isEmpty()) {
                inventoryListenerSupport.fireDiscoveryComplete(newOrModifiedResources, removedResources);
            } else {
                LOG.debugf("[%d] discovery errors occurred - not firing event: %s", errors.size(), errors);
            }
        }
    }

    private final MonitoredEndpoint<EndpointConfiguration> endpoint;
    private final String feedId;
    private final InventoryListenerSupport inventoryListenerSupport = new InventoryListenerSupport();
    private final ResourceManager<L> resourceManager;
    private final ResourceTypeManager<L> resourceTypeManager;
    private final LocationResolver<L> locationResolver;
    private final ProtocolDiagnostics diagnostics;
    private final ExecutorService fullDiscoveryScanThreadPool;

    protected volatile ServiceStatus status = ServiceStatus.INITIAL;

    public EndpointService(String feedId,
            MonitoredEndpoint<EndpointConfiguration> endpoint,
            ResourceTypeManager<L> resourceTypeManager,
            LocationResolver<L> locationResolver,
            ProtocolDiagnostics diagnostics) {
        super();
        this.feedId = feedId;
        this.endpoint = endpoint;
        this.resourceManager = new ResourceManager<>();
        this.resourceTypeManager = resourceTypeManager;
        this.locationResolver = locationResolver;
        this.diagnostics = diagnostics;

        // This thread pool is used to limit the number of full discovery scans that are performed at any one time.
        // At most one full discovery scan is being performed at a single time, with at most one other full
        // discovery request queued up. Any other full discovery scan requests will be rejected because they are
        // not needed - the queued discovery scan will do it. This minimizes redundant scans being performed.
        ThreadFactory threadFactory = ThreadFactoryGenerator.generateFactory(true,
                "Hawkular-Agent-Full-Discovery-Scan-" + endpoint.getName());
        this.fullDiscoveryScanThreadPool = new ThreadPoolExecutor(1, 1, 0L, TimeUnit.MILLISECONDS,
                new LinkedBlockingQueue<Runnable>(1), threadFactory);
    }

    public String getFeedId() {
        return feedId;
    }

    @Override
    public MonitoredEndpoint<EndpointConfiguration> getMonitoredEndpoint() {
        return endpoint;
    }

    public ResourceManager<L> getResourceManager() {
        return resourceManager;
    }

    public ResourceTypeManager<L> getResourceTypeManager() {
        return resourceTypeManager;
    }

    public LocationResolver<L> getLocationResolver() {
        return locationResolver;
    }

    public ProtocolDiagnostics getDiagnostics() {
        return diagnostics;
    }

    /**
     * Works only before {@link #start()} or after {@link #stop()}.
     *
     * @param listener to add
     */
    public void addInventoryListener(InventoryListener listener) {
        this.inventoryListenerSupport.inventoryListenerRWLock.writeLock().lock();
        try {
            status.assertInitialOrStopped(getClass(), "addInventoryListener()");
            this.inventoryListenerSupport.inventoryListeners.add(listener);
            LOG.debugf("Added inventory listener [%s] for endpoint [%s]", listener, getMonitoredEndpoint());
        } finally {
            this.inventoryListenerSupport.inventoryListenerRWLock.writeLock().unlock();
        }
    }

    /**
     * Works only before {@link #start()} or after {@link #stop()}.
     *
     * @param listener to remove
     */
    public void removeInventoryListener(InventoryListener listener) {
        this.inventoryListenerSupport.inventoryListenerRWLock.writeLock().lock();
        try {
            status.assertInitialOrStopped(getClass(), "removeInventoryListener()");
            this.inventoryListenerSupport.inventoryListeners.remove(listener);
            LOG.debugf("Removed inventory listener [%s] for endpoint [%s]", listener, getMonitoredEndpoint());
        } finally {
            this.inventoryListenerSupport.inventoryListenerRWLock.writeLock().unlock();
        }
    }

    /**
     * Opens a new protocl specific {@link Session} - do not forget to close it!
     *
     * @return a new {@link Session}
     */
    public abstract S openSession();

    /**
     * Discovers all resources, puts them in the {@link #resourceManager},
     * and triggers any listeners listening for new inventory.
     *
     * This will look for all root resources (resources whose types are that of the root resource types
     * as defined by {@link ResourceTypeManager#getRootResourceTypes()} and then obtain all their
     * children (recursively down to all descendents). Effectively, this discovers the full
     * resource hierarchy.
     */
    public void discoverAll() {
        status.assertRunning(getClass(), "discoverAll()");

        Runnable runnable = new Runnable() {
            @Override
            public void run() {
                DiscoveryResults discoveryResults = new DiscoveryResults();

                LOG.infoDiscoveryRequested(getMonitoredEndpoint());
                long duration = -1;
                try (S session = openSession()) {
                    Set<ResourceType<L>> rootTypes = getResourceTypeManager().getRootResourceTypes();
                    Context timer = getDiagnostics().getFullDiscoveryScanTimer().time();
                    for (ResourceType<L> rootType : rootTypes) {
                        discoverChildren(null, rootType, session, discoveryResults);
                    }
                    long nanos = timer.stop();
                    duration = TimeUnit.MILLISECONDS.convert(nanos, TimeUnit.NANOSECONDS);
                } catch (Exception e) {
                    LOG.errorCouldNotAccess(EndpointService.this, e);
                    discoveryResults.error(e);
                }

                getResourceManager().logTreeGraph("Discovered all resources for: " + getMonitoredEndpoint(), duration);

                discoveryResults.discoveryFinished();
            }
        };

        try {
            this.fullDiscoveryScanThreadPool.execute(runnable);
        } catch (RejectedExecutionException ree) {
            LOG.debugf("Redundant full discovery scan will be ignored for endpoint [%s]", getMonitoredEndpoint());
        }
    }

    /**
     * Discovers child resources of the given {@code parentLocation}, puts them to {@link #resourceManager} and triggers
     * {@link InventoryListener#receivedEvent(InventoryEvent)}.
     *
     * @param parentLocation the location under which the discovery should happen
     * @param childType the resources of this type will be discovered.
     * @param session If not <code>null</code>, this session is used; if <code>null</code> one will be created.
     *        If a non-null session is passed in, the caller is responsible for closing it - this method will
     *        not close it. If a null session is passed in, this method will create and close a session itself.
     * @param discoveryResults the object that collects results from this method as it executes recursively
     */
    private void discoverChildren(L parentLocation, ResourceType<L> childType, S session,
            DiscoveryResults discoveryResults) {

        status.assertRunning(getClass(), "discoverChildren()");
        LOG.debugf("Being asked to discover children of type [%s] under parent [%s] for endpoint [%s]",
                childType, parentLocation, getMonitoredEndpoint());

        S sessionToUse = null;
        try {
            sessionToUse = (session == null) ? openSession() : session;

            /* FIXME: resourceManager should be write-locked here over find and add */
            List<Resource<L>> parents;
            if (parentLocation != null) {
                parents = getResourceManager().findResources(parentLocation, sessionToUse.getLocationResolver());
            } else {
                parents = Arrays.asList((Resource<L>) null);
            }
            Discovery<L> discovery = new Discovery<>();
            for (Resource<L> parent : parents) {
                discovery.discoverChildren(parent, childType, sessionToUse, this, new Consumer<Resource<L>>() {
                    public void accept(Resource<L> resource) {
                        AddResult<L> addResult = getResourceManager().addResource(resource);
                        switch (addResult.getEffect()) {
                            case ADDED:
                                discoveryResults.added(addResult.getResource());
                                break;
                            case MODIFIED:
                                discoveryResults.modified(addResult.getResource());
                                break;
                            case UNCHANGED:
                                discoveryResults.unchanged(addResult.getResource());
                                break;
                            default:
                                throw new RuntimeException("Bad effect; report this bug: " + addResult.getEffect());
                        }
                    }

                    @Override
                    public void report(Throwable t) {
                        discoveryResults.error(t);
                        LOG.errorCouldNotAccess(EndpointService.this, t);
                    }
                });
            }
        } catch (Exception e) {
            discoveryResults.error(e);
            LOG.errorCouldNotAccess(this, e);
        } finally {
            // We only close the session that we used if it was one we created;
            // if the session was provided to us then the caller has the responsibility to close it.
            if (session == null && sessionToUse != null) {
                try {
                    sessionToUse.close();
                } catch (IOException ioe) {
                    LOG.warnf("Could not close session created for children discovery", ioe);
                }
            }
        }
    }

    @Override
    public String generateMetricFamily(MeasurementInstance<L, ? extends MeasurementType<L>> instance) {
        return instance.getType().getMetricFamily();
    }

    @Override
    public Map<String, String> generateMetricLabels(MeasurementInstance<L, ? extends MeasurementType<L>> instance) {
        // Metric labels are configured in one of three places - either in the metric definition itself, or on
        // the resource type hierarchy, or in the endpoint configuration.
        // If labels are defined in more than one place, all the labels found in all places are associated with
        // the metric. If, however, more than one place define the same label name, the metric definition takes precedence
        // over the the endpoint config but the resource type labels takes precedence over all.
        Map<String, String> generatedLabels = new HashMap<>();
        Map<String, String> tokenizedLabels = new HashMap<>();

        // first do the endpoint configuration's metric labels
        EndpointConfiguration config = getMonitoredEndpoint().getEndpointConfiguration();
        if (config.getMetricLabels() != null) {
            tokenizedLabels.putAll(config.getMetricLabels());
        }

        // second do the metric definitions metric labels
        if (instance.getType().getMetricLabels() != null) {
            tokenizedLabels.putAll(instance.getType().getMetricLabels());
        }

        if (!tokenizedLabels.isEmpty()) {
            for (Map.Entry<String, String> tokenizedLabel : tokenizedLabels.entrySet()) {
                String name = replaceTokens(instance.getAttributeLocation().getLocation(), instance, config,
                        tokenizedLabel.getKey());
                String value = replaceTokens(instance.getAttributeLocation().getLocation(), instance, config,
                        tokenizedLabel.getValue());
                generatedLabels.put(name, value);
            }
        }

        // third do all metric labels for all types in the resource's ancestry
        Resource<L> r = instance.getResource();
        while (r != null) {
            if (r.getResourceType().getMetricLabels() != null) {
                for (Map.Entry<String, String> tokenizedLabel : r.getResourceType().getMetricLabels().entrySet()) {
                    // we must use the parent resource location, not metric's resource location
                    String name = replaceTokens(r.getLocation(), instance, config, tokenizedLabel.getKey());
                    String value = replaceTokens(r.getLocation(), instance, config, tokenizedLabel.getValue());
                    generatedLabels.put(name, value);
                }
            }
            r = r.getParent();
        }

        return generatedLabels;
    }

    private String replaceTokens(L location, MeasurementInstance<L, ?> instance, EndpointConfiguration config,
            String string) {
        MetricUnit units = null;
        if (instance.getType() instanceof MetricType) {
            units = ((MetricType<?>) instance.getType()).getMetricUnits();
        }

        // replace additional types of tokens that are supported
        string = string
                .replaceAll("%FeedId", getFeedId())
                .replaceAll("%ManagedServerName", config.getName())
                .replaceAll("%ResourceName", instance.getResource().getName().getNameString())
                .replaceAll("%AttributeName", instance.getAttributeLocation().getAttribute())
                .replaceAll("%MetricTypeName", instance.getType().getName().getNameString())
                .replaceAll("%MetricTypeUnits", units == null ? "" : units.toString());

        // this replaces any positional tokens that might exist in the string (like "%1", "%key%", "%-", etc).
        string = getLocationResolver().applyTemplate(string, location, config.getName());

        return string;
    }

    /**
     * Remove resources matching the given {@code location} and all their direct and indirect descendant resources.
     *
     * @param location a location that can contain wildcards
     */
    public void removeResources(L location) {
        status.assertRunning(getClass(), "removeResources()");
        try (S session = openSession()) {
            List<Resource<L>> removed = getResourceManager().removeResources(location, session.getLocationResolver());
            inventoryListenerSupport.fireResourcesRemoved(removed);
        } catch (Exception e) {
            LOG.errorCouldNotAccess(this, e);
        }
    }

    public final void start() {
        status.assertInitialOrStopped(getClass(), "start()");
        status = ServiceStatus.STARTING;

        // block the start method until the endpoint is ready
        boolean ready = isEndpointReady();
        if (!ready) {
            LOG.infof("Endpoint [%s] is not ready yet - will wait", toString());
            do {
                try {
                    Thread.sleep(5000);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    status = ServiceStatus.STOPPED;
                    return;
                }
                ready = isEndpointReady();
            } while (!ready);
        }

        status = ServiceStatus.RUNNING;

        LOG.debugf("Started [%s]", toString());
    }

    public void stop() {
        status.assertRunning(getClass(), "stop()");
        status = ServiceStatus.STOPPING;
        // nothing to do
        status = ServiceStatus.STOPPED;

        LOG.debugf("Stopped [%s]", toString());
    }

    private boolean isEndpointReady() {
        // If the endpoint was not configured to wait for any resources, assume the endpoint is ready.
        // Otherwise, check to see if all resources to be waited on exist - if so, assume the endpoint is ready.
        // Even if just one wait-for resource does not exist, we assume the endpoint is NOT ready.
        List<WaitFor> waitForResources = this.getMonitoredEndpoint().getEndpointConfiguration().getWaitForResources();
        if (!waitForResources.isEmpty()) {
            try (S s = openSession()) {
                Driver<L> driver = s.getDriver();
                Map<L, Object> results;
                for (WaitFor waitFor : waitForResources) {
                    LOG.tracef("Checking if endpoint [%s] is ready: resource [%s]", this, waitFor.getResource());
                    results = driver.fetchNodes(s.getLocationResolver().buildLocation(waitFor.getResource()));
                    if (results.isEmpty()) {
                        LOG.debugf("Endpoint [%s] is not yet ready - resource [%s] is missing",
                                this, waitFor.getResource());
                        return false;
                    } else {
                        LOG.tracef("Endpoint [%s] wait-for resource [%s] is ready", this, waitFor.getResource());
                    }
                }
            } catch (Exception e) {
                LOG.debugf("Endpoint [%s] is not yet ready [%s]", this, e.toString());
                return false;
            }
        }

        return true;
    }

    @Override
    public String toString() {
        return String.format("%s[%s]", getClass().getSimpleName(), getMonitoredEndpoint());
    }

    @Override
    public int hashCode() {
        int result = 31 + ((endpoint == null) ? 0 : endpoint.hashCode());
        return result;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (!(obj instanceof EndpointService)) {
            return false;
        }
        @SuppressWarnings({ "rawtypes" })
        EndpointService other = (EndpointService) obj;
        if (this.endpoint == null) {
            if (other.endpoint != null) {
                return false;
            }
        } else if (!this.endpoint.equals(other.endpoint)) {
            return false;
        }
        return true;
    }
}
