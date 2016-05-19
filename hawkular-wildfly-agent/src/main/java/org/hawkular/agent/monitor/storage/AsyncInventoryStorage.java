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
import org.hawkular.inventory.api.model.AbstractElement;
import org.hawkular.inventory.api.model.AbstractElement.Blueprint;
import org.hawkular.inventory.api.model.DataEntity;
import org.hawkular.inventory.api.model.Entity;
import org.hawkular.inventory.api.model.Metric;
import org.hawkular.inventory.api.model.MetricDataType;
import org.hawkular.inventory.api.model.MetricUnit;
import org.hawkular.inventory.api.model.OperationType;
import org.hawkular.inventory.api.model.Relationship;
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
            new org.hawkular.inventory.api.model.MetricType.Blueprint(
                    getInventoryId(metricType),
                    metricType.getName().getNameString(),
                    mu,
                    metricDataType,
                    metricType.getProperties(),
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

                DataEntity.Blueprint<DataRole.Resource> dataEntity = new DataEntity.Blueprint<>(
                        DataRole.Resource.configuration, structDataBuilder.build(), null);

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

                DataEntity.Blueprint<DataRole.ResourceType> dataEntity = new DataEntity.Blueprint<>(
                        DataRole.ResourceType.configurationSchema, structDataBuilder.build(), null);

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
                deleteUrl.append("feeds/").append(resourceElement.getFeedId()).append("/resources");
                for (Resource<?> resource : getAncestry(resourceElement.getResource())) {
                    deleteUrl.append('/');
                    deleteUrl.append(Util.urlEncode(resource.getID().getIDString()));
                }

                Request request = AsyncInventoryStorage.this.httpClientBuilder
                        .buildJsonDeleteRequest(deleteUrl.toString(), getTenantHeader(resourceElement.getTenantId()));

                long start = System.currentTimeMillis(); // we don't store this time in our diagnostics
                Response response = AsyncInventoryStorage.this.httpClientBuilder
                        .getHttpClient()
                        .newCall(request)
                        .execute();
                final long duration = System.currentTimeMillis() - start;

                if (response.code() != 204 && response.code() != 404) {
                    // 204 means successfully deleted, 404 means it didn't exist in the first place.
                    // In either case, the resource no longer exists which is what we want;
                    // any other response code means it is an error and we didn't remove the resource.
                    throw new Exception("status-code=[" + response.code() + "], reason=["
                            + response.message() + "], url=[" + request.urlString() + "]");
                }
                log.debugf("Took [%d]ms to remove resource [%s]", duration, resourceElement.getResource());
            }

            return; // done removeResources
        }

        private void addResources(List<QueueElement> addQueueElements) throws Exception {
            log.debugf("Adding [%d] resources that were found in inventory work queue", addQueueElements.size());

            String tenantIdToUse = addQueueElements.get(0).getTenantId(); // all elements have same tenant id

            // build one big bulk request that will be used to add everything
            BulkPayloadBuilder builder = new BulkPayloadBuilder(tenantIdToUse, AsyncInventoryStorage.this.feedId);
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

            Map<String, Map<String, List<AbstractElement.Blueprint>>> payload = builder.build();

            if (!payload.isEmpty()) {
                try {
                    StringBuilder url = Util.getContextUrlString(AsyncInventoryStorage.this.config.getUrl(),
                            AsyncInventoryStorage.this.config.getInventoryContext());
                    url.append("bulk");
                    String jsonPayload = Util.toJson(payload);

                    Map<String, String> headers = getTenantHeader(tenantIdToUse);
                    log.tracef("About to send a bulk insert request to inventory: headers=[%s] body=[%s]", headers, jsonPayload);

                    // now send the REST request
                    Request request = AsyncInventoryStorage.this.httpClientBuilder.buildJsonPostRequest(url.toString(),
                            headers, jsonPayload);
                    Call call = AsyncInventoryStorage.this.httpClientBuilder.getHttpClient().newCall(request);
                    final Timer.Context timer = diagnostics.getInventoryStorageRequestTimer().time();
                    Response response = call.execute();
                    final long durationNanos = timer.stop();
                    log.tracef("Received bulk insert response from inventory: code [%d]", response.code());

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
                        log.tracef("Body of bulk insert request response: %s", body);
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
