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

import java.net.URL;
import java.util.Set;

import org.hawkular.agent.monitor.api.Avail;
import org.hawkular.agent.monitor.api.AvailDataPayloadBuilder;
import org.hawkular.agent.monitor.api.MetricDataPayloadBuilder;
import org.hawkular.agent.monitor.diagnostics.Diagnostics;
import org.hawkular.agent.monitor.extension.MonitorServiceConfiguration;
import org.hawkular.agent.monitor.inventory.Resource;
import org.hawkular.agent.monitor.log.AgentLoggers;
import org.hawkular.agent.monitor.log.MsgLogger;
import org.hawkular.agent.monitor.scheduler.polling.Task;
import org.hawkular.agent.monitor.service.ServerIdentifiers;
import org.hawkular.agent.monitor.service.Util;
import org.hawkular.bus.restclient.RestClient;

public class HawkularStorageAdapter implements StorageAdapter {
    private static final MsgLogger log = AgentLoggers.getLogger(HawkularStorageAdapter.class);
    private MonitorServiceConfiguration.StorageAdapter config;
    private Diagnostics diagnostics;
    private ServerIdentifiers selfId;
    private HttpClientBuilder httpClientBuilder;
    private AsyncInventoryStorage inventoryStorage;

    public HawkularStorageAdapter() {
    }

    @Override
    public void initialize(org.hawkular.agent.monitor.extension.MonitorServiceConfiguration.StorageAdapter config,
            Diagnostics diag, ServerIdentifiers selfId, HttpClientBuilder httpClientBuilder) {
        this.config = config;
        this.diagnostics = diag;
        this.selfId = selfId;
        this.httpClientBuilder = httpClientBuilder;
        this.inventoryStorage = new AsyncInventoryStorage(selfId, config, httpClientBuilder, diagnostics);
    }

    @Override
    public MonitorServiceConfiguration.StorageAdapter getStorageAdapterConfiguration() {
        return config;
    }

    @Override
    public MetricDataPayloadBuilder createMetricDataPayloadBuilder() {
        return new HawkularMetricDataPayloadBuilder();
    }

    @Override
    public AvailDataPayloadBuilder createAvailDataPayloadBuilder() {
        return new HawkularAvailDataPayloadBuilder();
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

        String tenantId = this.config.tenantId;
        ((HawkularMetricDataPayloadBuilder) payloadBuilder).setTenantId(tenantId);

        // for now, we need to send it twice:
        // 1) directly to metrics for storage
        // 2) on the message bus for further processing

        // send to metrics
        MetricsOnlyStorageAdapter metricsAdapter = new MetricsOnlyStorageAdapter();
        metricsAdapter.initialize(getStorageAdapterConfiguration(), diagnostics, selfId, httpClientBuilder);
        metricsAdapter.store(((HawkularMetricDataPayloadBuilder) payloadBuilder)
                .toMetricsOnlyMetricDataPayloadBuilder());

        // send to bus
        String jsonPayload = null;
        try {
            // build the URL to the bus interface
            StringBuilder urlStr = Util.getContextUrlString(this.config.url, this.config.busContext);
            urlStr = Util.convertToNonSecureUrl(urlStr.toString());
            URL url = new URL(urlStr.toString());

            // build the bus client
            RestClient busClient = new RestClient(url);

            // send the message to the bus
            jsonPayload = payloadBuilder.toPayload().toString();
            busClient.postTopicMessage("HawkularMetricData", jsonPayload, null);

            // looks like everything stored successfully
            // the metrics storage adapter already did this, so don't duplicate the stats here
            //diagnostics.getMetricRate().mark(payloadBuilder.getNumberDataPoints());

        } catch (Throwable t) {
            log.errorFailedToStoreMetricData(t, jsonPayload);
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

        String tenantId = this.config.tenantId;
        ((HawkularAvailDataPayloadBuilder) payloadBuilder).setTenantId(tenantId);

        // for now, we need to send it twice:
        // 1) directly to h-metrics for storage
        // 2) on the message bus for further processing

        // send to h-metrics
        MetricsOnlyStorageAdapter metricsAdapter = new MetricsOnlyStorageAdapter();
        metricsAdapter.initialize(getStorageAdapterConfiguration(), diagnostics, selfId, httpClientBuilder);
        metricsAdapter.store(((HawkularAvailDataPayloadBuilder) payloadBuilder)
                .toMetricsOnlyAvailDataPayloadBuilder());

        // send to bus
        String jsonPayload = null;
        try {
            // build the URL to the bus interface
            StringBuilder urlStr = Util.getContextUrlString(this.config.url, this.config.busContext);
            urlStr = Util.convertToNonSecureUrl(urlStr.toString());
            URL url = new URL(urlStr.toString());

            // build the bus client
            RestClient busClient = new RestClient(url);

            // send the message to the bus
            jsonPayload = payloadBuilder.toPayload().toString();
            busClient.postTopicMessage("HawkularAvailData", jsonPayload, null);

            // looks like everything stored successfully
            // the metrics storage adapter already did this, so don't duplicate the stats here
            //diagnostics.getAvailRate().mark(payloadBuilder.getNumberDataPoints());

        } catch (Throwable t) {
            log.errorFailedToStoreAvailData(t, jsonPayload);
            diagnostics.getStorageErrorRate().mark(1);
        }
    }

    @Override
    public void storeResource(Resource<?, ?, ?, ?, ?> resource) {
        inventoryStorage.storeResource(resource);
    }

    @Override
    public void shutdown() {
        inventoryStorage.shutdown();
    }
}
