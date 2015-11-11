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

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
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
import org.hawkular.agent.monitor.inventory.ResourceType;
import org.hawkular.agent.monitor.inventory.ResourceTypeManager;
import org.hawkular.agent.monitor.log.AgentLoggers;
import org.hawkular.agent.monitor.log.MsgLogger;
import org.hawkular.agent.monitor.service.ServiceStatus;
import org.hawkular.agent.monitor.storage.AvailDataPoint;
import org.hawkular.agent.monitor.storage.MetricDataPoint;
import org.hawkular.agent.monitor.util.Consumer;

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
    private class InventoryListenerSupport {
        private final List<InventoryListener> inventoryListeners = new ArrayList<>();

        public InventoryListenerSupport() {
            super();
        }

        public void fireDiscoverAllFinished(List<Resource<L>> resources) {
            InventoryEvent<L> event = new InventoryEvent<L>(feedId, endpoint, EndpointService.this, resources);
            for (InventoryListener inventoryListener : inventoryListeners) {
                inventoryListener.discoverAllFinished(event);
            }
        }

        public void fireResourcesAdded(List<Resource<L>> resources) {
            InventoryEvent<L> event = new InventoryEvent<L>(feedId, endpoint, EndpointService.this, resources);
            for (InventoryListener inventoryListener : inventoryListeners) {
                inventoryListener.resourcesAdded(event);
            }
        }

        public void fireResourcesRemoved(List<Resource<L>> resources) {
            InventoryEvent<L> event = new InventoryEvent<L>(feedId, endpoint, EndpointService.this, resources);
            for (InventoryListener inventoryListener : inventoryListeners) {
                inventoryListener.resourceRemoved(event);
            }
        }

    }

    private static final MsgLogger log = AgentLoggers.getLogger(EndpointService.class);

    private static double toDouble(Object valueObject) {
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

    protected final MonitoredEndpoint endpoint;
    protected final String feedId;
    protected final InventoryListenerSupport inventoryListenerSupport = new InventoryListenerSupport();
    protected final ResourceManager<L> resourceManager;
    protected final ResourceTypeManager<L> resourceTypeManager;
    protected final LocationResolver<L> locationResolver;

    protected volatile ServiceStatus status = ServiceStatus.INITIAL;
    protected final ProtocolDiagnostics diagnostics;

    public EndpointService(String feedId, MonitoredEndpoint endpoint, ResourceTypeManager<L> resourceTypeManager,
            LocationResolver<L> locationResolver, ProtocolDiagnostics diagnostics) {
        super();
        this.feedId = feedId;
        this.endpoint = endpoint;
        this.resourceManager = new ResourceManager<>();
        this.resourceTypeManager = resourceTypeManager;
        this.locationResolver = locationResolver;
        this.diagnostics = diagnostics;
    }

    /**
     * Works only before {@link #start()} or after {@link #stop()}.
     *
     * @param listener to add
     */
    public void addInventoryListener(InventoryListener listener) {
        status.assertInitialOrStopped(getClass(), "addInventoryListener()");
        this.inventoryListenerSupport.inventoryListeners.add(listener);
        log.debugf("Added inventory listener [%s] for endpoint [%s]", listener, getEndpoint());
    }

    /**
     * Opens a new protocl specific {@link Session} - do not forget to close it!
     *
     * @return a new {@link Session}
     */
    public abstract S openSession();

    /**
     * Discovers all resources, puts them to {@link #resourceManager} and triggers
     * {@link InventoryListener#discoverAllFinished(InventoryEvent)}.
     */
    public void discoverAll() {
        status.assertRunning(getClass(), "discoverAll()");
        doDiscoverAll();
    }

    /**
     * Discovers child resources of the given {@code parentLocation}, puts them to {@link #resourceManager} and triggers
     * {@link InventoryListener#resourcesAdded(InventoryEvent)}.
     *
     * @param parentLocation the location under which the discovery should happen
     * @param childType the resources of this type will be discovered.
     */
    public void discoverChildren(L parentLocation, ResourceType<L> childType) {
        status.assertRunning(getClass(), "discoverChildren()");
        Discovery<L> discovery = new Discovery<>();
        try (S session = openSession()) {
            /* FIXME: resourceManager should be write-locked here over find and add */
            List<Resource<L>> parents = resourceManager.findResources(parentLocation, session.getLocationResolver());
            List<Resource<L>> added = new ArrayList<>();
            for (Resource<L> parent : parents) {
                discovery.discoverChildren(parent, childType, session, new Consumer<Resource<L>>() {
                    public void accept(Resource<L> resource) {
                        resourceManager.addResource(resource);
                        added.add(resource);
                    }

                    @Override
                    public void report(Throwable e) {
                        log.errorCouldNotAccess(endpoint, e);
                    }
                });
            }
            inventoryListenerSupport.fireResourcesAdded(Collections.unmodifiableList(added));
        } catch (Exception e) {
            log.errorCouldNotAccess(endpoint, e);
        }
    }

    /**
     * Calls {@link Discovery#discoverAllResources(Session, Consumer)}, buffers the discovered resources in a list and
     * when the discovery is finished, submits the dicovered resources to {@link #resourceManager} using
     * {@link ResourceManager#replaceResources(List)}.
     */
    private void doDiscoverAll() {
        log.debugf("Being asked to discover all resources for endpoint [%s]", getEndpoint());

        Discovery<L> discovery = new Discovery<>();

        long duration = 0L;
        final ArrayList<Resource<L>> resources = new ArrayList<>();
        try (S session = openSession()) {
            long start = System.currentTimeMillis();

            discovery.discoverAllResources(session, new Consumer<Resource<L>>() {
                public void accept(Resource<L> resource) {
                    resources.add(resource);
                }

                @Override
                public void report(Throwable e) {
                    log.errorCouldNotAccess(endpoint, e);
                }
            });

            duration = System.currentTimeMillis() - start;

        } catch (Exception e) {
            log.errorCouldNotAccess(endpoint, e);
        }

        resourceManager.replaceResources(resources);
        resourceManager.logTreeGraph("Discovered all resources for [" + endpoint + "]", duration);

        /* there should be a listener for syncing with the remote inventory and also one to start the collection
         * of metrics */
        inventoryListenerSupport.fireDiscoverAllFinished(resources);
    }

    private String generateMeasurementKey(AttributeLocation<L> location) {
        String loc = location.toString();
        StringBuilder stringBuilder = new StringBuilder(feedId.length() + 1 + loc.length());
        return stringBuilder
                .append(feedId)
                .append('.')
                .append(loc)
                .toString();
    }

    @Override
    public MonitoredEndpoint getEndpoint() {
        return endpoint;
    }

    @Override
    public String getFeedId() {
        return feedId;
    }

    public ResourceManager<L> getResourceManager() {
        return resourceManager;
    }

    /**
     * @return the {@link ResourceTypeManager} used by this endpoint
     */
    public ResourceTypeManager<L> getResourceTypeManager() {
        return resourceTypeManager;
    }

    @Override
    public void measureAvails(Collection<MeasurementInstance<L, AvailType<L>>> instances,
            Consumer<AvailDataPoint> consumer) {

        status.assertRunning(getClass(), "measureAvails()");

        log.debugf("Checking [%d] avails for endpoint [%s]", instances.size(), getEndpoint());

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
                String key = generateMeasurementKey(location);
                AvailDataPoint dataPoint = new AvailDataPoint(key, ts, avail);
                consumer.accept(dataPoint);
            }
        } catch (Exception e) {
            log.errorCouldNotAccess(endpoint, e);
        }
    }

    @Override
    public void measureMetrics(Collection<MeasurementInstance<L, MetricType<L>>> instances,
            Consumer<MetricDataPoint> consumer) {

        status.assertRunning(getClass(), "measureMetrics()");

        log.debugf("Collecting [%d] metrics for endpoint [%s]", instances.size(), getEndpoint());

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
                String key = generateMeasurementKey(location);
                MetricDataPoint dataPoint = new MetricDataPoint(key, ts, value, instance.getType().getMetricType());
                consumer.accept(dataPoint);
            }
        } catch (Exception e) {
            log.errorCouldNotAccess(endpoint, e);
        }

    }

    /**
     * Works only before {@link #start()} or after {@link #stop()}.
     *
     * @param listener to remove
     */
    public void removeInventoryListener(InventoryListener listener) {
        status.assertInitialOrStopped(getClass(), "removeInventoryListener()");
        this.inventoryListenerSupport.inventoryListeners.remove(listener);
        log.debugf("Removed inventory listener [%s] for endpoint [%s]", listener, getEndpoint());
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
            log.errorCouldNotAccess(endpoint, e);
        }

    }

    public final void start() {
        status.assertInitialOrStopped(getClass(), "start()");
        status = ServiceStatus.STARTING;
        // scan all
        doDiscoverAll();

        // keep polling/listening for changes
        status = ServiceStatus.RUNNING;

        log.debugf("Started endpoint service for [%s]", getEndpoint());
    }

    public void stop() {
        status.assertRunning(getClass(), "stop()");
        status = ServiceStatus.STOPPING;

        // stop polling/listening for changes

        status = ServiceStatus.STOPPED;

        log.debugf("Stopped endpoint service for [%s]", getEndpoint());
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
