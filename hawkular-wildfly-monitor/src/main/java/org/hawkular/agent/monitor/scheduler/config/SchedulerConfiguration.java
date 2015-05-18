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
package org.hawkular.agent.monitor.scheduler.config;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.hawkular.agent.monitor.extension.MonitorServiceConfiguration;

public class SchedulerConfiguration {

    public enum DiagnosticsReportTo {
        LOG, // stores the diagnostics data as simple log messages
        STORAGE // stores the diagnostics as metrics to the storage adapter
    }

    public enum StorageReportTo {
        HAWKULAR, // stores metrics to a Hawkular system
        METRICS // stores metrics to just a Hawkular-Metrics standalone system
    }

    public static final int DEFAULT_NUM_METRIC_SCHEDULER_THREADS = 2;
    public static final int DEFAULT_NUM_AVAIL_SCHEDULER_THREADS = 2;

    public static final int DEFAULT_METRIC_DISPATCHER_BUFFER_SIZE = 1000;
    public static final int DEFAULT_METRIC_DISPATCHER_MAX_BATCH_SIZE = 100;
    public static final int DEFAULT_AVAIL_DISPATCHER_BUFFER_SIZE = 500;
    public static final int DEFAULT_AVAIL_DISPATCHER_MAX_BATCH_SIZE = 50;

    private int metricSchedulerThreads = DEFAULT_NUM_METRIC_SCHEDULER_THREADS;
    private int availSchedulerThreads = DEFAULT_NUM_AVAIL_SCHEDULER_THREADS;

    private int metricDispatcherBufferSize = DEFAULT_METRIC_DISPATCHER_BUFFER_SIZE;
    private int metricDispatcherMaxBatchSize = DEFAULT_METRIC_DISPATCHER_MAX_BATCH_SIZE;

    private int availDispatcherBufferSize = DEFAULT_AVAIL_DISPATCHER_BUFFER_SIZE;
    private int availDispatcherMaxBatchSize = DEFAULT_AVAIL_DISPATCHER_MAX_BATCH_SIZE;

    private final Map<DMREndpoint, List<DMRPropertyReference>> dmrMetricsToBeCollected = new HashMap<>();
    private final Map<DMREndpoint, List<AvailDMRPropertyReference>> dmrAvailsToBeChecked = new HashMap<>();

    private MonitorServiceConfiguration.StorageAdapter storageAdapterConfig;
    private MonitorServiceConfiguration.Diagnostics diagnosticsConfig;

    public Map<DMREndpoint, List<DMRPropertyReference>> getDMRMetricsToBeCollected() {
        return Collections.unmodifiableMap(dmrMetricsToBeCollected);
    }

    public Map<DMREndpoint, List<AvailDMRPropertyReference>> getDMRAvailsToBeChecked() {
        return Collections.unmodifiableMap(dmrAvailsToBeChecked);
    }

    public void addMetricToBeCollected(DMREndpoint endpoint, DMRPropertyReference ref) {
        List<DMRPropertyReference> map = dmrMetricsToBeCollected.get(endpoint);
        if (map == null) {
            map = new ArrayList<DMRPropertyReference>();
            dmrMetricsToBeCollected.put(endpoint, map);
        }

        map.add(ref);
    }

    public void addAvailToBeChecked(DMREndpoint endpoint, AvailDMRPropertyReference ref) {
        List<AvailDMRPropertyReference> map = dmrAvailsToBeChecked.get(endpoint);
        if (map == null) {
            map = new ArrayList<AvailDMRPropertyReference>();
            dmrAvailsToBeChecked.put(endpoint, map);
        }

        map.add(ref);
    }

    public int getMetricSchedulerThreads() {
        return metricSchedulerThreads;
    }

    public void setMetricSchedulerThreads(int schedulerThreads) {
        this.metricSchedulerThreads = schedulerThreads;
    }

    public int getAvailSchedulerThreads() {
        return availSchedulerThreads;
    }

    public void setAvailSchedulerThreads(int schedulerThreads) {
        this.availSchedulerThreads = schedulerThreads;
    }

    public int getMetricDispatcherBufferSize() {
        return metricDispatcherBufferSize;
    }

    public void setMetricDispatcherBufferSize(int metricDispatcherBufferSize) {
        this.metricDispatcherBufferSize = metricDispatcherBufferSize;
    }

    public int getMetricDispatcherMaxBatchSize() {
        return metricDispatcherMaxBatchSize;
    }

    public void setMetricDispatcherMaxBatchSize(int metricDispatcherMaxBatchSize) {
        this.metricDispatcherMaxBatchSize = metricDispatcherMaxBatchSize;
    }

    public int getAvailDispatcherBufferSize() {
        return availDispatcherBufferSize;
    }

    public void setAvailDispatcherBufferSize(int availDispatcherBufferSize) {
        this.availDispatcherBufferSize = availDispatcherBufferSize;
    }

    public int getAvailDispatcherMaxBatchSize() {
        return availDispatcherMaxBatchSize;
    }

    public void setAvailDispatcherMaxBatchSize(int availDispatcherMaxBatchSize) {
        this.availDispatcherMaxBatchSize = availDispatcherMaxBatchSize;
    }

    public MonitorServiceConfiguration.StorageAdapter getStorageAdapterConfig() {
        return this.storageAdapterConfig;
    }

    public void setStorageAdapterConfig(MonitorServiceConfiguration.StorageAdapter config) {
        this.storageAdapterConfig = config;
    }

    public MonitorServiceConfiguration.Diagnostics getDiagnosticsConfig() {
        return diagnosticsConfig;
    }

    public void setDiagnosticsConfig(MonitorServiceConfiguration.Diagnostics config) {
        this.diagnosticsConfig = config;
    }
}

