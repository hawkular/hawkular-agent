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
package org.hawkular.agent.monitor.scheduler;

import java.util.Set;

import org.hawkular.agent.monitor.extension.MonitorServiceConfiguration;

public class SchedulerConfiguration {

    public static final int DEFAULT_METRIC_DISPATCHER_BUFFER_SIZE = 1000;
    public static final int DEFAULT_METRIC_DISPATCHER_MAX_BATCH_SIZE = 100;
    public static final int DEFAULT_AVAIL_DISPATCHER_BUFFER_SIZE = 500;
    public static final int DEFAULT_AVAIL_DISPATCHER_MAX_BATCH_SIZE = 50;
    public static final int DEFAULT_PING_DISPATCHER_PERIOD_SECONDS = 60;

    private int metricDispatcherBufferSize = DEFAULT_METRIC_DISPATCHER_BUFFER_SIZE;
    private int metricDispatcherMaxBatchSize = DEFAULT_METRIC_DISPATCHER_MAX_BATCH_SIZE;

    private int availDispatcherBufferSize = DEFAULT_AVAIL_DISPATCHER_BUFFER_SIZE;
    private int availDispatcherMaxBatchSize = DEFAULT_AVAIL_DISPATCHER_MAX_BATCH_SIZE;

    private int pingDispatcherPeriodSeconds = DEFAULT_PING_DISPATCHER_PERIOD_SECONDS;

    private MonitorServiceConfiguration.StorageAdapterConfiguration storageAdapterConfig;
    private MonitorServiceConfiguration.DiagnosticsConfiguration diagnosticsConfig;

    private String pingDispatcherFeedId;
    private Set<String> pingDispatcherTenantIds;

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

    public int getPingDispatcherPeriodSeconds() {
        return pingDispatcherPeriodSeconds;
    }

    public void setPingDispatcherPeriodSeconds(int pingDispatcherPeriodSeconds) {
        this.pingDispatcherPeriodSeconds = pingDispatcherPeriodSeconds;
    }

    public String getPingDispatcherFeedId() {
        return pingDispatcherFeedId;
    }

    public void setPingDispatcherFeedId(String pingDispatcherFeedId) {
        this.pingDispatcherFeedId = pingDispatcherFeedId;
    }

    public Set<String> getPingDispatcherTenantIds() {
        return pingDispatcherTenantIds;
    }

    public void setPingDispatcherTenantIds(Set<String> pingDispatcherTenantIds) {
        this.pingDispatcherTenantIds = pingDispatcherTenantIds;
    }

    public MonitorServiceConfiguration.StorageAdapterConfiguration getStorageAdapterConfig() {
        return this.storageAdapterConfig;
    }

    public void setStorageAdapterConfig(MonitorServiceConfiguration.StorageAdapterConfiguration config) {
        this.storageAdapterConfig = config;
    }

    public MonitorServiceConfiguration.DiagnosticsConfiguration getDiagnosticsConfig() {
        return diagnosticsConfig;
    }

    public void setDiagnosticsConfig(MonitorServiceConfiguration.DiagnosticsConfiguration config) {
        this.diagnosticsConfig = config;
    }

}
