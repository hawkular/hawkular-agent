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

import java.util.Set;

import org.apache.http.HttpResponse;
import org.apache.http.StatusLine;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.ContentType;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.DefaultHttpClient;
import org.hawkular.agent.monitor.api.Avail;
import org.hawkular.agent.monitor.api.AvailDataPayloadBuilder;
import org.hawkular.agent.monitor.api.MetricDataPayloadBuilder;
import org.hawkular.agent.monitor.diagnostics.Diagnostics;
import org.hawkular.agent.monitor.extension.MonitorServiceConfiguration;
import org.hawkular.agent.monitor.log.MsgLogger;
import org.hawkular.agent.monitor.scheduler.config.SchedulerConfiguration;
import org.hawkular.agent.monitor.scheduler.polling.Task;
import org.hawkular.agent.monitor.service.ServerIdentifiers;
import org.jboss.logging.Logger;

public class HawkularMetricsStorageAdapter implements StorageAdapter {
    private static final Logger LOGGER = Logger.getLogger(HawkularMetricsStorageAdapter.class);

    private final HttpClient httpclient;
    private SchedulerConfiguration config;
    private Diagnostics diagnostics;
    private ServerIdentifiers selfId;

    public HawkularMetricsStorageAdapter() {
        this.httpclient = new DefaultHttpClient();
    }

    @Override
    public SchedulerConfiguration getSchedulerConfiguration() {
        return config;
    }

    @Override
    public void setSchedulerConfiguration(SchedulerConfiguration config) {
        this.config = config;
    }

    @Override
    public void setDiagnostics(Diagnostics diag) {
        this.diagnostics = diag;
    }

    @Override
    public void setSelfIdentifiers(ServerIdentifiers selfId) {
        this.selfId = selfId;
    }

    @Override
    public MetricDataPayloadBuilder createMetricDataPayloadBuilder() {
        return new HawkularMetricsMetricDataPayloadBuilder();
    }

    @Override
    public AvailDataPayloadBuilder createAvailDataPayloadBuilder() {
        return new HawkularMetricsAvailDataPayloadBuilder();
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
            payloadBuilder.addDataPoint(key, timestamp, value);
        }

        store(payloadBuilder);

        return;
    }

    @Override
    public void store(MetricDataPayloadBuilder payloadBuilder) {
        MonitorServiceConfiguration.StorageAdapter storageAdapterConfig = config.getStorageAdapterConfig();
        String jsonPayload = "?";
        HttpPost request = null;

        try {
            // get the payload in JSON format
            jsonPayload = payloadBuilder.toPayload().toString();

            // build the REST URL...

            // 1. start with the protocol, host, and port of the URL
            StringBuilder url = new StringBuilder(storageAdapterConfig.url);
            ensureEndsWithSlash(url);

            // 2. add any rest context path that is needed
            if (storageAdapterConfig.metricsContext != null) {
                if (storageAdapterConfig.metricsContext.startsWith("/")) {
                    url.append(storageAdapterConfig.metricsContext.substring(1));
                } else {
                    url.append(storageAdapterConfig.metricsContext);
                }
                ensureEndsWithSlash(url);
            }

            // 3. the REST URL requires the tenant ID next in the path
            String tenantId = this.selfId.getFullIdentifier();
            url.append(tenantId);

            // 4. now the final portion of the REST context
            url.append("/metrics/numeric/data");

            // now send the REST request
            request = new HttpPost(url.toString());
            request.setEntity(new StringEntity(jsonPayload, ContentType.APPLICATION_JSON));
            HttpResponse httpResponse = httpclient.execute(request);
            StatusLine statusLine = httpResponse.getStatusLine();

            // HTTP status of 200 means success; anything else is an error
            if (statusLine.getStatusCode() != 200) {
                throw new Exception("status-code=[" + statusLine.getStatusCode() + "], reason=["
                        + statusLine.getReasonPhrase() + "], url=[" + request.getURI() + "]");
            }

            // looks like everything stored successfully
            diagnostics.getMetricRate().mark(payloadBuilder.getNumberDataPoints());

        } catch (Throwable t) {
            MsgLogger.LOG.errorFailedToStoreMetricData(t, jsonPayload);
            diagnostics.getStorageErrorRate().mark(1);
        } finally {
            if (request != null) {
                request.releaseConnection();
            }
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
        MonitorServiceConfiguration.StorageAdapter storageAdapterConfig = config.getStorageAdapterConfig();
        String jsonPayload = "?";
        HttpPost request = null;

        try {
            // get the payload in JSON format
            jsonPayload = payloadBuilder.toPayload().toString();

            // build the REST URL...

            // 1. start with the protocol, host, and port of the URL
            StringBuilder url = new StringBuilder(storageAdapterConfig.url);
            ensureEndsWithSlash(url);

            // 2. add any rest context path that is needed
            if (storageAdapterConfig.metricsContext != null) {
                if (storageAdapterConfig.metricsContext.startsWith("/")) {
                    url.append(storageAdapterConfig.metricsContext.substring(1));
                } else {
                    url.append(storageAdapterConfig.metricsContext);
                }
                ensureEndsWithSlash(url);
            }

            // 3. the REST URL requires the tenant ID next in the path
            String tenantId = this.selfId.getFullIdentifier();
            url.append(tenantId);

            // 4. now the final portion of the REST context
            url.append("/metrics/availability/data");

            // now send the REST request
            request = new HttpPost(url.toString());
            request.setEntity(new StringEntity(jsonPayload, ContentType.APPLICATION_JSON));
            HttpResponse httpResponse = httpclient.execute(request);
            StatusLine statusLine = httpResponse.getStatusLine();

            // HTTP status of 200 means success; anything else is an error
            if (statusLine.getStatusCode() != 200) {
                throw new Exception("status-code=[" + statusLine.getStatusCode() + "], reason=["
                        + statusLine.getReasonPhrase() + "], url=[" + request.getURI() + "]");
            }

            // looks like everything stored successfully
            diagnostics.getAvailRate().mark(payloadBuilder.getNumberDataPoints());

        } catch (Throwable t) {
            MsgLogger.LOG.errorFailedToStoreAvailData(t, jsonPayload);
            diagnostics.getStorageErrorRate().mark(1);
        } finally {
            if (request != null) {
                request.releaseConnection();
            }
        }
    }

    private void ensureEndsWithSlash(StringBuilder str) {
        if (str.length() == 0 || str.charAt(str.length() - 1) != '/') {
            str.append('/');
        }
    }
}