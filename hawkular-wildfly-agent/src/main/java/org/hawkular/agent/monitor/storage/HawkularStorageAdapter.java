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

import java.io.IOException;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import org.hawkular.agent.monitor.api.Avail;
import org.hawkular.agent.monitor.api.AvailDataPayloadBuilder;
import org.hawkular.agent.monitor.api.InventoryEvent;
import org.hawkular.agent.monitor.api.MetricDataPayloadBuilder;
import org.hawkular.agent.monitor.diagnostics.Diagnostics;
import org.hawkular.agent.monitor.extension.MonitorServiceConfiguration;
import org.hawkular.agent.monitor.log.AgentLoggers;
import org.hawkular.agent.monitor.log.MsgLogger;
import org.hawkular.agent.monitor.util.Util;

import com.squareup.okhttp.Callback;
import com.squareup.okhttp.Request;
import com.squareup.okhttp.Response;

public class HawkularStorageAdapter implements StorageAdapter {
    private static final MsgLogger log = AgentLoggers.getLogger(HawkularStorageAdapter.class);

    private String feedId;
    private MonitorServiceConfiguration.StorageAdapterConfiguration config;
    private Diagnostics diagnostics;
    private HttpClientBuilder httpClientBuilder;
    private AsyncInventoryStorage inventoryStorage;
    private Map<String, String> agentTenantIdHeader;

    public HawkularStorageAdapter() {
    }

    @Override
    public void initialize(
            String feedId,
            MonitorServiceConfiguration.StorageAdapterConfiguration config,
            Diagnostics diag,
            HttpClientBuilder httpClientBuilder) {
        this.feedId = feedId;
        this.config = config;
        this.diagnostics = diag;
        this.httpClientBuilder = httpClientBuilder;
        this.agentTenantIdHeader = getTenantHeader(config.getTenantId());

        switch (config.getType()) {
            case HAWKULAR:
                // We are in a full hawkular environment - so we will integrate with inventory.
                this.inventoryStorage = new AsyncInventoryStorage(feedId, config, httpClientBuilder,
                        agentTenantIdHeader, diagnostics);
                break;

            case METRICS:
                // We are only integrating with standalone Hawkular Metrics which does not support inventory.
                this.inventoryStorage = null;
                break;

            default:
                throw new IllegalArgumentException("Invalid type. Please report this bug: " + config.getType());
        }
    }

    @Override
    public MonitorServiceConfiguration.StorageAdapterConfiguration getStorageAdapterConfiguration() {
        return config;
    }

    @Override
    public MetricDataPayloadBuilder createMetricDataPayloadBuilder() {
        return new MetricDataPayloadBuilderImpl();
    }

    @Override
    public AvailDataPayloadBuilder createAvailDataPayloadBuilder() {
        return new AvailDataPayloadBuilderImpl();
    }

    @Override
    public void storeMetrics(Set<MetricDataPoint> datapoints, long waitMillis) {
        if (datapoints == null || datapoints.isEmpty()) {
            return; // nothing to do
        }

        Map<String, Set<MetricDataPoint>> byTenantId = separateByTenantId(datapoints);
        for (Map.Entry<String, Set<MetricDataPoint>> entry : byTenantId.entrySet()) {
            String tenantId = entry.getKey();
            Set<MetricDataPoint> tenantDataPoints = entry.getValue();

            MetricDataPayloadBuilder payloadBuilder = createMetricDataPayloadBuilder();
            payloadBuilder.setTenantId(tenantId);

            for (MetricDataPoint datapoint : tenantDataPoints) {
                long timestamp = datapoint.getTimestamp();
                double value = datapoint.getValue();
                payloadBuilder.addDataPoint(datapoint.getKey(), timestamp, value, datapoint.getMetricType());
            }

            store(payloadBuilder, waitMillis);
        }

        return;
    }

    @Override
    public void store(MetricDataPayloadBuilder payloadBuilder, long waitMillis) {
        String jsonPayload = "?";

        try {
            // Determine what tenant header to use.
            // If no tenant override is specified in the payload, use the agent's tenant ID.
            Map<String, String> tenantIdHeader;
            String metricTenantId = payloadBuilder.getTenantId();
            if (metricTenantId == null) {
                tenantIdHeader = agentTenantIdHeader;
            } else {
                tenantIdHeader = getTenantHeader(metricTenantId);
            }

            // get the payload in JSON format
            jsonPayload = payloadBuilder.toPayload().toString();

            // build the REST URL...
            StringBuilder url = Util.getContextUrlString(config.getUrl(), config.getMetricsContext());
            url.append("metrics/data");

            // now send the REST request
            Request request = this.httpClientBuilder.buildJsonPostRequest(url.toString(), tenantIdHeader, jsonPayload);

            final CountDownLatch latch = (waitMillis <= 0) ? null : new CountDownLatch(1);
            final String jsonPayloadFinal = jsonPayload;
            this.httpClientBuilder.getHttpClient().newCall(request).enqueue(new Callback() {
                @Override
                public void onFailure(Request request, IOException e) {
                    try {
                        log.errorFailedToStoreMetricData(e, jsonPayloadFinal);
                        diagnostics.getStorageErrorRate().mark(1);
                    } finally {
                        if (latch != null) {
                            latch.countDown();
                        }
                    }
                }

                @Override
                public void onResponse(Response response) throws IOException {
                    try {
                        // HTTP status of 200 means success; anything else is an error
                        if (response.code() != 200) {
                            IOException e = new IOException("status-code=[" + response.code() + "], reason=["
                                    + response.message() + "], url=[" + request.urlString() + "]");
                            log.errorFailedToStoreMetricData(e, jsonPayloadFinal);
                            diagnostics.getStorageErrorRate().mark(1);
                        } else {
                            // looks like everything stored successfully
                            diagnostics.getMetricRate().mark(payloadBuilder.getNumberDataPoints());
                        }
                    } finally {
                        if (latch != null) {
                            latch.countDown();
                        }
                    }
                }
            });

            if (latch != null) {
                latch.await(waitMillis, TimeUnit.MILLISECONDS);
            }

        } catch (Throwable t) {
            log.errorFailedToStoreMetricData(t, jsonPayload);
            diagnostics.getStorageErrorRate().mark(1);
        }
    }

    @Override
    public void storeAvails(Set<AvailDataPoint> datapoints, long waitMillis) {
        if (datapoints == null || datapoints.isEmpty()) {
            return; // nothing to do
        }

        Map<String, Set<AvailDataPoint>> byTenantId = separateByTenantId(datapoints);
        for (Map.Entry<String, Set<AvailDataPoint>> entry : byTenantId.entrySet()) {
            String tenantId = entry.getKey();
            Set<AvailDataPoint> tenantDataPoints = entry.getValue();

            AvailDataPayloadBuilder payloadBuilder = createAvailDataPayloadBuilder();
            payloadBuilder.setTenantId(tenantId);

            for (AvailDataPoint datapoint : tenantDataPoints) {
                long timestamp = datapoint.getTimestamp();
                Avail value = datapoint.getValue();
                payloadBuilder.addDataPoint(datapoint.getKey(), timestamp, value);
            }

            store(payloadBuilder, waitMillis);
        }

        return;
    }

    @Override
    public void store(AvailDataPayloadBuilder payloadBuilder, long waitMillis) {
        String jsonPayload = "?";

        try {
            // Determine what tenant header to use.
            // If no tenant override is specified in the payload, use the agent's tenant ID.
            Map<String, String> tenantIdHeader;
            String metricTenantId = payloadBuilder.getTenantId();
            if (metricTenantId == null) {
                tenantIdHeader = agentTenantIdHeader;
            } else {
                tenantIdHeader = getTenantHeader(metricTenantId);
            }

            // get the payload in JSON format
            jsonPayload = payloadBuilder.toPayload().toString();

            // build the REST URL...
            StringBuilder url = Util.getContextUrlString(config.getUrl(), config.getMetricsContext());
            url.append("availability/data");

            // now send the REST request
            Request request = this.httpClientBuilder.buildJsonPostRequest(url.toString(), tenantIdHeader, jsonPayload);

            final CountDownLatch latch = (waitMillis <= 0) ? null : new CountDownLatch(1);
            final String jsonPayloadFinal = jsonPayload;
            this.httpClientBuilder.getHttpClient().newCall(request).enqueue(new Callback() {
                @Override
                public void onFailure(Request request, IOException e) {
                    try {
                        log.errorFailedToStoreAvailData(e, jsonPayloadFinal);
                        diagnostics.getStorageErrorRate().mark(1);
                    } finally {
                        if (latch != null) {
                            latch.countDown();
                        }
                    }
                }

                @Override
                public void onResponse(Response response) throws IOException {
                    try {
                        // HTTP status of 200 means success; anything else is an error
                        if (response.code() != 200) {
                            IOException e = new IOException("status-code=[" + response.code() + "], reason=["
                                    + response.message() + "], url=[" + request.urlString() + "]");
                            log.errorFailedToStoreAvailData(e, jsonPayloadFinal);
                            diagnostics.getStorageErrorRate().mark(1);
                        } else {
                            // looks like everything stored successfully
                            diagnostics.getAvailRate().mark(payloadBuilder.getNumberDataPoints());
                        }
                    } finally {
                        if (latch != null) {
                            latch.countDown();
                        }
                    }
                }
            });

            if (latch != null) {
                latch.await(waitMillis, TimeUnit.MILLISECONDS);
            }

        } catch (Throwable t) {
            log.errorFailedToStoreAvailData(t, jsonPayload);
            diagnostics.getStorageErrorRate().mark(1);
        }
    }

    @Override
    public <L> void resourcesAdded(InventoryEvent<L> event) {
        if (inventoryStorage != null) {
            inventoryStorage.resourcesAdded(event);
        }
    }

    @Override
    public <L> void resourcesRemoved(InventoryEvent<L> event) {
        if (inventoryStorage != null) {
            inventoryStorage.resourcesRemoved(event);
        }
    }

    @Override
    public void shutdown() {
        if (inventoryStorage != null) {
            inventoryStorage.shutdown();
        }
    }

    /**
     * Builds the header necessary for the tenant ID.
     *
     * @param tenantId the tenant ID string - this is the value of the returned map
     * @return the tenant header consisting of the header key and the value
     */
    private Map<String, String> getTenantHeader(String tenantId) {
        return Collections.singletonMap("Hawkular-Tenant", tenantId);
    }

    private <T extends DataPoint> Map<String, Set<T>> separateByTenantId(Set<T> dataPoints) {
        Map<String, Set<T>> byTenant = new HashMap<>();
        for (T dp : dataPoints) {
            Set<T> tenantDataPoints = byTenant.get(dp.getTenantId());
            if (tenantDataPoints == null) {
                tenantDataPoints = new HashSet<>();
                byTenant.put(dp.getTenantId(), tenantDataPoints);
            }
            tenantDataPoints.add(dp);
        }
        return byTenant;
    }
}
