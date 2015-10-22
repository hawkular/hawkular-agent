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

import java.util.Map;
import java.util.concurrent.TimeUnit;

import org.hawkular.agent.monitor.inventory.ManagedServer;
import org.hawkular.agent.monitor.inventory.Name;
import org.hawkular.agent.monitor.inventory.TypeSets;
import org.hawkular.agent.monitor.inventory.dmr.DMRAvailType;
import org.hawkular.agent.monitor.inventory.dmr.DMRMetricType;
import org.hawkular.agent.monitor.inventory.dmr.DMRResourceType;
import org.hawkular.agent.monitor.inventory.jmx.JMXAvailType;
import org.hawkular.agent.monitor.inventory.jmx.JMXMetricType;
import org.hawkular.agent.monitor.inventory.jmx.JMXResourceType;
import org.hawkular.agent.monitor.inventory.platform.PlatformAvailType;
import org.hawkular.agent.monitor.inventory.platform.PlatformMetricType;
import org.hawkular.agent.monitor.inventory.platform.PlatformResourceType;

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

    private final GlobalConfiguration globalConfiguration;
    private final StorageAdapter storageAdapter;
    private final Diagnostics diagnostics;

    private final TypeSets<DMRResourceType, DMRMetricType, DMRAvailType> dmrTypeSets;
    private final TypeSets<JMXResourceType, JMXMetricType, JMXAvailType> jmxTypeSets;
    private final TypeSets<PlatformResourceType, PlatformMetricType, PlatformAvailType> platformTypeSets;

    private final Map<Name, ManagedServer> managedServersMap;
    public MonitorServiceConfiguration(GlobalConfiguration globalConfiguration,
            Diagnostics diagnostics, StorageAdapter storageAdapter,
            TypeSets<DMRResourceType, DMRMetricType, DMRAvailType> dmrTypeSets,
            TypeSets<JMXResourceType, JMXMetricType, JMXAvailType> jmxTypeSets,
            TypeSets<PlatformResourceType, PlatformMetricType, PlatformAvailType> platformTypeSets,
            Map<Name, ManagedServer> managedServersMap) {
        super();

        this.globalConfiguration = globalConfiguration;

        this.diagnostics = diagnostics;
        this.storageAdapter = storageAdapter;

        this.dmrTypeSets = dmrTypeSets;
        this.jmxTypeSets = jmxTypeSets;
        this.platformTypeSets = platformTypeSets;

        this.managedServersMap = managedServersMap;
    }

    public static class StorageAdapter {
        private final StorageReportTo type;
        private final String username;
        private final String password;
        private final String tenantId;
        private final String url;
        private final boolean useSSL;
        private final String serverOutboundSocketBindingRef;
        private final String accountsContext;
        private final String inventoryContext;
        private final String metricsContext;
        private final String feedcommContext;
        private final String keystorePath;
        private final String keystorePassword;
        private final String securityRealm;

        public StorageAdapter(StorageReportTo type, String username, String password, String tenantId, String url,
                boolean useSSL, String serverOutboundSocketBindingRef, String accountsContext, String inventoryContext,
                String metricsContext, String feedcommContext, String keystorePath, String keystorePassword,
                String securityRealm) {
            super();
            this.type = type;
            this.username = username;
            this.password = password;
            this.tenantId = tenantId;
            this.url = url;
            this.useSSL = useSSL;
            this.serverOutboundSocketBindingRef = serverOutboundSocketBindingRef;
            this.accountsContext = accountsContext;
            this.inventoryContext = inventoryContext;
            this.metricsContext = metricsContext;
            this.feedcommContext = feedcommContext;
            this.keystorePath = keystorePath;
            this.keystorePassword = keystorePassword;
            this.securityRealm = securityRealm;
        }

        public StorageReportTo getType() {
            return type;
        }

        public String getUsername() {
            return username;
        }

        public String getPassword() {
            return password;
        }

        public String getTenantId() {
            return tenantId;
        }

        public String getUrl() {
            return url;
        }

        public boolean isUseSSL() {
            return useSSL;
        }

        public String getServerOutboundSocketBindingRef() {
            return serverOutboundSocketBindingRef;
        }

        public String getAccountsContext() {
            return accountsContext;
        }

        public String getInventoryContext() {
            return inventoryContext;
        }

        public String getMetricsContext() {
            return metricsContext;
        }

        public String getFeedcommContext() {
            return feedcommContext;
        }

        public String getKeystorePath() {
            return keystorePath;
        }

        public String getKeystorePassword() {
            return keystorePassword;
        }

        public String getSecurityRealm() {
            return securityRealm;
        }

    }

    public static class Diagnostics {
        public static final Diagnostics EMPTY = new Diagnostics(false, null, 0, null);
        private final boolean enabled;
        private final DiagnosticsReportTo reportTo;
        private final int interval;
        private final TimeUnit timeUnits;

        public Diagnostics(boolean enabled, DiagnosticsReportTo reportTo, int interval, TimeUnit timeUnits) {
            super();
            this.enabled = enabled;
            this.reportTo = reportTo;
            this.interval = interval;
            this.timeUnits = timeUnits;
        }

        public boolean isEnabled() {
            return enabled;
        }

        public DiagnosticsReportTo getReportTo() {
            return reportTo;
        }

        public int getInterval() {
            return interval;
        }

        public TimeUnit getTimeUnits() {
            return timeUnits;
        }
    }

    public static class GlobalConfiguration {

        private final boolean subsystemEnabled;
        private final String apiJndi;
        private final int numMetricSchedulerThreads;
        private final int numAvailSchedulerThreads;
        private final int numDmrSchedulerThreads;
        private final int metricDispatcherBufferSize;
        private final int metricDispatcherMaxBatchSize;
        private final int availDispatcherBufferSize;
        private final int availDispatcherMaxBatchSize;

        public GlobalConfiguration(boolean subsystemEnabled, String apiJndi, int numMetricSchedulerThreads,
                int numAvailSchedulerThreads, int numDmrSchedulerThreads, int metricDispatcherBufferSize,
                int metricDispatcherMaxBatchSize, int availDispatcherBufferSize, int availDispatcherMaxBatchSize) {
            super();
            this.subsystemEnabled = subsystemEnabled;
            this.apiJndi = apiJndi;
            this.numMetricSchedulerThreads = numMetricSchedulerThreads;
            this.numAvailSchedulerThreads = numAvailSchedulerThreads;
            this.numDmrSchedulerThreads = numDmrSchedulerThreads;
            this.metricDispatcherBufferSize = metricDispatcherBufferSize;
            this.metricDispatcherMaxBatchSize = metricDispatcherMaxBatchSize;
            this.availDispatcherBufferSize = availDispatcherBufferSize;
            this.availDispatcherMaxBatchSize = availDispatcherMaxBatchSize;
        }

    }


    public TypeSets<DMRResourceType, DMRMetricType, DMRAvailType> getDmrTypeSets() {
        return dmrTypeSets;
    }

    public TypeSets<JMXResourceType, JMXMetricType, JMXAvailType> getJmxTypeSets() {
        return jmxTypeSets;
    }

    public TypeSets<PlatformResourceType, PlatformMetricType, PlatformAvailType> getPlatformTypeSets() {
        return platformTypeSets;
    }

    public GlobalConfiguration getGlobalConfiguration() {
        return globalConfiguration;
    }

    public StorageAdapter getStorageAdapter() {
        return storageAdapter;
    }

    public Diagnostics getDiagnostics() {
        return diagnostics;
    }

    public Map<Name, ManagedServer> getManagedServersMap() {
        return managedServersMap;
    }

    public boolean isSubsystemEnabled() {
        return globalConfiguration.subsystemEnabled;
    }

    public String getApiJndi() {
        return globalConfiguration.apiJndi;
    }

    public int getNumMetricSchedulerThreads() {
        return globalConfiguration.numMetricSchedulerThreads;
    }

    public int getNumAvailSchedulerThreads() {
        return globalConfiguration.numAvailSchedulerThreads;
    }

    public int getNumDmrSchedulerThreads() {
        return globalConfiguration.numDmrSchedulerThreads;
    }

    public int getMetricDispatcherBufferSize() {
        return globalConfiguration.metricDispatcherBufferSize;
    }

    public int getMetricDispatcherMaxBatchSize() {
        return globalConfiguration.metricDispatcherMaxBatchSize;
    }

    public int getAvailDispatcherBufferSize() {
        return globalConfiguration.availDispatcherBufferSize;
    }

    public int getAvailDispatcherMaxBatchSize() {
        return globalConfiguration.availDispatcherMaxBatchSize;
    }

}
