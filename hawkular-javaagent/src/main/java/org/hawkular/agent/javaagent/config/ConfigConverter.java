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
import org.hawkular.agent.monitor.config.AgentCoreEngineConfiguration.PlatformConfiguration;
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
import org.hawkular.agent.monitor.inventory.TypeSet;
import org.hawkular.agent.monitor.inventory.TypeSet.TypeSetBuilder;
import org.hawkular.agent.monitor.inventory.TypeSets;
import org.hawkular.agent.monitor.protocol.dmr.DMRNodeLocation;
import org.hawkular.agent.monitor.protocol.jmx.JMXEndpointService;
import org.hawkular.agent.monitor.protocol.jmx.JMXNodeLocation;
import org.hawkular.agent.monitor.util.WildflyCompatibilityUtils;
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
                config.getSubsystem().getTypeVersion(),
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

        PlatformConfiguration platformConfiguration = new PlatformConfiguration(
                config.getPlatform().getEnabled(),
                config.getPlatform().getMemory().getEnabled(),
                config.getPlatform().getFileStores().getEnabled(),
                config.getPlatform().getProcessors().getEnabled(),
                config.getPlatform().getPowerSources().getEnabled(),
                config.getPlatform().getMachineId(),
                config.getPlatform().getContainerId());

        ProtocolConfiguration<DMRNodeLocation> dmrConfiguration = buildDmrConfiguration(config);
        ProtocolConfiguration<JMXNodeLocation> jmxConfiguration = buildJmxConfiguration(config);

        AgentCoreEngineConfiguration agentConfig = new AgentCoreEngineConfiguration(
                globalConfiguration,
                metricsExporter,
                diagnostics,
                storageAdapter,
                platformConfiguration,
                dmrConfiguration,
                jmxConfiguration);
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
                        metric.getMetricLabels(),
                        metric.getMetricExpression());
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
                rtBuilder.name(new Name(sourceConfig.getSubsystem().getTypeVersion() != null ?
                        rt.getName() + ' ' + sourceConfig.getSubsystem().getTypeVersion() : rt.getName()));
                rtBuilder.location(DMRNodeLocation.of(rt.getPath()));
                rtBuilder.resourceNameTemplate(rt.getResourceNameTemplate());
                if (rt.getParents() != null) {
                    for (String parent : rt.getParents()) {
                        rtBuilder.parent(new Name(sourceConfig.getSubsystem().getTypeVersion() != null ?
                                parent + ' ' + sourceConfig.getSubsystem().getTypeVersion() : parent));
                    }
                }
                if (rt.getMetricSets() != null) {
                    for (String metricSet : rt.getMetricSets()) {
                        rtBuilder.metricSetName(new Name(metricSet));
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

                if (rt.getMetricLabels() != null) {
                    rtBuilder.metricLabels(rt.getMetricLabels());
                }

                populateMetricTypesForResourceType(rtBuilder, typeSets);
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
                        metric.getMetricLabels(),
                        metric.getMetricExpression());
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
                rtBuilder.name(new Name(sourceConfig.getSubsystem().getTypeVersion() != null ?
                        rt.getName() + ' ' + sourceConfig.getSubsystem().getTypeVersion() : rt.getName()));
                rtBuilder.location(new JMXNodeLocation(getJmxObjectName(rt.getObjectName())));
                rtBuilder.resourceNameTemplate(rt.getResourceNameTemplate());
                if (rt.getParents() != null) {
                    for (String parent : rt.getParents()) {
                        rtBuilder.parent(new Name(sourceConfig.getSubsystem().getTypeVersion() != null ?
                                parent + ' ' + sourceConfig.getSubsystem().getTypeVersion() : parent));
                    }
                }
                if (rt.getMetricSets() != null) {
                    for (String metricSet : rt.getMetricSets()) {
                        rtBuilder.metricSetName(new Name(metricSet));
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

                if (rt.getMetricLabels() != null) {
                    rtBuilder.metricLabels(rt.getMetricLabels());
                }

                populateMetricTypesForResourceType(rtBuilder, typeSets);
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

    /**
     * Given a resource type builder, this will fill in its metric types.
     *
     * @param resourceTypeBuilder the type being built whose metric types are to be filled in
     * @param typeSetsBuilder all type metadata - this is where our metrics are
     */
    private static <L> void populateMetricTypesForResourceType(
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
