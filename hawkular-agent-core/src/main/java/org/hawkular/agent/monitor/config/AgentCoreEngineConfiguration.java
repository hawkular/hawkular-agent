/*
 * Copyright 2015-2017 Red Hat, Inc. and/or its affiliates
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
package org.hawkular.agent.monitor.config;

import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import org.hawkular.agent.monitor.api.Avail;
import org.hawkular.agent.monitor.inventory.ConnectionData;
import org.hawkular.agent.monitor.inventory.Name;
import org.hawkular.agent.monitor.inventory.TypeSets;
import org.hawkular.agent.monitor.log.AgentLoggers;
import org.hawkular.agent.monitor.log.MsgLogger;
import org.hawkular.agent.monitor.protocol.dmr.DMRNodeLocation;
import org.hawkular.agent.monitor.protocol.jmx.JMXNodeLocation;
import org.hawkular.agent.monitor.protocol.platform.PlatformNodeLocation;

/**
 * This represents the monitor service extension's XML configuration in a more consumable form.
 * To build this from the actual service model, see {@link MonitorServiceConfigurationBuilder}.
 */
public class AgentCoreEngineConfiguration {
    private static final MsgLogger log = AgentLoggers.getLogger(AgentCoreEngineConfiguration.class);

    public enum StorageReportTo {
        HAWKULAR, // stores metrics to a Hawkular system
        METRICS // stores metrics to just a Hawkular-Metrics standalone system
    }

    public enum DiagnosticsReportTo {
        LOG, // stores the diagnostics data as simple log messages
        STORAGE // stores the diagnostics as metrics to the storage adapter
    }

    /**
     * If feed ID is expicitly set to this value, it means the feed ID should be autogenerated at runtime.
     */
    private static final String FEED_ID_AUTOGENERATE = "autogenerate";

    public static class StorageAdapterConfiguration {
        private final StorageReportTo type;
        private final String username;
        private final String password;
        private final String tenantId;
        private final String feedId;
        private final String url;
        private final boolean useSSL;
        private final String serverOutboundSocketBindingRef;
        private final String metricsContext;
        private final String feedcommContext;
        private final String hawkularContext;
        private final String keystorePath;
        private final String keystorePassword;
        private final String securityRealm;
        private final int connectTimeoutSeconds;
        private final int readTimeoutSeconds;

        public StorageAdapterConfiguration(
                StorageReportTo type,
                String username,
                String password,
                String tenantId,
                String feedId,
                String url,
                boolean useSSL,
                String serverOutboundSocketBindingRef,
                String metricsContext,
                String feedcommContext,
                String hawkularContext,
                String keystorePath,
                String keystorePassword,
                String securityRealm,
                int connectTimeoutSeconds,
                int readTimeoutSeconds) {
            super();
            this.type = type;
            this.username = username;
            this.password = password;
            this.tenantId = tenantId;
            this.feedId = (FEED_ID_AUTOGENERATE.equalsIgnoreCase(feedId)) ? null : feedId;
            this.url = url;
            this.useSSL = useSSL;
            this.serverOutboundSocketBindingRef = serverOutboundSocketBindingRef;
            this.metricsContext = metricsContext;
            this.feedcommContext = feedcommContext;
            this.hawkularContext = hawkularContext;
            this.keystorePath = keystorePath;
            this.keystorePassword = keystorePassword;
            this.securityRealm = securityRealm;
            this.connectTimeoutSeconds = connectTimeoutSeconds;
            this.readTimeoutSeconds = readTimeoutSeconds;
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

        /**
         * This is the preconfigured feed ID and may not be set. If this is null (which under normal circumstances
         * it probably is) the agent will determine its feed ID at runtime. If this is not null, this is
         * the feed ID that the agent will be forced to use. It is here to allow a user to override the
         * runtime algorithm the agent uses to determine its feed ID.
         *
         * @return the feed ID to be used; may be <code>null</code>
         */
        public String getFeedId() {
            return feedId;
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

        public String getMetricsContext() {
            return metricsContext;
        }

        public String getHawkularContext() {
            return hawkularContext;
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

        public int getConnectTimeoutSeconds() {
            return connectTimeoutSeconds;
        }

        public int getReadTimeoutSeconds() {
            return readTimeoutSeconds;
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
        private final boolean immutable;
        private final boolean inContainer;
        private final String apiJndi;
        private final int autoDiscoveryScanPeriodSeconds;
        private final int minCollectionIntervalSeconds;
        private final int numDmrSchedulerThreads;
        private final int metricDispatcherBufferSize;
        private final int metricDispatcherMaxBatchSize;
        private final int availDispatcherBufferSize;
        private final int availDispatcherMaxBatchSize;
        private final int pingDispatcherPeriodSeconds;

        public GlobalConfiguration(boolean subsystemEnabled, boolean immutable, boolean inContainer, String apiJndi,
                int autoDiscoveryScanPeriodSeconds, int minCollectionIntervalSeconds, int numDmrSchedulerThreads,
                int metricDispatcherBufferSize, int metricDispatcherMaxBatchSize, int availDispatcherBufferSize,
                int availDispatcherMaxBatchSize, int pingDispatcherPeriodSeconds) {
            super();
            this.subsystemEnabled = subsystemEnabled;
            this.immutable = immutable;
            this.inContainer = inContainer;
            this.apiJndi = apiJndi;
            this.autoDiscoveryScanPeriodSeconds = autoDiscoveryScanPeriodSeconds;
            this.minCollectionIntervalSeconds = minCollectionIntervalSeconds;
            this.numDmrSchedulerThreads = numDmrSchedulerThreads;
            this.metricDispatcherBufferSize = metricDispatcherBufferSize;
            this.metricDispatcherMaxBatchSize = metricDispatcherMaxBatchSize;
            this.availDispatcherBufferSize = availDispatcherBufferSize;
            this.availDispatcherMaxBatchSize = availDispatcherMaxBatchSize;
            this.pingDispatcherPeriodSeconds = pingDispatcherPeriodSeconds;
        }

        public boolean isSubsystemEnabled() {
            return subsystemEnabled;
        }

        public boolean isImmutable() {
            return immutable;
        }

        public boolean isInContainer() {
            return inContainer;
        }

        public String getApiJndi() {
            return apiJndi;
        }

        public int getAutoDiscoveryScanPeriodSeconds() {
            return autoDiscoveryScanPeriodSeconds;
        }

        public int getMinCollectionIntervalSeconds() {
            return minCollectionIntervalSeconds;
        }

        public int getNumDmrSchedulerThreads() {
            return numDmrSchedulerThreads;
        }

        public int getMetricDispatcherBufferSize() {
            return metricDispatcherBufferSize;
        }

        public int getMetricDispatcherMaxBatchSize() {
            return metricDispatcherMaxBatchSize;
        }

        public int getAvailDispatcherBufferSize() {
            return availDispatcherBufferSize;
        }

        public int getAvailDispatcherMaxBatchSize() {
            return availDispatcherMaxBatchSize;
        }

        public int getPingDispatcherPeriodSeconds() {
            return pingDispatcherPeriodSeconds;
        }
    }

    public static class ProtocolConfiguration<L> {

        public static <L> Builder<L> builder() {
            return new Builder<L>();
        }

        public static class Builder<L> {
            private TypeSets<L> typeSets;
            private Map<String, EndpointConfiguration> endpoints = new LinkedHashMap<>();

            public Builder<L> endpoint(EndpointConfiguration endpoint) {
                endpoints.put(endpoint.getName(), endpoint);
                return this;
            }

            public Builder<L> typeSets(TypeSets<L> typeSets) {
                this.typeSets = typeSets;
                return this;
            }

            public ProtocolConfiguration<L> build() {
                for (EndpointConfiguration server : endpoints.values()) {
                    if (server.getResourceTypeSets() != null) {
                        for (Name resourceTypeSetName : server.getResourceTypeSets()) {
                            if (!typeSets.getResourceTypeSets().containsKey(resourceTypeSetName)) {
                                log.warnResourceTypeSetDoesNotExist(server.getName().toString(),
                                        resourceTypeSetName.toString());
                            }
                        }
                    }
                }

                return new ProtocolConfiguration<>(typeSets, endpoints);
            }
        }

        private final TypeSets<L> typeSets;
        private final Map<String, EndpointConfiguration> endpoints;

        public ProtocolConfiguration(TypeSets<L> typeSets,
                Map<String, EndpointConfiguration> managedServers) {
            super();
            this.typeSets = typeSets;
            this.endpoints = managedServers;
        }

        public TypeSets<L> getTypeSets() {
            return typeSets;
        }

        public Map<String, EndpointConfiguration> getEndpoints() {
            return endpoints;
        }
    }

    public static class AbstractEndpointConfiguration {

        public static class WaitFor {
            private final String resource;

            public WaitFor(String resource) {
                this.resource = resource;
            }

            /**
             * @return The resource to wait for (can be things like a DMR path or JMX object name).
             */
            public String getResource() {
                return this.resource;
            }
        }

        private final String name;
        private final boolean enabled;
        private final ConnectionData connectionData;
        private final String securityRealm;
        private final String tenantId;
        private final String metricIdTemplate;
        private final Map<String, String> metricTags;
        private final Map<String, ? extends Object> customData;
        private final List<WaitFor> waitForResources;

        public AbstractEndpointConfiguration(String name, boolean enabled, ConnectionData connectionData,
                String securityRealm, String tenantId, String metricIdTemplate, Map<String, String> metricTags,
                Map<String, ? extends Object> customData, List<WaitFor> waitForResources) {
            super();
            this.name = name;
            this.enabled = enabled;
            this.connectionData = connectionData;
            this.securityRealm = securityRealm;
            this.tenantId = tenantId;
            this.metricIdTemplate = metricIdTemplate;
            this.metricTags = metricTags;
            this.customData = (customData != null) ? Collections.unmodifiableMap(customData) : Collections.emptyMap();
            this.waitForResources = (waitForResources != null) ? Collections.unmodifiableList(waitForResources)
                    : Collections.emptyList();
        }

        public boolean isEnabled() {
            return enabled;
        }

        public String getName() {
            return name;
        }

        public ConnectionData getConnectionData() {
            return connectionData;
        }

        public String getSecurityRealm() {
            return securityRealm;
        }

        /**
         * @return if not null this is the tenant ID all metrics from this endpoint will be associated with. If null,
         *         the agent's tenant ID is used.
         */
        public String getTenantId() {
            return tenantId;
        }

        /**
         * @return if not null this is the template to use to create all metric IDs for this managed server.
         */
        public String getMetricIdTemplate() {
            return metricIdTemplate;
        }

        /**
         * @return if not null this is name/value pairs of tags to be associated with metrics that are
         *         collected from resources associated with this managed server. These tags are tokenized,
         *         meanining they can have substrings such as "%ManagedServerName" in them which are meant to
         *         be replaced at runtime with the values of the tokens.
         */
        public Map<String, String> getMetricTags() {
            return metricTags;
        }

        /**
         * @return custom information related to an endpoint. The endpoint service should know the value types.
         */
        public Map<String, ? extends Object> getCustomData() {
            return customData;
        }

        public boolean isLocal() {
            return connectionData == null;
        }

        /**
         * @return list of resources to wait for before starting to monitor the endpoint (will not be null)
         */
        public List<WaitFor> getWaitForResources() {
            return waitForResources;
        }
    }

    public static class EndpointConfiguration extends AbstractEndpointConfiguration {
        private final Collection<Name> resourceTypeSets;
        private final Avail setAvailOnShutdown;

        public EndpointConfiguration(String name, boolean enabled, Collection<Name> resourceTypeSets,
                ConnectionData connectionData, String securityRealm, Avail setAvailOnShutdown, String tenantId,
                String metricIdTemplate, Map<String, String> metricTags, Map<String, ? extends Object> customData,
                List<WaitFor> waitForResources) {
            super(name, enabled, connectionData, securityRealm, tenantId, metricIdTemplate, metricTags, customData,
                    waitForResources);
            this.resourceTypeSets = resourceTypeSets;
            this.setAvailOnShutdown = setAvailOnShutdown;
        }

        public Collection<Name> getResourceTypeSets() {
            return resourceTypeSets;
        }

        /**
         * @return if not null, when the agent shuts down all avail metrics for the endpoint will be set to this value.
         */
        public Avail getSetAvailOnShutdown() {
            return setAvailOnShutdown;
        }

    }

    private final GlobalConfiguration globalConfiguration;
    private final DiagnosticsConfiguration diagnostics;
    private final StorageAdapterConfiguration storageAdapter;
    private final ProtocolConfiguration<DMRNodeLocation> dmrConfiguration;
    private final ProtocolConfiguration<JMXNodeLocation> jmxConfiguration;
    private final ProtocolConfiguration<PlatformNodeLocation> platformConfiguration;

    public AgentCoreEngineConfiguration(GlobalConfiguration globalConfiguration,
            DiagnosticsConfiguration diagnostics,
            StorageAdapterConfiguration storageAdapter,
            ProtocolConfiguration<DMRNodeLocation> dmrConfiguration,
            ProtocolConfiguration<JMXNodeLocation> jmxConfiguration,
            ProtocolConfiguration<PlatformNodeLocation> platformConfiguration) {
        super();
        this.globalConfiguration = globalConfiguration;
        this.diagnostics = diagnostics;
        this.storageAdapter = storageAdapter;
        this.dmrConfiguration = dmrConfiguration;
        this.jmxConfiguration = jmxConfiguration;
        this.platformConfiguration = platformConfiguration;
    }

    public GlobalConfiguration getGlobalConfiguration() {
        return globalConfiguration;
    }

    public StorageAdapterConfiguration getStorageAdapter() {
        return storageAdapter;
    }

    public DiagnosticsConfiguration getDiagnostics() {
        return diagnostics;
    }

    public AgentCoreEngineConfiguration cloneWith(StorageAdapterConfiguration newStorageAdapter) {
        return new AgentCoreEngineConfiguration(globalConfiguration,
                diagnostics, newStorageAdapter, dmrConfiguration,
                jmxConfiguration, platformConfiguration);
    }

    public ProtocolConfiguration<DMRNodeLocation> getDmrConfiguration() {
        return dmrConfiguration;
    }

    public ProtocolConfiguration<JMXNodeLocation> getJmxConfiguration() {
        return jmxConfiguration;
    }

    public ProtocolConfiguration<PlatformNodeLocation> getPlatformConfiguration() {
        return platformConfiguration;
    }
}
