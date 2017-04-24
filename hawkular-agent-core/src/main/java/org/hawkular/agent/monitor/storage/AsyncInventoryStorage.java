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

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import java.util.zip.GZIPOutputStream;

import org.hawkular.agent.monitor.api.InventoryEvent;
import org.hawkular.agent.monitor.api.InventoryStorage;
import org.hawkular.agent.monitor.config.AgentCoreEngineConfiguration;
import org.hawkular.agent.monitor.config.AgentCoreEngineConfiguration.EndpointConfiguration;
import org.hawkular.agent.monitor.config.AgentCoreEngineConfiguration.StorageAdapterConfiguration;
import org.hawkular.agent.monitor.diagnostics.Diagnostics;
import org.hawkular.agent.monitor.inventory.AvailType;
import org.hawkular.agent.monitor.inventory.ID;
import org.hawkular.agent.monitor.inventory.IDObject;
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
import org.hawkular.inventory.api.Inventory;
import org.hawkular.inventory.api.model.DataEntity;
import org.hawkular.inventory.api.model.Entity;
import org.hawkular.inventory.api.model.ExtendedInventoryStructure;
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
import org.hawkular.inventory.paths.RelativePath;
import org.hawkular.inventory.paths.SegmentType;

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

        InventoryPayloadBuilder(String tenantId, String feedId) {
            this.tenantId = tenantId;
            this.feedId = feedId;
        }

        /**
         * Builds a sync structure that can be sent to Inventory metrics
         * in order to sync resources in the given resource manager. No types are synced.
         * @param resourceManager the resources manager
         * @param root the root resource to be sync'ed
         * @return sync structure for root resource in the given resource manager
         */
        Offline<org.hawkular.inventory.api.model.Resource.Blueprint> buildRootResource(
                ResourceManager<L> resourceManager,
                Resource<L> root) {
            org.hawkular.inventory.api.model.Resource.Blueprint rootBP = buildResourceBlueprint(root);
            InventoryStructure.Builder<org.hawkular.inventory.api.model.Resource.Blueprint> invBldr
                    = InventoryStructure.Offline.of(rootBP);
            resource(resourceManager, root, invBldr);
            return invBldr.build();
        }

        /**
         * Builds structure that can be sent to inventory to create or update resource type.
         * @return inventory structure rooted at the given resource type
         */
        Offline<org.hawkular.inventory.api.model.ResourceType.Blueprint> buildResourceType(ResourceType<L> resourceType) {
            // we don't sync parent-child relations for types; all types are stored at root level in inventory
            InventoryStructure.Builder<org.hawkular.inventory.api.model.ResourceType.Blueprint> invBldr;
            invBldr = InventoryStructure.Offline.of(buildResourceTypeBlueprint(resourceType));
            resourceType(resourceType, invBldr);
            return invBldr.build();
        }

        /**
         * Builds structure that can be sent to inventory to create or update metric type.
         *
         * @param measurementType the measurement type
         * @return inventory structure rooted at the given measurement type
         */
        Offline<org.hawkular.inventory.api.model.MetricType.Blueprint> buildMetricType(MeasurementType<L> measurementType) {
            org.hawkular.inventory.api.model.MetricType.Blueprint mtBP = buildMetricTypeBlueprint(measurementType);
            return InventoryStructure.Offline.of(mtBP).build();
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

            return org.hawkular.inventory.api.model.MetricType.Blueprint
                    .builder(metricDataType)
                    .withId(metricTypeId)
                    .withName(metricTypeName)
                    .withInterval(metricTypeIntervalSecs)
                    .withProperties(metricTypeProperties)
                    .withUnit(metricUnit)
                    .build();
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
    private static final int CHUNKS_SIZE = 1536;    // = 2048*4/3 due to Base64 conversion

    private final String feedId;
    private final AgentCoreEngineConfiguration.StorageAdapterConfiguration config;
    private final HttpClientBuilder httpClientBuilder;
    private final Diagnostics diagnostics;
    // Visible for testing
    final long persistenceRefreshDelay;

    public AsyncInventoryStorage(
            String feedId,
            StorageAdapterConfiguration config,
            int autoDiscoveryScanPeriodSeconds,
            HttpClientBuilder httpClientBuilder,
            Diagnostics diagnostics) {
        super();
        this.feedId = feedId;
        this.config = config;
        this.httpClientBuilder = httpClientBuilder;
        this.diagnostics = diagnostics;
        // Data will be refreshed after a given delay even when it's been persisted,
        // to make sure it's not lost due to Metric's TTL
        persistenceRefreshDelay = TimeUnit.DAYS.toMillis(InventoryMetric.DATA_RETENTION)
                - TimeUnit.SECONDS.toMillis(autoDiscoveryScanPeriodSeconds)
                - 300000L; // Additional 5min safety delay (but is it really necessary?)
    }

    public void shutdown() {
        log.debugf("Shutting down async inventory storage");
    }

    private boolean needToRefresh(IDObject idObject) {
        return System.currentTimeMillis() > idObject.getPersistedTime() + persistenceRefreshDelay;
    }

    @Override
    public <L> void receivedEvent(InventoryEvent<L> event) {
        log.debug("Received inventory event");
        ResourceManager<L> resourceManager = event.getResourceManager();
        MonitoredEndpoint<EndpointConfiguration> endpoint = event.getSamplingService().getMonitoredEndpoint();
        String endpointTenantId = endpoint.getEndpointConfiguration().getTenantId();
        String tenantIdToUse = (endpointTenantId != null) ? endpointTenantId : config.getTenantId();
        Map<String, String> headers = getTenantHeader(tenantIdToUse);

        InventoryPayloadBuilder<L> bldr = new InventoryPayloadBuilder<>(tenantIdToUse, feedId);
        long timestamp = System.currentTimeMillis();

        // Since we know types never change during the lifetime of the agent, we don't have to process
        // types that have already been flagged as having been persisted.
        // We first must persist metric types then resource types (remember, there are no hierarchies,
        // all metric types and resource types are peers to one another).
        event.getResourceTypeManager().ifPresent(resourceTypeManager -> {
            List<ResourceType<L>> allResourceTypes = resourceTypeManager.getResourceTypesBreadthFirst();
            getMetricTypesToSync(allResourceTypes).forEach(mt -> {
                log.infof("Updating metric type: %s", mt.getID().getIDString());
                performMetricTypeSync(bldr.buildMetricType(mt), tenantIdToUse);
                // indicate we persisted the resource
                mt.setPersistedTime(timestamp);
            });

            allResourceTypes.stream()
                    .filter(this::needToRefresh)
                    .forEach(rt -> {
                        log.infof("Updating resource type: %s", rt.getID().getIDString());
                        performResourceTypeSync(bldr.buildResourceType(rt), tenantIdToUse);
                        // indicate we persisted the resource
                        rt.setPersistedTime(timestamp);
                    });
        });

        // build the JSON blueprints for the sync resource requests
        // Note that it is possible for a endpoint to define multiple root resources.
        // We have to sync each root resource separately.
        Set<ID> addedOrModifiedIds = event.getAddedOrModifiedRootResources().stream()
                .map(IDObject::getID)
                .collect(Collectors.toSet());
        resourceManager.getRootResources().forEach(r -> {
            // Ignore unmodified resources that doesn't need to be refreshed
            if (addedOrModifiedIds.contains(r.getID()) || needToRefresh(r)) {
                log.infof("Updating root resource: %s", r.getID().getIDString());
                performResourceSync(
                        bldr.buildRootResource(resourceManager, r),
                        tenantIdToUse,
                        resourceManager.size(r));
                // indicate we persisted the resource
                r.setPersistedTime(timestamp);
            }
        });

        // Remove root resources
        event.getRemovedRootResources().forEach(r -> {
            log.infof("Removing root resource: %s", r.getID().getIDString());
            InventoryMetric metric = InventoryMetric.resource(feedId, r.getID().getIDString(), null, null);
            deleteMetric(metric, headers);
        });
    }

    private <L> Collection<MeasurementType<L>> getMetricTypesToSync(List<ResourceType<L>> resourceTypes) {
        Map<String, MeasurementType<L>> measTypes = new HashMap<>();
        resourceTypes.forEach(rt -> {
            rt.getMetricTypes().stream()
                    .filter(this::needToRefresh)
                    .forEach(mt -> measTypes.put(mt.getID().getIDString(), mt));
            rt.getAvailTypes().stream()
                    .filter(this::needToRefresh)
                    .forEach(mt -> measTypes.put(mt.getID().getIDString(), mt));
        });
        return measTypes.values();
    }

    private void deleteMetric(InventoryMetric metric, Map<String, String> headers) {
        try {
            StringBuilder url = Util.getContextUrlString(config.getUrl(), config.getMetricsContext())
                    .append("strings/")
                    .append(metric.encodedName());
            Request request = this.httpClientBuilder.buildJsonDeleteRequest(url.toString(), headers);
            Call call = this.httpClientBuilder.getHttpClient().newCall(request);
            try (Response response = call.execute()) {
                if (!response.isSuccessful()) {
                    throw new Exception("status-code=[" + response.code() + "], reason=["
                            + response.message() + "], url=[" + request.url().toString() + "]");
                }
            }
        } catch (InterruptedException ie) {
            log.errorFailedToStoreInventoryData(ie);
            Thread.currentThread().interrupt(); // preserve interrupt
        } catch (Exception e) {
            log.errorFailedToStoreInventoryData(e);
            diagnostics.getStorageErrorRate().mark(1);
        }
    }

    private void performResourceSync(
            InventoryStructure<org.hawkular.inventory.api.model.Resource.Blueprint> resourceStructure,
            String tenantIdToUse, int totalResourceCount) {

        if (resourceStructure.getRoot() != null) {
            try {
                Map<String, Collection<String>> resourceTypes = extractResourceTypes(resourceStructure);
                Map<String, Collection<String>> metricTypes = extractMetricTypes(resourceStructure);
                InventoryMetric metric = InventoryMetric.resource(
                        feedId,
                        resourceStructure.getRoot().getId(),
                        resourceTypes.keySet(),
                        metricTypes.keySet());
                syncInventoryData(
                        metric,
                        new ExtendedInventoryStructure(resourceStructure, resourceTypes, metricTypes),
                        tenantIdToUse,
                        totalResourceCount);
            } catch (InterruptedException ie) {
                log.errorFailedToStoreInventoryData(ie);
                Thread.currentThread().interrupt(); // preserve interrupt
            } catch (Exception e) {
                log.errorFailedToStoreInventoryData(e);
                diagnostics.getStorageErrorRate().mark(1);
            }
        }
    }

    private static Map<String, Collection<String>> extractResourceTypes(
            InventoryStructure<org.hawkular.inventory.api.model.Resource.Blueprint> resourceStructure) {
        Map<String, Collection<String>> resourcesPerType = new HashMap<>();
        extractResourceTypesForNode(resourceStructure, RelativePath.empty().get(), resourcesPerType);
        return resourcesPerType;
    }

    private static void extractResourceTypesForNode(
            InventoryStructure<org.hawkular.inventory.api.model.Resource.Blueprint> resourceStructure,
            RelativePath nodePath,
            Map<String, Collection<String>> resourcesPerType) {
        Entity.Blueprint bp = resourceStructure.get(nodePath);
        if (bp instanceof org.hawkular.inventory.api.model.Resource.Blueprint) {
            CanonicalPath resourceTypePath = CanonicalPath.fromString(
                    ((org.hawkular.inventory.api.model.Resource.Blueprint)bp).getResourceTypePath());
            Collection<String> idsForType = resourcesPerType.computeIfAbsent(resourceTypePath.getSegment().getElementId(),
                    k -> new ArrayList<>());
            idsForType.add(nodePath.toString());
        }
        // Process children
        resourceStructure.getAllChildren(nodePath).forEach(child -> {
            SegmentType childSegmentType = Inventory.types().byBlueprint(child.getClass()).getSegmentType();
            RelativePath childPath = nodePath.modified().extend(childSegmentType, child.getId()).get();
            extractResourceTypesForNode(resourceStructure, childPath, resourcesPerType);
        });
    }

    private static Map<String, Collection<String>> extractMetricTypes(
            InventoryStructure<org.hawkular.inventory.api.model.Resource.Blueprint> resourceStructure) {
        Map<String, Collection<String>> metricsPerType = new HashMap<>();
        extractMetricTypesForNode(resourceStructure, RelativePath.empty().get(), metricsPerType);
        return metricsPerType;
    }

    private static void extractMetricTypesForNode(
            InventoryStructure<org.hawkular.inventory.api.model.Resource.Blueprint> resourceStructure,
            RelativePath nodePath,
            Map<String, Collection<String>> metricsPerType) {
        Entity.Blueprint bp = resourceStructure.get(nodePath);
        if (bp instanceof org.hawkular.inventory.api.model.Metric.Blueprint) {
            CanonicalPath metricTypePath = CanonicalPath.fromString(
                    ((org.hawkular.inventory.api.model.Metric.Blueprint)bp).getMetricTypePath());
            Collection<String> idsForType = metricsPerType.computeIfAbsent(metricTypePath.getSegment().getElementId(),
                    k -> new ArrayList<>());
            idsForType.add(nodePath.toString());
        }
        // Process children
        resourceStructure.getAllChildren(nodePath).forEach(child -> {
            SegmentType childSegmentType = Inventory.types().byBlueprint(child.getClass()).getSegmentType();
            RelativePath childPath = nodePath.modified().extend(childSegmentType, child.getId()).get();
            extractMetricTypesForNode(resourceStructure, childPath, metricsPerType);
        });
    }

    private void performResourceTypeSync(
            Offline<org.hawkular.inventory.api.model.ResourceType.Blueprint> resourceTypeStructure,
            String tenantIdToUse) {
        if (resourceTypeStructure.getRoot() != null) {
            try {
                InventoryMetric metric = InventoryMetric.resourceType(feedId, resourceTypeStructure.getRoot().getId());
                syncInventoryData(metric, new ExtendedInventoryStructure(resourceTypeStructure), tenantIdToUse, 1);
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
                syncInventoryData(metric, new ExtendedInventoryStructure(metricTypeStructure), tenantIdToUse, 1);
            } catch (InterruptedException ie) {
                log.errorFailedToStoreInventoryData(ie);
                Thread.currentThread().interrupt(); // preserve interrupt
            } catch (Exception e) {
                log.errorFailedToStoreInventoryData(e);
                diagnostics.getStorageErrorRate().mark(1);
            }
        }
    }

    private void syncInventoryData(InventoryMetric metric, ExtendedInventoryStructure inventoryStructure, String tenantId, int totalResourceCount)
            throws Exception {

        InventoryMetric.WithData metricChunks = compressAndChunk(metric, inventoryStructure);
        if (metricChunks.isEmpty()) {
            return;
        }

        Map<String, String> headers = getTenantHeader(tenantId);
        Timer.Context timer = diagnostics.getInventoryStorageRequestTimer().time();

        try {
            log.tracef("Syncing [%d] elements to inventory: headers=[%s] metric=[%s]", totalResourceCount, headers, metric.name());

            runSync(metricChunks, headers);
            tagMetric(metricChunks, headers);

            if (totalResourceCount > 0) {
                diagnostics.getInventoryRate().mark(totalResourceCount);
            }
        } finally {
            long durationNanos = timer.stop();

            if (log.isDebugEnabled()) {
                log.debugf("Took [%d]ms to sync [%d] elements to inventory",
                        TimeUnit.MILLISECONDS.convert(durationNanos, TimeUnit.NANOSECONDS),
                        totalResourceCount);
            }
        }
    }

    private void runSync(InventoryMetric.WithData metric, Map<String, String> headers) throws Exception {
        StringBuilder url = Util.getContextUrlString(config.getUrl(), config.getMetricsContext())
                .append("strings/")
                .append(metric.encodedName())
                .append("/raw");
        Request request = httpClientBuilder.buildJsonPostRequest(url.toString(), headers, metric.getPayload());
        Call call = httpClientBuilder.getHttpClient().newCall(request);
        try (Response response = call.execute()) {
            log.tracef("Received response while uploading chunks: code [%d]", response.code());
            if (!response.isSuccessful()) {
                throw new Exception("status-code=[" + response.code() + "], reason=["
                        + response.message() + "], url=[" + request.url().toString() + "]");
            }
        }
    }

    private void tagMetric(InventoryMetric.WithData metric, Map<String, String> headers) throws Exception {

        StringBuilder url = Util.getContextUrlString(config.getUrl(), config.getMetricsContext())
                .append("strings?overwrite=true");
        MetricDefinition def = metric.toMetricDefinition();

        Request request = httpClientBuilder.buildJsonPostRequest(url.toString(), headers, Util.toJson(def));
        Call call = httpClientBuilder.getHttpClient().newCall(request);
        try (Response response = call.execute()) {
            log.tracef("Received response while committing chunks: code [%d]", response.code());
            if (!response.isSuccessful()) {
                throw new Exception("status-code=[" + response.code() + "], reason=["
                        + response.message() + "], url=[" + request.url().toString() + "]");
            }
        }
    }

    private InventoryMetric.WithData compressAndChunk(InventoryMetric metric, ExtendedInventoryStructure inventoryStructure)
                throws IOException {
        String json = Util.toJson(inventoryStructure);
        ByteArrayOutputStream obj = new ByteArrayOutputStream();
        GZIPOutputStream gzip = new GZIPOutputStream(obj);
        gzip.write(json.getBytes("UTF-8"));
        gzip.close();
        byte[] compressed = obj.toByteArray();
        if (compressed.length <= CHUNKS_SIZE) {
            // Don't chunk
            return metric.full(compressed);
        }
        int pos = 0;
        List<byte[]> chunks = new ArrayList<>();
        while (pos < compressed.length) {
            int size = Math.min(CHUNKS_SIZE, compressed.length - pos);
            byte[] chunk = new byte[size];
            System.arraycopy(compressed, pos, chunk, 0, size);
            chunks.add(chunk);
            pos += size;
        }
        return metric.chunks(chunks, compressed.length);
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
