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
package org.hawkular.agent.monitor.storage;

import static org.hawkular.inventory.api.Relationships.WellKnown.incorporates;

import java.io.Reader;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.TimeUnit;

import org.hawkular.agent.monitor.api.InventoryEvent;
import org.hawkular.agent.monitor.api.InventoryStorage;
import org.hawkular.agent.monitor.diagnostics.Diagnostics;
import org.hawkular.agent.monitor.extension.MonitorServiceConfiguration;
import org.hawkular.agent.monitor.extension.MonitorServiceConfiguration.StorageAdapterConfiguration;
import org.hawkular.agent.monitor.inventory.AvailType;
import org.hawkular.agent.monitor.inventory.ID;
import org.hawkular.agent.monitor.inventory.Instance;
import org.hawkular.agent.monitor.inventory.MeasurementInstance;
import org.hawkular.agent.monitor.inventory.MeasurementType;
import org.hawkular.agent.monitor.inventory.MetricType;
import org.hawkular.agent.monitor.inventory.NamedObject;
import org.hawkular.agent.monitor.inventory.Operation;
import org.hawkular.agent.monitor.inventory.Resource;
import org.hawkular.agent.monitor.inventory.ResourceConfigurationPropertyInstance;
import org.hawkular.agent.monitor.inventory.ResourceConfigurationPropertyType;
import org.hawkular.agent.monitor.inventory.ResourceType;
import org.hawkular.agent.monitor.log.AgentLoggers;
import org.hawkular.agent.monitor.log.MsgLogger;
import org.hawkular.agent.monitor.util.Util;
import org.hawkular.inventory.api.Relationships.Direction;
import org.hawkular.inventory.api.ResourceTypes;
import org.hawkular.inventory.api.Resources;
import org.hawkular.inventory.api.model.AbstractElement;
import org.hawkular.inventory.api.model.AbstractElement.Blueprint;
import org.hawkular.inventory.api.model.CanonicalPath;
import org.hawkular.inventory.api.model.DataEntity;
import org.hawkular.inventory.api.model.Entity;
import org.hawkular.inventory.api.model.Metric;
import org.hawkular.inventory.api.model.MetricDataType;
import org.hawkular.inventory.api.model.MetricUnit;
import org.hawkular.inventory.api.model.OperationType;
import org.hawkular.inventory.api.model.Relationship;
import org.hawkular.inventory.api.model.StructuredData;

import com.codahale.metrics.Timer;
import com.fasterxml.jackson.core.type.TypeReference;
import com.squareup.okhttp.Request;
import com.squareup.okhttp.Response;

/**
 * An {@link InventoryStorage} that sends the resources submitted via {@link #storeResources(String, List)}
 * asynchronously to inventory.
 *
 * @author <a href="https://github.com/ppalaga">Peter Palaga</a>
 */
public class AsyncInventoryStorage implements InventoryStorage {

    private static class QueueElement {
        private final String feedId;
        private final Resource<?> resource;

        public QueueElement(String feedId, Resource<?> resource) {
            super();
            this.feedId = feedId;
            this.resource = resource;
        }

        public String getFeedId() {
            return feedId;
        }

        public Resource<?> getResource() {
            return resource;
        }
    }

    /**
     * A builder of a {@link Map} structure that can be sent to {@code /bulk} endpoint of Inventory. See the docs inside
     * {@code org.hawkular.inventory.rest.RestBulk} in Inventory source tree.
     */
    private static class BulkPayloadBuilder {

        /** The result */
        private Map<String, Map<String, List<AbstractElement.Blueprint>>> result = new LinkedHashMap<>();

        /** A set of entity IDs already added to the currently built request */
        private Set<String> addedIds = new HashSet<>();

        private final String feedId;
        private final String tenantId;

        public BulkPayloadBuilder(String tenantId, String feedId) {
            super();
            this.tenantId = tenantId;
            this.feedId = feedId;
        }

        /**
         * Returns a {@link Map} structure that can be sent to {@code /bulk} endpoint of Inventory. See the docs inside
         * {@code org.hawkular.inventory.rest.RestBulk} in Inventory source tree.
         *
         * @return a {@link Map} structure that can be sent to {@code /bulk} endpoint of Inventory
         */
        public Map<String, Map<String, List<AbstractElement.Blueprint>>> build() {
            Map<String, Map<String, List<Blueprint>>> result = this.result;
            this.result = new LinkedHashMap<>();
            this.addedIds = new HashSet<>();
            return result;
        }

        /**
         * Adds an {@link Entity.Blueprint} unless its ID is available in {@link #addedIds}.
         *
         * @param blueprint the blueprint to add
         * @param entityClass the class of the blueprint's {@link Entity}
         * @return this builder
         */
        private BulkPayloadBuilder entity(Entity.Blueprint blueprint, Class<? extends Entity<?, ?>> entityClass) {
            String parentPath = newPathPrefix().get().toString();
            return entity(blueprint, entityClass, parentPath);
        }

        /**
         * Adds an {@link Entity.Blueprint} unless its ID is available in {@link #addedIds}.
         *
         * @param blueprint the blueprint to add
         * @param entityClass the class of the blueprint's {@link Entity}
         * @param parentPath the inventory path of the parent entity to add the given {@code blueprint} under
         * @return this builder
         */
        private BulkPayloadBuilder entity(Entity.Blueprint blueprint, Class<? extends Entity<?, ?>> entityClass,
                String parentPath) {
            String id = blueprint.getId();
            if (!addedIds.contains(id)) {
                relationshipOrEntity(parentPath, entityClass, blueprint);
                addedIds.add(id);
            }
            return this;
        }

        /**
         * Adds the given {@code metric} to the {@link #result}.
         *
         * @param metric the {@link MeasurementInstance} to add
         */
        private void metric(Instance<?, ?> metric) {

            String metricId = getInventoryId(metric);
            String metricTypeId = getInventoryId(metric.getType());
            String metricTypePath = newPathPrefix().metricType(metricTypeId).get().toString();

            Metric.Blueprint blueprint = new Metric.Blueprint(metricTypePath, metricId,
                    metric.getName().getNameString(), metric.getProperties(), null, null);

            entity(blueprint, Metric.class);

        }

        /**
         * Adds the given {@code metricType} to the {@link #result}.
         *
         * @param metricType the {@link MeasurementType} to add
         */
        private void metricType(MeasurementType<?> metricType) {
            MetricUnit mu = MetricUnit.NONE;
            MetricDataType metricDataType;
            if (metricType instanceof MetricType) {
                mu = MetricUnit.valueOf(((MetricType<?>) metricType).getMetricUnits().name());
                // we need to translate from metric API type to inventory API type
                switch (((MetricType<?>) metricType).getMetricType()) {
                    case GAUGE:
                        metricDataType = MetricDataType.GAUGE;
                        break;
                    case COUNTER:
                        metricDataType = MetricDataType.COUNTER;
                        break;
                    default:
                        metricDataType = MetricDataType.GAUGE;
                        break;

                }
            } else if (metricType instanceof AvailType) {
                metricDataType = MetricDataType.AVAILABILITY;
            } else {
                throw new IllegalArgumentException(
                        "Invalid measurement type - please report this bug: " + metricType.getClass());
            }

            org.hawkular.inventory.api.model.MetricType.Blueprint blueprint = //
                    new org.hawkular.inventory.api.model.MetricType.Blueprint(getInventoryId(metricType),
                            metricType.getName().getNameString(), mu, metricDataType, metricType.getProperties(),
                            metricType.getInterval().seconds(),
                            null,
                            null);

            entity(blueprint, org.hawkular.inventory.api.model.MetricType.class);

        }

        /**
         * Adds the given {@code operation} to the {@link #result}.
         *
         * @param operation the {@link Operation} to add
         * @param resourceTypePath the inventory path of the resourceType to add the given {@code operation} under
         */
        private void operation(Operation<?> operation, String resourceTypePath) {
            OperationType.Blueprint blueprint = new OperationType.Blueprint(getInventoryId(operation),
                    operation.getName().getNameString(), operation.getProperties(), null, null);

            entity(blueprint, OperationType.class, resourceTypePath);
        }

        /**
         * @return a new {@link CanonicalPath} made of {@link #tenantId} and {@link #feedId}
         */
        private CanonicalPath.FeedBuilder newPathPrefix() {
            return CanonicalPath.of().tenant(tenantId).feed(feedId);
        }

        /**
         * Add the given {@link Relationship.Blueprint} to the {@link #result}.
         *
         * @param parent the path to link {@code child under}
         * @param child the {@link Relationship} to create
         */
        private void relationship(CanonicalPath parent, Relationship.Blueprint child) {
            relationshipOrEntity(parent.toString(), Relationship.class, child);
        }

        /**
         * Puts the given {@code blueprint} at a proper location into {@link #result}.
         *
         * @param path the first level key
         * @param entityClass the base for the second level key (see {@link #toKey(Class)})
         * @param blueprint the blueprint to add
         */
        private void relationshipOrEntity(String path, Class<? extends AbstractElement<?, ?>> entityClass,
                AbstractElement.Blueprint blueprint) {

            Map<String, List<AbstractElement.Blueprint>> pathEntities = result.get(path);
            if (pathEntities == null) {
                pathEntities = new LinkedHashMap<>();
                result.put(path, pathEntities);
            }

            final String key = toKey(entityClass);
            List<AbstractElement.Blueprint> list = pathEntities.get(key);
            if (list == null) {
                list = new ArrayList<>();
                pathEntities.put(key, list);
            }
            list.add(blueprint);

        }

        /**
         * Adds the given {@code resource}, its metrics, availabilities and configurations to {@link #result}.
         *
         * @param resource the {@link Resource} to add
         * @return this builder
         */
        public <L> BulkPayloadBuilder resource(Resource<L> resource) {

            // resource
            org.hawkular.inventory.api.model.Resource.Blueprint rPojo;
            String resourceTypePath = newPathPrefix().resourceType(getInventoryId(resource.getResourceType())).get()
                    .toString();
            rPojo = new org.hawkular.inventory.api.model.Resource.Blueprint(
                    getInventoryId(resource),
                    resource.getName().getNameString(),
                    resourceTypePath,
                    resource.getProperties(),
                    null,
                    null);

            StringBuilder parentPath = new StringBuilder();
            Resource<?> parent = resource.getParent();
            while (parent != null) {
                String resourceIdPath = "/" + Util.urlEncode(parent.getID().getIDString());
                parentPath.insert(0, resourceIdPath);
                parent = parent.getParent();
            }

            CanonicalPath parentCanonicalPath = parentPath.length() == 0 ? newPathPrefix().get()
                    : CanonicalPath.fromPartiallyUntypedString(parentPath.toString(), newPathPrefix().get(),
                            org.hawkular.inventory.api.model.Resource.class);

            relationshipOrEntity(parentCanonicalPath.toString(), org.hawkular.inventory.api.model.Resource.class,
                    rPojo);

            String resourcePath = parentPath.toString() + "/" + Util.urlEncode(resource.getID().getIDString());
            CanonicalPath resourceCanonicalPath = CanonicalPath.fromPartiallyUntypedString(resourcePath,
                    newPathPrefix().get(), org.hawkular.inventory.api.model.Resource.class);

            // resource configuration
            Collection<ResourceConfigurationPropertyInstance<L>> resConfigInstances = resource
                    .getResourceConfigurationProperties();
            if (resConfigInstances != null && !resConfigInstances.isEmpty()) {
                StructuredData.MapBuilder structDataBuilder = StructuredData.get().map();
                for (ResourceConfigurationPropertyInstance<?> resConfigInstance : resConfigInstances) {
                    structDataBuilder.putString(resConfigInstance.getID().getIDString(), resConfigInstance.getValue());
                }

                DataEntity.Blueprint<Resources.DataRole> dataEntity = new DataEntity.Blueprint<>(
                        Resources.DataRole.configuration, structDataBuilder.build(), null);

                relationshipOrEntity(resourceCanonicalPath.toString(), DataEntity.class, dataEntity);
            }

            // metrics and avails (which are just metrics, too)
            Collection<? extends Instance<?, ?>> metricInstances = resource.getMetrics();
            for (Instance<?, ?> metric : metricInstances) {
                metric(metric);
                CanonicalPath metricPath = newPathPrefix().metric(getInventoryId(metric)).get();
                Relationship.Blueprint bp = new Relationship.Blueprint(Direction.outgoing, incorporates.toString(),
                        metricPath, Collections.emptyMap());
                relationship(resourceCanonicalPath, bp);
            }
            Collection<? extends Instance<?, ?>> availInstances = resource.getAvails();
            for (Instance<?, ?> metric : availInstances) {
                metric(metric);
                CanonicalPath metricPath = newPathPrefix().metric(getInventoryId(metric)).get();
                Relationship.Blueprint bp = new Relationship.Blueprint(Direction.outgoing, incorporates.toString(),
                        metricPath, Collections.emptyMap());
                relationship(resourceCanonicalPath, bp);
            }

            return this;
        }

        /**
         * Adds the given {@code resourceType}, its metric types (including availability) and operations to
         * {@link #result} linking them properly together.
         *
         * @param resourceType the {@link ResourceType} to add
         * @return this builder
         */
        public BulkPayloadBuilder resourceType(ResourceType<?> resourceType) {

            // resource type
            String resourceTypeId = getInventoryId(resourceType);
            org.hawkular.inventory.api.model.ResourceType.Blueprint blueprint = //
                    new org.hawkular.inventory.api.model.ResourceType.Blueprint(resourceTypeId,
                            resourceType.getName().getNameString(), resourceType.getProperties(), null, null);
            entity(blueprint, org.hawkular.inventory.api.model.ResourceType.class);

            CanonicalPath parentPath = newPathPrefix().resourceType(getInventoryId(resourceType)).get();

            // metrics
            Collection<? extends MetricType<?>> metricTypes = resourceType.getMetricTypes();
            for (MetricType<?> metricType : metricTypes) {
                metricType(metricType);

                String metricTypeId = getInventoryId(metricType);

                CanonicalPath metricTypePath = newPathPrefix().metricType(metricTypeId).get();
                Relationship.Blueprint bp = new Relationship.Blueprint(Direction.outgoing, incorporates.toString(),
                        metricTypePath, Collections.emptyMap());
                relationship(parentPath, bp);
            }

            // avails (which are just metrics, too)
            Collection<? extends AvailType<?>> availTypes = resourceType.getAvailTypes();
            for (AvailType<?> availType : availTypes) {
                metricType(availType);

                String metricTypeId = getInventoryId(availType);
                CanonicalPath metricTypePath = newPathPrefix().metricType(metricTypeId).get();
                Relationship.Blueprint bp = new Relationship.Blueprint(Direction.outgoing, incorporates.toString(),
                        metricTypePath, Collections.emptyMap());
                relationship(parentPath, bp);
            }

            // operations
            String resourceTypePath = newPathPrefix().resourceType(resourceTypeId).get().toString();
            Collection<? extends Operation<?>> ops = resourceType.getOperations();
            for (Operation<?> op : ops) {
                operation(op, resourceTypePath);
            }

            // resource configuration
            Collection<? extends ResourceConfigurationPropertyType<?>> rcpts = //
                    resourceType.getResourceConfigurationPropertyTypes();

            if (rcpts != null && !rcpts.isEmpty()) {
                StructuredData.MapBuilder structDataBuilder = StructuredData.get().map();
                for (ResourceConfigurationPropertyType<?> rcpt : rcpts) {
                    structDataBuilder.putString(rcpt.getID().getIDString(), rcpt.getName().getNameString());
                }

                DataEntity.Blueprint<ResourceTypes.DataRole> dataEntity = new DataEntity.Blueprint<>(
                        ResourceTypes.DataRole.configurationSchema, structDataBuilder.build(), null);

                relationshipOrEntity(parentPath.toString(), DataEntity.class, dataEntity);
            }

            return this;
        }

        /**
         * Returns the simple class name of {@code cl} with first character made lower case.
         *
         * @param cl the class to use as a base for the result of this method
         * @return the simple class name of {@code cl} with first character made lower case
         */
        private static String toKey(Class<? extends AbstractElement<?, ?>> cl) {
            String src = cl.getSimpleName();
            return new StringBuilder(src.length())
                    .append(Character.toLowerCase(src.charAt(0)))
                    .append(src.substring(1))
                    .toString();
        }
    }

    private class Worker extends Thread {
        private final ArrayBlockingQueue<QueueElement> queue;
        private boolean keepRunning = true;

        public Worker(ArrayBlockingQueue<QueueElement> queue) {
            super("Hawkular-WildFly-Agent-Inventory-Storage");
            this.queue = queue;
        }

        public void run() {
            try {
                while (keepRunning) {
                    // batch processing
                    QueueElement sample = queue.take();
                    List<QueueElement> resources = new ArrayList<>();
                    resources.add(sample);
                    queue.drainTo(resources);

                    AsyncInventoryStorage.this.diagnostics.getInventoryStorageBufferSize().dec(resources.size());

                    try {
                        storeAllResources(resources);
                    } catch (InterruptedException ie) {
                        throw ie;
                    } catch (Exception e) {
                        // don't do anything - we don't want to kill our thread by bubbling this exception up.
                        // A log message was already logged in storeAllResources, so there is nothing for us to do.
                    }
                }
            } catch (InterruptedException ie) {
            }
        }

        public void stopRunning() {
            this.keepRunning = false;
        }

        private void storeAllResources(List<QueueElement> resources) throws Exception {
            if (resources != null && !resources.isEmpty()) {
                for (QueueElement elem : resources) {
                    Resource<?> resource = elem.getResource();
                    BulkPayloadBuilder builder = new BulkPayloadBuilder(config.getTenantId(), elem.getFeedId());
                    ResourceType<?> resourceType = resource.getResourceType();

                    builder.resourceType(resourceType);

                    if (resource.getParent() != null && !resource.getParent().isPersisted()) {
                        log.errorf("Cannot persist a resource until its parent is persisted: [%s]", resource);
                        continue;
                    }

                    builder.resource(resource);

                    log.debugf("Storing resource and eventually its type in inventory: [%s]", resource);

                    Map<String, Map<String, List<AbstractElement.Blueprint>>> payload = builder.build();
                    if (!payload.isEmpty()) {
                        try {
                            StringBuilder url = Util.getContextUrlString(AsyncInventoryStorage.this.config.getUrl(),
                                    AsyncInventoryStorage.this.config.getInventoryContext());
                            url.append("bulk");
                            String jsonPayload = Util.toJson(payload);

                            log.tracef("About to send a bulk insert request to inventory: [%s]", jsonPayload);

                            // now send the REST request
                            Request request = AsyncInventoryStorage.this.httpClientBuilder
                                    .buildJsonPostRequest(url.toString(), null, jsonPayload);

                            final Timer.Context timer = diagnostics.getInventoryStorageRequestTimer().time();
                            Response response = AsyncInventoryStorage.this.httpClientBuilder
                                    .getHttpClient()
                                    .newCall(request)
                                    .execute();
                            final long durationNanos = timer.stop();

                            final Reader responseBodyReader;

                            if (log.isDebugEnabled()) {
                                long durationMs = TimeUnit.MILLISECONDS.convert(durationNanos, TimeUnit.NANOSECONDS);
                                log.debugf("Took [%d]ms to store resource [%s]", durationMs, resource.getName());
                                String body = response.body().string();
                                responseBodyReader = new StringReader(body);
                                log.tracef("Body of bulk insert request response: %s", body);
                            } else {
                                responseBodyReader = response.body().charStream();
                            }

                            // HTTP status of 201 means success, 409 means it already exists; anything else is an error
                            if (response.code() != 201 && response.code() != 409) {
                                throw new Exception("status-code=[" + response.code() + "], reason=["
                                        + response.message() + "], url=[" + request.urlString() + "]");
                            }

                            TypeReference<LinkedHashMap<String, LinkedHashMap<String, Object>>> typeRef = //
                                    new TypeReference<LinkedHashMap<String, LinkedHashMap<String, Object>>>() {
                                    };
                            LinkedHashMap<String, LinkedHashMap<String, Object>> responses = Util
                                    .fromJson(responseBodyReader, typeRef);
                            for (Entry<String, LinkedHashMap<String, Object>> typeEntry : responses.entrySet()) {
                                for (Entry<String, Object> entityEntry : typeEntry.getValue().entrySet()) {
                                    Object rawCode = entityEntry.getValue();
                                    if (rawCode instanceof Integer) {
                                        int code = ((Integer) rawCode).intValue();
                                        switch (code) {
                                            case 201: // success
                                            case 409: // already existed
                                                resource.getResourceType().setPersisted(true);
                                                resource.setPersisted(true);
                                                break;
                                            default:
                                                log.errorFailedToStorePathToInventory(code, typeEntry.getKey(),
                                                        entityEntry.getKey());
                                                break;
                                        }
                                    }
                                }
                            }

                            diagnostics.getInventoryRate().mark(1); // we processed 1 resource and its type

                        } catch (InterruptedException ie) {
                            throw ie;
                        } catch (Exception e) {
                            diagnostics.getStorageErrorRate().mark(1);
                            log.errorFailedToStoreInventoryData(e);
                            throw new Exception("Cannot create resource or its resourceType: " + resource, e);
                        }
                    }
                }
            }

            return; // end of method
        }
    }

    private static final MsgLogger log = AgentLoggers.getLogger(AsyncInventoryStorage.class);

    private static String getInventoryId(NamedObject no) {
        String id;
        if (no.getID().equals(ID.NULL_ID)) {
            id = no.getName().getNameString();
        } else {
            id = no.getID().getIDString();
        }
        return id;
    }

    private final MonitorServiceConfiguration.StorageAdapterConfiguration config;
    private final HttpClientBuilder httpClientBuilder;
    private final Diagnostics diagnostics;
    private final ArrayBlockingQueue<QueueElement> queue;
    private final Worker worker;

    public AsyncInventoryStorage(StorageAdapterConfiguration config,
            HttpClientBuilder httpClientBuilder,
            Diagnostics diagnostics) {
        super();
        this.config = config;
        this.httpClientBuilder = httpClientBuilder;
        this.diagnostics = diagnostics;
        this.queue = new ArrayBlockingQueue<>(1000); // TODO make bufferSize configurable (it is 1000 right now)
        this.worker = new Worker(queue);
        this.worker.start();
    }

    public void shutdown() {
        log.debugf("Shutting down async inventory storage");
        worker.stopRunning();
    }

    @Override
    public <L> void discoverAllFinished(InventoryEvent<L> event) {
        String feedId = event.getFeedId();
        for (Resource<?> resource : event.getPayload()) {
            diagnostics.getInventoryStorageBufferSize().inc();
            queue.add(new QueueElement(feedId, resource));
        }
    }

    @Override
    public <L> void resourcesAdded(InventoryEvent<L> event) {
        String feedId = event.getFeedId();
        for (Resource<?> resource : event.getPayload()) {
            diagnostics.getInventoryStorageBufferSize().inc();
            queue.add(new QueueElement(feedId, resource));
        }
    }

    @Override
    public <L> void resourceRemoved(InventoryEvent<L> event) {
        log.warnf("[%s].removeResource() needs to be implemented", getClass().getName());
    }

}
