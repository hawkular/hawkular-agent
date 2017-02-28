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
package org.hawkular.agent.monitor.storage;

import java.io.IOException;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import org.hawkular.agent.monitor.api.DiscoveryEvent;
import org.hawkular.agent.monitor.api.InventoryEvent;
import org.hawkular.agent.monitor.api.InventoryStorage;
import org.hawkular.agent.monitor.config.AgentCoreEngineConfiguration;
import org.hawkular.agent.monitor.config.AgentCoreEngineConfiguration.EndpointConfiguration;
import org.hawkular.agent.monitor.config.AgentCoreEngineConfiguration.StorageAdapterConfiguration;
import org.hawkular.agent.monitor.diagnostics.Diagnostics;
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
import org.hawkular.agent.monitor.inventory.ResourceTypeManager;
import org.hawkular.agent.monitor.log.AgentLoggers;
import org.hawkular.agent.monitor.log.MsgLogger;
import org.hawkular.agent.monitor.util.Util;
import org.hawkular.inventory.api.model.DataEntity;
import org.hawkular.inventory.api.model.InventoryStructure;
import org.hawkular.inventory.api.model.InventoryStructure.AbstractBuilder;
import org.hawkular.inventory.api.model.InventoryStructure.ChildBuilder;
import org.hawkular.inventory.api.model.InventoryStructure.Offline;
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

import okhttp3.Call;
import okhttp3.Request;
import okhttp3.Response;

/**
 * An {@link InventoryStorage} that syncs inventory that has been discovered.
 *
 * @author John Mazzitelli
 * @author <a href="https://github.com/ppalaga">Peter Palaga</a>
 */
public class AsyncInventoryStorage implements InventoryStorage {

    /**
     * A builder of inventory JSON structures that can be sent to Hawkular-Inventory.
     */
    private static class InventoryPayloadBuilder<L> {

        private final String feedId;
        private final String tenantId;

        private enum IdType {
            RESOURCE_TYPE, METRIC_TYPE
        }

        private final Map<IdType, Set<String>> addedIds; // tracks what we already added so we don't add duplicates

        public InventoryPayloadBuilder(String tenantId, String feedId) {
            this.tenantId = tenantId;
            this.feedId = feedId;
            this.addedIds = new HashMap<>();
        }

        /**
         * Builds a sync structure that can be sent to {@code /sync} endpoint of Inventory
         * in order to sync resources in the given resource manager. No types are synced.
         *
         * @param resourceManager the resources to be sync'ed
         * @return sync structure for each root resource in the given resource manager
         */
        public Map<Resource<L>, Offline<org.hawkular.inventory.api.model.Resource.Blueprint>> build(
                ResourceManager<L> resourceManager) {

            Map<Resource<L>, Offline<org.hawkular.inventory.api.model.Resource.Blueprint>> retVal;
            Set<Resource<L>> roots = resourceManager.getRootResources();
            retVal = new HashMap<>(roots.size());

            synchronized (addedIds) {
                prepareAddedIds();

                // recursively builds the sync structure starting at the roots of the inventory
                for (Resource<L> root : roots) {
                    InventoryStructure.Builder<org.hawkular.inventory.api.model.Resource.Blueprint> invBldr;
                    org.hawkular.inventory.api.model.Resource.Blueprint rootBP = buildResourceBlueprint(root);
                    invBldr = InventoryStructure.Offline.of(rootBP);
                    resource(resourceManager, root, invBldr);
                    retVal.put(root, invBldr.build());
                }
            }

            return retVal;
        }

        /**
         * Builds structures that can be sent to inventory to create or update resource types.
         *
         * @param resourceTypes
         * @return inventory structures all rooted at the given resource types
         */
        public Map<ResourceType<L>, Offline<org.hawkular.inventory.api.model.ResourceType.Blueprint>> build(
                List<ResourceType<L>> resourceTypes) {

            Map<ResourceType<L>, Offline<org.hawkular.inventory.api.model.ResourceType.Blueprint>> retVal;
            retVal = new HashMap<>(resourceTypes.size());

            synchronized (addedIds) {
                prepareAddedIds();

                // we don't sync parent-child relations for types; all types are stored at root level in inventory
                for (ResourceType<L> rt : resourceTypes) {
                    InventoryStructure.Builder<org.hawkular.inventory.api.model.ResourceType.Blueprint> invBldr;
                    org.hawkular.inventory.api.model.ResourceType.Blueprint rtBP = buildResourceTypeBlueprint(rt);
                    invBldr = InventoryStructure.Offline.of(rtBP);
                    resourceType(rt, invBldr);
                    retVal.put(rt, invBldr.build());
                }
            }

            return retVal;
        }

        /**
         * Builds structures that can be sent to inventory to create or update metric types.
         *
         * @param resourceTypes all resource types whose metrics are to be processed
         * @return inventory structures all rooted at the given resource types
         */
        public Map<MeasurementType<L>, Offline<org.hawkular.inventory.api.model.MetricType.Blueprint>> buildMetrics(
                List<ResourceType<L>> resourceTypes) {

            Set<MeasurementType<L>> measTypes = new HashSet<>();
            for (ResourceType<L> resourceType : resourceTypes) {
                if (resourceType.isPersisted() == false) {
                    measTypes.addAll(resourceType.getMetricTypes().stream().filter(t -> t.isPersisted() == false)
                            .collect(Collectors.toList()));
                    measTypes.addAll(resourceType.getAvailTypes().stream().filter(t -> t.isPersisted() == false)
                            .collect(Collectors.toList()));
                }
            }

            Map<MeasurementType<L>, Offline<org.hawkular.inventory.api.model.MetricType.Blueprint>> retVal;
            retVal = new HashMap<>(measTypes.size());

            synchronized (addedIds) {
                prepareAddedIds();

                // we don't sync parent-child relations for types; all types are stored at root level in inventory
                for (MeasurementType<L> mt : measTypes) {
                    if (!addedIds.get(IdType.METRIC_TYPE).add(mt.getID().getIDString())) {
                        continue; // we already did this metric type
                    }
                    InventoryStructure.Builder<org.hawkular.inventory.api.model.MetricType.Blueprint> invBldr;
                    org.hawkular.inventory.api.model.MetricType.Blueprint mtBP = buildMetricTypeBlueprint(mt);
                    invBldr = InventoryStructure.Offline.of(mtBP);
                    retVal.put(mt, invBldr.build());
                }
            }

            return retVal;
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

        private org.hawkular.inventory.api.model.ResourceType.Blueprint buildResourceTypeBlueprint(
                ResourceType<L> resourceType) {
            String resourceTypeId = getInventoryId(resourceType);
            String resourceTypeName = resourceType.getName().getNameString();
            Map<String, Object> resourceTypeProperties = resourceType.getProperties();

            org.hawkular.inventory.api.model.ResourceType.Blueprint resourceTypeBP;
            resourceTypeBP = org.hawkular.inventory.api.model.ResourceType.Blueprint.builder()
                    .withId(resourceTypeId)
                    .withName(resourceTypeName)
                    .withProperties(resourceTypeProperties)
                    .build();
            return resourceTypeBP;
        }

        private org.hawkular.inventory.api.model.MetricType.Blueprint buildMetricTypeBlueprint(
                MeasurementType<L> metricType) {
            String metricTypeId = getInventoryId(metricType);
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
                    .build();
            return blueprint;
        }

        private void resource(ResourceManager<L> resourceManager, Resource<L> resource,
                AbstractBuilder<?> theBuilder) {

            // resource configuration
            Collection<ResourceConfigurationPropertyInstance<L>> resConfigInstances = resource
                    .getResourceConfigurationProperties();
            resourceConfigurations(resConfigInstances, theBuilder);

            // metrics and avails (which are just metrics, too)
            Collection<? extends Instance<L, ?>> metricInstances = resource.getMetrics();
            for (Instance<L, ?> metric : metricInstances) {
                metric(metric, theBuilder);
            }
            Collection<? extends Instance<L, ?>> availInstances = resource.getAvails();
            for (Instance<L, ?> metric : availInstances) {
                metric(metric, theBuilder);
            }

            // children resources
            Set<Resource<L>> children = resourceManager.getChildren(resource);
            for (Resource<L> child : children) {
                org.hawkular.inventory.api.model.Resource.Blueprint childBP = buildResourceBlueprint(child);
                ChildBuilder<?> childBuilder = theBuilder.startChild(childBP);
                try {
                    resource(resourceManager, child, childBuilder);
                } finally {
                    childBuilder.end();
                }
            }
        }

        private void resourceType(ResourceType<L> resourceType, AbstractBuilder<?> theBuilder) {

            if (!addedIds.get(IdType.RESOURCE_TYPE).add(theBuilder.getBlueprint().getId())) {
                return; // we already did this resource type
            }

            // operations (which are children of the resource type)
            Collection<? extends Operation<L>> ops = resourceType.getOperations();
            for (Operation<L> op : ops) {
                operation(op, theBuilder);
            }

            // resource configuration types (which are children of the resource type)
            Collection<? extends ResourceConfigurationPropertyType<L>> rcpts = //
                    resourceType.getResourceConfigurationPropertyTypes();
            resourceConfigurationTypes(rcpts, theBuilder);
        }

        private void metric(Instance<L, ?> metric, AbstractBuilder<?> theBuilder) {

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

            theBuilder.addChild(blueprint);
        }

        private void operation(Operation<L> operation, AbstractBuilder<?> theBuilder) {

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
                theBuilder.addChild(blueprint);
            } else {
                ChildBuilder<?> opBuilder = theBuilder.startChild(blueprint);
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
                        if (param.isRequired() != null) {
                            innerMap.putBool("required", param.isRequired());
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
                AbstractBuilder<?> theBuilder) {

            if (!rcpts.isEmpty()) {
                StructuredData.MapBuilder structDataBuilder = StructuredData.get().map();
                for (ResourceConfigurationPropertyType<?> rcpt : rcpts) {
                    structDataBuilder.putString(rcpt.getID().getIDString(), rcpt.getName().getNameString());
                }

                DataEntity.Blueprint<DataRole> dataEntity = DataEntity.Blueprint.builder()
                        .withRole(DataRole.ResourceType.configurationSchema)
                        .withValue(structDataBuilder.build())
                        .build();

                theBuilder.addChild(dataEntity);
            }
        }

        private void resourceConfigurations(Collection<? extends ResourceConfigurationPropertyInstance<L>> rcpis,
                AbstractBuilder<?> theBuilder) {

            if (!rcpis.isEmpty()) {
                StructuredData.MapBuilder structDataBuilder = StructuredData.get().map();
                for (ResourceConfigurationPropertyInstance<?> rcpi : rcpis) {
                    structDataBuilder.putString(rcpi.getID().getIDString(), rcpi.getValue());
                }

                DataEntity.Blueprint<DataRole> dataEntity = DataEntity.Blueprint.builder()
                        .withRole(DataRole.Resource.configuration)
                        .withValue(structDataBuilder.build())
                        .build();

                theBuilder.addChild(dataEntity);
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
    private final AgentCoreEngineConfiguration.StorageAdapterConfiguration config;
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
    }

    @Override
    public <L> void resourcesRemoved(InventoryEvent<L> event) {
        // due to the way inventory sync works and how we are using it, we only care about explicitly
        // removing root resources. We can't individually sync a root resource (because it doesn't exist!)
        // so we remove it here. Any children resources will be synced via discoveryCompleted so we don't
        // do anything in here.
        List<Resource<L>> removedResources = event.getPayload();
        for (Resource<L> removedResource : removedResources) {
            if (removedResource.getParent() == null) {
                try {
                    log.debugf("Removing root resource: %s", removedResource);

                    MonitoredEndpoint<EndpointConfiguration> endpoint = event.getSamplingService()
                            .getMonitoredEndpoint();
                    String endpointTenantId = endpoint.getEndpointConfiguration().getTenantId();
                    String tenantIdToUse = (endpointTenantId != null) ? endpointTenantId : config.getTenantId();

                    // The final URL should be in the form: entity/<resource_canonical_path>
                    // for example: entity/t;hawkular/f;myfeed/r;resource_id
                    //  => NOW strings/inventory.myfeed.r.resource_id

                    InventoryMetric metric = InventoryMetric.resource(feedId, removedResource.getID().getIDString());
                    StringBuilder deleteUrl = Util.getContextUrlString(config.getUrl(), config.getMetricsContext())
                            .append("strings/")
                            .append(metric.name());

                    Request request = httpClientBuilder.buildJsonDeleteRequest(deleteUrl.toString(),
                            getTenantHeader(tenantIdToUse));

                    long start = System.currentTimeMillis(); // we don't store this time in our diagnostics
                    Response response = httpClientBuilder.getHttpClient().newCall(request).execute();

                    try {
                        final long duration = System.currentTimeMillis() - start;

                        if (response.code() != 204 && response.code() != 404) {
                            // 204 means successfully deleted, 404 means it didn't exist in the first place.
                            // In either case, the resource no longer exists which is what we want;
                            // any other response code means it is an error and we didn't remove the resource.
                            throw new Exception("status-code=[" + response.code() + "], reason=["
                                    + response.message() + "], url=[" + request.url().toString() + "]");
                        }

                        log.debugf("Took [%d]ms to remove root resource [%s]", duration, removedResource);
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
        }
    }

    @Override
    public <L> void discoveryCompleted(DiscoveryEvent<L> event) {
        ResourceManager<L> resourceManager = event.getResourceManager();
        ResourceTypeManager<L> resourceTypeManager = event.getResourceTypeManager();
        MonitoredEndpoint<EndpointConfiguration> endpoint = event.getSamplingService().getMonitoredEndpoint();
        String endpointTenantId = endpoint.getEndpointConfiguration().getTenantId();
        String tenantIdToUse = (endpointTenantId != null) ? endpointTenantId : config.getTenantId();

        InventoryPayloadBuilder<L> bldr = new InventoryPayloadBuilder<>(tenantIdToUse, feedId);

        // Since we know types never change during the lifetime of the agent, we don't have to process
        // types that have already been flagged as having been persisted.
        // We first must persist metric types then resource types (remember, there are no hierarchies,
        // all metric types and resource types are peers to one another).
        List<ResourceType<L>> allResourceTypes = resourceTypeManager.getResourceTypesBreadthFirst()
                .stream()
                .filter(rt -> rt.isPersisted() == false)
                .collect(Collectors.toList());
        Map<MeasurementType<L>, Offline<org.hawkular.inventory.api.model.MetricType.Blueprint>> mtBlueprints;
        mtBlueprints = bldr.buildMetrics(allResourceTypes);
        mtBlueprints.forEach((mt, bp) -> performMetricTypeSync(bp, tenantIdToUse));

        Map<ResourceType<L>, Offline<org.hawkular.inventory.api.model.ResourceType.Blueprint>> rtBlueprints;
        rtBlueprints = bldr.build(allResourceTypes);
        rtBlueprints.forEach((rt, bp) -> performResourceTypeSync(bp, tenantIdToUse));

        // indicate we persisted the resource types
        allResourceTypes.forEach(rt -> rt.setPersisted(true));

        // build the JSON blueprints for the sync resource requests
        // Note that it is possible for a endpoint to define multiple root resources.
        // We have to sync each root resource separately.
        Map<Resource<L>, Offline<org.hawkular.inventory.api.model.Resource.Blueprint>> rBlueprints;
        rBlueprints = bldr.build(resourceManager);
        rBlueprints.forEach((r, bp) -> performResourceSync(bp, tenantIdToUse, resourceManager.size(r)));

        // indicate we persisted the resources
        resourceManager.getResourcesBreadthFirst().forEach(r -> r.setPersisted(true));
    }

    private void performResourceSync(
            InventoryStructure<org.hawkular.inventory.api.model.Resource.Blueprint> resourceStructure,
            String tenantIdToUse,
            int totalResourceCount) {

        if (resourceStructure.getRoot() != null) {
            try {
                InventoryMetric metric = InventoryMetric.resource(feedId, resourceStructure.getRoot().getId());
                // TODO: tag only once
                initMetric(tenantIdToUse, metric);
                StringBuilder url = Util.getContextUrlString(config.getUrl(), config.getMetricsContext())
                        .append("strings/")
                        .append(metric.name())
                        .append("/raw");
                String jsonPayload = Util.toJson(Collections.singleton(new InventoryStringDataPoint(System.currentTimeMillis(), resourceStructure)));
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

                    // HTTP status of 200 means success, anything else is an error
                    if (response.code() != 200) {
                        throw new Exception("status-code=[" + response.code() + "], reason=["
                                + response.message() + "], url=[" + request.url().toString() + "]");
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
    }

    private void performResourceTypeSync(
            Offline<org.hawkular.inventory.api.model.ResourceType.Blueprint> resourceTypeStructure,
            String tenantIdToUse) {
        if (resourceTypeStructure.getRoot() != null) {
            try {
                InventoryMetric metric = InventoryMetric.resourceType(feedId, resourceTypeStructure.getRoot().getId());
                // TODO: tag only once
                initMetric(tenantIdToUse, metric);
                StringBuilder url = Util.getContextUrlString(AsyncInventoryStorage.this.config.getUrl(),
                        AsyncInventoryStorage.this.config.getMetricsContext())
                        .append("strings/")
                        .append(metric.name())
                        .append("/raw");
                String jsonPayload = Util.toJson(Collections.singleton(new InventoryStringDataPoint(System.currentTimeMillis(), resourceTypeStructure)));
                Map<String, String> headers = getTenantHeader(tenantIdToUse);

                log.tracef("Syncing resource type to inventory: headers=[%s] body=[%s]", headers, jsonPayload);

                Request request = this.httpClientBuilder.buildJsonPostRequest(url.toString(), headers, jsonPayload);
                Call call = this.httpClientBuilder.getHttpClient().newCall(request);
                final Timer.Context timer = diagnostics.getInventoryStorageRequestTimer().time();
                Response response = call.execute();

                try {
                    final long durationNanos = timer.stop();

                    log.tracef("Received sync response from inventory: code [%d]", response.code());

                    // HTTP status of 200 means success, anything else is an error
                    if (response.code() != 200) {
                        throw new Exception("status-code=[" + response.code() + "], reason=["
                                + response.message() + "], url=[" + request.url().toString() + "]");
                    }

                    diagnostics.getInventoryRate().mark(1);

                    if (log.isDebugEnabled()) {
                        log.debugf("Took [%d]ms to sync resource type to inventory",
                                TimeUnit.MILLISECONDS.convert(durationNanos, TimeUnit.NANOSECONDS));
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
    }

    private void performMetricTypeSync(
            Offline<org.hawkular.inventory.api.model.MetricType.Blueprint> metricTypeStructure,
            String tenantIdToUse) {

        if (metricTypeStructure.getRoot() != null) {
            try {
                InventoryMetric metric = InventoryMetric.metricType(feedId, metricTypeStructure.getRoot().getId());
                // TODO: tag only once
                initMetric(tenantIdToUse, metric);
                StringBuilder url = Util.getContextUrlString(AsyncInventoryStorage.this.config.getUrl(),
                        AsyncInventoryStorage.this.config.getMetricsContext())
                        .append("strings/")
                        .append(metric.name())
                        .append("/raw");
                String jsonPayload = Util.toJson(Collections.singleton(new InventoryStringDataPoint(System.currentTimeMillis(), metricTypeStructure)));
                System.out.println("JSON PAYLOAD: " + jsonPayload);
                Map<String, String> headers = getTenantHeader(tenantIdToUse);

                log.tracef("Syncing metric type to inventory: headers=[%s] body=[%s]", headers, jsonPayload);

                Request request = this.httpClientBuilder.buildJsonPostRequest(url.toString(), headers, jsonPayload);
                Call call = this.httpClientBuilder.getHttpClient().newCall(request);
                final Timer.Context timer = diagnostics.getInventoryStorageRequestTimer().time();
                Response response = call.execute();

                try {
                    final long durationNanos = timer.stop();

                    log.tracef("Received sync response from inventory: code [%d]", response.code());

                    // HTTP status of 200 means success, anything else is an error
                    if (response.code() != 200) {
                        throw new Exception("status-code=[" + response.code() + "], reason=["
                                + response.message() + "], url=[" + request.url().toString() + "]");
                    }

                    diagnostics.getInventoryRate().mark(1);

                    if (log.isDebugEnabled()) {
                        log.debugf("Took [%d]ms to sync metric type to inventory",
                                TimeUnit.MILLISECONDS.convert(durationNanos, TimeUnit.NANOSECONDS));
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

    private void initMetric(String tenant, InventoryMetric metric) throws IOException {
        System.out.println("Tagging " + tenant + " / " + metric);
        // FIXME: better json
        String json = "{\"id\": \"" + metric.name() + "\"," +
                "\"dataRetention\": 90," +
                "\"tags\": {" +
                "\"module\": \"inventory\"," +
                "\"feed\": \"" + metric.getFeed() + "\"," +
                "\"type\": \"" + metric.getType() + "\"" +
                "}}";
        StringBuilder url = Util.getContextUrlString(AsyncInventoryStorage.this.config.getUrl(),
                AsyncInventoryStorage.this.config.getMetricsContext())
                .append("strings");
        Map<String, String> headers = getTenantHeader(tenant);

        // FIXME: manage errors...
        Request request = this.httpClientBuilder.buildJsonPostRequest(url.toString(), headers, json);
        Call call = this.httpClientBuilder.getHttpClient().newCall(request);
        Response response = call.execute();
        System.out.println("Tagging response code: " + response.code());
    }
}
