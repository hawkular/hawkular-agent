/*
 * Copyright 2015 Red Hat, Inc. and/or its affiliates
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
import java.util.List;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;

import org.hawkular.agent.monitor.api.Avail;
import org.hawkular.agent.monitor.api.InventoryEvent;
import org.hawkular.agent.monitor.api.InventoryListener;
import org.hawkular.agent.monitor.api.SamplingService;
import org.hawkular.agent.monitor.diagnostics.ProtocolDiagnostics;
import org.hawkular.agent.monitor.inventory.AttributeLocation;
import org.hawkular.agent.monitor.inventory.AvailType;
import org.hawkular.agent.monitor.inventory.MeasurementInstance;
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
import org.hawkular.agent.monitor.util.Consumer;
import org.hawkular.agent.monitor.util.ThreadFactoryGenerator;

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
                LOG.debugf("Firing inventory event for [%s] added/modified resources", resources.size());
                InventoryEvent<L> event = new InventoryEvent<L>(EndpointService.this, resources);
                for (InventoryListener inventoryListener : inventoryListeners) {
                    inventoryListener.resourcesAdded(event);
                }
            }
        }

        public void fireResourcesRemoved(List<Resource<L>> resources) {
            if (!resources.isEmpty()) {
                LOG.debugf("Firing inventory event for [%s] removed resources", resources.size());
                InventoryEvent<L> event = new InventoryEvent<L>(EndpointService.this, resources);
                for (InventoryListener inventoryListener : inventoryListeners) {
                    inventoryListener.resourceRemoved(event);
                }
            }
        }
    }

    private final MonitoredEndpoint endpoint;
    private final String feedId;
    private final InventoryListenerSupport inventoryListenerSupport = new InventoryListenerSupport();
    private final ResourceManager<L> resourceManager;
    private final ResourceTypeManager<L> resourceTypeManager;
    private final LocationResolver<L> locationResolver;
    private final ProtocolDiagnostics diagnostics;
    private final ExecutorService fullDiscoveryScanThreadPool;

    protected volatile ServiceStatus status = ServiceStatus.INITIAL;

    public EndpointService(String feedId, MonitoredEndpoint endpoint, ResourceTypeManager<L> resourceTypeManager,
            LocationResolver<L> locationResolver, ProtocolDiagnostics diagnostics) {
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
                "Hawkular WildFly Agent Full Discovery Scan");
        this.fullDiscoveryScanThreadPool = new ThreadPoolExecutor(1, 1, 0L, TimeUnit.MILLISECONDS,
                new LinkedBlockingQueue<Runnable>(1), threadFactory);
    }

    @Override
    public String getFeedId() {
        return feedId;
    }

    @Override
    public MonitoredEndpoint getEndpoint() {
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
        LOG.debugf("Added inventory listener [%s] for endpoint [%s]", listener, getEndpoint());
    }

    /**
     * Works only before {@link #start()} or after {@link #stop()}.
     *
     * @param listener to remove
     */
    public void removeInventoryListener(InventoryListener listener) {
        status.assertInitialOrStopped(getClass(), "removeInventoryListener()");
        this.inventoryListenerSupport.inventoryListeners.remove(listener);
        LOG.debugf("Removed inventory listener [%s] for endpoint [%s]", listener, getEndpoint());
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
                LOG.infof("Being asked to discover all resources for endpoint [%s]", getEndpoint());
                long duration = 0L;
                try (S session = openSession()) {
                    long start = System.currentTimeMillis();
                    Set<ResourceType<L>> rootTypes = resourceTypeManager.getRootResourceTypes();
                    for (ResourceType<L> rootType : rootTypes) {
                        discoverChildren(null, rootType, session);
                    }
                    duration = System.currentTimeMillis() - start;
                } catch (Exception e) {
                    LOG.errorCouldNotAccess(EndpointService.this, e);
                }
                resourceManager.logTreeGraph("Discovered all resources for [" + endpoint + "]", duration);
            }
        };

        try {
            this.fullDiscoveryScanThreadPool.execute(runnable);
        } catch (RejectedExecutionException ree) {
            LOG.debugf("Redundant full discovery scan will be ignored for endpoint [%s]", getEndpoint());
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
     */
    public void discoverChildren(L parentLocation, ResourceType<L> childType, S session) {
        status.assertRunning(getClass(), "discoverChildren()");
        LOG.debugf("Being asked to discover children of type [%s] under parent [%s] for endpoint [%s]",
                childType, parentLocation, getEndpoint());

        S sessionToUse = null;
        try {
            sessionToUse = (session == null) ? openSession() : session;

            /* FIXME: resourceManager should be write-locked here over find and add */
            List<Resource<L>> parents;
            if (parentLocation != null) {
                parents = resourceManager.findResources(parentLocation, sessionToUse.getLocationResolver());
            } else {
                parents = Arrays.asList((Resource<L>) null);
            }
            List<Resource<L>> added = new ArrayList<>();
            Discovery<L> discovery = new Discovery<>();
            for (Resource<L> parent : parents) {
                discovery.discoverChildren(parent, childType, sessionToUse, new Consumer<Resource<L>>() {
                    public void accept(Resource<L> resource) {
                        AddResult<L> result = resourceManager.addResource(resource);
                        if (result.getEffect() != AddResult.Effect.UNCHANGED) {
                            added.add(result.getResource());
                        }
                    }

                    @Override
                    public void report(Throwable e) {
                        LOG.errorCouldNotAccess(EndpointService.this, e);
                    }
                });
            }
            inventoryListenerSupport.fireResourcesAdded(Collections.unmodifiableList(added));
        } catch (Exception e) {
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

        LOG.debugf("Checking [%d] avails for endpoint [%s]", instances.size(), getEndpoint());

        try (S session = openSession()) {
            Driver<L> driver = session.getDriver();
            for (MeasurementInstance<L, AvailType<L>> instance : instances) {
                AttributeLocation<L> location = instance.getAttributeLocation();
                Object o = driver.fetchAttribute(location);
                final Pattern pattern = instance.getType().getUpPattern();
                Avail avail = null;
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
                long ts = System.currentTimeMillis();
                String key = generateMeasurementKey(instance);
                AvailDataPoint dataPoint = new AvailDataPoint(key, ts, avail);
                consumer.accept(dataPoint);
            }
        } catch (Exception e) {
            LOG.errorCouldNotAccess(this, e);
        }
    }

    @Override
    public void measureMetrics(Collection<MeasurementInstance<L, MetricType<L>>> instances,
            Consumer<MetricDataPoint> consumer) {

        status.assertRunning(getClass(), "measureMetrics()");

        LOG.debugf("Collecting [%d] metrics for endpoint [%s]", instances.size(), getEndpoint());

        try (S session = openSession()) {
            Driver<L> driver = session.getDriver();
            for (MeasurementInstance<L, MetricType<L>> instance : instances) {
                AttributeLocation<L> location = instance.getAttributeLocation();
                Object o = driver.fetchAttribute(location);
                double value = 0;
                if (o instanceof List<?>) {
                    /* aggregate */
                    List<?> list = (List<?>) o;
                    for (Object item : list) {
                        double num = toDouble(item);
                        value += num;
                    }
                } else {
                    value = toDouble(o);
                }
                long ts = System.currentTimeMillis();
                String key = generateMeasurementKey(instance);
                MetricDataPoint dataPoint = new MetricDataPoint(key, ts, value, instance.getType().getMetricType());
                consumer.accept(dataPoint);
            }
        } catch (Exception e) {
            LOG.errorCouldNotAccess(this, e);
        }

    }

    /**
     * Remove resources matching the given {@code location} and all their direct and indirect descendant resources.
     *
     * @param location a location that can contain wildcards
     */
    public void removeResources(L location) {
        status.assertRunning(getClass(), "removeResources()");
        try (S session = openSession()) {
            List<Resource<L>> removed = resourceManager.removeResources(location, session.getLocationResolver());
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
        return String.format("%s[%s]", getClass().getSimpleName(), getEndpoint());
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

    private String generateMeasurementKey(MeasurementInstance<L, ?> instance) {
        String key = instance.getID().getIDString();
        return key;
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
