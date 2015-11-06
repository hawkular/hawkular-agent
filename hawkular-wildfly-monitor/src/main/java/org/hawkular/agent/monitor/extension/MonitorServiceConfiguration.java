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

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import org.hawkular.agent.monitor.inventory.ManagedServer;
import org.hawkular.agent.monitor.inventory.Name;
import org.hawkular.agent.monitor.inventory.TypeSets;
import org.hawkular.agent.monitor.log.AgentLoggers;
import org.hawkular.agent.monitor.log.MsgLogger;
import org.hawkular.agent.monitor.protocol.dmr.DMRManagedServer;
import org.hawkular.agent.monitor.protocol.dmr.DMRNodeLocation;
import org.hawkular.agent.monitor.protocol.jmx.JMXNodeLocation;
import org.hawkular.agent.monitor.protocol.jmx.RemoteJMXManagedServer;
import org.hawkular.agent.monitor.protocol.platform.PlatformManagedServer;
import org.hawkular.agent.monitor.protocol.platform.PlatformNodeLocation;

/**
 * This represents the monitor service extension's XML configuration in a more consumable form.
 * To build this from the actual service model, see {@link MonitorServiceConfigurationBuilder}.
 */
public class MonitorServiceConfiguration {
    private static final MsgLogger log = AgentLoggers.getLogger(MonitorServiceConfiguration.class);

    public enum StorageReportTo {
        HAWKULAR, // stores metrics to a Hawkular system
        METRICS // stores metrics to just a Hawkular-Metrics standalone system
    }

    public enum DiagnosticsReportTo {
        LOG, // stores the diagnostics data as simple log messages
        STORAGE // stores the diagnostics as metrics to the storage adapter
    }

    public static class StorageAdapterConfiguration {
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

        public StorageAdapterConfiguration(StorageReportTo type, String username, String password, String tenantId,
                String url,
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

    public static class DiagnosticsConfiguration {
        public static final DiagnosticsConfiguration EMPTY = new DiagnosticsConfiguration(false, null, 0, null);
        private final boolean enabled;
        private final DiagnosticsReportTo reportTo;
        private final int interval;
        private final TimeUnit timeUnits;

        public DiagnosticsConfiguration(boolean enabled, DiagnosticsReportTo reportTo, int interval,
                TimeUnit timeUnits) {
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

    public static class ProtocolConfiguration<L, //
    S extends ManagedServer> {

        public static <L, S extends ManagedServer> Builder<L, S>
                builder() {
            return new Builder<L, S>();
        }

        public static class Builder<L, S extends ManagedServer> {
            private TypeSets<L> typeSets;
            private Map<Name, S> managedServers = new LinkedHashMap<>();

            public Builder<L, S> managedServer(S server) {
                managedServers.put(server.getName(), server);
                return this;
            }

            public Builder<L, S> typeSets(TypeSets<L> typeSets) {
                this.typeSets = typeSets;
                return this;
            }

            public ProtocolConfiguration<L, S> build() {
                for (S server : managedServers.values()) {
                    for (Name resourceTypeSetName : server.getResourceTypeSets()) {
                        if (!typeSets.getResourceTypeSets().containsKey(resourceTypeSetName)) {
                            log.warnResourceTypeSetDoesNotExist(server.getName().toString(),
                                    resourceTypeSetName.toString());
                        }
                    }
                }

                return new ProtocolConfiguration<>(typeSets, managedServers);
            }
        }

        private final TypeSets<L> typeSets;
        private final Map<Name, S> managedServers;

        public ProtocolConfiguration(TypeSets<L> typeSets,
                Map<Name, S> managedServers) {
            super();
            this.typeSets = typeSets;
            this.managedServers = managedServers;
        }

        public TypeSets<L> getTypeSets() {
            return typeSets;
        }

        public Map<Name, S> getManagedServers() {
            return managedServers;
        }
    }

    private final GlobalConfiguration globalConfiguration;
    private final DiagnosticsConfiguration diagnostics;
    private final StorageAdapterConfiguration storageAdapter;
    private final ProtocolConfiguration<DMRNodeLocation, DMRManagedServer> dmrConfiguration;
    private final ProtocolConfiguration<JMXNodeLocation, RemoteJMXManagedServer> //
    jmxConfiguration;
    private final ProtocolConfiguration<PlatformNodeLocation, PlatformManagedServer> //
    platformConfiguration;

    public MonitorServiceConfiguration(GlobalConfiguration globalConfiguration, DiagnosticsConfiguration diagnostics,
            StorageAdapterConfiguration storageAdapter,
            ProtocolConfiguration<DMRNodeLocation, DMRManagedServer> dmrConfiguration,
            ProtocolConfiguration<JMXNodeLocation, RemoteJMXManagedServer> jmxConfiguration,
            ProtocolConfiguration<PlatformNodeLocation, PlatformManagedServer> //
            platformConfiguration) {
        super();
        this.globalConfiguration = globalConfiguration;
        this.diagnostics = diagnostics;
        this.storageAdapter = storageAdapter;
        this.dmrConfiguration = dmrConfiguration;
        this.jmxConfiguration = jmxConfiguration;
        this.platformConfiguration = platformConfiguration;
    }

    //private final Map<Name, ManagedServer> managedServersMap;

    public GlobalConfiguration getGlobalConfiguration() {
        return globalConfiguration;
    }

    public StorageAdapterConfiguration getStorageAdapter() {
        return storageAdapter;
    }

    public DiagnosticsConfiguration getDiagnostics() {
        return diagnostics;
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

    public MonitorServiceConfiguration cloneWith(StorageAdapterConfiguration newStorageAdapter) {
        return new MonitorServiceConfiguration(globalConfiguration,
                diagnostics, newStorageAdapter, dmrConfiguration,
                jmxConfiguration, platformConfiguration);
    }

    public ProtocolConfiguration<DMRNodeLocation, DMRManagedServer> getDmrConfiguration() {
        return dmrConfiguration;
    }

    public ProtocolConfiguration<JMXNodeLocation, RemoteJMXManagedServer>
            getJmxConfiguration() {
        return jmxConfiguration;
    }

    public ProtocolConfiguration<PlatformNodeLocation, PlatformManagedServer>
            getPlatformConfiguration() {
        return platformConfiguration;
    }

}
