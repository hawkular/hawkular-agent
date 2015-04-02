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
package org.hawkular.agent.monitor.scheduler.storage;

import java.net.URL;
import java.util.Set;

import org.hawkular.agent.monitor.extension.MonitorServiceConfiguration;
import org.hawkular.agent.monitor.log.MsgLogger;
import org.hawkular.agent.monitor.scheduler.config.SchedulerConfiguration;
import org.hawkular.agent.monitor.scheduler.diagnostics.Diagnostics;
import org.hawkular.agent.monitor.scheduler.polling.Task;
import org.hawkular.agent.monitor.service.ServerIdentifiers;
import org.hawkular.bus.restclient.RestClient;

public class HawkularStorageAdapter implements StorageAdapter {

    private final KeyResolution keyResolution;
    private SchedulerConfiguration config;
    private Diagnostics diagnostics;
    private ServerIdentifiers selfId;

    public HawkularStorageAdapter() {
        this.keyResolution = new KeyResolution();
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
        return new HawkularDataPayloadBuilder();
    }

    @Override
    public void store(Set<DataPoint> datapoints) {
        if (datapoints == null || datapoints.isEmpty()) {
            return; // nothing to do
        }

        MetricDataPayloadBuilder payloadBuilder = createMetricDataPayloadBuilder();
        for (DataPoint datapoint : datapoints) {
            Task task = datapoint.getTask();
            String key = keyResolution.resolve(task);
            long timestamp = datapoint.getTimestamp();
            double value = datapoint.getValue();
            payloadBuilder.addDataPoint(key, timestamp, value);
        }

        store(payloadBuilder);

        return;
    }

    @Override
    public void store(MetricDataPayloadBuilder payloadBuilder) {

        String tenantId = this.selfId.getFullIdentifier();
        ((HawkularDataPayloadBuilder) payloadBuilder).setTenantId(tenantId);

        // for now, we need to send it twice:
        // 1) directly to metrics for storage
        // 2) on the message bus for further processing

        // send to metrics
        HawkularMetricsStorageAdapter metricsAdapter = new HawkularMetricsStorageAdapter();
        metricsAdapter.setDiagnostics(diagnostics);
        metricsAdapter.setSchedulerConfiguration(getSchedulerConfiguration());
        metricsAdapter.setSelfIdentifiers(selfId);
        metricsAdapter.store(((HawkularDataPayloadBuilder) payloadBuilder).toHawkularMetricsDataPayloadBuilder());

        // send to bus
        String jsonPayload = null;
        try {
            // build the URL to the bus interface
            MonitorServiceConfiguration.StorageAdapter storageAdapterConfig = config.getStorageAdapterConfig();
            StringBuilder urlStr = new StringBuilder(storageAdapterConfig.url);
            ensureEndsWithSlash(urlStr);
            if (storageAdapterConfig.context != null) {
                if (storageAdapterConfig.context.startsWith("/")) {
                    urlStr.append(storageAdapterConfig.context.substring(1));
                } else {
                    urlStr.append(storageAdapterConfig.context);
                }
                ensureEndsWithSlash(urlStr);
            }
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
            MsgLogger.LOG.errorFailedToStoreData(t, jsonPayload);
            diagnostics.getStorageErrorRate().mark(1);
        }
    }

    private void ensureEndsWithSlash(StringBuilder str) {
        if (str.length() == 0 || str.charAt(str.length() - 1) != '/') {
            str.append('/');
        }
    }
}