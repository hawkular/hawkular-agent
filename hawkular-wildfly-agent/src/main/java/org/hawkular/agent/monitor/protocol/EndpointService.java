/*
 * Copyright 2015-2016 Red Hat, Inc. and/or its affiliates
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
import java.util.Collection;
import java.util.Collections;
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
import java.util.regex.Pattern;

import org.hawkular.agent.monitor.api.Avail;
import org.hawkular.agent.monitor.api.DiscoveryEvent;
import org.hawkular.agent.monitor.api.InventoryEvent;
import org.hawkular.agent.monitor.api.InventoryListener;
import org.hawkular.agent.monitor.api.SamplingService;
import org.hawkular.agent.monitor.diagnostics.ProtocolDiagnostics;
import org.hawkular.agent.monitor.extension.MonitorServiceConfiguration.EndpointConfiguration;
import org.hawkular.agent.monitor.inventory.AttributeLocation;
import org.hawkular.agent.monitor.inventory.AvailType;
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
import org.hawkular.agent.monitor.storage.AvailDataPoint;
import org.hawkular.agent.monitor.storage.MetricDataPoint;
import org.hawkular.agent.monitor.storage.NumericMetricDataPoint;
import org.hawkular.agent.monitor.storage.StringMetricDataPoint;
import org.hawkular.agent.monitor.util.Consumer;
import org.hawkular.agent.monitor.util.ThreadFactoryGenerator;
import org.jboss.as.controller.client.helpers.MeasurementUnit;

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

        public void fireResourcesAdded(List<Resource<L>> resources) {
            if (!resources.isEmpty()) {
                LOG.debugf("Firing inventory event for [%d] added/modified resources", resources.size());
                InventoryEvent<L> event = new InventoryEvent<L>(EndpointService.this, resources);
                for (InventoryListener inventoryListener : inventoryListeners) {
                    inventoryListener.resourcesAdded(event);
                }
            }
        }

        public void fireResourcesRemoved(List<Resource<L>> resources) {
            if (!resources.isEmpty()) {
                LOG.debugf("Firing inventory event for [%d] removed resources", resources.size());
                InventoryEvent<L> event = new InventoryEvent<L>(EndpointService.this, resources);
                for (InventoryListener inventoryListener : inventoryListeners) {
                    inventoryListener.resourcesRemoved(event);
                }
            }
        }

        public void fireDiscoveryComplete() {
            LOG.debugf("Firing inventory event for discovery complete");
            DiscoveryEvent<L> event = new DiscoveryEvent<L>(EndpointService.this, getResourceManager(),
                    getResourceTypeManager());
            for (InventoryListener inventoryListener : inventoryListeners) {
                inventoryListener.discoveryCompleted(event);
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

            // now emit events so other parts of the system can add/remove resources and persist to Hawkular Inventory
            inventoryListenerSupport.fireResourcesAdded(newOrModifiedResources);
            inventoryListenerSupport.fireResourcesRemoved(removedResources);

            // do not fire a discovery complete event if errors occurred since we might be missing resources
            // that really do exist - we don't want to permanently delete those during an inventory sync
            if (errors.isEmpty()) {
                inventoryListenerSupport.fireDiscoveryComplete();
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
                "Hawkular WildFly Agent Full Discovery Scan-" + endpoint.getName());
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
        status.assertInitialOrStopped(getClass(), "addInventoryListener()");
        this.inventoryListenerSupport.inventoryListeners.add(listener);
        LOG.debugf("Added inventory listener [%s] for endpoint [%s]", listener, getMonitoredEndpoint());
    }

    /**
     * Works only before {@link #start()} or after {@link #stop()}.
     *
     * @param listener to remove
     */
    public void removeInventoryListener(InventoryListener listener) {
        status.assertInitialOrStopped(getClass(), "removeInventoryListener()");
        this.inventoryListenerSupport.inventoryListeners.remove(listener);
        LOG.debugf("Removed inventory listener [%s] for endpoint [%s]", listener, getMonitoredEndpoint());
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
     * {@link InventoryListener#resourcesAdded(InventoryEvent)}.
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
    public void measureAvails(Collection<MeasurementInstance<L, AvailType<L>>> instances,
            Consumer<AvailDataPoint> consumer) {

        status.assertRunning(getClass(), "measureAvails()");

        LOG.debugf("Checking [%d] avails for endpoint [%s]", instances.size(), getMonitoredEndpoint());

        S session = null;
        Driver<L> driver = null;

        try {
            session = openSession();
            driver = session.getDriver();
        } catch (Exception e) {
            LOG.errorCouldNotAccess(this, e);
        }

        try {
            for (MeasurementInstance<L, AvailType<L>> instance : instances) {
                Avail avail = null;
                if (driver != null) {
                    AttributeLocation<L> location = instance.getAttributeLocation();
                    try {
                        Object o = driver.fetchAttribute(location);
                        final Pattern pattern = instance.getType().getUpPattern();
                        if (o instanceof List<?>) {
                            /* aggregate */
                            List<?> list = (List<?>) o;
                            for (Object item : list) {
                                Avail a = toAvail(pattern, item);
                                if (avail == null) {
                                    avail = a;
                                } else {
                                    avail = (a == Avail.DOWN) ? Avail.DOWN : avail;
                                }
                            }
                        } else {
                            avail = toAvail(instance.getType().getUpPattern(), o);
                        }
                    } catch (Exception e) {
                        LOG.errorAvailCheckFailed(e);
                        avail = Avail.DOWN;
                    }
                } else {
                    avail = Avail.DOWN;
                }
                long ts = System.currentTimeMillis();
                String key = instance.getAssociatedMetricId();
                AvailDataPoint dataPoint = new AvailDataPoint(key, ts, avail,
                        getMonitoredEndpoint().getEndpointConfiguration().getTenantId());
                consumer.accept(dataPoint);
            }
        } catch (Exception e) {
            LOG.errorAvailCheckFailed(e);
        } finally {
            if (session != null) {
                try {
                    session.close();
                } catch (IOException e) {
                    LOG.tracef(e, "Failed to close session for endpoint [%s]", session.getEndpoint());
                }
            }
        }
    }

    @Override
    public void measureMetrics(Collection<MeasurementInstance<L, MetricType<L>>> instances,
            Consumer<MetricDataPoint> consumer) {

        status.assertRunning(getClass(), "measureMetrics()");

        LOG.debugf("Collecting [%d] metrics for endpoint [%s]", instances.size(), getMonitoredEndpoint());

        try (S session = openSession()) {
            Driver<L> driver = session.getDriver();
            for (MeasurementInstance<L, MetricType<L>> instance : instances) {
                AttributeLocation<L> location = instance.getAttributeLocation();
                Object o = driver.fetchAttribute(location);
                Object metricValue; // will be either a String or Double
                if (instance.getType().getMetricType() == org.hawkular.metrics.client.common.MetricType.STRING) {
                    StringBuilder svalue = new StringBuilder();
                    if (o instanceof List<?>) {
                        /* aggregate */
                        List<?> list = (List<?>) o;
                        for (Object item : list) {
                            if (svalue.length() > 0) {
                                svalue.append(",");
                            }
                            svalue.append(String.valueOf(item));
                        }
                    } else {
                        svalue.append(String.valueOf(o));
                    }
                    metricValue = svalue.toString();
                } else {
                    double dvalue = 0;
                    if (o instanceof List<?>) {
                        /* aggregate */
                        List<?> list = (List<?>) o;
                        for (Object item : list) {
                            double num = toDouble(item);
                            dvalue += num;
                        }
                    } else {
                        dvalue = toDouble(o);
                    }
                    metricValue = Double.valueOf(dvalue);
                }

                long ts = System.currentTimeMillis();
                String key = instance.getAssociatedMetricId();
                String tenantId = getMonitoredEndpoint().getEndpointConfiguration().getTenantId();
                MetricDataPoint dataPoint;

                if (instance.getType().getMetricType() == org.hawkular.metrics.client.common.MetricType.STRING) {
                    dataPoint = new StringMetricDataPoint(key, ts, (String) metricValue, tenantId);
                } else {
                    dataPoint = new NumericMetricDataPoint(key, ts, (Double) metricValue,
                            instance.getType().getMetricType(), tenantId);
                }
                consumer.accept(dataPoint);
            }
        } catch (Exception e) {
            LOG.errorCouldNotAccess(this, e);
        }

    }

    @Override
    public String generateAssociatedMetricId(MeasurementInstance<L, ? extends MeasurementType<L>> instance) {
        // the user can configure a metric's ID in one of two places - either in the metric definition itself or
        // in the endpoint configuration. The metric definition takes precedence in case a metric ID template
        // is provided in both.
        String generatedKey;
        EndpointConfiguration config = getMonitoredEndpoint().getEndpointConfiguration();
        String metricIdTemplate = instance.getType().getMetricIdTemplate();
        if (metricIdTemplate == null || metricIdTemplate.isEmpty()) {
            metricIdTemplate = config.getMetricIdTemplate();
        }
        if (metricIdTemplate == null || metricIdTemplate.isEmpty()) {
            generatedKey = instance.getID().getIDString();
        } else {
            generatedKey = replaceTokens(instance, config, metricIdTemplate);
        }
        return generatedKey;
    }

    @Override
    public Map<String, String> generateAssociatedMetricTags(
            MeasurementInstance<L, ? extends MeasurementType<L>> instance) {
        // Metric tags are configured in one of two places - either in the metric definition itself or in the endpoint
        // configuration. If tags are defined in both places, all the tags found in both places are associated with
        // the metric. If, however, both places define the same tag name, the metric definition takes precedence.
        Map<String, String> generatedTags;
        EndpointConfiguration config = getMonitoredEndpoint().getEndpointConfiguration();
        Map<String, String> tokenizedTags = new HashMap<>();
        if (config.getMetricTags() != null) {
            tokenizedTags.putAll(config.getMetricTags());
        }
        if (instance.getType().getMetricTags() != null) {
            tokenizedTags.putAll(instance.getType().getMetricTags());
        }

        if (tokenizedTags.isEmpty()) {
            generatedTags = Collections.emptyMap();
        } else {
            generatedTags = new HashMap<>(tokenizedTags.size());
            for (Map.Entry<String, String> tokenizedTag : tokenizedTags.entrySet()) {
                String name = replaceTokens(instance, config, tokenizedTag.getKey());
                String value = replaceTokens(instance, config, tokenizedTag.getValue());
                generatedTags.put(name, value);
            }
        }
        return generatedTags;
    }

    private String replaceTokens(MeasurementInstance<L, ?> instance, EndpointConfiguration config, String string) {
        MeasurementUnit units = null;
        if (instance.getType() instanceof MetricType) {
            units = ((MetricType<?>) instance.getType()).getMetricUnits();
        }

        return string
                .replaceAll("%FeedId", getFeedId())
                .replaceAll("%ManagedServerName", config.getName())
                .replaceAll("%ResourceName", instance.getResource().getName().getNameString())
                .replaceAll("%ResourceID", instance.getResource().getID().getIDString())
                .replaceAll("%MetricTypeName", instance.getType().getName().getNameString())
                .replaceAll("%MetricTypeID", instance.getType().getID().getIDString())
                .replaceAll("%MetricTypeUnits", units == null ? "" : units.toString())
                .replaceAll("%MetricInstanceID", instance.getID().getIDString());
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
        // nothing to do
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

    private double toDouble(Object valueObject) {
        double value;
        if (valueObject == null) {
            value = Double.NaN;
        } else if (valueObject instanceof Number) {
            value = ((Number) valueObject).doubleValue();
        } else {
            value = Double.valueOf(valueObject.toString());
        }
        return value;
    }

    private Avail toAvail(Pattern pattern, Object value) {
        if (pattern == null) {
            if (value instanceof Boolean) {
                return (Boolean) value ? Avail.UP : Avail.DOWN;
            } else if (value instanceof String) {
                return AvailType.getDefaultUpPattern().matcher((String) value).matches() ? Avail.UP : Avail.DOWN;
            } else if (value instanceof Number) {
                return ((Number) value).intValue() == 0 ? Avail.DOWN : Avail.UP;
            } else {
                throw new RuntimeException(
                        "Cannot handle an availability value of type [" + value.getClass().getName() + "]");
            }
        } else {
            return pattern.matcher(String.valueOf(value)).matches() ? Avail.UP : Avail.DOWN;
        }
    }
}
