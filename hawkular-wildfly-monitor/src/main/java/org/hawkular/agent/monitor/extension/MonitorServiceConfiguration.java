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
package org.hawkular.agent.monitor.extension;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import org.hawkular.agent.monitor.inventory.ManagedServer;
import org.hawkular.agent.monitor.inventory.Name;
import org.hawkular.agent.monitor.inventory.dmr.DMRAvailTypeSet;
import org.hawkular.agent.monitor.inventory.dmr.DMRMetricTypeSet;
import org.hawkular.agent.monitor.inventory.dmr.DMRResourceTypeSet;

/**
 * This represents the monitor service extension's XML configuration in a more consumable form.
 * To build this from the actual service model, see {@link MonitorServiceConfigurationBuilder}.
 */
public class MonitorServiceConfiguration {

    public enum StorageReportTo {
        HAWKULAR, // stores metrics to a Hawkular system
        METRICS // stores metrics to just a Hawkular-Metrics standalone system
    }

    public enum DiagnosticsReportTo {
        LOG, // stores the diagnostics data as simple log messages
        STORAGE // stores the diagnostics as metrics to the storage adapter
    }

    public boolean subsystemEnabled;
    public String apiJndi;
    public int numMetricSchedulerThreads;
    public int numAvailSchedulerThreads;
    public int numDmrSchedulerThreads;
    public int metricDispatcherBufferSize;
    public int metricDispatcherMaxBatchSize;
    public int availDispatcherBufferSize;
    public int availDispatcherMaxBatchSize;
    public StorageAdapter storageAdapter = new StorageAdapter();
    public Diagnostics diagnostics = new Diagnostics();
    public Map<Name, DMRMetricTypeSet> dmrMetricTypeSetMap = new HashMap<>();
    public Map<Name, DMRAvailTypeSet> dmrAvailTypeSetMap = new HashMap<>();
    public Map<Name, DMRResourceTypeSet> dmrResourceTypeSetMap = new HashMap<>();
    public Map<Name, ManagedServer> managedServersMap = new HashMap<>();

    public static class StorageAdapter {
        public StorageReportTo type;
        public String username;
        public String password;
        public String tenantId;
        public String url;
        public boolean useSSL;
        public String serverOutboundSocketBindingRef;
        public String accountsContext;
        public String busContext;
        public String inventoryContext;
        public String metricsContext;
        public String feedcommContext;
        public String keystorePath;
        public String keystorePassword;
    }

    public static class Diagnostics {
        public DiagnosticsReportTo reportTo;
        public boolean enabled;
        public int interval;
        public TimeUnit timeUnits;
    }
}
