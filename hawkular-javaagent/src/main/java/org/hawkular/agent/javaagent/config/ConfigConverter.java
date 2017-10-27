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
package org.hawkular.agent.javaagent.config;

import java.net.URI;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.management.ObjectName;

import org.hawkular.agent.monitor.config.AgentCoreEngineConfiguration;
import org.hawkular.agent.monitor.config.AgentCoreEngineConfiguration.AbstractEndpointConfiguration.WaitFor;
import org.hawkular.agent.monitor.config.AgentCoreEngineConfiguration.DiagnosticsConfiguration;
import org.hawkular.agent.monitor.config.AgentCoreEngineConfiguration.EndpointConfiguration;
import org.hawkular.agent.monitor.config.AgentCoreEngineConfiguration.GlobalConfiguration;
import org.hawkular.agent.monitor.config.AgentCoreEngineConfiguration.MetricsExporterConfiguration;
import org.hawkular.agent.monitor.config.AgentCoreEngineConfiguration.ProtocolConfiguration;
import org.hawkular.agent.monitor.config.AgentCoreEngineConfiguration.StorageAdapterConfiguration;
import org.hawkular.agent.monitor.inventory.AttributeLocation;
import org.hawkular.agent.monitor.inventory.ConnectionData;
import org.hawkular.agent.monitor.inventory.ID;
import org.hawkular.agent.monitor.inventory.MetricType;
import org.hawkular.agent.monitor.inventory.Name;
import org.hawkular.agent.monitor.inventory.Operation;
import org.hawkular.agent.monitor.inventory.OperationParam;
import org.hawkular.agent.monitor.inventory.ResourceConfigurationPropertyType;
import org.hawkular.agent.monitor.inventory.ResourceType;
import org.hawkular.agent.monitor.inventory.ResourceType.Builder;
import org.hawkular.agent.monitor.inventory.SupportedMetricType;
import org.hawkular.agent.monitor.inventory.TypeSet;
import org.hawkular.agent.monitor.inventory.TypeSet.TypeSetBuilder;
import org.hawkular.agent.monitor.inventory.TypeSets;
import org.hawkular.agent.monitor.protocol.dmr.DMRNodeLocation;
import org.hawkular.agent.monitor.protocol.jmx.JMXEndpointService;
import org.hawkular.agent.monitor.protocol.jmx.JMXNodeLocation;
import org.hawkular.agent.monitor.protocol.platform.Constants;
import org.hawkular.agent.monitor.protocol.platform.Constants.PlatformMetricType;
import org.hawkular.agent.monitor.protocol.platform.Constants.PlatformResourceType;
import org.hawkular.agent.monitor.protocol.platform.PlatformNodeLocation;
import org.hawkular.agent.monitor.protocol.platform.PlatformPath;
import org.hawkular.agent.monitor.util.WildflyCompatibilityUtils;
import org.hawkular.inventory.api.model.MetricUnit;
import org.jboss.as.controller.PathAddress;

/**
 * Converts the YAML configuration to the configuration used by the agent ({@link AgentCoreEngineConfiguration}).
 */
public class ConfigConverter {

    private final Configuration sourceConfig;

    public ConfigConverter(Configuration config) {
        if (config == null) {
            throw new IllegalArgumentException("config cannot be null");
        }
        this.sourceConfig = config;
    }

    /**
     * @return the agent core engine config
     */
    public AgentCoreEngineConfiguration convert() throws Exception {
        Configuration config = this.sourceConfig;

        // first make sure everything is valid

        config.validate();

        // now convert our yaml config to the config object the agent core engine needs

        GlobalConfiguration globalConfiguration = new GlobalConfiguration(
                config.getSubsystem().getEnabled(),
                config.getSubsystem().getImmutable(),
                config.getSubsystem().getInContainer(),
                config.getSubsystem().getAutoDiscoveryScanPeriodSecs(),
                2);

        MetricsExporterConfiguration metricsExporter = new MetricsExporterConfiguration(
                config.getMetricsExporter().getEnabled(),
                config.getMetricsExporter().getHost(),
                config.getMetricsExporter().getPort(),
                config.getMetricsExporter().getConfigDir(),
                config.getMetricsExporter().getConfigFile());

        DiagnosticsConfiguration diagnostics = new DiagnosticsConfiguration(
                config.getDiagnostics().getEnabled(),
                config.getDiagnostics().getInterval(),
                config.getDiagnostics().getTimeUnits().toJavaTimeUnit());

        StorageAdapterConfiguration storageAdapter = new StorageAdapterConfiguration(
                config.getStorageAdapter().getUsername(),
                config.getStorageAdapter().getPassword(),
                config.getStorageAdapter().getFeedId(),
                config.getStorageAdapter().getUrl(),
                config.getStorageAdapter().useSSL(),
                config.getStorageAdapter().getInventoryContext(),
                config.getStorageAdapter().getFeedcommContext(),
                config.getStorageAdapter().getHawkularContext(),
                null, // we use security realm exclusively
                null, // we use security realm exclusively
                config.getStorageAdapter().getSecurityRealmName(),
                config.getStorageAdapter().getConnectTimeoutSecs(),
                config.getStorageAdapter().getReadTimeoutSecs());

        ProtocolConfiguration<DMRNodeLocation> dmrConfiguration = buildDmrConfiguration(config);
        ProtocolConfiguration<JMXNodeLocation> jmxConfiguration = buildJmxConfiguration(config);
        ProtocolConfiguration<PlatformNodeLocation> platformConfiguration = buildPlatformConfiguration(config);

        AgentCoreEngineConfiguration agentConfig = new AgentCoreEngineConfiguration(
                globalConfiguration,
                metricsExporter,
                diagnostics,
                storageAdapter,
                dmrConfiguration,
                jmxConfiguration,
                platformConfiguration);
        return agentConfig;
    }

    private ProtocolConfiguration<DMRNodeLocation> buildDmrConfiguration(Configuration config) throws Exception {

        TypeSets.Builder<DMRNodeLocation> typeSets = new TypeSets.Builder<>();

        for (DMRMetricSet metricSet : config.getDmrMetricSets()) {
            TypeSetBuilder<MetricType<DMRNodeLocation>> typeSet = TypeSet.<MetricType<DMRNodeLocation>> builder();
            typeSet.name(new Name(metricSet.getName()));
            typeSet.enabled(metricSet.getEnabled());
            for (DMRMetric metric : metricSet.getDmrMetrics()) {
                DMRNodeLocation location = new DMRNodeLocation(
                        getDmrPathAddress(metric.getPath()),
                        metric.getResolveExpressions(),
                        metric.getIncludeDefaults());
                AttributeLocation<DMRNodeLocation> aLocation = new AttributeLocation<>(location,
                        metric.getAttribute());
                MetricType<DMRNodeLocation> type = new MetricType<DMRNodeLocation>(
                        new ID(metricSet.getName() + "~" + metric.getName()),
                        new Name(metric.getName()),
                        aLocation,
                        metric.getMetricUnits(),
                        metric.getMetricType(),
                        metric.getMetricFamily(),
                        metric.getMetricLabels());
                typeSet.type(type);
            }
            typeSets.metricTypeSet(typeSet.build());
        }

        for (DMRResourceTypeSet rtSet : config.getDmrResourceTypeSets()) {
            TypeSetBuilder<ResourceType<DMRNodeLocation>> typeSet = TypeSet.<ResourceType<DMRNodeLocation>> builder();
            typeSet.name(new Name(rtSet.getName()));
            typeSet.enabled(rtSet.getEnabled());
            for (DMRResourceType rt : rtSet.getDmrResourceTypes()) {
                Builder<?, DMRNodeLocation> rtBuilder = ResourceType.<DMRNodeLocation> builder();
                rtBuilder.name(new Name(rt.getName()));
                rtBuilder.location(DMRNodeLocation.of(rt.getPath()));
                rtBuilder.resourceNameTemplate(rt.getResourceNameTemplate());
                if (rt.getParents() != null) {
                    for (String parent : rt.getParents()) {
                        rtBuilder.parent(new Name(parent));
                    }
                }
                if (rt.getMetricSets() != null) {
                    for (String metricSet : rt.getMetricSets()) {
                        rtBuilder.metricSetName(new Name(metricSet));
                    }
                }
                if (rt.getAvailSets() != null) {
                    for (String availSet : rt.getAvailSets()) {
                        rtBuilder.availSetName(new Name(availSet));
                    }
                }

                if (rt.getDmrNotifications() != null) {
                    for (DMRNotification notification : rt.getDmrNotifications()) {
                        rtBuilder.notificationType(notification.getNotificationType());
                    }
                }

                if (rt.getDmrResourceConfigs() != null) {
                    for (DMRResourceConfig resConfig : rt.getDmrResourceConfigs()) {
                        DMRNodeLocation location = new DMRNodeLocation(
                                getDmrPathAddress(resConfig.getPath()),
                                resConfig.getResolveExpressions(),
                                resConfig.getIncludeDefaults());
                        AttributeLocation<DMRNodeLocation> aLocation = new AttributeLocation<>(location,
                                resConfig.getAttribute());

                        rtBuilder.resourceConfigurationPropertyType(new ResourceConfigurationPropertyType<>(
                                ID.NULL_ID,
                                new Name(resConfig.getName()),
                                aLocation));
                    }
                }

                if (rt.getDmrOperations() != null) {
                    for (DMROperation dmrOp : rt.getDmrOperations()) {
                        PathAddress path = getDmrPathAddress(dmrOp.getPath());
                        List<OperationParam> params = new ArrayList<>();
                        if (dmrOp.getDmrOperationParams() != null) {
                            for (DMROperationParam dmrParam : dmrOp.getDmrOperationParams()) {
                                OperationParam param = new OperationParam(
                                        dmrParam.getName(),
                                        dmrParam.getType(),
                                        dmrParam.getDescription(),
                                        dmrParam.getDefaultValue(),
                                        dmrParam.getRequired());
                                params.add(param);
                            }
                        }
                        Operation<DMRNodeLocation> op = new Operation<>(
                                ID.NULL_ID,
                                new Name(dmrOp.getName()),
                                new DMRNodeLocation(path),
                                dmrOp.getInternalName(),
                                dmrOp.getModifies(),
                                params);
                        rtBuilder.operation(op);
                    }
                }

                populateMetricAndAvailTypesForResourceType(rtBuilder, typeSets);
                typeSet.type(rtBuilder.build());
            }
            typeSets.resourceTypeSet(typeSet.build());
        }

        Map<String, EndpointConfiguration> managedServers = new HashMap<>();

        if (config.getManagedServers().getLocalDmr() != null) {
            ConnectionData connectionData = null;

            // the agent cannot get a local client when running as a javaagent, so really
            // this is a "remote" endpoint, pointing to the local machine.
            // If user doesn't like these defaults, let them define their own remote-dmr
            String localHost = System.getProperty("jboss.bind.address.management", "127.0.0.1");
            // If bind address is 0.0.0.0 just use 127.0.0.1
            if (localHost.equals("0.0.0.0")) {
                localHost = "127.0.0.1";
            }
            int localPortOffset;
            int localPort;
            try {
                String localPortOffsetString = System.getProperty("jboss.socket.binding.port-offset", "0");
                localPortOffset = Integer.parseInt(localPortOffsetString);
            } catch (Exception e) {
                throw new Exception("jboss.socket.binding.port-offset is invalid", e);
            }
            String localProtocol = System.getProperty("hawkular.local.dmr.protocol", "http-remoting");
            if (localProtocol.contains("https")) {
                try {
                    String localPortString = System.getProperty("jboss.management.https.port", "9443");
                    localPort = Integer.parseInt(localPortString);
                } catch (Exception e) {
                    throw new Exception("jboss.management.https.port is invalid", e);
                }
            } else if (localProtocol.contains("http")) {
                try {
                    String localPortString = System.getProperty("jboss.management.http.port", "9990");
                    localPort = Integer.parseInt(localPortString);
                } catch (Exception e) {
                    throw new Exception("jboss.management.http.port is invalid", e);
                }
            } else {
                try {
                    String localPortString = System.getProperty("jboss.management.native.port", "9999");
                    localPort = Integer.parseInt(localPortString);
                } catch (Exception e) {
                    throw new Exception("jboss.management.native.port is invalid", e);
                }
            }

            connectionData = new ConnectionData(localProtocol, localHost, localPort + localPortOffset, null, null);

            EndpointConfiguration localDmrEndpointConfig = new EndpointConfiguration(
                    config.getManagedServers().getLocalDmr().getName(),
                    config.getManagedServers().getLocalDmr().getEnabled(),
                    getNamesFromStrings(config.getManagedServers().getLocalDmr().getResourceTypeSets()),
                    connectionData,
                    null,
                    config.getManagedServers().getLocalDmr().getMetricLabels(),
                    null,
                    asWaitForList(config.getManagedServers().getLocalDmr().getWaitFor()));
            managedServers.put(config.getManagedServers().getLocalDmr().getName(), localDmrEndpointConfig);
        }

        if (config.getManagedServers().getRemoteDmrs() != null) {
            for (RemoteDMR remoteDmr : config.getManagedServers().getRemoteDmrs()) {
                if (remoteDmr.getProtocol() == null) {
                    remoteDmr.setProtocol(remoteDmr.getUseSsl() ? "https-remoting" : "http-remoting");
                }

                ConnectionData connectionData = new ConnectionData(
                        remoteDmr.getProtocol(),
                        remoteDmr.getHost(),
                        remoteDmr.getPort(),
                        remoteDmr.getUsername(),
                        remoteDmr.getPassword());

                EndpointConfiguration remoteDmrEndpointConfig = new EndpointConfiguration(
                        remoteDmr.getName(),
                        remoteDmr.getEnabled(),
                        getNamesFromStrings(remoteDmr.getResourceTypeSets()),
                        connectionData,
                        remoteDmr.getSecurityRealmName(),
                        remoteDmr.getMetricLabels(),
                        null,
                        asWaitForList(remoteDmr.getWaitFor()));

                managedServers.put(remoteDmr.getName(), remoteDmrEndpointConfig);
            }
        }

        return new ProtocolConfiguration<DMRNodeLocation>(typeSets.build(), managedServers);
    }

    private ProtocolConfiguration<JMXNodeLocation> buildJmxConfiguration(Configuration config) throws Exception {

        TypeSets.Builder<JMXNodeLocation> typeSets = new TypeSets.Builder<>();

        for (JMXMetricSet metricSet : config.getJmxMetricSets()) {
            TypeSetBuilder<MetricType<JMXNodeLocation>> typeSet = TypeSet.<MetricType<JMXNodeLocation>> builder();
            typeSet.name(new Name(metricSet.getName()));
            typeSet.enabled(metricSet.getEnabled());
            for (JMXMetric metric : metricSet.getJmxMetrics()) {
                JMXNodeLocation location = new JMXNodeLocation(getJmxObjectName(metric.getObjectName()));
                AttributeLocation<JMXNodeLocation> aLocation = new AttributeLocation<>(location,
                        metric.getAttribute());
                MetricType<JMXNodeLocation> type = new MetricType<JMXNodeLocation>(
                        new ID(metricSet.getName() + "~" + metric.getName()),
                        new Name(metric.getName()),
                        aLocation,
                        metric.getMetricUnits(),
                        metric.getMetricType(),
                        metric.getMetricFamily(),
                        metric.getMetricLabels());
                typeSet.type(type);
            }
            typeSets.metricTypeSet(typeSet.build());
        }

        for (JMXResourceTypeSet rtSet : config.getJmxResourceTypeSets()) {
            TypeSetBuilder<ResourceType<JMXNodeLocation>> typeSet = TypeSet.<ResourceType<JMXNodeLocation>> builder();
            typeSet.name(new Name(rtSet.getName()));
            typeSet.enabled(rtSet.getEnabled());
            for (JMXResourceType rt : rtSet.getJmxResourceTypes()) {
                Builder<?, JMXNodeLocation> rtBuilder = ResourceType.<JMXNodeLocation> builder();
                rtBuilder.name(new Name(rt.getName()));
                rtBuilder.location(new JMXNodeLocation(getJmxObjectName(rt.getObjectName())));
                rtBuilder.resourceNameTemplate(rt.getResourceNameTemplate());
                if (rt.getParents() != null) {
                    for (String parent : rt.getParents()) {
                        rtBuilder.parent(new Name(parent));
                    }
                }
                if (rt.getMetricSets() != null) {
                    for (String metricSet : rt.getMetricSets()) {
                        rtBuilder.metricSetName(new Name(metricSet));
                    }
                }
                if (rt.getAvailSets() != null) {
                    for (String availSet : rt.getAvailSets()) {
                        rtBuilder.availSetName(new Name(availSet));
                    }
                }

                if (rt.getJmxResourceConfigs() != null) {
                    for (JMXResourceConfig resConfig : rt.getJmxResourceConfigs()) {
                        JMXNodeLocation location = new JMXNodeLocation(getJmxObjectName(resConfig.getObjectName()));
                        AttributeLocation<JMXNodeLocation> aLocation = new AttributeLocation<>(location,
                                resConfig.getAttribute());

                        rtBuilder.resourceConfigurationPropertyType(new ResourceConfigurationPropertyType<>(
                                ID.NULL_ID,
                                new Name(resConfig.getName()),
                                aLocation));
                    }
                }

                if (rt.getJmxOperations() != null) {
                    for (JMXOperation jmxOp : rt.getJmxOperations()) {
                        List<OperationParam> params = new ArrayList<>();
                        if (jmxOp.getJmxOperationParams() != null) {
                            for (JMXOperationParam jmxParam : jmxOp.getJmxOperationParams()) {
                                OperationParam param = new OperationParam(
                                        jmxParam.getName(),
                                        jmxParam.getType(),
                                        jmxParam.getDescription(),
                                        jmxParam.getDefaultValue(),
                                        jmxParam.getRequired());
                                params.add(param);
                            }
                        }
                        Operation<JMXNodeLocation> op = new Operation<>(
                                ID.NULL_ID,
                                new Name(jmxOp.getName()),
                                new JMXNodeLocation(jmxOp.getObjectName()),
                                jmxOp.getInternalName(),
                                jmxOp.getModifies(),
                                params);
                        rtBuilder.operation(op);
                    }
                }

                populateMetricAndAvailTypesForResourceType(rtBuilder, typeSets);
                typeSet.type(rtBuilder.build());
            }
            typeSets.resourceTypeSet(typeSet.build());
        }

        Map<String, EndpointConfiguration> managedServers = new HashMap<>();

        if (config.getManagedServers().getLocalJmx() != null) {
            EndpointConfiguration localJmx = new EndpointConfiguration(
                    config.getManagedServers().getLocalJmx().getName(),
                    config.getManagedServers().getLocalJmx().getEnabled(),
                    getNamesFromStrings(config.getManagedServers().getLocalJmx().getResourceTypeSets()),
                    null,
                    null,
                    config.getManagedServers().getLocalJmx().getMetricLabels(),
                    Collections.singletonMap(JMXEndpointService.MBEAN_SERVER_NAME_KEY,
                            config.getManagedServers().getLocalJmx().getMbeanServerName()),
                    asWaitForList(config.getManagedServers().getLocalJmx().getWaitFor()));
            managedServers.put(config.getManagedServers().getLocalJmx().getName(), localJmx);
        }

        if (config.getManagedServers().getRemoteJmxs() != null) {
            for (RemoteJMX remoteJmx : config.getManagedServers().getRemoteJmxs()) {
                URI url;
                try {
                    url = new URI(remoteJmx.getUrl());
                } catch (Exception e) {
                    throw new Exception("Remote JMX [" + remoteJmx.getName() + "] has invalid URL", e);
                }

                ConnectionData connectionData = new ConnectionData(
                        url,
                        remoteJmx.getUsername(),
                        remoteJmx.getPassword());

                EndpointConfiguration remoteJmxEndpointConfig = new EndpointConfiguration(
                        remoteJmx.getName(),
                        remoteJmx.getEnabled(),
                        getNamesFromStrings(remoteJmx.getResourceTypeSets()),
                        connectionData,
                        remoteJmx.getSecurityRealmName(),
                        remoteJmx.getMetricLabels(),
                        null,
                        asWaitForList(remoteJmx.getWaitFor()));

                managedServers.put(remoteJmx.getName(), remoteJmxEndpointConfig);
            }
        }

        return new ProtocolConfiguration<JMXNodeLocation>(typeSets.build(), managedServers);
    }

    private ProtocolConfiguration<PlatformNodeLocation> buildPlatformConfiguration(Configuration config) {
        // assume they are disabled unless configured otherwise

        if (!config.getPlatform().getEnabled()) {
            Map<String, EndpointConfiguration> managedServers = new HashMap<>();
            return new ProtocolConfiguration<PlatformNodeLocation>(TypeSets.empty(), managedServers);
        }

        TypeSets.Builder<PlatformNodeLocation> typeSets = TypeSets.builder();

        // all the type metadata is dependent upon the capabilities of the oshi SystemInfo API

        // since platform monitoring is enabled, we will always have at least the root OS type
        final ID osId = PlatformResourceType.OPERATING_SYSTEM.getResourceTypeId();
        final Name osName = PlatformResourceType.OPERATING_SYSTEM.getResourceTypeName();

        Builder<?, PlatformNodeLocation> rootTypeBldr = ResourceType.<PlatformNodeLocation> builder()
                .id(osId)
                .name(osName)
                .location(new PlatformNodeLocation(
                        PlatformPath.builder().any(PlatformResourceType.OPERATING_SYSTEM).build()))
                .resourceNameTemplate("%s");

        ResourceConfigurationPropertyType<PlatformNodeLocation> machineIdConfigType = //
                new ResourceConfigurationPropertyType<>(
                        ID.NULL_ID,
                        new Name(Constants.MACHINE_ID),
                        new AttributeLocation<>(new PlatformNodeLocation(PlatformPath.empty()), Constants.MACHINE_ID));
        rootTypeBldr.resourceConfigurationPropertyType(machineIdConfigType);

        ResourceConfigurationPropertyType<PlatformNodeLocation> containerIdConfigType = //
                new ResourceConfigurationPropertyType<>(
                        ID.NULL_ID,
                        new Name(Constants.CONTAINER_ID),
                        new AttributeLocation<>(new PlatformNodeLocation(PlatformPath.empty()),
                                Constants.CONTAINER_ID));
        rootTypeBldr.resourceConfigurationPropertyType(containerIdConfigType);

        // OS top-level metrics

        MetricType<PlatformNodeLocation> systemCpuLoad = new MetricType<PlatformNodeLocation>(
                PlatformMetricType.OS_SYS_CPU_LOAD.getMetricTypeId(),
                PlatformMetricType.OS_SYS_CPU_LOAD.getMetricTypeName(),
                new AttributeLocation<>(
                        new PlatformNodeLocation(PlatformPath.empty()),
                        PlatformMetricType.OS_SYS_CPU_LOAD.getMetricTypeId().getIDString()),
                MetricUnit.PERCENTAGE,
                SupportedMetricType.GAUGE,
                null,
                null);

        MetricType<PlatformNodeLocation> systemLoadAverage = new MetricType<PlatformNodeLocation>(
                PlatformMetricType.OS_SYS_LOAD_AVG.getMetricTypeId(),
                PlatformMetricType.OS_SYS_LOAD_AVG.getMetricTypeName(),
                new AttributeLocation<>(
                        new PlatformNodeLocation(PlatformPath.empty()),
                        PlatformMetricType.OS_SYS_LOAD_AVG.getMetricTypeId().getIDString()),
                MetricUnit.NONE,
                SupportedMetricType.GAUGE,
                null,
                null);

        MetricType<PlatformNodeLocation> processCount = new MetricType<PlatformNodeLocation>(
                PlatformMetricType.OS_PROCESS_COUNT.getMetricTypeId(),
                PlatformMetricType.OS_PROCESS_COUNT.getMetricTypeName(),
                new AttributeLocation<>(
                        new PlatformNodeLocation(PlatformPath.empty()),
                        PlatformMetricType.OS_PROCESS_COUNT.getMetricTypeId().getIDString()),
                MetricUnit.NONE,
                SupportedMetricType.GAUGE,
                null,
                null);

        TypeSet<MetricType<PlatformNodeLocation>> osMetrics = TypeSet
                .<MetricType<PlatformNodeLocation>> builder()
                .name(PlatformResourceType.OPERATING_SYSTEM.getResourceTypeName())
                .type(systemCpuLoad)
                .type(systemLoadAverage)
                .type(processCount)
                .build();

        typeSets.metricTypeSet(osMetrics);

        rootTypeBldr.metricSetName(osMetrics.getName());
        populateMetricAndAvailTypesForResourceType(rootTypeBldr, typeSets);

        ResourceType<PlatformNodeLocation> rootType = rootTypeBldr.build();
        TypeSet<ResourceType<PlatformNodeLocation>> rootTypeSet = TypeSet
                .<ResourceType<PlatformNodeLocation>> builder()
                .enabled(true)
                .name(osName)
                .type(rootType)
                .build();

        typeSets.resourceTypeSet(rootTypeSet);

        // now add children types if they are enabled

        if (config.getPlatform().getFileStores() != null && config.getPlatform().getFileStores().getEnabled()) {

            MetricType<PlatformNodeLocation> usableSpace = new MetricType<PlatformNodeLocation>(
                    PlatformMetricType.FILE_STORE_USABLE_SPACE.getMetricTypeId(),
                    PlatformMetricType.FILE_STORE_USABLE_SPACE.getMetricTypeName(),
                    new AttributeLocation<>(
                            new PlatformNodeLocation(PlatformPath.empty()),
                            PlatformMetricType.FILE_STORE_USABLE_SPACE.getMetricTypeId().getIDString()),
                    MetricUnit.BYTES,
                    SupportedMetricType.GAUGE,
                    null,
                    null);

            MetricType<PlatformNodeLocation> totalSpace = new MetricType<PlatformNodeLocation>(
                    PlatformMetricType.FILE_STORE_TOTAL_SPACE.getMetricTypeId(),
                    PlatformMetricType.FILE_STORE_TOTAL_SPACE.getMetricTypeName(),
                    new AttributeLocation<>(
                            new PlatformNodeLocation(PlatformPath.empty()),
                            PlatformMetricType.FILE_STORE_TOTAL_SPACE.getMetricTypeId().getIDString()),
                    MetricUnit.BYTES,
                    SupportedMetricType.GAUGE,
                    null,
                    null);

            TypeSet<MetricType<PlatformNodeLocation>> fileStoreMetrics = TypeSet
                    .<MetricType<PlatformNodeLocation>> builder()
                    .name(PlatformResourceType.FILE_STORE.getResourceTypeName())
                    .type(usableSpace)
                    .type(totalSpace)
                    .build();

            typeSets.metricTypeSet(fileStoreMetrics);

            PlatformNodeLocation fileStoreLocation = new PlatformNodeLocation(
                    PlatformPath.builder().any(PlatformResourceType.FILE_STORE).build());
            Builder<?, PlatformNodeLocation> fileStoreBldr = ResourceType.<PlatformNodeLocation> builder()
                    .id(PlatformResourceType.FILE_STORE.getResourceTypeId())
                    .name(PlatformResourceType.FILE_STORE.getResourceTypeName())
                    .location(fileStoreLocation)
                    .resourceNameTemplate(
                            PlatformResourceType.FILE_STORE.getResourceTypeName().getNameString() + " [%s]")
                    .parent(rootType.getName())
                    .metricSetName(fileStoreMetrics.getName());

            populateMetricAndAvailTypesForResourceType(fileStoreBldr, typeSets);

            ResourceType<PlatformNodeLocation> fileStore = fileStoreBldr.build();
            TypeSet<ResourceType<PlatformNodeLocation>> typeSet = TypeSet
                    .<ResourceType<PlatformNodeLocation>> builder()
                    .name(PlatformResourceType.FILE_STORE.getResourceTypeName())
                    .type(fileStore)
                    .build();

            typeSets.resourceTypeSet(typeSet);
        }

        if (config.getPlatform().getMemory() != null && config.getPlatform().getMemory().getEnabled()) {
            MetricType<PlatformNodeLocation> available = new MetricType<PlatformNodeLocation>(
                    PlatformMetricType.MEMORY_AVAILABLE.getMetricTypeId(),
                    PlatformMetricType.MEMORY_AVAILABLE.getMetricTypeName(),
                    new AttributeLocation<>(
                            new PlatformNodeLocation(PlatformPath.empty()),
                            PlatformMetricType.MEMORY_AVAILABLE.getMetricTypeId().getIDString()),
                    MetricUnit.BYTES,
                    SupportedMetricType.GAUGE,
                    null,
                    null);

            MetricType<PlatformNodeLocation> total = new MetricType<PlatformNodeLocation>(
                    PlatformMetricType.MEMORY_TOTAL.getMetricTypeId(),
                    PlatformMetricType.MEMORY_TOTAL.getMetricTypeName(),
                    new AttributeLocation<>(
                            new PlatformNodeLocation(PlatformPath.empty()),
                            PlatformMetricType.MEMORY_TOTAL.getMetricTypeId().getIDString()),
                    MetricUnit.BYTES,
                    SupportedMetricType.GAUGE,
                    null,
                    null);

            TypeSet<MetricType<PlatformNodeLocation>> memoryMetrics = TypeSet
                    .<MetricType<PlatformNodeLocation>> builder()
                    .name(PlatformResourceType.MEMORY.getResourceTypeName())
                    .type(available)
                    .type(total)
                    .build();

            typeSets.metricTypeSet(memoryMetrics);

            PlatformNodeLocation memoryLocation = new PlatformNodeLocation(
                    PlatformPath.builder().any(PlatformResourceType.MEMORY).build());
            Builder<?, PlatformNodeLocation> memoryBldr = ResourceType.<PlatformNodeLocation> builder()
                    .id(PlatformResourceType.MEMORY.getResourceTypeId())
                    .name(PlatformResourceType.MEMORY.getResourceTypeName())
                    .parent(rootType.getName())
                    .location(memoryLocation)
                    .metricSetName(memoryMetrics.getName())
                    .resourceNameTemplate(PlatformResourceType.MEMORY.getResourceTypeName().getNameString());

            populateMetricAndAvailTypesForResourceType(memoryBldr, typeSets);

            ResourceType<PlatformNodeLocation> memory = memoryBldr.build();
            TypeSet<ResourceType<PlatformNodeLocation>> typeSet = TypeSet
                    .<ResourceType<PlatformNodeLocation>> builder()
                    .name(PlatformResourceType.MEMORY.getResourceTypeName())
                    .type(memory)
                    .build();

            typeSets.resourceTypeSet(typeSet);
        }

        if (config.getPlatform().getProcessors() != null && config.getPlatform().getProcessors().getEnabled()) {
            // this is the Processor.getProcessorCpuLoadBetweenTicks value
            MetricType<PlatformNodeLocation> cpuUsage = new MetricType<PlatformNodeLocation>(
                    PlatformMetricType.PROCESSOR_CPU_USAGE.getMetricTypeId(),
                    PlatformMetricType.PROCESSOR_CPU_USAGE.getMetricTypeName(),
                    new AttributeLocation<>(
                            new PlatformNodeLocation(PlatformPath.empty()),
                            PlatformMetricType.PROCESSOR_CPU_USAGE.getMetricTypeId().getIDString()),
                    MetricUnit.PERCENTAGE,
                    SupportedMetricType.GAUGE,
                    null,
                    null);

            TypeSet<MetricType<PlatformNodeLocation>> processorMetrics = TypeSet
                    .<MetricType<PlatformNodeLocation>> builder()
                    .name(PlatformResourceType.PROCESSOR.getResourceTypeName())
                    .type(cpuUsage)
                    .build();

            typeSets.metricTypeSet(processorMetrics);

            PlatformNodeLocation processorsLocation = new PlatformNodeLocation(
                    PlatformPath.builder().any(PlatformResourceType.PROCESSOR).build());
            Builder<?, PlatformNodeLocation> processorBldr = ResourceType.<PlatformNodeLocation> builder()
                    .id(PlatformResourceType.PROCESSOR.getResourceTypeId())
                    .name(PlatformResourceType.PROCESSOR.getResourceTypeName())
                    .parent(rootType.getName())
                    .location(processorsLocation)
                    .metricSetName(processorMetrics.getName())
                    .resourceNameTemplate(
                            PlatformResourceType.PROCESSOR.getResourceTypeName().getNameString() + " [%s]");

            populateMetricAndAvailTypesForResourceType(processorBldr, typeSets);

            ResourceType<PlatformNodeLocation> processor = processorBldr.build();
            TypeSet<ResourceType<PlatformNodeLocation>> typeSet = TypeSet
                    .<ResourceType<PlatformNodeLocation>> builder()
                    .name(PlatformResourceType.PROCESSOR.getResourceTypeName())
                    .type(processor)
                    .build();

            typeSets.resourceTypeSet(typeSet);
        }

        if (config.getPlatform().getPowerSources() != null && config.getPlatform().getPowerSources().getEnabled()) {
            MetricType<PlatformNodeLocation> remainingCap = new MetricType<PlatformNodeLocation>(
                    PlatformMetricType.POWER_SOURCE_REMAINING_CAPACITY.getMetricTypeId(),
                    PlatformMetricType.POWER_SOURCE_REMAINING_CAPACITY.getMetricTypeName(),
                    new AttributeLocation<>(
                            new PlatformNodeLocation(PlatformPath.empty()),
                            PlatformMetricType.POWER_SOURCE_REMAINING_CAPACITY.getMetricTypeId()
                                    .getIDString()),
                    MetricUnit.PERCENTAGE,
                    SupportedMetricType.GAUGE,
                    null,
                    null);

            MetricType<PlatformNodeLocation> timeRemaining = new MetricType<PlatformNodeLocation>(
                    PlatformMetricType.POWER_SOURCE_TIME_REMAINING.getMetricTypeId(),
                    PlatformMetricType.POWER_SOURCE_TIME_REMAINING.getMetricTypeName(),
                    new AttributeLocation<>(
                            new PlatformNodeLocation(PlatformPath.empty()),
                            PlatformMetricType.POWER_SOURCE_TIME_REMAINING.getMetricTypeId().getIDString()),
                    MetricUnit.SECONDS,
                    SupportedMetricType.GAUGE,
                    null,
                    null);

            TypeSet<MetricType<PlatformNodeLocation>> powerSourceMetrics = TypeSet
                    .<MetricType<PlatformNodeLocation>> builder()
                    .name(PlatformResourceType.POWER_SOURCE.getResourceTypeName())
                    .type(remainingCap)
                    .type(timeRemaining)
                    .build();

            typeSets.metricTypeSet(powerSourceMetrics);

            PlatformNodeLocation powerSourcesLocation = new PlatformNodeLocation(
                    PlatformPath.builder().any(PlatformResourceType.POWER_SOURCE).build());
            Builder<?, PlatformNodeLocation> powerSourceBldr = ResourceType.<PlatformNodeLocation> builder()
                    .id(PlatformResourceType.POWER_SOURCE.getResourceTypeId())
                    .name(PlatformResourceType.POWER_SOURCE.getResourceTypeName())
                    .parent(rootType.getName())
                    .location(powerSourcesLocation)
                    .metricSetName(powerSourceMetrics.getName())
                    .resourceNameTemplate(
                            PlatformResourceType.POWER_SOURCE.getResourceTypeName().getNameString() + " [%s]");

            populateMetricAndAvailTypesForResourceType(powerSourceBldr, typeSets);

            ResourceType<PlatformNodeLocation> powerSource = powerSourceBldr.build();
            TypeSet<ResourceType<PlatformNodeLocation>> typeSet = TypeSet
                    .<ResourceType<PlatformNodeLocation>> builder()
                    .name(PlatformResourceType.POWER_SOURCE.getResourceTypeName())
                    .type(powerSource)
                    .build();

            typeSets.resourceTypeSet(typeSet);
        }

        Map<String, EndpointConfiguration> managedServers = new HashMap<>();
        if (config.getPlatform().getEnabled()) {
            Map<String, String> customData = new HashMap<>(2);
            customData.put(Constants.MACHINE_ID, config.getPlatform().getMachineId());
            customData.put(Constants.CONTAINER_ID, config.getPlatform().getContainerId());
            EndpointConfiguration localPlatform = new EndpointConfiguration(
                    "platform",
                    true,
                    null,
                    null,
                    null,
                    null,
                    customData,
                    null);
            managedServers.put("platform", localPlatform);
        }

        return new ProtocolConfiguration<PlatformNodeLocation>(typeSets.build(), managedServers);
    }

    /**
     * Given a resource type builder, this will fill in its metric types and avail types.
     *
     * @param resourceTypeBuilder the type being built whose metric and avail types are to be filled in
     * @param typeSetsBuilder all type metadata - this is where our metrics and avails are
     */
    private static <L> void populateMetricAndAvailTypesForResourceType(
            ResourceType.Builder<?, L> resourceTypeBuilder,
            TypeSets.Builder<L> typeSetsBuilder) {

        Map<Name, TypeSet<MetricType<L>>> metricTypeSets = typeSetsBuilder.getMetricTypeSets();
        List<Name> metricSetNames = resourceTypeBuilder.getMetricSetNames();
        for (Name metricSetName : metricSetNames) {
            TypeSet<MetricType<L>> metricSet = metricTypeSets.get(metricSetName);
            if (metricSet != null && metricSet.isEnabled()) {
                resourceTypeBuilder.metricTypes(metricSet.getTypeMap().values());
            }
        }

    }

    private PathAddress getDmrPathAddress(String path) {
        if (path == null) {
            return PathAddress.EMPTY_ADDRESS;
        } else if ("/".equals(path)) {
            return PathAddress.EMPTY_ADDRESS;
        } else {
            return WildflyCompatibilityUtils.parseCLIStyleAddress(path);
        }
    }

    private ObjectName getJmxObjectName(String on) throws Exception {
        if (on == null || on.trim().isEmpty()) {
            return null;
        } else {
            return new ObjectName(on);
        }
    }

    private Collection<Name> getNamesFromStrings(String[] arr) {
        if (arr == null) {
            return Collections.emptyList();
        }
        ArrayList<Name> names = new ArrayList<>();
        for (String s : arr) {
            names.add(new Name(s));
        }
        return names;
    }

    private List<WaitFor> asWaitForList(org.hawkular.agent.javaagent.config.WaitFor[] arr) {
        if (arr == null) {
            return Collections.emptyList();
        }
        List<WaitFor> list = new ArrayList<>(arr.length);
        for (org.hawkular.agent.javaagent.config.WaitFor arrEle : arr) {
            WaitFor wf = new WaitFor(arrEle.getName());
            list.add(wf);
        }
        return list;
    }
}
