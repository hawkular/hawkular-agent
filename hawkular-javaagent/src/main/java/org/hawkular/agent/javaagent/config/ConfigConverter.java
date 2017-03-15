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
import java.util.regex.Pattern;

import javax.management.ObjectName;

import org.hawkular.agent.monitor.api.Avail;
import org.hawkular.agent.monitor.config.AgentCoreEngineConfiguration;
import org.hawkular.agent.monitor.config.AgentCoreEngineConfiguration.DiagnosticsConfiguration;
import org.hawkular.agent.monitor.config.AgentCoreEngineConfiguration.EndpointConfiguration;
import org.hawkular.agent.monitor.config.AgentCoreEngineConfiguration.GlobalConfiguration;
import org.hawkular.agent.monitor.config.AgentCoreEngineConfiguration.ProtocolConfiguration;
import org.hawkular.agent.monitor.config.AgentCoreEngineConfiguration.StorageAdapterConfiguration;
import org.hawkular.agent.monitor.inventory.AttributeLocation;
import org.hawkular.agent.monitor.inventory.AvailType;
import org.hawkular.agent.monitor.inventory.ConnectionData;
import org.hawkular.agent.monitor.inventory.ID;
import org.hawkular.agent.monitor.inventory.Interval;
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
import org.hawkular.agent.monitor.protocol.platform.Constants;
import org.hawkular.agent.monitor.protocol.platform.Constants.PlatformMetricType;
import org.hawkular.agent.monitor.protocol.platform.Constants.PlatformResourceType;
import org.hawkular.agent.monitor.protocol.platform.PlatformNodeLocation;
import org.hawkular.agent.monitor.protocol.platform.PlatformPath;
import org.hawkular.agent.monitor.util.WildflyCompatibilityUtils;
import org.jboss.as.controller.PathAddress;
import org.jboss.as.controller.client.helpers.MeasurementUnit;

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
                config.subsystem.enabled,
                config.subsystem.immutable,
                config.subsystem.inContainer,
                null,
                config.subsystem.autoDiscoveryScanPeriodSecs,
                config.subsystem.minCollectionIntervalSecs,
                2,
                config.subsystem.metricDispatcherBufferSize,
                config.subsystem.metricDispatcherMaxBatchSize,
                config.subsystem.availDispatcherBufferSize,
                config.subsystem.availDispatcherMaxBatchSize,
                config.subsystem.pingPeriodSecs);

        DiagnosticsConfiguration diagnostics = new DiagnosticsConfiguration(
                config.diagnostics.enabled,
                AgentCoreEngineConfiguration.DiagnosticsReportTo.valueOf(config.diagnostics.reportTo.name()),
                config.diagnostics.interval,
                config.diagnostics.timeUnits.toJavaTimeUnit());

        StorageAdapterConfiguration storageAdapter = new StorageAdapterConfiguration(
                AgentCoreEngineConfiguration.StorageReportTo.valueOf(config.storageAdapter.type.name()),
                config.storageAdapter.username,
                config.storageAdapter.password,
                config.storageAdapter.tenantId,
                config.storageAdapter.feedId,
                config.storageAdapter.url,
                config.storageAdapter.useSSL(),
                null, // we don't use socket binding ref
                config.storageAdapter.inventoryContext,
                config.storageAdapter.metricsContext,
                config.storageAdapter.feedcommContext,
                null, // we use security realm exclusively
                null, // we use security realm exclusively
                config.storageAdapter.securityRealmName,
                config.storageAdapter.connectTimeoutSecs,
                config.storageAdapter.readTimeoutSecs);

        ProtocolConfiguration<DMRNodeLocation> dmrConfiguration = buildDmrConfiguration(config);
        ProtocolConfiguration<JMXNodeLocation> jmxConfiguration = buildJmxConfiguration(config);
        ProtocolConfiguration<PlatformNodeLocation> platformConfiguration = buildPlatformConfiguration(config);

        AgentCoreEngineConfiguration agentConfig = new AgentCoreEngineConfiguration(
                globalConfiguration,
                diagnostics,
                storageAdapter,
                dmrConfiguration,
                jmxConfiguration,
                platformConfiguration);
        return agentConfig;
    }

    private ProtocolConfiguration<DMRNodeLocation> buildDmrConfiguration(Configuration config) throws Exception {

        TypeSets.Builder<DMRNodeLocation> typeSets = new TypeSets.Builder<>();

        for (DMRMetricSet metricSet : config.dmrMetricSets) {
            TypeSetBuilder<MetricType<DMRNodeLocation>> typeSet = TypeSet.<MetricType<DMRNodeLocation>> builder();
            typeSet.name(new Name(metricSet.name));
            typeSet.enabled(metricSet.enabled);
            for (DMRMetric metric : metricSet.dmrMetrics) {
                DMRNodeLocation location = new DMRNodeLocation(
                        getDmrPathAddress(metric.path),
                        metric.resolveExpressions,
                        metric.includeDefaults);
                AttributeLocation<DMRNodeLocation> aLocation = new AttributeLocation<>(location, metric.attribute);
                MetricType<DMRNodeLocation> type = new MetricType<DMRNodeLocation>(
                        new ID(metricSet.name + "~" + metric.name),
                        new Name(metric.name),
                        aLocation,
                        new Interval(metric.interval, metric.timeUnits.toJavaTimeUnit()),
                        metric.metricUnits,
                        metric.metricType,
                        metric.metricIdTemplate,
                        metric.metricTags);
                typeSet.type(type);
            }
            typeSets.metricTypeSet(typeSet.build());
        }

        for (DMRAvailSet availSet : config.dmrAvailSets) {
            TypeSetBuilder<AvailType<DMRNodeLocation>> typeSet = TypeSet.<AvailType<DMRNodeLocation>> builder();
            typeSet.name(new Name(availSet.name));
            typeSet.enabled(availSet.enabled);
            for (DMRAvail avail : availSet.dmrAvails) {
                DMRNodeLocation location = new DMRNodeLocation(
                        getDmrPathAddress(avail.path),
                        avail.resolveExpressions,
                        avail.includeDefaults);
                AttributeLocation<DMRNodeLocation> aLocation = new AttributeLocation<>(location, avail.attribute);
                AvailType<DMRNodeLocation> type = new AvailType<DMRNodeLocation>(
                        new ID(availSet.name + "~" + avail.name),
                        new Name(avail.name),
                        aLocation,
                        new Interval(avail.interval, avail.timeUnits.toJavaTimeUnit()),
                        Pattern.compile(avail.upRegex),
                        avail.metricIdTemplate,
                        avail.metricTags);
                typeSet.type(type);
            }
            typeSets.availTypeSet(typeSet.build());
        }

        for (DMRResourceTypeSet rtSet : config.dmrResourceTypeSets) {
            TypeSetBuilder<ResourceType<DMRNodeLocation>> typeSet = TypeSet.<ResourceType<DMRNodeLocation>> builder();
            typeSet.name(new Name(rtSet.name));
            typeSet.enabled(rtSet.enabled);
            for (DMRResourceType rt : rtSet.dmrResourceTypes) {
                Builder<?, DMRNodeLocation> rtBuilder = ResourceType.<DMRNodeLocation> builder();
                rtBuilder.name(new Name(rt.name));
                rtBuilder.location(DMRNodeLocation.of(rt.path));
                rtBuilder.resourceNameTemplate(rt.resourceNameTemplate);
                if (rt.parents != null) {
                    for (String parent : rt.parents) {
                        rtBuilder.parent(new Name(parent));
                    }
                }
                if (rt.metricSets != null) {
                    for (String metricSet : rt.metricSets) {
                        rtBuilder.metricSetName(new Name(metricSet));
                    }
                }
                if (rt.availSets != null) {
                    for (String availSet : rt.availSets) {
                        rtBuilder.availSetName(new Name(availSet));
                    }
                }

                if (rt.dmrResourceConfigs != null) {
                    for (DMRResourceConfig resConfig : rt.dmrResourceConfigs) {
                        DMRNodeLocation location = new DMRNodeLocation(
                                getDmrPathAddress(resConfig.path),
                                resConfig.resolveExpressions,
                                resConfig.includeDefaults);
                        AttributeLocation<DMRNodeLocation> aLocation = new AttributeLocation<>(location,
                                resConfig.attribute);

                        rtBuilder.resourceConfigurationPropertyType(new ResourceConfigurationPropertyType<>(
                                ID.NULL_ID,
                                new Name(resConfig.name),
                                aLocation));
                    }
                }

                if (rt.dmrOperations != null) {
                    for (DMROperation dmrOp : rt.dmrOperations) {
                        PathAddress path = getDmrPathAddress(dmrOp.path);
                        List<OperationParam> params = new ArrayList<>();
                        if (dmrOp.dmrOperationParams != null) {
                            for (DMROperationParam dmrParam : dmrOp.dmrOperationParams) {
                                OperationParam param = new OperationParam(
                                        dmrParam.name,
                                        dmrParam.type,
                                        dmrParam.description,
                                        dmrParam.defaultValue,
                                        dmrParam.required);
                                params.add(param);
                            }
                        }
                        Operation<DMRNodeLocation> op = new Operation<>(
                                ID.NULL_ID,
                                new Name(dmrOp.name),
                                new DMRNodeLocation(path),
                                dmrOp.internalName,
                                dmrOp.modifies,
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

        if (config.managedServers.localDmr != null) {
            ConnectionData connectionData = null;

            // the agent cannot get a local client when running as a javaagent, so really
            // this is a "remote" endpoint, pointing to the local machine.
            // If user doesn't like these defaults, let them define their own remote-dmr
            connectionData = new ConnectionData("http-remoting", "127.0.0.1", 9990, null, null);

            EndpointConfiguration localDmrEndpointConfig = new EndpointConfiguration(
                    config.managedServers.localDmr.name,
                    config.managedServers.localDmr.enabled,
                    getNamesFromStrings(config.managedServers.localDmr.resourceTypeSets),
                    connectionData,
                    null,
                    config.managedServers.localDmr.setAvailOnShutdown,
                    config.managedServers.localDmr.tenantId,
                    config.managedServers.localDmr.metricIdTemplate,
                    config.managedServers.localDmr.metricTags,
                    null);
            managedServers.put(config.managedServers.localDmr.name, localDmrEndpointConfig);
        }

        if (config.managedServers.remoteDmrs != null) {
            for (RemoteDMR remoteDmr : config.managedServers.remoteDmrs) {
                if (remoteDmr.protocol == null) {
                    remoteDmr.protocol = remoteDmr.useSsl ? "https-remoting" : "http-remoting";
                }

                ConnectionData connectionData = new ConnectionData(
                        remoteDmr.protocol,
                        remoteDmr.host,
                        remoteDmr.port,
                        remoteDmr.username,
                        remoteDmr.password);

                EndpointConfiguration remoteDmrEndpointConfig = new EndpointConfiguration(
                        remoteDmr.name,
                        remoteDmr.enabled,
                        getNamesFromStrings(remoteDmr.resourceTypeSets),
                        connectionData,
                        remoteDmr.securityRealmName,
                        remoteDmr.setAvailOnShutdown,
                        remoteDmr.tenantId,
                        remoteDmr.metricIdTemplate,
                        remoteDmr.metricTags,
                        null);

                managedServers.put(remoteDmr.name, remoteDmrEndpointConfig);
            }
        }

        return new ProtocolConfiguration<DMRNodeLocation>(typeSets.build(), managedServers);
    }

    private ProtocolConfiguration<JMXNodeLocation> buildJmxConfiguration(Configuration config) throws Exception {

        TypeSets.Builder<JMXNodeLocation> typeSets = new TypeSets.Builder<>();

        for (JMXMetricSet metricSet : config.jmxMetricSets) {
            TypeSetBuilder<MetricType<JMXNodeLocation>> typeSet = TypeSet.<MetricType<JMXNodeLocation>> builder();
            typeSet.name(new Name(metricSet.name));
            typeSet.enabled(metricSet.enabled);
            for (JMXMetric metric : metricSet.jmxMetrics) {
                JMXNodeLocation location = new JMXNodeLocation(getJmxObjectName(metric.objectName));
                AttributeLocation<JMXNodeLocation> aLocation = new AttributeLocation<>(location, metric.attribute);
                MetricType<JMXNodeLocation> type = new MetricType<JMXNodeLocation>(
                        new ID(metricSet.name + "~" + metric.name),
                        new Name(metric.name),
                        aLocation,
                        new Interval(metric.interval, metric.timeUnits.toJavaTimeUnit()),
                        metric.metricUnits,
                        metric.metricType,
                        metric.metricIdTemplate,
                        metric.metricTags);
                typeSet.type(type);
            }
            typeSets.metricTypeSet(typeSet.build());
        }

        for (JMXAvailSet availSet : config.jmxAvailSets) {
            TypeSetBuilder<AvailType<JMXNodeLocation>> typeSet = TypeSet.<AvailType<JMXNodeLocation>> builder();
            typeSet.name(new Name(availSet.name));
            typeSet.enabled(availSet.enabled);
            for (JMXAvail avail : availSet.jmxAvails) {
                JMXNodeLocation location = new JMXNodeLocation(getJmxObjectName(avail.objectName));
                AttributeLocation<JMXNodeLocation> aLocation = new AttributeLocation<>(location, avail.attribute);
                AvailType<JMXNodeLocation> type = new AvailType<JMXNodeLocation>(
                        new ID(availSet.name + "~" + avail.name),
                        new Name(avail.name),
                        aLocation,
                        new Interval(avail.interval, avail.timeUnits.toJavaTimeUnit()),
                        Pattern.compile(avail.upRegex),
                        avail.metricIdTemplate,
                        avail.metricTags);
                typeSet.type(type);
            }
            typeSets.availTypeSet(typeSet.build());
        }

        for (JMXResourceTypeSet rtSet : config.jmxResourceTypeSets) {
            TypeSetBuilder<ResourceType<JMXNodeLocation>> typeSet = TypeSet.<ResourceType<JMXNodeLocation>> builder();
            typeSet.name(new Name(rtSet.name));
            typeSet.enabled(rtSet.enabled);
            for (JMXResourceType rt : rtSet.jmxResourceTypes) {
                Builder<?, JMXNodeLocation> rtBuilder = ResourceType.<JMXNodeLocation> builder();
                rtBuilder.name(new Name(rt.name));
                rtBuilder.location(new JMXNodeLocation(getJmxObjectName(rt.objectName)));
                rtBuilder.resourceNameTemplate(rt.resourceNameTemplate);
                if (rt.parents != null) {
                    for (String parent : rt.parents) {
                        rtBuilder.parent(new Name(parent));
                    }
                }
                if (rt.metricSets != null) {
                    for (String metricSet : rt.metricSets) {
                        rtBuilder.metricSetName(new Name(metricSet));
                    }
                }
                if (rt.availSets != null) {
                    for (String availSet : rt.availSets) {
                        rtBuilder.availSetName(new Name(availSet));
                    }
                }

                if (rt.jmxResourceConfigs != null) {
                    for (JMXResourceConfig resConfig : rt.jmxResourceConfigs) {
                        JMXNodeLocation location = new JMXNodeLocation(getJmxObjectName(resConfig.objectName));
                        AttributeLocation<JMXNodeLocation> aLocation = new AttributeLocation<>(location,
                                resConfig.attribute);

                        rtBuilder.resourceConfigurationPropertyType(new ResourceConfigurationPropertyType<>(
                                ID.NULL_ID,
                                new Name(resConfig.name),
                                aLocation));
                    }
                }

                if (rt.jmxOperations != null) {
                    for (JMXOperation jmxOp : rt.jmxOperations) {
                        List<OperationParam> params = new ArrayList<>();
                        if (jmxOp.jmxOperationParams != null) {
                            for (JMXOperationParam jmxParam : jmxOp.jmxOperationParams) {
                                OperationParam param = new OperationParam(
                                        jmxParam.name,
                                        jmxParam.type,
                                        jmxParam.description,
                                        jmxParam.defaultValue,
                                        jmxParam.required);
                                params.add(param);
                            }
                        }
                        Operation<JMXNodeLocation> op = new Operation<>(
                                ID.NULL_ID,
                                new Name(jmxOp.name),
                                new JMXNodeLocation(jmxOp.objectName),
                                jmxOp.internalName,
                                jmxOp.modifies,
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

        if (config.managedServers.localJmx != null) {
            EndpointConfiguration localJmx = new EndpointConfiguration(
                    config.managedServers.localJmx.name,
                    config.managedServers.localJmx.enabled,
                    getNamesFromStrings(config.managedServers.localJmx.resourceTypeSets),
                    null,
                    null,
                    config.managedServers.localJmx.setAvailOnShutdown,
                    config.managedServers.localJmx.tenantId,
                    config.managedServers.localJmx.metricIdTemplate,
                    config.managedServers.localJmx.metricTags,
                    Collections.singletonMap(JMXEndpointService.MBEAN_SERVER_NAME_KEY,
                            config.managedServers.localJmx.mbeanServerName));
            managedServers.put(config.managedServers.localJmx.name, localJmx);
        }

        if (config.managedServers.remoteJmxs != null) {
            for (RemoteJMX remoteJmx : config.managedServers.remoteJmxs) {
                URI url;
                try {
                    url = new URI(remoteJmx.url);
                } catch (Exception e) {
                    throw new Exception("Remote JMX [" + remoteJmx.name + "] has invalid URL", e);
                }

                ConnectionData connectionData = new ConnectionData(
                        url,
                        remoteJmx.username,
                        remoteJmx.password);

                EndpointConfiguration remoteJmxEndpointConfig = new EndpointConfiguration(
                        remoteJmx.name,
                        remoteJmx.enabled,
                        getNamesFromStrings(remoteJmx.resourceTypeSets),
                        connectionData,
                        remoteJmx.securityRealmName,
                        remoteJmx.setAvailOnShutdown,
                        remoteJmx.tenantId,
                        remoteJmx.metricIdTemplate,
                        remoteJmx.metricTags,
                        null);

                managedServers.put(remoteJmx.name, remoteJmxEndpointConfig);
            }
        }

        return new ProtocolConfiguration<JMXNodeLocation>(typeSets.build(), managedServers);
    }

    private ProtocolConfiguration<PlatformNodeLocation> buildPlatformConfiguration(Configuration config) {
        // assume they are disabled unless configured otherwise

        if (!config.platform.enabled) {
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

        // OS top-level metrics

        Interval osInterval = new Interval(config.platform.interval, config.platform.timeUnits.toJavaTimeUnit());

        MetricType<PlatformNodeLocation> systemCpuLoad = new MetricType<PlatformNodeLocation>(
                PlatformMetricType.OS_SYS_CPU_LOAD.getMetricTypeId(),
                PlatformMetricType.OS_SYS_CPU_LOAD.getMetricTypeName(),
                new AttributeLocation<>(
                        new PlatformNodeLocation(PlatformPath.empty()),
                        PlatformMetricType.OS_SYS_CPU_LOAD.getMetricTypeId().getIDString()),
                osInterval,
                MeasurementUnit.PERCENTAGE,
                org.hawkular.metrics.client.common.MetricType.GAUGE,
                null,
                null);

        MetricType<PlatformNodeLocation> systemLoadAverage = new MetricType<PlatformNodeLocation>(
                PlatformMetricType.OS_SYS_LOAD_AVG.getMetricTypeId(),
                PlatformMetricType.OS_SYS_LOAD_AVG.getMetricTypeName(),
                new AttributeLocation<>(
                        new PlatformNodeLocation(PlatformPath.empty()),
                        PlatformMetricType.OS_SYS_LOAD_AVG.getMetricTypeId().getIDString()),
                osInterval,
                MeasurementUnit.NONE,
                org.hawkular.metrics.client.common.MetricType.GAUGE,
                null,
                null);

        MetricType<PlatformNodeLocation> processCount = new MetricType<PlatformNodeLocation>(
                PlatformMetricType.OS_PROCESS_COUNT.getMetricTypeId(),
                PlatformMetricType.OS_PROCESS_COUNT.getMetricTypeName(),
                new AttributeLocation<>(
                        new PlatformNodeLocation(PlatformPath.empty()),
                        PlatformMetricType.OS_PROCESS_COUNT.getMetricTypeId().getIDString()),
                osInterval,
                MeasurementUnit.NONE,
                org.hawkular.metrics.client.common.MetricType.GAUGE,
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

        if (config.platform.fileStores != null && config.platform.fileStores.enabled) {
            Interval interval = new Interval(config.platform.fileStores.interval,
                    config.platform.fileStores.timeUnits.toJavaTimeUnit());

            MetricType<PlatformNodeLocation> usableSpace = new MetricType<PlatformNodeLocation>(
                    PlatformMetricType.FILE_STORE_USABLE_SPACE.getMetricTypeId(),
                    PlatformMetricType.FILE_STORE_USABLE_SPACE.getMetricTypeName(),
                    new AttributeLocation<>(
                            new PlatformNodeLocation(PlatformPath.empty()),
                            PlatformMetricType.FILE_STORE_USABLE_SPACE.getMetricTypeId().getIDString()),
                    interval,
                    MeasurementUnit.BYTES,
                    org.hawkular.metrics.client.common.MetricType.GAUGE,
                    null,
                    null);

            MetricType<PlatformNodeLocation> totalSpace = new MetricType<PlatformNodeLocation>(
                    PlatformMetricType.FILE_STORE_TOTAL_SPACE.getMetricTypeId(),
                    PlatformMetricType.FILE_STORE_TOTAL_SPACE.getMetricTypeName(),
                    new AttributeLocation<>(
                            new PlatformNodeLocation(PlatformPath.empty()),
                            PlatformMetricType.FILE_STORE_TOTAL_SPACE.getMetricTypeId().getIDString()),
                    interval,
                    MeasurementUnit.BYTES,
                    org.hawkular.metrics.client.common.MetricType.GAUGE,
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

        if (config.platform.memory != null && config.platform.memory.enabled) {
            Interval interval = new Interval(config.platform.memory.interval,
                    config.platform.memory.timeUnits.toJavaTimeUnit());

            MetricType<PlatformNodeLocation> available = new MetricType<PlatformNodeLocation>(
                    PlatformMetricType.MEMORY_AVAILABLE.getMetricTypeId(),
                    PlatformMetricType.MEMORY_AVAILABLE.getMetricTypeName(),
                    new AttributeLocation<>(
                            new PlatformNodeLocation(PlatformPath.empty()),
                            PlatformMetricType.MEMORY_AVAILABLE.getMetricTypeId().getIDString()),
                    interval,
                    MeasurementUnit.BYTES,
                    org.hawkular.metrics.client.common.MetricType.GAUGE,
                    null,
                    null);

            MetricType<PlatformNodeLocation> total = new MetricType<PlatformNodeLocation>(
                    PlatformMetricType.MEMORY_TOTAL.getMetricTypeId(),
                    PlatformMetricType.MEMORY_TOTAL.getMetricTypeName(),
                    new AttributeLocation<>(
                            new PlatformNodeLocation(PlatformPath.empty()),
                            PlatformMetricType.MEMORY_TOTAL.getMetricTypeId().getIDString()),
                    interval,
                    MeasurementUnit.BYTES,
                    org.hawkular.metrics.client.common.MetricType.GAUGE,
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

        if (config.platform.processors != null && config.platform.processors.enabled) {
            Interval interval = new Interval(config.platform.processors.interval,
                    config.platform.processors.timeUnits.toJavaTimeUnit());

            // this is the Processor.getProcessorCpuLoadBetweenTicks value
            MetricType<PlatformNodeLocation> cpuUsage = new MetricType<PlatformNodeLocation>(
                    PlatformMetricType.PROCESSOR_CPU_USAGE.getMetricTypeId(),
                    PlatformMetricType.PROCESSOR_CPU_USAGE.getMetricTypeName(),
                    new AttributeLocation<>(
                            new PlatformNodeLocation(PlatformPath.empty()),
                            PlatformMetricType.PROCESSOR_CPU_USAGE.getMetricTypeId().getIDString()),
                    interval,
                    MeasurementUnit.PERCENTAGE,
                    org.hawkular.metrics.client.common.MetricType.GAUGE,
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

        if (config.platform.powerSources != null && config.platform.powerSources.enabled) {
            Interval interval = new Interval(config.platform.powerSources.interval,
                    config.platform.powerSources.timeUnits.toJavaTimeUnit());

            MetricType<PlatformNodeLocation> remainingCap = new MetricType<PlatformNodeLocation>(
                    PlatformMetricType.POWER_SOURCE_REMAINING_CAPACITY.getMetricTypeId(),
                    PlatformMetricType.POWER_SOURCE_REMAINING_CAPACITY.getMetricTypeName(),
                    new AttributeLocation<>(
                            new PlatformNodeLocation(PlatformPath.empty()),
                            PlatformMetricType.POWER_SOURCE_REMAINING_CAPACITY.getMetricTypeId()
                                    .getIDString()),
                    interval,
                    MeasurementUnit.PERCENTAGE,
                    org.hawkular.metrics.client.common.MetricType.GAUGE,
                    null,
                    null);

            MetricType<PlatformNodeLocation> timeRemaining = new MetricType<PlatformNodeLocation>(
                    PlatformMetricType.POWER_SOURCE_TIME_REMAINING.getMetricTypeId(),
                    PlatformMetricType.POWER_SOURCE_TIME_REMAINING.getMetricTypeName(),
                    new AttributeLocation<>(
                            new PlatformNodeLocation(PlatformPath.empty()),
                            PlatformMetricType.POWER_SOURCE_TIME_REMAINING.getMetricTypeId().getIDString()),
                    interval,
                    MeasurementUnit.SECONDS,
                    org.hawkular.metrics.client.common.MetricType.GAUGE,
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
        if (config.platform.enabled) {
            EndpointConfiguration localPlatform = new EndpointConfiguration(
                    "platform",
                    true,
                    null,
                    null,
                    null,
                    Avail.DOWN,
                    null,
                    null,
                    null,
                    Collections.singletonMap(Constants.MACHINE_ID, config.platform.machineId));
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

        Map<Name, TypeSet<AvailType<L>>> availTypeSets = typeSetsBuilder.getAvailTypeSets();
        List<Name> availSetNames = resourceTypeBuilder.getAvailSetNames();
        for (Name availSetName : availSetNames) {
            TypeSet<AvailType<L>> availSet = availTypeSets.get(availSetName);
            if (availSet != null && availSet.isEnabled()) {
                resourceTypeBuilder.availTypes(availSet.getTypeMap().values());
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
}
