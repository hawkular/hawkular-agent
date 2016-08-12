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

import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;

import org.hawkular.agent.monitor.api.DiscoveryEvent;
import org.hawkular.agent.monitor.api.InventoryEvent;
import org.hawkular.agent.monitor.api.InventoryStorage;
import org.hawkular.agent.monitor.diagnostics.Diagnostics;
import org.hawkular.agent.monitor.extension.MonitorServiceConfiguration;
import org.hawkular.agent.monitor.extension.MonitorServiceConfiguration.EndpointConfiguration;
import org.hawkular.agent.monitor.extension.MonitorServiceConfiguration.StorageAdapterConfiguration;
import org.hawkular.agent.monitor.inventory.AvailType;
import org.hawkular.agent.monitor.inventory.ID;
import org.hawkular.agent.monitor.inventory.Instance;
import org.hawkular.agent.monitor.inventory.MeasurementType;
import org.hawkular.agent.monitor.inventory.MetricType;
import org.hawkular.agent.monitor.inventory.MonitoredEndpoint;
import org.hawkular.agent.monitor.inventory.NamedObject;
import org.hawkular.agent.monitor.inventory.Operation;
import org.hawkular.agent.monitor.inventory.OperationParam;
import org.hawkular.agent.monitor.inventory.Resource;
import org.hawkular.agent.monitor.inventory.ResourceConfigurationPropertyInstance;
import org.hawkular.agent.monitor.inventory.ResourceConfigurationPropertyType;
import org.hawkular.agent.monitor.inventory.ResourceManager;
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
import org.hawkular.inventory.api.model.Metric;
import org.hawkular.inventory.api.model.MetricDataType;
import org.hawkular.inventory.api.model.MetricUnit;
import org.hawkular.inventory.api.model.OperationType;
import org.hawkular.inventory.api.model.StructuredData;
import org.hawkular.inventory.api.model.StructuredData.InnerMapBuilder;
import org.hawkular.inventory.api.model.StructuredData.MapBuilder;
import org.hawkular.inventory.paths.CanonicalPath;
import org.hawkular.inventory.paths.DataRole;

import com.codahale.metrics.Timer;
import com.squareup.okhttp.Call;
import com.squareup.okhttp.Request;
import com.squareup.okhttp.Response;

/**
 * An {@link InventoryStorage} that syncs inventory that has been discovered.
 *
 * @author John Mazzitelli
 * @author <a href="https://github.com/ppalaga">Peter Palaga</a>
 */
public class AsyncInventoryStorage implements InventoryStorage {

    /**
     * A builder of inventory JSON structures that can be sent to {@code /sync} endpoint of Hawkular-Inventory.
     */
    private static class SyncPayloadBuilder<L> {

        private final String feedId;
        private final String tenantId;

        private enum IdType {
            RESOURCE_TYPE, METRIC_TYPE
        }

        private final Map<IdType, Set<String>> addedIds; // tracks what we already added so we don't add duplicates

        public SyncPayloadBuilder(String tenantId, String feedId) {
            this.tenantId = tenantId;
            this.feedId = feedId;
            this.addedIds = new HashMap<>();
        }

        /**
         * Builds a sync structure that can be sent to {@code /sync} endpoint of Inventory
         * in order to sync resources in the given resource manager. No types are synced.
         *
         * @param resourceManager the resources to be sync'ed
         * @return sync structure
         */
        public InventoryStructure<Feed.Blueprint> build(ResourceManager<L> resourceManager) {

            Builder<Blueprint> inventoryBuilder;
            inventoryBuilder = InventoryStructure.Offline.of(Feed.Blueprint.builder().withId(feedId).build());

            synchronized (addedIds) {
                prepareAddedIds();

                // recursively builds the sync structure starting at the roots of the inventory
                Set<Resource<L>> roots = resourceManager.getRootResources();
                for (Resource<L> root : roots) {
                    org.hawkular.inventory.api.model.Resource.Blueprint rootBP = buildResourceBlueprint(root);
                    ChildBuilder<?> childBuilder = inventoryBuilder.startChild(rootBP);
                    try {
                        resource(resourceManager, root, childBuilder);
                    } finally {
                        childBuilder.end();
                    }
                }
            }

            return inventoryBuilder.build();
        }

        /**
         * Builds a sync structure that can be sent to {@code /sync} endpoint of Inventory
         * in order to sync resourced types. No resources are synced.
         *
         * @param resourceTypes
         * @return sync structure
         */
        public InventoryStructure<Feed.Blueprint> build(List<ResourceType<L>> resourceTypes) {

            Builder<Blueprint> inventoryBuilder;
            inventoryBuilder = InventoryStructure.Offline.of(Feed.Blueprint.builder().withId(feedId).build());

            synchronized (addedIds) {
                prepareAddedIds();

                // we don't sync parent-child relations for types; all types are stored at root level in inventory
                for (ResourceType<L> resourceType : resourceTypes) {
                    resourceType(resourceType, inventoryBuilder);
                }
            }

            return inventoryBuilder.build();
        }

        private void prepareAddedIds() {
            addedIds.clear();
            addedIds.put(IdType.RESOURCE_TYPE, new HashSet<>());
            addedIds.put(IdType.METRIC_TYPE, new HashSet<>());
        }

        private org.hawkular.inventory.api.model.Resource.Blueprint buildResourceBlueprint(Resource<L> resource) {
            String resourceId = getInventoryId(resource);
            String resourceName = resource.getName().getNameString();
            String resourceTypePath = newPathPrefix().resourceType(getInventoryId(resource.getResourceType())).get()
                    .toString();
            Map<String, Object> resourceProperties = resource.getProperties();

            org.hawkular.inventory.api.model.Resource.Blueprint resourceBP;
            resourceBP = org.hawkular.inventory.api.model.Resource.Blueprint.builder()
                    .withId(resourceId)
                    .withName(resourceName)
                    .withResourceTypePath(resourceTypePath)
                    .withProperties(resourceProperties)
                    .build();
            return resourceBP;
        }

        private void resource(ResourceManager<L> resourceManager, Resource<L> resource,
                ChildBuilder<?> resourceBuilder) {

            // resource configuration
            Collection<ResourceConfigurationPropertyInstance<L>> resConfigInstances = resource
                    .getResourceConfigurationProperties();
            resourceConfigurations(resConfigInstances, resourceBuilder);

            // metrics and avails (which are just metrics, too)
            Collection<? extends Instance<L, ?>> metricInstances = resource.getMetrics();
            for (Instance<L, ?> metric : metricInstances) {
                metric(metric, resourceBuilder);
            }
            Collection<? extends Instance<L, ?>> availInstances = resource.getAvails();
            for (Instance<L, ?> metric : availInstances) {
                metric(metric, resourceBuilder);
            }

            // children resources
            Set<Resource<L>> children = resourceManager.getChildren(resource);
            for (Resource<L> child : children) {
                org.hawkular.inventory.api.model.Resource.Blueprint childBP = buildResourceBlueprint(child);
                ChildBuilder<?> childBuilder = resourceBuilder.startChild(childBP);
                try {
                    resource(resourceManager, child, childBuilder);
                } finally {
                    childBuilder.end();
                }
            }
        }

        private void resourceType(ResourceType<L> resourceType, Builder<?> inventoryBuilder) {

            // resource type
            String resourceTypeId = getInventoryId(resourceType);

            if (!addedIds.get(IdType.RESOURCE_TYPE).add(resourceTypeId)) {
                return; // we already did this resource type
            }

            String resourceTypeName = resourceType.getName().getNameString();
            Map<String, Object> resourceTypeProperties = resourceType.getProperties();

            org.hawkular.inventory.api.model.ResourceType.Blueprint resourceTypeBP = //
                    org.hawkular.inventory.api.model.ResourceType.Blueprint.builder()
                            .withId(resourceTypeId)
                            .withName(resourceTypeName)
                            .withProperties(resourceTypeProperties)
                            .build();

            ChildBuilder<?> childBuilder = inventoryBuilder.startChild(resourceTypeBP);

            try {
                // operations (which are children of the resource type)
                Collection<? extends Operation<L>> ops = resourceType.getOperations();
                for (Operation<L> op : ops) {
                    operation(op, childBuilder);
                }

                // resource configuration types (which are children of the resource type)
                Collection<? extends ResourceConfigurationPropertyType<L>> rcpts = //
                        resourceType.getResourceConfigurationPropertyTypes();
                resourceConfigurationTypes(rcpts, childBuilder);

            } finally {
                childBuilder.end();
            }

            // NOTE: Metric types and avail types are not children of the resource type itself.
            // Inventory doesn't yet support child relationships like that.

            // metric types
            Collection<? extends MetricType<L>> metricTypes = resourceType.getMetricTypes();
            for (MetricType<L> metricType : metricTypes) {
                metricType(metricType, inventoryBuilder);
            }

            // avail types (which are just metric types, too)
            Collection<? extends AvailType<L>> availTypes = resourceType.getAvailTypes();
            for (AvailType<L> availType : availTypes) {
                metricType(availType, inventoryBuilder);
            }

            return;
        }

        private void metric(Instance<L, ?> metric, ChildBuilder<?> childBuilder) {

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

        private void metricType(MeasurementType<L> metricType, Builder<?> inventoryBuilder) {

            String metricTypeId = getInventoryId(metricType);

            if (!addedIds.get(IdType.METRIC_TYPE).add(metricTypeId)) {
                return; // we already did this metric type
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

        private void operation(Operation<L> operation, ChildBuilder<?> childBuilder) {
            String operationId = getInventoryId(operation);
            String operationName = operation.getName().getNameString();
            Map<String, Object> operationProperties = operation.getProperties();

            OperationType.Blueprint blueprint = OperationType.Blueprint.builder()
                    .withId(operationId)
                    .withName(operationName)
                    .withProperties(operationProperties)
                    .build();

            List<OperationParam> params = operation.getParameters();
            if (params.isEmpty()) {
                childBuilder.addChild(blueprint);
            } else {
                ChildBuilder<?> opBuilder = childBuilder.startChild(blueprint);
                try {
                    StructuredData.MapBuilder structDataBuilder = StructuredData.get().map();

                    for (OperationParam param : params) {
                        InnerMapBuilder<MapBuilder> innerMap = structDataBuilder.putMap(param.getName());
                        if (param.getType() != null) {
                            innerMap.putString("type", param.getType());
                        }
                        if (param.getDescription() != null) {
                            innerMap.putString("description", param.getDescription());
                        }
                        if (param.getDefaultValue() != null) {
                            innerMap.putString("defaultValue", param.getDefaultValue());
                        }
                        innerMap.closeMap();
                    }

                    DataEntity.Blueprint<DataRole> paramsDataEntity = DataEntity.Blueprint.builder()
                            .withRole(DataRole.OperationType.parameterTypes)
                            .withValue(structDataBuilder.build())
                            .build();

                    opBuilder.addChild(paramsDataEntity);
                } finally {
                    opBuilder.end();
                }
            }
        }

        private void resourceConfigurationTypes(Collection<? extends ResourceConfigurationPropertyType<L>> rcpts,
                ChildBuilder<?> childBuilder) {
            // TODO inventory sync doesn't work when resource config exists
            //      put this back when fixed
            if (true) {
                return;
            }

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

        private void resourceConfigurations(Collection<? extends ResourceConfigurationPropertyInstance<L>> rcpis,
                ChildBuilder<?> childBuilder) {
            // TODO inventory sync doesn't work when resource config exists
            //      put this back when fixed
            if (true) {
                return;
            }

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

        private String getInventoryId(NamedObject no) {
            String id;
            if (no.getID().equals(ID.NULL_ID)) {
                id = no.getName().getNameString();
            } else {
                id = no.getID().getIDString();
            }
            return id;
        }

        /**
         * @return a new {@link CanonicalPath} made of {@link #tenantId} and {@link #feedId}
         */
        private CanonicalPath.FeedBuilder newPathPrefix() {
            return CanonicalPath.of().tenant(tenantId).feed(feedId);
        }
    }

    private static final MsgLogger log = AgentLoggers.getLogger(AsyncInventoryStorage.class);

    private final String feedId;
    private final MonitorServiceConfiguration.StorageAdapterConfiguration config;
    private final HttpClientBuilder httpClientBuilder;
    private final Diagnostics diagnostics;

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
    }

    public void shutdown() {
        log.debugf("Shutting down async inventory storage");
    }

    @Override
    public <L> void resourcesAdded(InventoryEvent<L> event) {
        // We don't do anything here - the real work will be done when discovery is completed.
        return;
    }

    @Override
    public <L> void resourcesRemoved(InventoryEvent<L> event) {
        // We don't do anything here - the real work will be done when discovery is completed.
        return;
    }

    @Override
    public <L> void discoveryCompleted(DiscoveryEvent<L> event) {
        MonitoredEndpoint<EndpointConfiguration> endpoint = event.getSamplingService().getMonitoredEndpoint();
        String endpointTenantId = endpoint.getEndpointConfiguration().getTenantId();
        String tenantIdToUse = (endpointTenantId != null) ? endpointTenantId : config.getTenantId();
        SyncPayloadBuilder<L> bldr = new SyncPayloadBuilder<>(tenantIdToUse, feedId);
        InventoryStructure<Blueprint> payload = bldr.build(event.getResourceManager());

        performResourceSync(payload, tenantIdToUse, event.getResourceManager().size());
    }

    @Override
    public <L> void allResourceTypes(Map<String, List<ResourceType<L>>> typesByTenantId) {
        for (Map.Entry<String, List<ResourceType<L>>> entry : typesByTenantId.entrySet()) {
            String tenantIdToUse = entry.getKey();
            List<ResourceType<L>> types = entry.getValue();
            SyncPayloadBuilder<L> bldr = new SyncPayloadBuilder<>(tenantIdToUse, feedId);
            InventoryStructure<Blueprint> payload = bldr.build(types);

            performResourceTypeSync(payload, tenantIdToUse, types.size());

        }
    }

    private <L> void performResourceSync(InventoryStructure<Blueprint> payload, String tenantIdToUse,
            int totalResourceCount) {

        if (payload.getRoot() != null) {
            try {
                StringBuilder url = Util.getContextUrlString(AsyncInventoryStorage.this.config.getUrl(),
                        AsyncInventoryStorage.this.config.getInventoryContext());
                url.append("sync");
                url.append("/f;").append(this.feedId);
                url.append("/r;").append(payload.getRoot().getId());
                String jsonPayload = Util.toJson(payload);
                Map<String, String> headers = getTenantHeader(tenantIdToUse);

                log.tracef("Syncing [%d] resources to inventory: headers=[%s] body=[%s]",
                        totalResourceCount, headers, jsonPayload);

                Request request = this.httpClientBuilder.buildJsonPostRequest(url.toString(), headers, jsonPayload);
                Call call = this.httpClientBuilder.getHttpClient().newCall(request);
                final Timer.Context timer = diagnostics.getInventoryStorageRequestTimer().time();
                Response response = call.execute();

                try {
                    final long durationNanos = timer.stop();

                    log.tracef("Received sync response from inventory: code [%d]", response.code());

                    // HTTP status of 204 means success, anything else is an error
                    if (response.code() != 204) {
                        throw new Exception("status-code=[" + response.code() + "], reason=["
                                + response.message() + "], url=[" + request.urlString() + "]");
                    }

                    diagnostics.getInventoryRate().mark(totalResourceCount);

                    if (log.isDebugEnabled()) {
                        log.debugf("Took [%d]ms to sync [%d] resources to inventory",
                                TimeUnit.MILLISECONDS.convert(durationNanos, TimeUnit.NANOSECONDS),
                                totalResourceCount);
                    }
                } finally {
                    response.body().close();
                }
            } catch (InterruptedException ie) {
                log.errorFailedToStoreInventoryData(ie);
                Thread.currentThread().interrupt(); // preserve interrupt
            } catch (Exception e) {
                log.errorFailedToStoreInventoryData(e);
                diagnostics.getStorageErrorRate().mark(1);
            }
        }

        return;
    }

    private <L> void performResourceTypeSync(InventoryStructure<Blueprint> payload, String tenantIdToUse,
            int totalResourceTypeCount) {

        if (payload.getRoot() != null) {
            try {
                StringBuilder url = Util.getContextUrlString(AsyncInventoryStorage.this.config.getUrl(),
                        AsyncInventoryStorage.this.config.getInventoryContext());
                url.append("sync");
                url.append("/f;").append(this.feedId);
                String jsonPayload = Util.toJson(payload);
                Map<String, String> headers = getTenantHeader(tenantIdToUse);

                log.tracef("Syncing [%d] resource types to inventory: headers=[%s] body=[%s]",
                        totalResourceTypeCount, headers, jsonPayload);

                Request request = this.httpClientBuilder.buildJsonPostRequest(url.toString(), headers, jsonPayload);
                Call call = this.httpClientBuilder.getHttpClient().newCall(request);
                final Timer.Context timer = diagnostics.getInventoryStorageRequestTimer().time();
                Response response = call.execute();

                try {
                    final long durationNanos = timer.stop();

                    log.tracef("Received type sync response from inventory: code [%d]", response.code());

                    // HTTP status of 204 means success, anything else is an error
                    if (response.code() != 204) {
                        throw new Exception("status-code=[" + response.code() + "], reason=["
                                + response.message() + "], url=[" + request.urlString() + "]");
                    }

                    diagnostics.getInventoryRate().mark(totalResourceTypeCount);

                    if (log.isDebugEnabled()) {
                        log.debugf("Took [%d]ms to sync [%d] resource types to inventory",
                                TimeUnit.MILLISECONDS.convert(durationNanos, TimeUnit.NANOSECONDS),
                                totalResourceTypeCount);
                    }
                } finally {
                    response.body().close();
                }
            } catch (InterruptedException ie) {
                log.errorFailedToStoreInventoryData(ie);
                Thread.currentThread().interrupt(); // preserve interrupt
            } catch (Exception e) {
                log.errorFailedToStoreInventoryData(e);
                diagnostics.getStorageErrorRate().mark(1);
            }
        }

        return;
    }

    /**
     * Builds the header necessary for the tenant ID.
     *
     * @param tenantId the tenant ID string - this is the value of the returned map
     * @return the tenant header consisting of the header key and the value
     */
    private Map<String, String> getTenantHeader(String tenantId) {
        if (tenantId == null) {
            throw new IllegalArgumentException("tenantId must not be null");
        }
        return Collections.singletonMap("Hawkular-Tenant", tenantId);
    }
}
