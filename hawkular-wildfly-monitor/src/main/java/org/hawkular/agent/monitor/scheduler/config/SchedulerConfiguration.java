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
import java.util.List;

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

    private final List<DMRPropertyReference> dmrMetricsToBeCollected = new ArrayList<>();
    private final List<AvailDMRPropertyReference> dmrAvailsToBeChecked = new ArrayList<>();

    private int schedulerThreads = 2;

    private MonitorServiceConfiguration.StorageAdapter storageAdapterConfig;
    private MonitorServiceConfiguration.Diagnostics diagnosticsConfig;

    public List<DMRPropertyReference> getDMRMetricsToBeCollected() {
        return Collections.unmodifiableList(dmrMetricsToBeCollected);
    }

    public List<AvailDMRPropertyReference> getDMRAvailsToBeChecked() {
        return Collections.unmodifiableList(dmrAvailsToBeChecked);
    }

    public void addMetricToBeCollected(DMRPropertyReference ref) {
        dmrMetricsToBeCollected.add(ref);
    }

    public void addAvailToBeChecked(AvailDMRPropertyReference ref) {
        dmrAvailsToBeChecked.add(ref);
    }

    public int getSchedulerThreads() {
        return schedulerThreads;
    }

    public void setSchedulerThreads(int schedulerThreads) {
        this.schedulerThreads = schedulerThreads;
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

