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
package org.hawkular.agent.monitor.storage;

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
import org.hawkular.inventory.api.model.DataEntity;
import org.hawkular.inventory.api.model.Feed;
import org.hawkular.inventory.api.model.Feed.Blueprint;
import org.hawkular.inventory.api.model.InventoryStructure;
import org.hawkular.inventory.api.model.InventoryStructure.Builder;
import org.hawkular.inventory.api.model.InventoryStructure.ChildBuilder;
import org.hawkular.inventory.api.model.InventoryStructure.Offline;
import org.hawkular.inventory.api.model.Metric;
import org.hawkular.inventory.api.model.MetricDataType;
import org.hawkular.inventory.api.model.MetricUnit;
import org.hawkular.inventory.api.model.OperationType;
import org.hawkular.inventory.api.model.StructuredData;
import org.hawkular.inventory.paths.CanonicalPath;
import org.hawkular.inventory.paths.DataRole;

import com.codahale.metrics.Timer;
import com.fasterxml.jackson.core.type.TypeReference;
import com.squareup.okhttp.Call;
import com.squareup.okhttp.Request;
import com.squareup.okhttp.Response;

/**
 * An {@link InventoryStorage} that sends the resources submitted via {@link #storeResources(String, List)}
 * asynchronously to inventory.
 *
 * @author John Mazzitelli
 * @author <a href="https://github.com/ppalaga">Peter Palaga</a>
 */
public class AsyncInventoryStorage implements InventoryStorage {

    private abstract static class QueueElement {
        private final String tenantId;
        private final String feedId;
        private final Resource<?> resource;

        public QueueElement(String tenantId, String feedId, Resource<?> resource) {
            super();
            this.tenantId = tenantId;
            this.feedId = feedId;
            this.resource = resource;
        }

        public String getTenantId() {
            return tenantId;
        }

        public String getFeedId() {
            return feedId;
        }

        public Resource<?> getResource() {
            return resource;
        }

        @Override
        public String toString() {
            return String.format("%s:%s:%s", getClass().getSimpleName(), getFeedId(), getResource());
        }
    }

    private static class AddResourceQueueElement extends QueueElement {
        public AddResourceQueueElement(String tenantId, String feedId, Resource<?> resource) {
            super(tenantId, feedId, resource);
        }
    }

    private static class RemoveResourceQueueElement extends QueueElement {
        public RemoveResourceQueueElement(String tenantId, String feedId, Resource<?> resource) {
            super(tenantId, feedId, resource);
        }
    }

    /**
     * A builder of a {@link Map} structure that can be sent to {@code /sync} endpoint of Inventory.
     */
    private static class SyncPayloadBuilder {

        private final String feedId;
        private final String tenantId;

        private InventoryStructure.Builder<Feed.Blueprint> inventoryBuilder;

        // a set of ids already added
        private Set<String> addedIds;

        public SyncPayloadBuilder(String tenantId, String feedId) {
            this.tenantId = tenantId;
            this.feedId = feedId;

            this.inventoryBuilder = InventoryStructure.Offline.of(Feed.Blueprint.builder().withId(feedId).build());
            this.addedIds = new HashSet<>();
        }

        /**
         * @return a structure that can be sent to {@code /sync} endpoint of Inventory
         */
        public InventoryStructure<Feed.Blueprint> build() {
            Offline<Blueprint> result = inventoryBuilder.build();
            inventoryBuilder = InventoryStructure.Offline.of(Feed.Blueprint.builder().withId(feedId).build());
            addedIds = new HashSet<>();
            return result;
        }

        /**
         * Adds the given {@code resource}, its metrics, availabilities, and configurations
         * to {@link #inventoryBuilder}.
         *
         * @param resource the {@link Resource} to add
         * @return this builder
         */
        public <L> SyncPayloadBuilder resource(Resource<L> resource) {

            // resource
            String resourceId = getInventoryId(resource);

            // skip if we already did it
            if (!addedIds.add(resourceId)) {
                return this;
            }

            // the relationship between the resource and its parent (which should already be in inventory)
            StringBuilder parentPathStr = new StringBuilder();
            Resource<?> parent = resource.getParent();
            while (parent != null) {
                String resourceIdPath = "/" + Util.urlEncode(parent.getID().getIDString());
                parentPathStr.insert(0, resourceIdPath);
                parent = parent.getParent();
            }

            CanonicalPath parentCanonicalPath = parentPathStr.length() == 0 ? newPathPrefix().get()
                    : CanonicalPath.fromPartiallyUntypedString(parentPathStr.toString(), newPathPrefix().get(),
                            org.hawkular.inventory.api.model.Resource.class);
            // Relationship.Blueprint parentRelationshipBP = Relationship.Blueprint.builder()
            //         .withDirection(org.hawkular.inventory.api.Relationships.Direction.incoming)
            //         .withName(WellKnown.isParentOf.toString())
            //         .withOtherEnd(parentCanonicalPath)
            //         .build();

            // the resource blueprint itself
            String resourceTypePath = newPathPrefix().resourceType(getInventoryId(resource.getResourceType())).get()
                    .toString();
            String resourceName = resource.getName().getNameString();
            Map<String, Object> resourceProperties = resource.getProperties();

            org.hawkular.inventory.api.model.Resource.Blueprint resourceBP;
            resourceBP = org.hawkular.inventory.api.model.Resource.Blueprint.builder()
                    .withId(resourceId)
                    .withName(resourceName)
                    .withResourceTypePath(resourceTypePath)
                    .withProperties(resourceProperties)
                    .build();

            // now the resource's associated data - resource config, metrics, and avails
            ChildBuilder<Builder<Blueprint>> childBuilder = inventoryBuilder.startChild(resourceBP);

            try {
                // resource configuration
                Collection<ResourceConfigurationPropertyInstance<L>> resConfigInstances = resource
                        .getResourceConfigurationProperties();
                resourceConfigurations(resConfigInstances, childBuilder);

                // metrics and avails (which are just metrics, too)
                Collection<? extends Instance<?, ?>> metricInstances = resource.getMetrics();
                for (Instance<?, ?> metric : metricInstances) {
                    metric(metric, childBuilder);
                }
                Collection<? extends Instance<?, ?>> availInstances = resource.getAvails();
                for (Instance<?, ?> metric : availInstances) {
                    metric(metric, childBuilder);
                }

            } finally {
                childBuilder.end();
            }

            return this;
        }

        /**
         * Adds the given {@code resourceType}, its metric types (including availability) and operations to
         * {@link #inventoryBuilder} linking them properly together.
         *
         * @param resourceType the {@link ResourceType} to add
         * @return this builder
         */
        public SyncPayloadBuilder resourceType(ResourceType<?> resourceType) {

            // resource type
            String resourceTypeId = getInventoryId(resourceType);

            // skip if we already did it
            if (!addedIds.add(resourceTypeId)) {
                return this;
            }

            String resourceTypeName = resourceType.getName().getNameString();
            Map<String, Object> resourceTypeProperties = resourceType.getProperties();

            org.hawkular.inventory.api.model.ResourceType.Blueprint resourceTypeBP = //
                    org.hawkular.inventory.api.model.ResourceType.Blueprint.builder()
                            .withId(resourceTypeId)
                            .withName(resourceTypeName)
                            .withProperties(resourceTypeProperties)
                            .build();

            ChildBuilder<Builder<Blueprint>> childBuilder = inventoryBuilder.startChild(resourceTypeBP);

            try {
                // operations (which are children of the resource type)
                Collection<? extends Operation<?>> ops = resourceType.getOperations();
                for (Operation<?> op : ops) {
                    operation(op, childBuilder);
                }

                // resource configuration types (which are children of the resource type)
                Collection<? extends ResourceConfigurationPropertyType<?>> rcpts = //
                        resourceType.getResourceConfigurationPropertyTypes();
                resourceConfigurationTypes(rcpts, childBuilder);

            } finally {
                childBuilder.end();
            }

            // Metric types and avail types are at the root level, not children of the resource type itself.
            // I don't know if that's the right or wrong thing to do, but its how we've been doing it.

            // metric types
            Collection<? extends MetricType<?>> metricTypes = resourceType.getMetricTypes();
            for (MetricType<?> metricType : metricTypes) {
                metricType(metricType);
            }

            // avail types (which are just metric types, too)
            Collection<? extends AvailType<?>> availTypes = resourceType.getAvailTypes();
            for (AvailType<?> availType : availTypes) {
                metricType(availType);
            }

            return this;
        }

        private void metric(Instance<?, ?> metric, ChildBuilder<Builder<Blueprint>> childBuilder) {

            String metricId = getInventoryId(metric);
            String metricTypeId = getInventoryId(metric.getType());
            String metricTypePath = newPathPrefix().metricType(metricTypeId).get().toString();
            String metricName = metric.getName().getNameString();
            Map<String, Object> metricProperties = metric.getProperties();

            Metric.Blueprint blueprint = Metric.Blueprint.builder()
                    .withId(metricId)
                    .withMetricTypePath(metricTypePath)
                    .withName(metricName)
                    .withProperties(metricProperties)
                    .build();

            childBuilder.addChild(blueprint);
        }

        private void metricType(MeasurementType<?> metricType) {

            String metricTypeId = getInventoryId(metricType);

            // skip if we already did it
            if (!addedIds.add(metricTypeId)) {
                return;
            }

            MetricUnit metricUnit = MetricUnit.NONE;
            MetricDataType metricDataType;
            if (metricType instanceof MetricType) {
                metricUnit = MetricUnit.valueOf(((MetricType<?>) metricType).getMetricUnits().name());
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

            String metricTypeName = metricType.getName().getNameString();
            long metricTypeIntervalSecs = metricType.getInterval().seconds();
            Map<String, Object> metricTypeProperties = metricType.getProperties();

            org.hawkular.inventory.api.model.MetricType.Blueprint blueprint = org.hawkular.inventory.api.model.MetricType.Blueprint
                    .builder(metricDataType)
                    .withId(metricTypeId)
                    .withName(metricTypeName)
                    .withInterval(metricTypeIntervalSecs)
                    .withProperties(metricTypeProperties)
                    .withUnit(metricUnit)
                    .withType(metricDataType)
                    .build();

            inventoryBuilder.addChild(blueprint);
        }

        private void operation(Operation<?> operation, ChildBuilder<Builder<Blueprint>> childBuilder) {
            String operationId = getInventoryId(operation);
            String operationName = operation.getName().getNameString();
            Map<String, Object> operationProperties = operation.getProperties();

            OperationType.Blueprint blueprint = OperationType.Blueprint.builder()
                    .withId(operationId)
                    .withName(operationName)
                    .withProperties(operationProperties)
                    .build();

            childBuilder.addChild(blueprint);
        }

        private void resourceConfigurationTypes(Collection<? extends ResourceConfigurationPropertyType<?>> rcpts,
                ChildBuilder<Builder<Blueprint>> childBuilder) {
            if (!rcpts.isEmpty()) {
                StructuredData.MapBuilder structDataBuilder = StructuredData.get().map();
                for (ResourceConfigurationPropertyType<?> rcpt : rcpts) {
                    structDataBuilder.putString(rcpt.getID().getIDString(), rcpt.getName().getNameString());
                }

                DataEntity.Blueprint<DataRole> dataEntity = DataEntity.Blueprint.builder()
                        .withRole(DataRole.ResourceType.configurationSchema)
                        .withValue(structDataBuilder.build())
                        .build();

                childBuilder.addChild(dataEntity);
            }
        }

        private void resourceConfigurations(Collection<? extends ResourceConfigurationPropertyInstance<?>> rcpis,
                ChildBuilder<Builder<Blueprint>> childBuilder) {
            if (!rcpis.isEmpty()) {
                StructuredData.MapBuilder structDataBuilder = StructuredData.get().map();
                for (ResourceConfigurationPropertyInstance<?> rcpi : rcpis) {
                    structDataBuilder.putString(rcpi.getID().getIDString(), rcpi.getValue());
                }

                DataEntity.Blueprint<DataRole> dataEntity = DataEntity.Blueprint.builder()
                        .withRole(DataRole.Resource.configuration)
                        .withValue(structDataBuilder.build())
                        .build();

                childBuilder.addChild(dataEntity);
            }
        }

        /**
         * @return a new {@link CanonicalPath} made of {@link #tenantId} and {@link #feedId}
         */
        private CanonicalPath.FeedBuilder newPathPrefix() {
            return CanonicalPath.of().tenant(tenantId).feed(feedId);
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
                    List<QueueElement> qElements = new ArrayList<>();
                    qElements.add(sample);

                    // Something is going on with inventory (things are being added or removed or both).
                    // Because we want to do as much as possible in bulk, let's keep draining the queue
                    // as long as things keep going into the queue. We'll stop once it looks like nothing
                    // more is being added to the queue.
                    do {
                        Thread.sleep(2000);
                    } while (queue.drainTo(qElements) > 0);

                    AsyncInventoryStorage.this.diagnostics.getInventoryStorageBufferSize().dec(qElements.size());

                    try {
                        // We need to process contiguous groups of elements of the same types. So we need to
                        // split the queue items we just drained into contiguous groups. For example, if we drained
                        // queue elements in this order: "add resource A, add resource B, remove resource C,
                        // remove resource D, add resource E, remove resource F" then we need to group them such that
                        // contiguousGroups has 4 lists with list #1 being "add resource A, add resource B",
                        // list #2 being "remove resource C, remove resource D", list item #3 being "add resource E"
                        // and list item #4 being "remove resource F".
                        // Note also that because we could be performing inventory tasks with resources across different
                        // tenant IDs, we have to split into different groups when different tenant IDs are encountered.
                        List<List<QueueElement>> contiguousGroups = new ArrayList<>();
                        List<QueueElement> nextGroup = new ArrayList<>();
                        contiguousGroups.add(nextGroup); // seed it with an empty list
                        for (QueueElement qElement : qElements) {
                            if (!nextGroup.isEmpty()
                                    && ((!nextGroup.get(0).getClass().isInstance(qElement))
                                            || (!nextGroup.get(0).getTenantId().equals(qElement.getTenantId())))) {
                                nextGroup = new ArrayList<>();
                                contiguousGroups.add(nextGroup);
                            }
                            nextGroup.add(qElement);
                        }

                        // process the contiguous groups of queue elements
                        for (List<QueueElement> contiguousGroup : contiguousGroups) {
                            QueueElement firstElement = contiguousGroup.get(0);
                            if (firstElement instanceof AddResourceQueueElement) {
                                addResources(contiguousGroup);
                            } else if (firstElement instanceof RemoveResourceQueueElement) {
                                removeResources(contiguousGroup);
                            } else {
                                log.errorInvalidQueueElement(firstElement.getClass());
                            }
                        }
                    } catch (InterruptedException ie) {
                        throw ie;
                    } catch (Exception e) {
                        // don't do anything - we don't want to kill our thread by bubbling this exception up.
                        // A log message was already logged so there is nothing for us to do.
                    }
                }
            } catch (InterruptedException ie) {
            }
        }

        public void stopRunning() {
            this.keepRunning = false;
        }

        private void removeResources(List<QueueElement> removeQueueElements) throws Exception {
            log.debugf("Removing [%d] resources that were found in inventory work queue", removeQueueElements.size());

            StringBuilder url = Util.getContextUrlString(AsyncInventoryStorage.this.config.getUrl(),
                    AsyncInventoryStorage.this.config.getInventoryContext());
            for (QueueElement resourceElement : removeQueueElements) {
                StringBuilder deleteUrl = new StringBuilder(url.toString());
                List<Resource<?>> ancestries = getAncestry(resourceElement.getResource());
                String tenandId = resourceElement.getTenantId();
                StringBuilder resourcePath = new StringBuilder();
                Collections.reverse(ancestries);
                for (Resource<?> resource : ancestries) {
                    String resourceIdPath = "/" + Util.urlEncode(resource.getID().getIDString());
                    resourcePath.insert(0, resourceIdPath);
                }

                // The final URL should be in the form: entity/<resource_canonical_path>
                // for example: entity/t;hawkular/f;myfeed/r;resource_parent/r;resource_child

                CanonicalPath pathPrefix = CanonicalPath.of().tenant(tenandId).feed(feedId).get();
                CanonicalPath resourceCanonicalPath = CanonicalPath.fromPartiallyUntypedString(resourcePath.toString(),
                        pathPrefix, org.hawkular.inventory.api.model.Resource.class);

                deleteUrl.append("entity").append(resourceCanonicalPath.toString());

                Request request = AsyncInventoryStorage.this.httpClientBuilder
                        .buildJsonDeleteRequest(deleteUrl.toString(), getTenantHeader(resourceElement.getTenantId()));

                long start = System.currentTimeMillis(); // we don't store this time in our diagnostics
                Response response = AsyncInventoryStorage.this.httpClientBuilder
                        .getHttpClient()
                        .newCall(request)
                        .execute();

                try {
                    final long duration = System.currentTimeMillis() - start;

                    if (response.code() != 204 && response.code() != 404) {
                        // 204 means successfully deleted, 404 means it didn't exist in the first place.
                        // In either case, the resource no longer exists which is what we want;
                        // any other response code means it is an error and we didn't remove the resource.
                        throw new Exception("status-code=[" + response.code() + "], reason=["
                                + response.message() + "], url=[" + request.urlString() + "]");
                    }

                    log.debugf("Took [%d]ms to remove resource [%s]", duration, resourceElement.getResource());
                } finally {
                    response.body().close();
                }
            }

            return; // done removeResources
        }

        private void addResources(List<QueueElement> addQueueElements) throws Exception {
            log.debugf("Adding [%d] resources that were found in inventory work queue", addQueueElements.size());

            String tenantIdToUse = addQueueElements.get(0).getTenantId(); // all elements have same tenant id

            // build one big bulk request that will be used to add everything
            SyncPayloadBuilder builder = new SyncPayloadBuilder(tenantIdToUse, AsyncInventoryStorage.this.feedId);
            for (QueueElement elem : addQueueElements) {
                Resource<?> resource = elem.getResource();
                ResourceType<?> resourceType = resource.getResourceType();

                builder.resourceType(resourceType);

                if (resource.getParent() != null && !resource.getParent().isPersisted()) {
                    log.debugf("Parent [%s] of resource [%s] might not have been persisted. "
                            + "This may or may not cause problems storing to inventory.",
                            resource.getParent(), resource);
                }

                builder.resource(resource);

                log.debugf("Storing resource and eventually its type in inventory: [%s]", resource);
            }

            InventoryStructure<Blueprint> payload = builder.build();

            if (payload.getRoot() != null) {
                try {
                    StringBuilder url = Util.getContextUrlString(AsyncInventoryStorage.this.config.getUrl(),
                            AsyncInventoryStorage.this.config.getInventoryContext());
                    url.append("sync/");
                    url.append("f;").append(builder.feedId);
                    String jsonPayload = Util.toJson(payload);

                    Map<String, String> headers = getTenantHeader(tenantIdToUse);
                    log.tracef("About to send a sync insert request to inventory: headers=[%s] body=[%s]", headers,
                            jsonPayload);

                    // now send the REST request
                    Request request = AsyncInventoryStorage.this.httpClientBuilder.buildJsonPostRequest(url.toString(),
                            headers, jsonPayload);
                    Call call = AsyncInventoryStorage.this.httpClientBuilder.getHttpClient().newCall(request);
                    final Timer.Context timer = diagnostics.getInventoryStorageRequestTimer().time();
                    Response response = call.execute();

                    try {
                        final long durationNanos = timer.stop();
                        log.tracef("Received sync insert response from inventory: code [%d]", response.code());

                        // HTTP status of 201 means success, 409 means it already exists; anything else is an error
                        if (response.code() != 201 && response.code() != 409) {
                            throw new Exception("status-code=[" + response.code() + "], reason=["
                                    + response.message() + "], url=[" + request.urlString() + "]");
                        }

                        diagnostics.getInventoryRate().mark(addQueueElements.size());

                        final Reader responseBodyReader;
                        if (log.isDebugEnabled()) {
                            long durationMs = TimeUnit.MILLISECONDS.convert(durationNanos, TimeUnit.NANOSECONDS);
                            log.debugf("Took [%d]ms to store [%d] resources", durationMs, addQueueElements.size());
                            String body = response.body().string();
                            responseBodyReader = new StringReader(body);
                            log.tracef("Body of sync insert request response: %s", body);
                        } else {
                            responseBodyReader = response.body().charStream();
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
                                            break;
                                        default:
                                            log.errorFailedToStorePathToInventory(code, typeEntry.getKey(),
                                                    entityEntry.getKey());
                                            break;
                                    }
                                }
                            }
                        }
                    } finally {
                        response.body().close();
                    }

                    // mark our resource pojos to indicate we persisted everything to inventory
                    for (QueueElement elem : addQueueElements) {
                        elem.getResource().setPersisted(true);
                        elem.getResource().getResourceType().setPersisted(true);
                    }

                } catch (InterruptedException ie) {
                    throw ie;
                } catch (Exception e) {
                    diagnostics.getStorageErrorRate().mark(1);
                    log.errorFailedToStoreInventoryData(e);
                    throw new Exception("Cannot create resources or their resourceTypes", e);
                }
            }

            return; // end of method
        }

        /**
         * Returns a list of ancestors of the given resource.
         * The first in the returned list is the top-level parent, followed by the rest of the resource's parentage.
         * The final in the list is the given resource itself.
         *
         * @return resource's ancestry starting from the highest parent in the hierarchy down to the given resource.
         */
        private List<Resource<?>> getAncestry(Resource<?> resource) {
            ArrayList<Resource<?>> ancestry = new ArrayList<>();
            Resource<?> current = resource;
            while (current != null) {
                ancestry.add(current);
                current = current.getParent();
            }
            Collections.reverse(ancestry);
            return ancestry;
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

    private final String feedId;
    private final MonitorServiceConfiguration.StorageAdapterConfiguration config;
    private final HttpClientBuilder httpClientBuilder;
    private final Diagnostics diagnostics;
    private final ArrayBlockingQueue<QueueElement> queue;
    private final Worker worker;

    public AsyncInventoryStorage(
            String feedId,
            StorageAdapterConfiguration config,
            HttpClientBuilder httpClientBuilder,
            Diagnostics diagnostics) {
        super();
        this.feedId = feedId;
        this.config = config;
        this.httpClientBuilder = httpClientBuilder;
        this.diagnostics = diagnostics;
        this.queue = new ArrayBlockingQueue<>(10_000); // TODO make bufferSize configurable
        this.worker = new Worker(queue);
        this.worker.start();
    }

    public void shutdown() {
        log.debugf("Shutting down async inventory storage");
        worker.stopRunning();
        worker.interrupt();
        try {
            worker.join(60_000L); // wait for it to finish, but not forever
        } catch (InterruptedException ie) {
            // Preserve interrupt status
            Thread.currentThread().interrupt();
        }
    }

    @Override
    public <L> void resourcesAdded(InventoryEvent<L> event) {
        String tenantId = event.getSamplingService().getMonitoredEndpoint().getEndpointConfiguration().getTenantId();
        for (Resource<?> resource : event.getPayload()) {
            diagnostics.getInventoryStorageBufferSize().inc();
            queue.add(new AddResourceQueueElement(getTenantIdToUse(tenantId), feedId, resource));
        }
    }

    @Override
    public <L> void resourcesRemoved(InventoryEvent<L> event) {
        String tenantId = event.getSamplingService().getMonitoredEndpoint().getEndpointConfiguration().getTenantId();
        for (Resource<?> resource : event.getPayload()) {
            diagnostics.getInventoryStorageBufferSize().inc();
            queue.add(new RemoveResourceQueueElement(getTenantIdToUse(tenantId), feedId, resource));
        }
    }

    /**
     * Determines what tenant ID to use. If the given tenant ID is not null, it is returned.
     * If it is null, then the agent's tenant ID is returned.
     *
     * @param tenantId the tenant ID to use, or null which means use the agent tenant ID
     * @return the tenant ID to use
     */
    private String getTenantIdToUse(String tenantId) {
        return (tenantId != null) ? tenantId : config.getTenantId();
    }

    /**
     * Builds the header necessary for the tenant ID.
     *
     * @param tenantId the tenant ID string - this is the value of the returned map
     * @return the tenant header consisting of the header key and the value
     */
    private Map<String, String> getTenantHeader(String tenantId) {
        return Collections.singletonMap("Hawkular-Tenant", getTenantIdToUse(tenantId));
    }
}
