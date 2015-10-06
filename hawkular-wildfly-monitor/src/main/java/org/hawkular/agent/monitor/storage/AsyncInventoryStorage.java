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
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;

import org.hawkular.agent.monitor.api.InventoryStorage;
import org.hawkular.agent.monitor.extension.MonitorServiceConfiguration;
import org.hawkular.agent.monitor.extension.MonitorServiceConfiguration.StorageAdapter;
import org.hawkular.agent.monitor.inventory.AvailInstance;
import org.hawkular.agent.monitor.inventory.AvailType;
import org.hawkular.agent.monitor.inventory.ID;
import org.hawkular.agent.monitor.inventory.MeasurementInstance;
import org.hawkular.agent.monitor.inventory.MeasurementType;
import org.hawkular.agent.monitor.inventory.MetricInstance;
import org.hawkular.agent.monitor.inventory.MetricType;
import org.hawkular.agent.monitor.inventory.NamedObject;
import org.hawkular.agent.monitor.inventory.Resource;
import org.hawkular.agent.monitor.inventory.ResourceConfigurationPropertyInstance;
import org.hawkular.agent.monitor.inventory.ResourceType;
import org.hawkular.agent.monitor.log.AgentLoggers;
import org.hawkular.agent.monitor.log.MsgLogger;
import org.hawkular.agent.monitor.service.ServerIdentifiers;
import org.hawkular.agent.monitor.service.ThreadFactoryGenerator;
import org.hawkular.agent.monitor.service.Util;
import org.hawkular.inventory.api.Relationships.Direction;
import org.hawkular.inventory.api.Resources;
import org.hawkular.inventory.api.model.AbstractElement;
import org.hawkular.inventory.api.model.AbstractElement.Blueprint;
import org.hawkular.inventory.api.model.CanonicalPath;
import org.hawkular.inventory.api.model.DataEntity;
import org.hawkular.inventory.api.model.Entity;
import org.hawkular.inventory.api.model.Metric;
import org.hawkular.inventory.api.model.MetricDataType;
import org.hawkular.inventory.api.model.MetricUnit;
import org.hawkular.inventory.api.model.Relationship;
import org.hawkular.inventory.api.model.StructuredData;

import com.fasterxml.jackson.core.type.TypeReference;
import com.squareup.okhttp.Request;
import com.squareup.okhttp.Response;

/**
 * An {@link InventoryStorage} that sends the resources submitted via {@link #storeResource(Resource)} asynchronously to
 * inventory.
 *
 * @author <a href="https://github.com/ppalaga">Peter Palaga</a>
 */
public class AsyncInventoryStorage implements InventoryStorage {

    /**
     * A builder of a {@link Map} structure that can be sent to {@code /bulk} endpoint of Inventory. See the docs inside
     * {@code org.hawkular.inventory.rest.RestBulk} in Inventory source tree.
     */
    private static class BulkPayloadBuilder {

        /** The result */
        private Map<String, Map<String, List<AbstractElement.Blueprint>>> result = new LinkedHashMap<>();

        /** A set of entity IDs already added to the currently built request */
        private Set<String> addedIds = new HashSet<>();

        private final String environmentId;
        private final String feedId;
        private final String tenantId;

        public BulkPayloadBuilder(String tenantId, String environmentId, String feedId) {
            super();
            this.tenantId = tenantId;
            this.environmentId = environmentId;
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
            String id = blueprint.getId();
            if (!addedIds.contains(id)) {
                String path = newPathPrefix().get().toString();
                relationshipOrEntity(path, entityClass, blueprint);
                addedIds.add(id);
            }
            return this;
        }

        /**
         * Adds the given {@code metric} to the {@link #result}.
         *
         * @param metric the {@link MeasurementInstance} to add
         */
        private void metric(MeasurementInstance<?, ?, ?> metric) {

            String metricId = getInventoryId(metric);
            String metricTypeId = getInventoryId(metric.getMeasurementType());
            String metricTypePath = newPathPrefix().metricType(metricTypeId).get().toString();

            Metric.Blueprint blueprint = new Metric.Blueprint(metricTypePath, metricId, metric.getProperties());

            entity(blueprint, Metric.class);

        }

        /**
         * Adds the given {@code metricType} to the {@link #result}.
         *
         * @param metricType the {@link MeasurementType} to add
         */
        private void metricType(MeasurementType metricType) {
            MetricUnit mu = MetricUnit.NONE;
            MetricDataType metricDataType = MetricDataType.GAUGE;
            if (metricType instanceof MetricType) {
                mu = MetricUnit.valueOf(((MetricType) metricType).getMetricUnits().name());
                // we need to translate from metric API type to inventory API type
                switch (((MetricType) metricType).getMetricType()) {
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
            }

            org.hawkular.inventory.api.model.MetricType.Blueprint blueprint = //
            new org.hawkular.inventory.api.model.MetricType.Blueprint(getInventoryId(metricType), mu, metricDataType,
                    metricType.getProperties());

            entity(blueprint, org.hawkular.inventory.api.model.MetricType.class);

        }

        /**
         * @return a new {@link CanonicalPath} made of {@link #tenantId}, {@link #environmentId} and {@link #feedId}
         */
        private CanonicalPath.FeedBuilder newPathPrefix() {
            return CanonicalPath.of().tenant(tenantId).environment(environmentId).feed(feedId);
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
        public BulkPayloadBuilder resource(Resource<?, ?, ?, ?, ?> resource) {

            // get the payload in JSON format
            org.hawkular.inventory.api.model.Resource.Blueprint rPojo;
            String resourceTypePath = newPathPrefix().resourceType(getInventoryId(resource.getResourceType())).get()
                    .toString();
            rPojo = new org.hawkular.inventory.api.model.Resource.Blueprint(getInventoryId(resource), resourceTypePath,
                    resource.getProperties());

            StringBuilder parentPath = new StringBuilder();
            Resource<?, ?, ?, ?, ?> parent = resource.getParent();
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

            // now that the resource is registered, immediately register its configuration
            Collection<? extends ResourceConfigurationPropertyInstance<?>> resConfigInstances = resource
                    .getResourceConfigurationProperties();
            if (resConfigInstances != null && !resConfigInstances.isEmpty()) {
                // get the payload in JSON format
                StructuredData.MapBuilder structDataBuilder = StructuredData.get().map();
                for (ResourceConfigurationPropertyInstance<?> resConfigInstance : resConfigInstances) {
                    structDataBuilder.putString(resConfigInstance.getID().getIDString(), resConfigInstance.getValue());
                }

                org.hawkular.inventory.api.model.DataEntity.Blueprint<Resources.DataRole> dataEntity = //
                new org.hawkular.inventory.api.model.DataEntity.Blueprint<>(Resources.DataRole.configuration,
                        structDataBuilder.build(), null);

                relationshipOrEntity(resourceCanonicalPath.toString(), DataEntity.class, dataEntity);
            }

            Collection<? extends MetricInstance<?, ?, ?>> metricInstances = resource.getMetrics();
            for (MetricInstance<?, ?, ?> metric : metricInstances) {
                metric(metric);
                CanonicalPath metricPath = newPathPrefix().metric(getInventoryId(metric)).get();
                Relationship.Blueprint bp = new Relationship.Blueprint(Direction.outgoing, incorporates.toString(),
                        metricPath, Collections.emptyMap());
                relationship(resourceCanonicalPath, bp);
            }
            Collection<? extends AvailInstance<?, ?, ?>> availInstances = resource.getAvails();
            for (AvailInstance<?, ?, ?> metric : availInstances) {
                metric(metric);
                CanonicalPath metricPath = newPathPrefix().metric(getInventoryId(metric)).get();
                Relationship.Blueprint bp = new Relationship.Blueprint(Direction.outgoing, incorporates.toString(),
                        metricPath, Collections.emptyMap());
                relationship(resourceCanonicalPath, bp);
            }

            return this;
        }

        /**
         * Adds the given {@code resourceType} and its metric types and availability types to {@link #result} linking
         * them properly together.
         *
         * @param resourceType the {@link ResourceType} to add
         * @return this builder
         */
        public BulkPayloadBuilder resourceType(ResourceType<?, ?, ?, ?> resourceType) {

            org.hawkular.inventory.api.model.ResourceType.Blueprint blueprint = //
            new org.hawkular.inventory.api.model.ResourceType.Blueprint(getInventoryId(resourceType),
                    resourceType.getProperties());
            entity(blueprint, org.hawkular.inventory.api.model.ResourceType.class);

            CanonicalPath parentPath = newPathPrefix().resourceType(getInventoryId(resourceType)).get();

            Collection<? extends MetricType> metricTypes = resourceType.getMetricTypes();
            for (MetricType metricType : metricTypes) {
                metricType(metricType);

                String metricTypeId = getInventoryId(metricType);

                CanonicalPath metricTypePath = newPathPrefix().metricType(metricTypeId).get();
                Relationship.Blueprint bp = new Relationship.Blueprint(Direction.outgoing, incorporates.toString(),
                        metricTypePath, Collections.emptyMap());
                relationship(parentPath, bp);
            }

            Collection<? extends AvailType> availTypes = resourceType.getAvailTypes();
            for (AvailType availType : availTypes) {
                metricType(availType);

                String metricTypeId = getInventoryId(availType);
                CanonicalPath metricTypePath = newPathPrefix().metricType(metricTypeId).get();
                Relationship.Blueprint bp = new Relationship.Blueprint(Direction.outgoing, incorporates.toString(),
                        metricTypePath, Collections.emptyMap());
                relationship(parentPath, bp);
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
            return new StringBuilder(src.length()).append(Character.toLowerCase(src.charAt(0))).append(src.substring(1))
                    .toString();
        }
    }

    /**
     * A simple {@link Runnable} to flush {@link AsyncInventoryStorage#resourceQueue}.
     */
    private class QueueFlush implements Runnable {

        /** @see java.lang.Runnable#run() */
        @Override
        public void run() {

            List<Resource<?, ?, ?, ?, ?>> resources = null;

            synchronized (queueLock) {
                if (!resourceQueue.isEmpty()) {
                    resources = resourceQueue;
                    resourceQueue = new ArrayList<>();
                    batchCounter++;
                }
            }

            if (resources != null) {
                for (Resource<?, ?, ?, ?, ?> resource : resources) {
                    // FIXME environmentId should be configurable
                    BulkPayloadBuilder builder = new BulkPayloadBuilder(config.tenantId, "test",
                            AsyncInventoryStorage.this.selfId.getFullIdentifier());

                    ResourceType<?, ?, ?, ?> resourceType = resource.getResourceType();

                    builder.resourceType(resourceType);

                    if (resource.getParent() != null) {
                        storeResource(resource.getParent());
                    }
                    builder.resource(resource);

                    log.debugf("Storing resource and eventually its type in Inventory: %s", resource);

                    Map<String, Map<String, List<AbstractElement.Blueprint>>> payload = builder.build();
                    if (!payload.isEmpty()) {
                        try {
                            StringBuilder url = Util.getContextUrlString(AsyncInventoryStorage.this.config.url,
                                    AsyncInventoryStorage.this.config.inventoryContext);
                            url = Util.convertToNonSecureUrl(url.toString());
                            url.append("bulk");
                            String jsonPayload = Util.toJson(payload);

                            log.tracef("About to send a bulk insert request to inventory: %s", jsonPayload);

                            // now send the REST request
                            Request request = AsyncInventoryStorage.this.httpClientBuilder
                                    .buildJsonPostRequest(url.toString(), null, jsonPayload);
                            Response response = AsyncInventoryStorage.this.httpClientBuilder.getHttpClient()
                                    .newCall(request).execute();

                            final Reader responseBodyReader;
                            if (log.isDebugEnabled()) {
                                String body = response.body().string();
                                responseBodyReader = new StringReader(body);
                                log.tracef("Got response for a bulk insert request: %s", body);
                            } else {
                                responseBodyReader = response.body().charStream();
                            }

                            // HTTP status of 201 means success, 409 means it already exists; anything else is an error
                            if (response.code() != 201) {
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
                                        case 201:
                                        case 409:
                                            /* expected */
                                            break;
                                        default:
                                            log.errorFailedToStorePathToInventory(code, typeEntry.getKey(),
                                                    entityEntry.getKey());
                                            break;
                                        }
                                    }
                                }
                            }

                        } catch (Exception e) {
                            log.errorFailedToStoreInventoryData(e);
                            throw new RuntimeException("Cannot create resource or its resourceType: " + resource, e);
                        }
                    }
                }
            }

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

    private final MonitorServiceConfiguration.StorageAdapter config;

    private final ExecutorService executor;

    private final HttpClientBuilder httpClientBuilder;
    private final Object queueLock = new Object();
    private volatile List<Resource<?, ?, ?, ?, ?>> resourceQueue = new ArrayList<>();

    /** A counter to help to roughly see how many resources are sent per batch */
    private int resourceCounter = 0;
    /** A counter to help to roughly see how many resources are sent per batch */
    private int batchCounter = 0;

    private final ServerIdentifiers selfId;

    public AsyncInventoryStorage(ServerIdentifiers selfId, StorageAdapter config, HttpClientBuilder httpClientBuilder) {
        super();
        this.selfId = selfId;
        this.config = config;
        this.httpClientBuilder = httpClientBuilder;

        final ThreadFactory factoryGenerator = ThreadFactoryGenerator.generateFactory(true,
                "Hawkular-Monitor-Discovered-Resources-Storage");
        this.executor = Executors.newFixedThreadPool(1, factoryGenerator);

    }

    /**
     * Puts the given {@code resource} to {@link #resourceQueue} and submits a new {@link QueueFlush} to the
     * {@link #executor}.
     *
     * @param resource to be stored
     */
    @Override
    public void storeResource(Resource<?, ?, ?, ?, ?> resource) {
        synchronized (queueLock) {
            resourceQueue.add(resource);
            resourceCounter++;
        }
        executor.submit(new QueueFlush());
    }

    /**
     * Stops the {@link #executor}.
     */
    public void shutdown() {
        log.debugf("Shutting down. Stored [%d] resources in [%d] batches", resourceCounter, batchCounter);
        executor.shutdownNow();
    }

}
