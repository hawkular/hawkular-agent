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

import java.io.IOException;
import java.util.Collections;
import java.util.Set;

import org.hawkular.agent.monitor.api.Avail;
import org.hawkular.agent.monitor.api.AvailDataPayloadBuilder;
import org.hawkular.agent.monitor.api.MetricDataPayloadBuilder;
import org.hawkular.agent.monitor.diagnostics.Diagnostics;
import org.hawkular.agent.monitor.extension.MonitorServiceConfiguration;
import org.hawkular.agent.monitor.inventory.Resource;
import org.hawkular.agent.monitor.inventory.ResourceType;
import org.hawkular.agent.monitor.log.MsgLogger;
import org.hawkular.agent.monitor.scheduler.polling.Task;
import org.hawkular.agent.monitor.service.ServerIdentifiers;
import org.hawkular.agent.monitor.service.Util;

import com.squareup.okhttp.Callback;
import com.squareup.okhttp.Request;
import com.squareup.okhttp.Response;

public class MetricsOnlyStorageAdapter implements StorageAdapter {
    private MonitorServiceConfiguration.StorageAdapter config;
    private Diagnostics diagnostics;
    private ServerIdentifiers selfId;
    private HttpClientBuilder httpClientBuilder;

    public MetricsOnlyStorageAdapter() {
    }

    @Override
    public void initialize(org.hawkular.agent.monitor.extension.MonitorServiceConfiguration.StorageAdapter config,
            Diagnostics diag, ServerIdentifiers selfId, HttpClientBuilder httpClientBuilder) {
        this.config = config;
        this.diagnostics = diag;
        this.selfId = selfId;
        this.httpClientBuilder = httpClientBuilder;
    }

    @Override
    public MonitorServiceConfiguration.StorageAdapter getStorageAdapterConfiguration() {
        return config;
    }

    @Override
    public MetricDataPayloadBuilder createMetricDataPayloadBuilder() {
        return new MetricsOnlyMetricDataPayloadBuilder();
    }

    @Override
    public AvailDataPayloadBuilder createAvailDataPayloadBuilder() {
        return new MetricsOnlyAvailDataPayloadBuilder();
    }

    @Override
    public void storeMetrics(Set<MetricDataPoint> datapoints) {
        if (datapoints == null || datapoints.isEmpty()) {
            return; // nothing to do
        }

        MetricDataPayloadBuilder payloadBuilder = createMetricDataPayloadBuilder();
        for (MetricDataPoint datapoint : datapoints) {
            Task task = datapoint.getTask();
            String key = task.getKeyGenerator().generateKey(task);
            long timestamp = datapoint.getTimestamp();
            double value = datapoint.getValue();
            payloadBuilder.addDataPoint(key, timestamp, value, datapoint.getMetricType());
        }

        store(payloadBuilder);

        return;
    }

    @Override
    public void store(MetricDataPayloadBuilder payloadBuilder) {
        String jsonPayload = "?";

        try {
            // get the payload in JSON format
            jsonPayload = payloadBuilder.toPayload().toString();

            // build the REST URL...
            StringBuilder url = Util.getContextUrlString(config.url, config.metricsContext);
            url.append("metrics/data");

            // now send the REST request
            Request request = this.httpClientBuilder.buildJsonPostRequest(url.toString(),
                    Collections.singletonMap("Hawkular-Tenant", config.tenantId), jsonPayload);

            final String jsonPayloadFinal = jsonPayload;
            this.httpClientBuilder.getHttpClient().newCall(request).enqueue(new Callback() {
                @Override
                public void onFailure(Request request, IOException e) {
                    MsgLogger.LOG.errorFailedToStoreMetricData(e, jsonPayloadFinal);
                    diagnostics.getStorageErrorRate().mark(1);
                }

                @Override
                public void onResponse(Response response) throws IOException {
                    // HTTP status of 200 means success; anything else is an error
                    if (response.code() != 200) {
                        IOException e = new IOException("status-code=[" + response.code() + "], reason=["
                                + response.message() + "], url=[" + request.urlString() + "]");
                        MsgLogger.LOG.errorFailedToStoreMetricData(e, jsonPayloadFinal);
                        diagnostics.getStorageErrorRate().mark(1);
                        throw e;
                    }

                    // looks like everything stored successfully
                    diagnostics.getMetricRate().mark(payloadBuilder.getNumberDataPoints());

                }
            });

        } catch (Throwable t) {
            MsgLogger.LOG.errorFailedToStoreMetricData(t, jsonPayload);
            diagnostics.getStorageErrorRate().mark(1);
        }
    }

    @Override
    public void storeAvails(Set<AvailDataPoint> datapoints) {
        if (datapoints == null || datapoints.isEmpty()) {
            return; // nothing to do
        }

        AvailDataPayloadBuilder payloadBuilder = createAvailDataPayloadBuilder();
        for (AvailDataPoint datapoint : datapoints) {
            Task task = datapoint.getTask();
            String key = task.getKeyGenerator().generateKey(task);
            long timestamp = datapoint.getTimestamp();
            Avail value = datapoint.getValue();
            payloadBuilder.addDataPoint(key, timestamp, value);
        }

        store(payloadBuilder);

        return;
    }

    @Override
    public void store(AvailDataPayloadBuilder payloadBuilder) {
        String jsonPayload = "?";

        try {
            // get the payload in JSON format
            jsonPayload = payloadBuilder.toPayload().toString();

            // build the REST URL...
            StringBuilder url = Util.getContextUrlString(config.url, config.metricsContext);
            url.append("availability/data");

            // now send the REST request
            Request request = this.httpClientBuilder.buildJsonPostRequest(url.toString(),
                    Collections.singletonMap("Hawkular-Tenant", config.tenantId), jsonPayload);

            final String jsonPayloadFinal = jsonPayload;
            this.httpClientBuilder.getHttpClient().newCall(request).enqueue(new Callback() {
                @Override
                public void onFailure(Request request, IOException e) {
                    MsgLogger.LOG.errorFailedToStoreAvailData(e, jsonPayloadFinal);
                    diagnostics.getStorageErrorRate().mark(1);
                }

                @Override
                public void onResponse(Response response) throws IOException {
                    // HTTP status of 200 means success; anything else is an error
                    if (response.code() != 200) {
                        IOException e = new IOException("status-code=[" + response.code() + "], reason=["
                                + response.message() + "], url=[" + request.urlString() + "]");
                        MsgLogger.LOG.errorFailedToStoreAvailData(e, jsonPayloadFinal);
                        diagnostics.getStorageErrorRate().mark(1);
                        throw e;
                    }

                    // looks like everything stored successfully
                    diagnostics.getAvailRate().mark(payloadBuilder.getNumberDataPoints());

                }
            });

        } catch (Throwable t) {
            MsgLogger.LOG.errorFailedToStoreAvailData(t, jsonPayload);
            diagnostics.getStorageErrorRate().mark(1);
        }
    }

    @Override
    public void storeResourceType(ResourceType<?, ?, ?, ?> resourceType) {
        throw new UnsupportedOperationException("Standalone Hawkular Metrics does not support inventory");
    }

    @Override
    public void storeResource(Resource<?, ?, ?, ?, ?> resourceType) {
        throw new UnsupportedOperationException("Standalone Hawkular Metrics does not support inventory");
    }
}
