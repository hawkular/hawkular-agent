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

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import org.hawkular.agent.monitor.api.InventoryEvent;
import org.hawkular.agent.monitor.api.InventoryStorage;
import org.hawkular.agent.monitor.config.AgentCoreEngineConfiguration;
import org.hawkular.agent.monitor.config.AgentCoreEngineConfiguration.EndpointConfiguration;
import org.hawkular.agent.monitor.config.AgentCoreEngineConfiguration.StorageAdapterConfiguration;
import org.hawkular.agent.monitor.diagnostics.Diagnostics;
import org.hawkular.agent.monitor.inventory.ID;
import org.hawkular.agent.monitor.inventory.MeasurementInstance;
import org.hawkular.agent.monitor.inventory.MeasurementType;
import org.hawkular.agent.monitor.inventory.MonitoredEndpoint;
import org.hawkular.agent.monitor.inventory.NamedObject;
import org.hawkular.agent.monitor.inventory.Operation;
import org.hawkular.agent.monitor.inventory.OperationParam;
import org.hawkular.agent.monitor.inventory.Resource;
import org.hawkular.agent.monitor.inventory.ResourceType;
import org.hawkular.agent.monitor.inventory.ResourceTypeManager;
import org.hawkular.agent.monitor.log.AgentLoggers;
import org.hawkular.agent.monitor.log.MsgLogger;
import org.hawkular.agent.monitor.util.Util;
import org.hawkular.inventory.api.model.Inventory;
import org.hawkular.inventory.api.model.Metric;
import org.hawkular.inventory.api.model.MetricUnit;
import org.hawkular.inventory.api.model.RawResource;
import org.jboss.as.controller.client.helpers.MeasurementUnit;

import com.codahale.metrics.Timer;

import okhttp3.Call;
import okhttp3.Request;
import okhttp3.Response;

/**
 * An {@link InventoryStorage} that syncs inventory that has been discovered.
 *
 * @author John Mazzitelli
 */
public class AsyncInventoryStorage implements InventoryStorage {

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
        this.feedId = feedId;
        this.config = config;
        this.httpClientBuilder = httpClientBuilder;
        this.diagnostics = diagnostics;
    }

    public void shutdown() {
        log.debugf("Shutting down async inventory storage");
    }

    @Override
    public <L> void receivedEvent(InventoryEvent<L> event) {
        try {
            MonitoredEndpoint<EndpointConfiguration> endpoint = event.getSamplingService().getMonitoredEndpoint();
            log.debugf("Received inventory event for endpoint: %s", endpoint);

            long timestamp = System.currentTimeMillis();

            List<RawResource> importResources = new ArrayList<>();
            List<org.hawkular.inventory.api.model.ResourceType> importTypes = new ArrayList<>();
            Inventory importData = new Inventory(importResources, importTypes);

            // Since we know types never change during the lifetime of the agent, we don't have to process
            // types that have already been flagged as having been persisted.
            // Remember, there are no hierarchies, all resource types are peers to one another.
            if (event.getResourceTypeManager().isPresent()) {
                ResourceTypeManager<L> resourceTypeManager = event.getResourceTypeManager().get();
                List<ResourceType<L>> allResourceTypes = resourceTypeManager.getResourceTypesBreadthFirst();
                for (ResourceType<L> rt : allResourceTypes) {
                    if (rt.isPersisted()) {
                        continue;
                    }
                    log.debugf("Updating resource type: %s", rt.getID().getIDString());

                    org.hawkular.inventory.api.model.ResourceType.Builder rtb = org.hawkular.inventory.api.model.ResourceType
                            .builder();
                    rtb.id(getInventoryId(rt));
                    rt.getProperties().forEach((k, v) -> {
                        rtb.property(k, v.toString());
                    });

                    for (Operation<L> op : rt.getOperations()) {
                        org.hawkular.inventory.api.model.Operation.Builder ob = org.hawkular.inventory.api.model.Operation
                                .builder();
                        ob.name(op.getName().getNameString());
                        for (OperationParam param : op.getParameters()) {
                            Map<String, String> metadata = new HashMap<>();
                            metadata.put("description", param.getDescription());
                            metadata.put("defaultValue", param.getDefaultValue());
                            metadata.put("type", param.getType());
                            ob.parameter(param.getName(), metadata);
                        }
                        rtb.operation(ob.build());
                    }
                    org.hawkular.inventory.api.model.ResourceType resourceType = rtb.build();
                    log.debugf("Adding resource type: %s", resourceType);
                    importTypes.add(resourceType);

                    // indicate we persisted the resource
                    rt.setPersistedTime(timestamp);
                }
            }

            // Build the JSON for the resources.
            // Note that it is possible for a endpoint to define multiple root resources.
            event.getAddedOrModified().forEach(r -> {
                log.debugf("Updating resource: %s", r.getID().getIDString());

                addResourceToImport(r, importResources);

                // indicate we persisted the resource
                r.setPersistedTime(timestamp);
                r.getMetrics().forEach(m -> m.setPersistedTime(timestamp));
            });

            // Remove deleted resources
            if (!event.getRemoved().isEmpty()) {
                List<String> resourcesToRemove = event.getRemoved().stream()
                        .map(r -> r.getID().getIDString())
                        .collect(Collectors.toList());
                log.debugf("Removing resources: %s", resourcesToRemove);
                deleteInventoryData(resourcesToRemove);
            }
            if (!importResources.isEmpty() || !importTypes.isEmpty()) {
                importInventoryData(importData);
            }

        } catch (Exception e) {
            log.errorf("Failed to process inventory event: ", e.toString());
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

    private <L> void addResourceToImport(Resource<L> r, List<RawResource> importResources) {
        String parentId = (r.getParent() != null) ? r.getParent().getID().getIDString() : null;
        RawResource.Builder rb = RawResource.builder()
                .id(getInventoryId(r))
                .parentId(parentId)
                .feedId(feedId)
                .typeId(getInventoryId(r.getResourceType()))
                .name(r.getName().getNameString());
        r.getResourceConfigurationProperties().forEach(c -> rb.config(c.getName().getNameString(), c.getValue()));
        r.getProperties().forEach((k, v) -> rb.property(k, v.toString()));
        r.getMetrics().forEach(m -> rb.metric(buildMetric(m, m.getType().getMetricUnits())));
        r.getAvails().forEach(m -> rb.metric(buildMetric(m, null)));
        RawResource resource = rb.build();
        log.debugf("Adding resource: %s", resource);
        importResources.add(resource);
    }

    private <L, M extends MeasurementType<L>> Metric buildMetric(MeasurementInstance<L, M> m,
                                                                 MeasurementUnit metricUnits) {
        Metric.Builder mb = Metric.builder()
                .name(m.getName().getNameString())
                .type(m.getType().getName().getNameString());
        if (metricUnits != null) {
            mb.unit(MetricUnit.valueOf(metricUnits.name()));
        }
        m.getProperties().forEach((k, v) -> mb.property(k, v.toString()));
        return mb.build();
    }

    private void importInventoryData(Inventory importData) throws Exception {
        try {
            log.tracef("Importing [%d] resources to inventory", importData.getResources().size());
            sendImportRestRequest(importData);
            diagnostics.getInventoryRate().mark(importData.getResources().size());
        } catch (InterruptedException ie) {
            log.errorFailedToStoreInventoryData(ie);
            Thread.currentThread().interrupt(); // preserve interrupt
            diagnostics.getStorageErrorRate().mark(1);
            throw ie;
        } catch (Exception e) {
            log.errorFailedToStoreInventoryData(e);
            diagnostics.getStorageErrorRate().mark(1);
            throw e;
        }
    }

    private void deleteInventoryData(List<String> resourceIds) throws Exception {
        try {
            log.tracef("Deleting resources [%s] from inventory", resourceIds);
            sendDeleteResourcesRestRequest(resourceIds);
        } catch (InterruptedException ie) {
            log.errorFailedToStoreInventoryData(ie);
            Thread.currentThread().interrupt(); // preserve interrupt
            diagnostics.getStorageErrorRate().mark(1);
            throw ie;
        } catch (Exception e) {
            log.errorFailedToStoreInventoryData(e);
            diagnostics.getStorageErrorRate().mark(1);
            throw e;
        }
    }

    private void sendImportRestRequest(Inventory importData) throws Exception {
        StringBuilder url = Util.getContextUrlString(config.getUrl(), config.getInventoryContext())
                .append("import");
        Request request = httpClientBuilder.buildJsonPostRequest(url.toString(), null, Util.toJson(importData));
        Call call = httpClientBuilder.getHttpClient().newCall(request);

        Timer.Context timer = diagnostics.getInventoryStorageRequestTimer().time();
        try (Response response = call.execute()) {
            log.tracef("Received response while importing inventory: code [%d]", response.code());
            if (!response.isSuccessful()) {
                throw new Exception("status-code=[" + response.code() + "], reason=["
                        + response.message() + "], url=[" + request.url().toString() + "]");
            }
        } finally {
            long durationNanos = timer.stop();
            if (log.isDebugEnabled()) {
                log.debugf("Import inventory request time: [%d]ms",
                        TimeUnit.MILLISECONDS.convert(durationNanos, TimeUnit.NANOSECONDS));
            }
        }
    }

    private void sendDeleteResourcesRestRequest(List<String> resourceId) throws Exception {
        String ids = resourceId.stream()
                .map(id -> "ids=" + Util.urlEncode(id))
                .collect(Collectors.joining("&"));
        StringBuilder url = Util.getContextUrlString(config.getUrl(), config.getInventoryContext())
                .append("resources?").append(ids);
        Request request = httpClientBuilder.buildJsonDeleteRequest(url.toString(), null);
        Call call = httpClientBuilder.getHttpClient().newCall(request);

        Timer.Context timer = diagnostics.getInventoryStorageRequestTimer().time();
        try (Response response = call.execute()) {
            log.tracef("Received response while deleting inventory: code [%d]", response.code());
            if (!response.isSuccessful()) {
                throw new Exception("status-code=[" + response.code() + "], reason=["
                        + response.message() + "], url=[" + request.url().toString() + "]");
            }
        } finally {
            long durationNanos = timer.stop();
            if (log.isDebugEnabled()) {
                log.debugf("Delete inventory request time: [%d]ms",
                        TimeUnit.MILLISECONDS.convert(durationNanos, TimeUnit.NANOSECONDS));
            }
        }
    }
}
