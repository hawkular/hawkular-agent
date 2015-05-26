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

import java.net.MalformedURLException;
import java.net.URL;
import java.util.Set;

import org.hawkular.agent.monitor.api.Avail;
import org.hawkular.agent.monitor.api.AvailDataPayloadBuilder;
import org.hawkular.agent.monitor.api.InventoryDataPayloadBuilder;
import org.hawkular.agent.monitor.api.MetricDataPayloadBuilder;
import org.hawkular.agent.monitor.diagnostics.Diagnostics;
import org.hawkular.agent.monitor.extension.MonitorServiceConfiguration;
import org.hawkular.agent.monitor.inventory.Resource;
import org.hawkular.agent.monitor.inventory.ResourceType;
import org.hawkular.agent.monitor.log.MsgLogger;
import org.hawkular.agent.monitor.scheduler.polling.Task;
import org.hawkular.agent.monitor.service.ServerIdentifiers;
import org.hawkular.bus.restclient.RestClient;
import org.jboss.logging.Logger;

public class HawkularStorageAdapter implements StorageAdapter {
    private static final Logger LOGGER = Logger.getLogger(HawkularStorageAdapter.class);

    private MonitorServiceConfiguration.StorageAdapter config;
    private Diagnostics diagnostics;
    private ServerIdentifiers selfId;

    public HawkularStorageAdapter() {
    }

    @Override
    public MonitorServiceConfiguration.StorageAdapter getStorageAdapterConfiguration() {
        return config;
    }

    @Override
    public void setStorageAdapterConfiguration(MonitorServiceConfiguration.StorageAdapter config) {
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
        return new HawkularMetricDataPayloadBuilder();
    }

    @Override
    public AvailDataPayloadBuilder createAvailDataPayloadBuilder() {
        return new HawkularAvailDataPayloadBuilder();
    }

    @Override
    public InventoryDataPayloadBuilder createInventoryDataPayloadBuilder() {
        return new HawkularInventoryDataPayloadBuilder();
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
            // append feed ID to the key
            key = String.format("%s~%s", this.selfId.getFullIdentifier(), key);
            long timestamp = datapoint.getTimestamp();
            double value = datapoint.getValue();
            payloadBuilder.addDataPoint(key, timestamp, value);
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
        metricsAdapter.setDiagnostics(diagnostics);
        metricsAdapter.setStorageAdapterConfiguration(getStorageAdapterConfiguration());
        metricsAdapter.setSelfIdentifiers(selfId);
        metricsAdapter.store(((HawkularMetricDataPayloadBuilder) payloadBuilder)
                .toMetricsOnlyMetricDataPayloadBuilder());

        // send to bus
        String jsonPayload = null;
        try {
            // build the URL to the bus interface
            StringBuilder urlStr = getContextUrlString(config.busContext);
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
            // append feed ID to the key
            key = String.format("%s~%s", this.selfId.getFullIdentifier(), key);
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
        metricsAdapter.setDiagnostics(diagnostics);
        metricsAdapter.setStorageAdapterConfiguration(getStorageAdapterConfiguration());
        metricsAdapter.setSelfIdentifiers(selfId);
        metricsAdapter.store(((HawkularAvailDataPayloadBuilder) payloadBuilder)
                .toMetricsOnlyAvailDataPayloadBuilder());

        // send to bus
        String jsonPayload = null;
        try {
            // build the URL to the bus interface
            StringBuilder urlStr = getContextUrlString(config.busContext);
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
            MsgLogger.LOG.errorFailedToStoreAvailData(t, jsonPayload);
            diagnostics.getStorageErrorRate().mark(1);
        }
    }

    @Override
    public void storeResourceType(ResourceType<?, ?> resourceType) {
        // TODO
        InventoryDataPayloadBuilder payloadBulider = createInventoryDataPayloadBuilder();
        payloadBulider.addResourceType(resourceType);
        store(payloadBulider);
    }

    @Override
    public void storeResource(Resource<?, ?, ?, ?> resource) {
        // TODO
        InventoryDataPayloadBuilder payloadBulider = createInventoryDataPayloadBuilder();
        payloadBulider.addResource(resource);
        store(payloadBulider);
    }

    @Override
    public void store(InventoryDataPayloadBuilder payloadBuilder) {
        // TODO
        String tenantId = this.config.tenantId;
        ((HawkularInventoryDataPayloadBuilder) payloadBuilder).setTenantId(tenantId);

        try {
            // build the URL to the inventory interface
            StringBuilder urlStr = getContextUrlString(config.inventoryContext);
            URL url = new URL(urlStr.toString());

            LOGGER.warnf("STORE TO INVENTORY: url=[%s], payload=[%s]", url, payloadBuilder.toPayload());
        } catch (Throwable t) {
            MsgLogger.LOG.errorFailedToStoreInventoryData(t);
            diagnostics.getStorageErrorRate().mark(1);
        }
    }

    private StringBuilder getContextUrlString(String context) throws MalformedURLException {
        StringBuilder urlStr = new StringBuilder(config.url);
        ensureEndsWithSlash(urlStr);
        if (context != null) {
            if (context.startsWith("/")) {
                urlStr.append(context.substring(1));
            } else {
                urlStr.append(context);
            }
            ensureEndsWithSlash(urlStr);
        }
        return urlStr;
    }

    private void ensureEndsWithSlash(StringBuilder str) {
        if (str.length() == 0 || str.charAt(str.length() - 1) != '/') {
            str.append('/');
        }
    }
}