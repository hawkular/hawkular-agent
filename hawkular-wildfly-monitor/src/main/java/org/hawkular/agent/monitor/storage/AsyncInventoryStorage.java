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
 * @author <a href="https://github.com/ppalaga">Peter Palaga</a>
 */
public class AsyncInventoryStorage implements InventoryStorage {
    private static class BulkPayloadBuilder {

        private Map<String, Map<String, List<AbstractElement.Blueprint>>> entities = new LinkedHashMap<>();
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

        public Map<String, Map<String, List<AbstractElement.Blueprint>>> build() {
            return entities;
        }

        private BulkPayloadBuilder entity(Entity.Blueprint newObject, Class<? extends Entity<?, ?>> entityClass) {

            String id = newObject.getId();
            if (!addedIds.contains(id)) {
                String path = newPathPrefix().get().toString();
                relationshipOrEntity(path, newObject, entityClass);
                addedIds.add(id);
            }

            return this;
        }

        private void metric(MeasurementInstance<?, ?, ?> measurementInstance) {

            String metricId = getInventoryId(measurementInstance);
            String metricTypeId = getInventoryId(measurementInstance.getMeasurementType());
            String metricTypePath = newPathPrefix().metricType(metricTypeId).get().toString();

            org.hawkular.inventory.api.model.Metric.Blueprint mPojo;
            mPojo = new org.hawkular.inventory.api.model.Metric.Blueprint(metricTypePath, metricId,
                    measurementInstance.getProperties());

            entity(mPojo, Metric.class);

        }

        private void metricType(MeasurementType measurementType) {
            MetricUnit mu = MetricUnit.NONE;
            MetricDataType metricDataType = MetricDataType.GAUGE;
            if (measurementType instanceof MetricType) {
                mu = MetricUnit.valueOf(((MetricType) measurementType).getMetricUnits().name());
                // we need to translate from metric API type to inventory API type
                switch (((MetricType) measurementType).getMetricType()) {
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

            // TODO: correctly map the MetricDataType from the MeasurementType instance type (avail from AvailType etc.)
            org.hawkular.inventory.api.model.MetricType.Blueprint blueprint = //
            new org.hawkular.inventory.api.model.MetricType.Blueprint(getInventoryId(measurementType), mu,
                    metricDataType, measurementType.getProperties());

            entity(blueprint, org.hawkular.inventory.api.model.MetricType.class);

        }

        private CanonicalPath.FeedBuilder newPathPrefix() {
            return CanonicalPath.of().tenant(tenantId).environment(environmentId).feed(feedId);
        }

        private BulkPayloadBuilder relationship(CanonicalPath parent, Relationship.Blueprint child) {
            return relationshipOrEntity(parent.toString(), child, Relationship.class);
        }

        private BulkPayloadBuilder relationshipOrEntity(String path, AbstractElement.Blueprint newObject,
                Class<? extends AbstractElement<?, ?>> entityClass) {

            Map<String, List<AbstractElement.Blueprint>> pathEntities = entities.get(path);
            if (pathEntities == null) {
                pathEntities = new LinkedHashMap<>();
                entities.put(path, pathEntities);
            }

            final String key = toKey(entityClass);
            List<AbstractElement.Blueprint> list = pathEntities.get(key);
            if (list == null) {
                list = new ArrayList<>();
                pathEntities.put(key, list);
            }
            list.add(newObject);

            return this;

        }

        /**
         * @param resource
         */
        public void resource(Resource<?, ?, ?, ?, ?> resource) {

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

            relationshipOrEntity(parentCanonicalPath.toString(), rPojo,
                    org.hawkular.inventory.api.model.Resource.class);

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

                relationshipOrEntity(resourceCanonicalPath.toString(), dataEntity, DataEntity.class);
            }

            Collection<? extends MetricInstance<?, ?, ?>> metricInstances = resource.getMetrics();
            for (MetricInstance<?, ?, ?> metric : metricInstances) {
                metric(metric);
                CanonicalPath metricPath = newPathPrefix().metric(getInventoryId(metric)).get();
                Relationship.Blueprint bp = new Relationship.Blueprint(Direction.outgoing, incorporates.toString(),
                        metricPath, metric.getProperties());
                relationship(resourceCanonicalPath, bp);
            }
            Collection<? extends AvailInstance<?, ?, ?>> availInstances = resource.getAvails();
            for (AvailInstance<?, ?, ?> metric : availInstances) {
                metric(metric);
                CanonicalPath metricPath = newPathPrefix().metric(getInventoryId(metric)).get();
                Relationship.Blueprint bp = new Relationship.Blueprint(Direction.outgoing, incorporates.toString(),
                        metricPath, metric.getProperties());
                relationship(resourceCanonicalPath, bp);
            }

        }

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

        private String toKey(Class<? extends AbstractElement<?, ?>> cl) {
            String src = cl.getSimpleName();
            return new StringBuilder(src.length()).append(Character.toLowerCase(src.charAt(0))).append(src.substring(1))
                    .toString();
        }
    }

    private class QueueFlush implements Runnable {

        /** @see java.lang.Runnable#run() */
        @Override
        public void run() {

            List<Resource<?, ?, ?, ?, ?>> resources = null;

            synchronized (queueLock) {
                if (!resourceQueue.isEmpty()) {
                    resources = resourceQueue;
                    resourceQueue = new ArrayList<>();
                    batchConter++;
                }
            }

            if (resources != null) {
                for (Resource<?, ?, ?, ?, ?> resource : resources) {
                    // FIXME environmentId should be configurable
                    BulkPayloadBuilder builder = new BulkPayloadBuilder(config.tenantId, "test",
                            AsyncInventoryStorage.this.selfId.getFullIdentifier());

                    ResourceType<?, ?, ?, ?> resourceType = resource.getResourceType();

                    builder.resourceType(resourceType);

                    log.debugf("Storing resource type in Inventory: %s", resourceType);

                    if (resource.getParent() != null) {
                        storeResource(resource.getParent());
                    }
                    builder.resource(resource);

                    log.debugf("Storing resource type in Inventory: %s", resource);

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
    private int resourceConter = 0;
    private int batchConter = 0;

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

    /** @see InventoryStorage#storeResource(org.hawkular.agent.monitor.inventory.Resource) */
    @Override
    public void storeResource(Resource<?, ?, ?, ?, ?> resource) {
        synchronized (queueLock) {
            resourceQueue.add(resource);
            resourceConter++;
        }
        executor.submit(new QueueFlush());
    }

    public void shutdown() {
        log.debugf("About to shut down. Stored [%d] resources in [%d] batches", resourceConter, batchConter);
        executor.shutdownNow();
    }

}
