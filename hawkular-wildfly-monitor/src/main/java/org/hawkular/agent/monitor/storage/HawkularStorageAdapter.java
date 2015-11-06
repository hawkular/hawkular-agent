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

import org.hawkular.agent.monitor.api.Avail;
import org.hawkular.agent.monitor.api.AvailDataPayloadBuilder;
import org.hawkular.agent.monitor.api.InventoryEvent;
import org.hawkular.agent.monitor.api.MetricDataPayloadBuilder;
import org.hawkular.agent.monitor.diagnostics.Diagnostics;
import org.hawkular.agent.monitor.extension.MonitorServiceConfiguration;
import org.hawkular.agent.monitor.inventory.MonitoredEndpoint;
import org.hawkular.agent.monitor.log.AgentLoggers;
import org.hawkular.agent.monitor.log.MsgLogger;

public class HawkularStorageAdapter implements StorageAdapter {
    @SuppressWarnings("unused")
    private static final MsgLogger log = AgentLoggers.getLogger(HawkularStorageAdapter.class);
    private MonitorServiceConfiguration.StorageAdapterConfiguration config;
    private Diagnostics diagnostics;
    private HttpClientBuilder httpClientBuilder;
    private AsyncInventoryStorage inventoryStorage;

    public HawkularStorageAdapter() {
    }

    @Override
    public void initialize(
            org.hawkular.agent.monitor.extension.MonitorServiceConfiguration.StorageAdapterConfiguration config,
            Diagnostics diag, HttpClientBuilder httpClientBuilder) {
        this.config = config;
        this.diagnostics = diag;
        this.httpClientBuilder = httpClientBuilder;
        this.inventoryStorage = new AsyncInventoryStorage(config, httpClientBuilder, diagnostics);
    }

    @Override
    public MonitorServiceConfiguration.StorageAdapterConfiguration getStorageAdapterConfiguration() {
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
            long timestamp = datapoint.getTimestamp();
            double value = datapoint.getValue();
            payloadBuilder.addDataPoint(datapoint.getKey(), timestamp, value, datapoint.getMetricType());
        }

        store(payloadBuilder);

        return;
    }

    @Override
    public void store(MetricDataPayloadBuilder payloadBuilder) {

        // send to metrics
        MetricsOnlyStorageAdapter metricsAdapter = new MetricsOnlyStorageAdapter();
        metricsAdapter.initialize(getStorageAdapterConfiguration(), diagnostics, httpClientBuilder);
        metricsAdapter.store(payloadBuilder);

        // looks like everything stored successfully
        // the metrics storage adapter already did this, so don't duplicate the stats here
        //diagnostics.getMetricRate().mark(payloadBuilder.getNumberDataPoints());
    }

    @Override
    public void storeAvails(Set<AvailDataPoint> datapoints) {
        if (datapoints == null || datapoints.isEmpty()) {
            return; // nothing to do
        }

        AvailDataPayloadBuilder payloadBuilder = createAvailDataPayloadBuilder();
        for (AvailDataPoint datapoint : datapoints) {
            long timestamp = datapoint.getTimestamp();
            Avail value = datapoint.getValue();
            payloadBuilder.addDataPoint(datapoint.getKey(), timestamp, value);
        }

        store(payloadBuilder);

        return;
    }

    @Override
    public void store(AvailDataPayloadBuilder payloadBuilder) {

        // send to metrics
        MetricsOnlyStorageAdapter metricsAdapter = new MetricsOnlyStorageAdapter();
        metricsAdapter.initialize(getStorageAdapterConfiguration(), diagnostics, httpClientBuilder);
        metricsAdapter.store(payloadBuilder);

        // looks like everything stored successfully
        // the metrics storage adapter already did this, so don't duplicate the stats here
        //diagnostics.getAvailRate().mark(payloadBuilder.getNumberDataPoints());
    }

    @Override
    public <L, E extends MonitoredEndpoint> void discoverAllFinished(InventoryEvent<L, E> event) {
        inventoryStorage.discoverAllFinished(event);
    }

    @Override
    public <L, E extends MonitoredEndpoint> void resourcesAdded(InventoryEvent<L, E> event) {
        inventoryStorage.resourcesAdded(event);
    }

    @Override
    public <L, E extends MonitoredEndpoint> void resourceRemoved(InventoryEvent<L, E> event) {
        inventoryStorage.resourceRemoved(event);
    }

    @Override
    public void shutdown() {
        inventoryStorage.shutdown();
    }
}
