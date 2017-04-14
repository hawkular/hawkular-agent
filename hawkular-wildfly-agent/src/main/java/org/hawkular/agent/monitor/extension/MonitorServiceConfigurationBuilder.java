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
package org.hawkular.agent.monitor.extension;

import java.net.URI;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.StringTokenizer;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;

import javax.management.MalformedObjectNameException;
import javax.management.ObjectName;

import org.hawkular.agent.monitor.api.Avail;
import org.hawkular.agent.monitor.config.AgentCoreEngineConfiguration;
import org.hawkular.agent.monitor.config.AgentCoreEngineConfiguration.AbstractEndpointConfiguration.WaitFor;
import org.hawkular.agent.monitor.config.AgentCoreEngineConfiguration.DiagnosticsConfiguration;
import org.hawkular.agent.monitor.config.AgentCoreEngineConfiguration.DiagnosticsReportTo;
import org.hawkular.agent.monitor.config.AgentCoreEngineConfiguration.EndpointConfiguration;
import org.hawkular.agent.monitor.config.AgentCoreEngineConfiguration.GlobalConfiguration;
import org.hawkular.agent.monitor.config.AgentCoreEngineConfiguration.ProtocolConfiguration;
import org.hawkular.agent.monitor.config.AgentCoreEngineConfiguration.StorageAdapterConfiguration;
import org.hawkular.agent.monitor.config.AgentCoreEngineConfiguration.StorageReportTo;
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
import org.hawkular.agent.wildfly.log.AgentLoggers;
import org.hawkular.agent.wildfly.log.MsgLogger;
import org.jboss.as.controller.OperationContext;
import org.jboss.as.controller.OperationFailedException;
import org.jboss.as.controller.PathAddress;
import org.jboss.as.controller.SimpleAttributeDefinition;
import org.jboss.as.controller.client.helpers.MeasurementUnit;
import org.jboss.dmr.ModelNode;
import org.jboss.dmr.Property;

/**
 * Builds a {@link AgentCoreEngineConfiguration} object from the service's model.
 */
public class MonitorServiceConfigurationBuilder {
    private static final MsgLogger log = AgentLoggers.getLogger(MonitorServiceConfigurationBuilder.class);

    private ProtocolConfiguration.Builder<DMRNodeLocation> dmrConfigBuilder;
    private ProtocolConfiguration.Builder<JMXNodeLocation> jmxConfigBuilder;
    private ProtocolConfiguration.Builder<PlatformNodeLocation> platformConfigBuilder;

    private DiagnosticsConfiguration diagnostics;
    private StorageAdapterConfiguration storageAdapter;

    private GlobalConfiguration globalConfiguration;

    public MonitorServiceConfigurationBuilder(ModelNode config, OperationContext context)
            throws OperationFailedException {

        this.globalConfiguration = determineGlobalConfig(config, context);
        this.storageAdapter = determineStorageAdapterConfig(config, context);
        this.diagnostics = determineDiagnosticsConfig(config, context);

        dmrConfigBuilder = ProtocolConfiguration.builder();
        jmxConfigBuilder = ProtocolConfiguration.builder();
        platformConfigBuilder = ProtocolConfiguration.builder();

        TypeSets.Builder<DMRNodeLocation> dmrTypeSetsBuilder = TypeSets.builder();
        TypeSets.Builder<JMXNodeLocation> jmxTypeSetsBuilder = TypeSets.builder();

        determineMetricSetDmr(config, context, dmrTypeSetsBuilder, globalConfiguration);
        determineAvailSetDmr(config, context, dmrTypeSetsBuilder, globalConfiguration);

        determineMetricSetJmx(config, context, jmxTypeSetsBuilder, globalConfiguration);
        determineAvailSetJmx(config, context, jmxTypeSetsBuilder, globalConfiguration);

        // make sure to call this AFTER the metric sets and avail sets have been determined
        determineResourceTypeSetDmr(config, context, dmrTypeSetsBuilder);

        determineResourceTypeSetJmx(config, context, jmxTypeSetsBuilder);

        dmrConfigBuilder.typeSets(dmrTypeSetsBuilder.build());
        jmxConfigBuilder.typeSets(jmxTypeSetsBuilder.build());

        TypeSets<PlatformNodeLocation> platformTypeSets = buildPlatformTypeSets(config, context);
        platformConfigBuilder.typeSets(platformTypeSets);
        if (!platformTypeSets.isDisabledOrEmpty()) {
            Map<String, String> customData = new HashMap<>(2);
            customData.put(Constants.MACHINE_ID, determinePlatformMachineId(config, context));
            customData.put(Constants.CONTAINER_ID, determinePlatformContainerId(config, context));
            EndpointConfiguration endpoint = new EndpointConfiguration("platform", true, null, null, null, Avail.DOWN,
                    null, null, null, customData, null);
            platformConfigBuilder.endpoint(endpoint);
        }

        // make sure to call this AFTER the resource type sets have been determined
        this.determineManagedServers(config, context);

    }

    public AgentCoreEngineConfiguration build() {

        return new AgentCoreEngineConfiguration(globalConfiguration,
                diagnostics, storageAdapter, dmrConfigBuilder.build(), jmxConfigBuilder.build(),
                platformConfigBuilder.build());
    }

    private static void determineMetricSetDmr(ModelNode config,
            OperationContext context,
            org.hawkular.agent.monitor.inventory.TypeSets.Builder<DMRNodeLocation> typeSetsBuilder,
            GlobalConfiguration globalConfiguration)
            throws OperationFailedException {

        boolean enabled = false;
        long minInterval = globalConfiguration.getMinCollectionIntervalSeconds();

        if (config.hasDefined(DMRMetricSetDefinition.METRIC_SET)) {
            List<Property> metricSetsList = config.get(DMRMetricSetDefinition.METRIC_SET).asPropertyList();
            for (Property metricSetProperty : metricSetsList) {
                String metricSetName = metricSetProperty.getName();
                if (metricSetName.indexOf(',') > -1) {
                    log.warnCommaInName(metricSetName);
                }
                ModelNode metricSetValueNode = metricSetProperty.getValue();

                TypeSetBuilder<MetricType<DMRNodeLocation>> typeSetBuilder = TypeSet
                        .<MetricType<DMRNodeLocation>> builder()
                        .name(new Name(metricSetName))
                        .enabled(getBoolean(metricSetValueNode, context, DMRMetricSetAttributes.ENABLED));

                if (metricSetValueNode.hasDefined(DMRMetricDefinition.METRIC)) {
                    List<Property> metricsList = metricSetValueNode.get(DMRMetricDefinition.METRIC).asPropertyList();
                    for (Property metricProperty : metricsList) {
                        String metricId = metricSetName + "~" + metricProperty.getName();
                        String metricName = metricProperty.getName();
                        ModelNode metricValueNode = metricProperty.getValue();
                        String attributeString = getString(metricValueNode, context, DMRMetricAttributes.ATTRIBUTE);
                        PathAddress pathAddress = getPath(metricValueNode, context, DMRMetricAttributes.PATH);
                        boolean re = getBoolean(metricValueNode, context, DMRMetricAttributes.RESOLVE_EXPRESSIONS);
                        boolean id = getBoolean(metricValueNode, context, DMRMetricAttributes.INCLUDE_DEFAULTS);
                        String metricIdTemplate = getString(metricValueNode, context,
                                DMRMetricAttributes.METRIC_ID_TEMPLATE);
                        Map<String, String> metricTags = getMapFromString(metricValueNode, context,
                                DMRMetricAttributes.METRIC_TAGS);

                        AttributeLocation<DMRNodeLocation> location = new AttributeLocation<>(
                                new DMRNodeLocation(pathAddress, re, id),
                                attributeString);

                        Interval interval = new Interval(
                                getInt(metricValueNode, context, DMRMetricAttributes.INTERVAL),
                                getTimeUnit(metricValueNode, context, DMRMetricAttributes.TIME_UNITS));
                        long intervalSecs = interval.seconds();
                        if (intervalSecs > 0 && intervalSecs < minInterval) {
                            log.warnf("Metric [%s] interval being changed from [%s]s to minimum of [%s]s",
                                    metricName, intervalSecs, minInterval);
                            interval = new Interval((int) minInterval, TimeUnit.SECONDS);
                        }

                        MetricType<DMRNodeLocation> metric = new MetricType<>(
                                new ID(metricId),
                                new Name(metricName),
                                location,
                                interval,
                                getMeasurementUnit(metricValueNode, context, DMRMetricAttributes.METRIC_UNITS),
                                getMetricType(metricValueNode, context, DMRMetricAttributes.METRIC_TYPE),
                                metricIdTemplate,
                                metricTags);
                        if (metric.isDisabled()) {
                            log.debugf("Metric [%s] is Disabled for collection.", metric.getName());
                        }
                        typeSetBuilder.type(metric);
                    }
                }
                TypeSet<MetricType<DMRNodeLocation>> typeSet = typeSetBuilder.build();
                enabled = enabled || !typeSet.isDisabledOrEmpty();
                typeSetsBuilder.metricTypeSet(typeSet);
            }
        }
        if (!enabled) {
            log.infoNoEnabledMetricsConfigured("DMR");
        }

    }

    private static org.hawkular.metrics.client.common.MetricType getMetricType(ModelNode metricValueNode,
            OperationContext context, SimpleAttributeDefinition metricType) throws OperationFailedException {
        String metricTypeStr = getString(metricValueNode, context, metricType);
        if (metricTypeStr == null) {
            return org.hawkular.metrics.client.common.MetricType.GAUGE;
        } else {
            return org.hawkular.metrics.client.common.MetricType.valueOf(metricTypeStr.toUpperCase(Locale.ENGLISH));
        }
    }

    private static MeasurementUnit getMeasurementUnit(ModelNode metricValueNode, OperationContext context,
            SimpleAttributeDefinition metricUnits) throws OperationFailedException {
        String metricUnitsStr = getString(metricValueNode, context, metricUnits);
        if (metricUnitsStr == null) {
            return MeasurementUnit.NONE;
        } else {
            return MeasurementUnit.valueOf(metricUnitsStr.toUpperCase(Locale.ENGLISH));
        }
    }

    private static TimeUnit getTimeUnit(ModelNode metricValueNode, OperationContext context,
            SimpleAttributeDefinition timeUnits) throws OperationFailedException {
        String metricTimeUnitsStr = getString(metricValueNode, context,
                timeUnits);
        return TimeUnit.valueOf(metricTimeUnitsStr.toUpperCase());
    }

    private static void determineAvailSetDmr(ModelNode config,
            OperationContext context,
            TypeSets.Builder<DMRNodeLocation> typeSetsBuilder,
            GlobalConfiguration globalConfiguration)
            throws OperationFailedException {

        boolean enabled = false;
        long minInterval = globalConfiguration.getMinCollectionIntervalSeconds();

        if (config.hasDefined(DMRAvailSetDefinition.AVAIL_SET)) {
            List<Property> availSetsList = config.get(DMRAvailSetDefinition.AVAIL_SET).asPropertyList();
            for (Property availSetProperty : availSetsList) {
                String availSetName = availSetProperty.getName();
                if (availSetName.indexOf(',') > -1) {
                    log.warnCommaInName(availSetName);
                }
                ModelNode availSetValueNode = availSetProperty.getValue();
                TypeSetBuilder<AvailType<DMRNodeLocation>> typeSetBuilder = TypeSet
                        .<AvailType<DMRNodeLocation>> builder()
                        .name(new Name(availSetName))
                        .enabled(getBoolean(availSetValueNode, context, DMRAvailSetAttributes.ENABLED));

                if (availSetValueNode.hasDefined(DMRAvailDefinition.AVAIL)) {
                    List<Property> availsList = availSetValueNode.get(DMRAvailDefinition.AVAIL).asPropertyList();
                    for (Property availProperty : availsList) {
                        String availId = availSetName + "~" + availProperty.getName();
                        String availName = availProperty.getName();
                        ModelNode availValueNode = availProperty.getValue();
                        String attributeString = getString(availValueNode, context, DMRAvailAttributes.ATTRIBUTE);
                        PathAddress pathAddress = getPath(availValueNode, context, DMRAvailAttributes.PATH);
                        boolean re = getBoolean(availValueNode, context, DMRAvailAttributes.RESOLVE_EXPRESSIONS);
                        boolean id = getBoolean(availValueNode, context, DMRAvailAttributes.INCLUDE_DEFAULTS);
                        String metricIdTemplate = getString(availValueNode, context,
                                DMRAvailAttributes.METRIC_ID_TEMPLATE);
                        Map<String, String> metricTags = getMapFromString(availValueNode, context,
                                DMRAvailAttributes.METRIC_TAGS);

                        AttributeLocation<DMRNodeLocation> location = new AttributeLocation<>(
                                new DMRNodeLocation(pathAddress, re, id),
                                attributeString);

                        Interval interval = new Interval(
                                getInt(availValueNode, context, DMRMetricAttributes.INTERVAL),
                                getTimeUnit(availValueNode, context, DMRMetricAttributes.TIME_UNITS));
                        long intervalSecs = interval.seconds();
                        if (intervalSecs > 0 && intervalSecs < minInterval) {
                            log.warnf("Avail [%s] interval being changed from [%s]s to minimum of [%s]s",
                                    availName, intervalSecs, minInterval);
                            interval = new Interval((int) minInterval, TimeUnit.SECONDS);
                        }

                        AvailType<DMRNodeLocation> avail = new AvailType<DMRNodeLocation>(
                                new ID(availId),
                                new Name(availName),
                                location,
                                interval,
                                Pattern.compile(getString(availValueNode, context, DMRAvailAttributes.UP_REGEX)),
                                metricIdTemplate,
                                metricTags);
                        if (avail.isDisabled()) {
                            log.debugf("Avail [%s] is Disabled for collection.", avail.getName());
                        }
                        typeSetBuilder.type(avail);
                    }
                    TypeSet<AvailType<DMRNodeLocation>> typeSet = typeSetBuilder.build();
                    enabled = enabled || !typeSet.isDisabledOrEmpty();
                    typeSetsBuilder.availTypeSet(typeSet);
                }
            }
        }
        if (!enabled) {
            log.infoNoEnabledAvailsConfigured("DMR");
        }
    }

    private static void determineMetricSetJmx(ModelNode config,
            OperationContext context,
            TypeSets.Builder<JMXNodeLocation> typeSetsBuilder,
            GlobalConfiguration globalConfiguration)
            throws OperationFailedException {

        boolean enabled = false;
        long minInterval = globalConfiguration.getMinCollectionIntervalSeconds();

        if (config.hasDefined(JMXMetricSetDefinition.METRIC_SET)) {
            List<Property> metricSetsList = config.get(JMXMetricSetDefinition.METRIC_SET).asPropertyList();
            for (Property metricSetProperty : metricSetsList) {
                String metricSetName = metricSetProperty.getName();
                if (metricSetName.indexOf(',') > -1) {
                    log.warnCommaInName(metricSetName);
                }
                ModelNode metricSetValueNode = metricSetProperty.getValue();
                TypeSetBuilder<MetricType<JMXNodeLocation>> typeSetBuilder = TypeSet
                        .<MetricType<JMXNodeLocation>> builder()
                        .name(new Name(metricSetName))
                        .enabled(getBoolean(metricSetValueNode, context, JMXMetricSetAttributes.ENABLED));
                if (metricSetValueNode.hasDefined(JMXMetricDefinition.METRIC)) {
                    List<Property> metricsList = metricSetValueNode.get(JMXMetricDefinition.METRIC).asPropertyList();
                    for (Property metricProperty : metricsList) {
                        String metricId = metricSetName + "~" + metricProperty.getName();
                        String metricName = metricProperty.getName();

                        ModelNode metricValueNode = metricProperty.getValue();
                        String objectName = getString(metricValueNode, context, JMXMetricAttributes.OBJECT_NAME);
                        String metricIdTemplate = getString(metricValueNode, context,
                                JMXMetricAttributes.METRIC_ID_TEMPLATE);
                        Map<String, String> metricTags = getMapFromString(metricValueNode, context,
                                JMXMetricAttributes.METRIC_TAGS);

                        try {
                            AttributeLocation<JMXNodeLocation> location = new AttributeLocation<>(
                                    new JMXNodeLocation(objectName),
                                    getString(metricValueNode, context, JMXMetricAttributes.ATTRIBUTE));

                            Interval interval = new Interval(
                                    getInt(metricValueNode, context, DMRMetricAttributes.INTERVAL),
                                    getTimeUnit(metricValueNode, context, DMRMetricAttributes.TIME_UNITS));
                            long intervalSecs = interval.seconds();
                            if (intervalSecs > 0 && intervalSecs < minInterval) {
                                log.warnf("Metric [%s] interval being changed from [%s]s to minimum of [%s]s",
                                        metricName, intervalSecs, minInterval);
                                interval = new Interval((int) minInterval, TimeUnit.SECONDS);
                            }

                            MetricType<JMXNodeLocation> metric = new MetricType<JMXNodeLocation>(
                                    new ID(metricId),
                                    new Name(metricName),
                                    location,
                                    interval,
                                    getMeasurementUnit(metricValueNode, context, JMXMetricAttributes.METRIC_UNITS),
                                    getMetricType(metricValueNode, context, JMXMetricAttributes.METRIC_TYPE),
                                    metricIdTemplate,
                                    metricTags);
                            if (metric.isDisabled()) {
                                log.debugf("Metric [%s] is Disabled for collection.", metric.getName());
                            }
                            typeSetBuilder.type(metric);
                        } catch (MalformedObjectNameException e) {
                            log.warnMalformedJMXObjectName(objectName, e);
                        }
                    }
                    TypeSet<MetricType<JMXNodeLocation>> typeSet = typeSetBuilder.build();
                    enabled = enabled || !typeSet.isDisabledOrEmpty();
                    typeSetsBuilder.metricTypeSet(typeSet);
                }
            }
        }
        if (!enabled) {
            log.infoNoEnabledMetricsConfigured("JMX");
        }

    }

    private static void determineAvailSetJmx(ModelNode config,
            OperationContext context,
            TypeSets.Builder<JMXNodeLocation> typeSetsBuilder,
            GlobalConfiguration globalConfiguration)
            throws OperationFailedException {

        boolean enabled = false;
        long minInterval = globalConfiguration.getMinCollectionIntervalSeconds();

        if (config.hasDefined(JMXAvailSetDefinition.AVAIL_SET)) {
            List<Property> availSetsList = config.get(JMXAvailSetDefinition.AVAIL_SET).asPropertyList();
            for (Property availSetProperty : availSetsList) {
                String availSetName = availSetProperty.getName();
                if (availSetName.indexOf(',') > -1) {
                    log.warnCommaInName(availSetName);
                }
                ModelNode availSetValueNode = availSetProperty.getValue();
                TypeSetBuilder<AvailType<JMXNodeLocation>> typeSetBuilder = TypeSet
                        .<AvailType<JMXNodeLocation>> builder() //
                        .name(new Name(availSetName)) //
                        .enabled(getBoolean(availSetValueNode, context, JMXAvailSetAttributes.ENABLED));
                if (availSetValueNode.hasDefined(JMXAvailDefinition.AVAIL)) {
                    List<Property> availsList = availSetValueNode.get(JMXAvailDefinition.AVAIL).asPropertyList();
                    for (Property availProperty : availsList) {
                        String availId = availSetName + "~" + availProperty.getName();
                        String availName = availProperty.getName();
                        ModelNode availValueNode = availProperty.getValue();
                        String objectName = getString(availValueNode, context, JMXAvailAttributes.OBJECT_NAME);
                        String metricIdTemplate = getString(availValueNode, context,
                                JMXAvailAttributes.METRIC_ID_TEMPLATE);
                        Map<String, String> metricTags = getMapFromString(availValueNode, context,
                                JMXAvailAttributes.METRIC_TAGS);

                        try {
                            AttributeLocation<JMXNodeLocation> location = new AttributeLocation<>(
                                    new JMXNodeLocation(objectName),
                                    getString(availValueNode, context, JMXAvailAttributes.ATTRIBUTE));

                            Interval interval = new Interval(
                                    getInt(availValueNode, context, DMRMetricAttributes.INTERVAL),
                                    getTimeUnit(availValueNode, context, DMRMetricAttributes.TIME_UNITS));
                            long intervalSecs = interval.seconds();
                            if (intervalSecs > 0 && intervalSecs < minInterval) {
                                log.warnf("Avail [%s] interval being changed from [%s]s to minimum of [%s]s",
                                        availName, intervalSecs, minInterval);
                                interval = new Interval((int) minInterval, TimeUnit.SECONDS);
                            }

                            AvailType<JMXNodeLocation> avail = new AvailType<JMXNodeLocation>(
                                    new ID(availId),
                                    new Name(availName),
                                    location,
                                    interval,
                                    Pattern.compile(getString(availValueNode, context, JMXAvailAttributes.UP_REGEX)),
                                    metricIdTemplate,
                                    metricTags);
                            if (avail.isDisabled()) {
                                log.debugf("Avail [%s] is Disabled for collection.", avail.getName());
                            }
                            typeSetBuilder.type(avail);
                        } catch (MalformedObjectNameException e) {
                            log.warnMalformedJMXObjectName(objectName, e);
                        }
                    }
                    TypeSet<AvailType<JMXNodeLocation>> typeSet = typeSetBuilder.build();
                    enabled = enabled || !typeSet.isDisabledOrEmpty();
                    typeSetsBuilder.availTypeSet(typeSet);
                }
            }
        }

        if (!enabled) {
            log.infoNoEnabledAvailsConfigured("JMX");
        }
    }

    private static String determinePlatformMachineId(ModelNode config, OperationContext context)
            throws OperationFailedException {

        if (!config.hasDefined(PlatformDefinition.PLATFORM)) {
            return null; // not monitoring platform, so we don't collect machine ID
        }

        List<Property> asPropertyList = config.get(PlatformDefinition.PLATFORM).asPropertyList();
        if (asPropertyList.size() == 0) {
            return null; // not monitoring platform, so we don't collect machine ID
        } else if (asPropertyList.size() > 1) {
            throw new IllegalArgumentException("Only one platform config allowed: " + config.toJSONString(true));
        }

        ModelNode platformValueNode = asPropertyList.get(0).getValue();
        String machineId = getString(platformValueNode, context, PlatformAttributes.MACHINE_ID);
        return machineId;
    }

    private static String determinePlatformContainerId(ModelNode config, OperationContext context)
            throws OperationFailedException {

        if (!config.hasDefined(PlatformDefinition.PLATFORM)) {
            return null; // not monitoring platform, so we don't collect container ID
        }

        List<Property> asPropertyList = config.get(PlatformDefinition.PLATFORM).asPropertyList();
        if (asPropertyList.size() == 0) {
            return null; // not monitoring platform, so we don't collect container ID
        } else if (asPropertyList.size() > 1) {
            throw new IllegalArgumentException("Only one platform config allowed: " + config.toJSONString(true));
        }

        ModelNode platformValueNode = asPropertyList.get(0).getValue();
        String containerId = getString(platformValueNode, context, PlatformAttributes.CONTAINER_ID);
        return containerId;
    }

    private static TypeSets<PlatformNodeLocation> buildPlatformTypeSets(ModelNode config, OperationContext context)
            throws OperationFailedException {

        // assume they are disabled unless configured otherwise

        if (!config.hasDefined(PlatformDefinition.PLATFORM)) {
            log.infoNoPlatformConfig();
            return TypeSets.empty();
        }

        List<Property> asPropertyList = config.get(PlatformDefinition.PLATFORM).asPropertyList();
        if (asPropertyList.size() == 0) {
            log.infoNoPlatformConfig();
            return TypeSets.empty();
        } else if (asPropertyList.size() > 1) {
            throw new IllegalArgumentException("Only one platform config allowed: " + config.toJSONString(true));
        }

        ModelNode platformValueNode = asPropertyList.get(0).getValue();
        boolean typeSetsEnabled = getBoolean(platformValueNode, context, PlatformAttributes.ENABLED);
        if (!typeSetsEnabled) {
            log.debugf("Platform monitoring is disabled");
            return TypeSets.empty();
        }

        TypeSets.Builder<PlatformNodeLocation> typeSetsBuilder = TypeSets.builder();

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
                new ResourceConfigurationPropertyType<>(ID.NULL_ID,
                        new Name(Constants.MACHINE_ID),
                        new AttributeLocation<>(
                                new PlatformNodeLocation(PlatformPath.empty()), Constants.MACHINE_ID));
        rootTypeBldr.resourceConfigurationPropertyType(machineIdConfigType);

        ResourceConfigurationPropertyType<PlatformNodeLocation> containerIdConfigType = //
                new ResourceConfigurationPropertyType<>(ID.NULL_ID,
                        new Name(Constants.CONTAINER_ID),
                        new AttributeLocation<>(
                                new PlatformNodeLocation(PlatformPath.empty()), Constants.CONTAINER_ID));
        rootTypeBldr.resourceConfigurationPropertyType(containerIdConfigType);

        // OS top-level metrics

        Interval osInterval = new Interval(getInt(platformValueNode, context, PlatformAttributes.INTERVAL),
                TimeUnit.valueOf(getString(platformValueNode, context, PlatformAttributes.TIME_UNITS).toUpperCase()));

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

        typeSetsBuilder.metricTypeSet(osMetrics);

        rootTypeBldr.metricSetName(osMetrics.getName());
        populateMetricAndAvailTypesForResourceType(rootTypeBldr, typeSetsBuilder);

        ResourceType<PlatformNodeLocation> rootType = rootTypeBldr.build();
        TypeSet<ResourceType<PlatformNodeLocation>> rootTypeSet = TypeSet
                .<ResourceType<PlatformNodeLocation>> builder()
                .enabled(true)
                .name(osName)
                .type(rootType)
                .build();

        typeSetsBuilder.resourceTypeSet(rootTypeSet);

        // now add children types if they are enabled

        if (platformValueNode.hasDefined(FileStoresDefinition.FILE_STORES)) {
            asPropertyList = platformValueNode.get(FileStoresDefinition.FILE_STORES).asPropertyList();
            if (asPropertyList.size() == 1) {
                ModelNode fileStoresNode = asPropertyList.get(0).getValue();
                boolean enabled = getBoolean(fileStoresNode, context, FileStoresAttributes.ENABLED);
                if (enabled) {
                    Interval interval = new Interval(getInt(fileStoresNode, context, FileStoresAttributes.INTERVAL),
                            TimeUnit.valueOf(getString(fileStoresNode, context, FileStoresAttributes.TIME_UNITS)
                                    .toUpperCase()));

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

                    typeSetsBuilder.metricTypeSet(fileStoreMetrics);

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

                    populateMetricAndAvailTypesForResourceType(fileStoreBldr, typeSetsBuilder);

                    ResourceType<PlatformNodeLocation> fileStore = fileStoreBldr.build();
                    TypeSet<ResourceType<PlatformNodeLocation>> typeSet = TypeSet
                            .<ResourceType<PlatformNodeLocation>> builder()
                            .name(PlatformResourceType.FILE_STORE.getResourceTypeName())
                            .type(fileStore)
                            .build();

                    typeSetsBuilder.resourceTypeSet(typeSet);
                }
            } else if (asPropertyList.size() > 1) {
                throw new IllegalArgumentException("Only one platform.file-stores config allowed: "
                        + platformValueNode.toJSONString(true));
            }
        }

        if (platformValueNode.hasDefined(MemoryDefinition.MEMORY)) {
            asPropertyList = platformValueNode.get(MemoryDefinition.MEMORY).asPropertyList();
            if (asPropertyList.size() == 1) {
                ModelNode memoryNode = asPropertyList.get(0).getValue();
                boolean enabled = getBoolean(memoryNode, context, MemoryAttributes.ENABLED);
                if (enabled) {
                    Interval interval = new Interval(getInt(memoryNode, context, MemoryAttributes.INTERVAL),
                            TimeUnit.valueOf(
                                    getString(memoryNode, context, MemoryAttributes.TIME_UNITS).toUpperCase()));

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

                    typeSetsBuilder.metricTypeSet(memoryMetrics);

                    PlatformNodeLocation memoryLocation = new PlatformNodeLocation(
                            PlatformPath.builder().any(PlatformResourceType.MEMORY).build());
                    Builder<?, PlatformNodeLocation> memoryBldr = ResourceType.<PlatformNodeLocation> builder()
                            .id(PlatformResourceType.MEMORY.getResourceTypeId())
                            .name(PlatformResourceType.MEMORY.getResourceTypeName())
                            .parent(rootType.getName())
                            .location(memoryLocation)
                            .metricSetName(memoryMetrics.getName())
                            .resourceNameTemplate(PlatformResourceType.MEMORY.getResourceTypeName().getNameString());

                    populateMetricAndAvailTypesForResourceType(memoryBldr, typeSetsBuilder);

                    ResourceType<PlatformNodeLocation> memory = memoryBldr.build();
                    TypeSet<ResourceType<PlatformNodeLocation>> typeSet = TypeSet
                            .<ResourceType<PlatformNodeLocation>> builder()
                            .name(PlatformResourceType.MEMORY.getResourceTypeName())
                            .type(memory)
                            .build();

                    typeSetsBuilder.resourceTypeSet(typeSet);
                }
            } else if (asPropertyList.size() > 1) {
                throw new IllegalArgumentException("Only one platform.memory config allowed: "
                        + platformValueNode.toJSONString(true));
            }
        }

        if (platformValueNode.hasDefined(ProcessorsDefinition.PROCESSORS)) {
            asPropertyList = platformValueNode.get(ProcessorsDefinition.PROCESSORS).asPropertyList();
            if (asPropertyList.size() == 1) {
                ModelNode processorsNode = asPropertyList.get(0).getValue();
                boolean enabled = getBoolean(processorsNode, context, ProcessorsAttributes.ENABLED);
                if (enabled) {
                    Interval interval = new Interval(getInt(processorsNode, context, ProcessorsAttributes.INTERVAL),
                            TimeUnit.valueOf(getString(processorsNode, context, ProcessorsAttributes.TIME_UNITS)
                                    .toUpperCase()));

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

                    typeSetsBuilder.metricTypeSet(processorMetrics);

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

                    populateMetricAndAvailTypesForResourceType(processorBldr, typeSetsBuilder);

                    ResourceType<PlatformNodeLocation> processor = processorBldr.build();
                    TypeSet<ResourceType<PlatformNodeLocation>> typeSet = TypeSet
                            .<ResourceType<PlatformNodeLocation>> builder()
                            .name(PlatformResourceType.PROCESSOR.getResourceTypeName())
                            .type(processor)
                            .build();

                    typeSetsBuilder.resourceTypeSet(typeSet);
                }
            } else if (asPropertyList.size() > 1) {
                throw new IllegalArgumentException("Only one platform.processors config allowed: "
                        + platformValueNode.toJSONString(true));
            }
        }

        if (platformValueNode.hasDefined(PowerSourcesDefinition.POWER_SOURCES)) {
            asPropertyList = platformValueNode.get(PowerSourcesDefinition.POWER_SOURCES).asPropertyList();
            if (asPropertyList.size() == 1) {
                ModelNode powerSourcesNode = asPropertyList.get(0).getValue();
                boolean enabled = getBoolean(powerSourcesNode, context, PowerSourcesAttributes.ENABLED);
                if (enabled) {
                    Interval interval = new Interval(
                            getInt(powerSourcesNode, context, PowerSourcesAttributes.INTERVAL),
                            TimeUnit.valueOf(getString(powerSourcesNode, context, PowerSourcesAttributes.TIME_UNITS)
                                    .toUpperCase()));

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

                    typeSetsBuilder.metricTypeSet(powerSourceMetrics);

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

                    populateMetricAndAvailTypesForResourceType(powerSourceBldr, typeSetsBuilder);

                    ResourceType<PlatformNodeLocation> powerSource = powerSourceBldr.build();
                    TypeSet<ResourceType<PlatformNodeLocation>> typeSet = TypeSet
                            .<ResourceType<PlatformNodeLocation>> builder()
                            .name(PlatformResourceType.POWER_SOURCE.getResourceTypeName())
                            .type(powerSource)
                            .build();

                    typeSetsBuilder.resourceTypeSet(typeSet);
                }
            } else if (asPropertyList.size() > 1) {
                throw new IllegalArgumentException("Only one platform.power-sources config allowed: "
                        + platformValueNode.toJSONString(true));
            }
        }

        return typeSetsBuilder.build();
    }

    private static DiagnosticsConfiguration determineDiagnosticsConfig(ModelNode config, OperationContext context)
            throws OperationFailedException {
        if (!config.hasDefined(DiagnosticsDefinition.DIAGNOSTICS)) {
            log.infoNoDiagnosticsConfig();
            return DiagnosticsConfiguration.EMPTY;
        }

        List<Property> asPropertyList = config.get(DiagnosticsDefinition.DIAGNOSTICS).asPropertyList();
        if (asPropertyList.size() == 0) {
            log.infoNoDiagnosticsConfig();
            return DiagnosticsConfiguration.EMPTY;
        } else if (asPropertyList.size() > 1) {
            throw new IllegalArgumentException("Only one diagnostics config allowed: " + config.toJSONString(true));
        }

        ModelNode diagnosticsValueNode = asPropertyList.get(0).getValue();

        String reportToStr = getString(diagnosticsValueNode, context, DiagnosticsAttributes.REPORT_TO);
        DiagnosticsReportTo reportTo = AgentCoreEngineConfiguration.DiagnosticsReportTo.valueOf(reportToStr
                .toUpperCase());
        boolean enabled = getBoolean(diagnosticsValueNode, context, DiagnosticsAttributes.ENABLED);
        int interval = getInt(diagnosticsValueNode, context, DiagnosticsAttributes.INTERVAL);
        String diagnosticsTimeUnitsStr = getString(diagnosticsValueNode, context, DiagnosticsAttributes.TIME_UNITS);
        TimeUnit timeUnits = TimeUnit.valueOf(diagnosticsTimeUnitsStr.toUpperCase());
        return new DiagnosticsConfiguration(enabled, reportTo, interval, timeUnits);
    }

    private static StorageAdapterConfiguration determineStorageAdapterConfig(ModelNode config,
            OperationContext context)
            throws OperationFailedException {

        if (!config.hasDefined(StorageDefinition.STORAGE_ADAPTER)) {
            throw new IllegalArgumentException("Missing storage adapter configuration: " + config.toJSONString(true));
        }

        List<Property> asPropertyList = config.get(StorageDefinition.STORAGE_ADAPTER).asPropertyList();
        if (asPropertyList.size() == 0) {
            throw new IllegalArgumentException("Missing storage adapter configuration: " + config.toJSONString(true));
        } else if (asPropertyList.size() > 1) {
            throw new IllegalArgumentException("Only one storage adapter allowed: " + config.toJSONString(true));
        }

        ModelNode storageAdapterConfig = asPropertyList.get(0).getValue();

        String url = getString(storageAdapterConfig, context, StorageAttributes.URL);
        boolean useSSL = false;
        if (url != null) {
            useSSL = url.startsWith("https");
            log.infoUsingSSL(url, useSSL);
        } else {
            useSSL = getBoolean(storageAdapterConfig, context, StorageAttributes.USE_SSL);
        }
        String securityRealm = getString(storageAdapterConfig, context, StorageAttributes.SECURITY_REALM);
        String keystorePath = getString(storageAdapterConfig, context, StorageAttributes.KEYSTORE_PATH);
        String keystorePassword = getString(storageAdapterConfig, context, StorageAttributes.KEYSTORE_PASSWORD);
        String serverOutboundSocketBindingRef = getString(storageAdapterConfig, context,
                StorageAttributes.SERVER_OUTBOUND_SOCKET_BINDING_REF);
        String tenantId = getString(storageAdapterConfig, context, StorageAttributes.TENANT_ID);
        String feedId = getString(storageAdapterConfig, context, StorageAttributes.FEED_ID);
        String metricsContext = getString(storageAdapterConfig, context, StorageAttributes.METRICS_CONTEXT);
        String feedcommContext = getString(storageAdapterConfig, context, StorageAttributes.FEEDCOMM_CONTEXT);
        String username = getString(storageAdapterConfig, context, StorageAttributes.USERNAME);
        String password = getString(storageAdapterConfig, context, StorageAttributes.PASSWORD);
        String typeStr = getString(storageAdapterConfig, context, StorageAttributes.TYPE);
        StorageReportTo type = AgentCoreEngineConfiguration.StorageReportTo.valueOf(typeStr.toUpperCase());
        int connectTimeoutSeconds = getInt(storageAdapterConfig, context, StorageAttributes.CONNECT_TIMEOUT_SECONDS);
        int readTimeoutSeconds = getInt(storageAdapterConfig, context, StorageAttributes.READ_TIMEOUT_SECONDS);

        if (useSSL) {
            if (securityRealm == null) {
                if (keystorePath == null) {
                    throw new IllegalArgumentException(
                            "In order to use SSL, a securityRealm or keystorePath must be specified");
                }
                if (keystorePassword == null) {
                    throw new IllegalArgumentException(
                            "In order to use SSL, a securityRealm or keystorePassword must be specified");
                }
            }
        }

        if (username == null || password == null) {
            throw new IllegalArgumentException("Must have a username/password");
        }

        return new StorageAdapterConfiguration(type, username, password, tenantId, feedId, url, useSSL,
                serverOutboundSocketBindingRef, metricsContext, feedcommContext,
                keystorePath, keystorePassword, securityRealm, connectTimeoutSeconds, readTimeoutSeconds);
    }

    private static GlobalConfiguration determineGlobalConfig(ModelNode config, OperationContext context)
            throws OperationFailedException {
        boolean subsystemEnabled = getBoolean(config, context, SubsystemAttributes.ENABLED);
        boolean immutable = getBoolean(config, context, SubsystemAttributes.IMMUTABLE);
        boolean inContainer = getBoolean(config, context, SubsystemAttributes.IN_CONTAINER);
        String apiJndi = getString(config, context, SubsystemAttributes.API_JNDI);
        int autoDiscoveryScanPeriodSecs = getInt(config, context,
                SubsystemAttributes.AUTO_DISCOVERY_SCAN_PERIOD_SECONDS);
        int minCollectionIntervalSecs = getInt(config, context,
                SubsystemAttributes.MIN_COLLECTION_INTERVAL_SECS);
        int numDmrSchedulerThreads = getInt(config, context, SubsystemAttributes.NUM_DMR_SCHEDULER_THREADS);
        int metricDispatcherBufferSize = getInt(config, context, SubsystemAttributes.METRIC_DISPATCHER_BUFFER_SIZE);
        int metricDispatcherMaxBatchSize = getInt(config, context,
                SubsystemAttributes.METRIC_DISPATCHER_MAX_BATCH_SIZE);
        int availDispatcherBufferSize = getInt(config, context, SubsystemAttributes.AVAIL_DISPATCHER_BUFFER_SIZE);
        int availDispatcherMaxBatchSize = getInt(config, context, SubsystemAttributes.AVAIL_DISPATCHER_MAX_BATCH_SIZE);
        int pingDispatcherPeriodSeconds = getInt(config, context, SubsystemAttributes.PING_DISPATCHER_PERIOD_SECONDS);

        return new GlobalConfiguration(subsystemEnabled, immutable, inContainer, apiJndi, autoDiscoveryScanPeriodSecs,
                minCollectionIntervalSecs, numDmrSchedulerThreads, metricDispatcherBufferSize,
                metricDispatcherMaxBatchSize, availDispatcherBufferSize, availDispatcherMaxBatchSize,
                pingDispatcherPeriodSeconds);
    }

    private static void determineResourceTypeSetDmr(ModelNode config,
            OperationContext context,
            TypeSets.Builder<DMRNodeLocation> typeSetsBuilder)
            throws OperationFailedException {
        boolean enabled = false;

        if (config.hasDefined(DMRResourceTypeSetDefinition.RESOURCE_TYPE_SET)) {
            List<Property> resourceTypeSetsList = config.get(DMRResourceTypeSetDefinition.RESOURCE_TYPE_SET)
                    .asPropertyList();
            for (Property resourceTypeSetProperty : resourceTypeSetsList) {
                String resourceTypeSetName = resourceTypeSetProperty.getName();
                ModelNode resourceTypeSetValueNode = resourceTypeSetProperty.getValue();
                TypeSetBuilder<ResourceType<DMRNodeLocation>> typeSetBuilder = TypeSet
                        .<ResourceType<DMRNodeLocation>> builder()
                        .name(new Name(resourceTypeSetName))
                        .enabled(getBoolean(resourceTypeSetValueNode, context,
                                DMRResourceTypeSetAttributes.ENABLED));
                if (resourceTypeSetName.indexOf(',') > -1) {
                    log.warnCommaInName(resourceTypeSetName);
                }

                if (resourceTypeSetValueNode.hasDefined(DMRResourceTypeDefinition.RESOURCE_TYPE)) {
                    List<Property> resourceTypesList = resourceTypeSetValueNode.get(
                            DMRResourceTypeDefinition.RESOURCE_TYPE).asPropertyList();
                    for (Property resourceTypeProperty : resourceTypesList) {
                        ModelNode resourceTypeValueNode = resourceTypeProperty.getValue();

                        String resourceTypeName = resourceTypeProperty.getName();

                        Builder<?, DMRNodeLocation> resourceTypeBuilder = ResourceType.<DMRNodeLocation> builder()
                                .id(ID.NULL_ID)
                                .name(new Name(resourceTypeName))
                                .location(new DMRNodeLocation(
                                        getPath(resourceTypeValueNode, context, DMRResourceTypeAttributes.PATH)))
                                .resourceNameTemplate(getString(resourceTypeValueNode, context,
                                        DMRResourceTypeAttributes.RESOURCE_NAME_TEMPLATE))
                                .parents(getNameListFromString(resourceTypeValueNode, context,
                                        DMRResourceTypeAttributes.PARENTS));

                        List<Name> metricSets = getNameListFromString(resourceTypeValueNode, context,
                                DMRResourceTypeAttributes.METRIC_SETS);
                        List<Name> availSets = getNameListFromString(resourceTypeValueNode, context,
                                DMRResourceTypeAttributes.AVAIL_SETS);

                        resourceTypeBuilder.metricSetNames(metricSets)
                                .availSetNames(availSets);

                        // get operations
                        ModelNode opModelNode = resourceTypeValueNode.get(DMROperationDefinition.OPERATION);
                        if (opModelNode != null && opModelNode.isDefined()) {
                            List<Property> operationList = opModelNode.asPropertyList();
                            for (Property operationProperty : operationList) {
                                ModelNode operationValueNode = operationProperty.getValue();
                                String name = operationProperty.getName();

                                List<OperationParam> params = getOpParamListFromOpNode(operationValueNode, context);

                                PathAddress pathAddress = getPath(operationValueNode, context,
                                        DMROperationAttributes.PATH);
                                String internalName = getString(operationValueNode, context,
                                        DMROperationAttributes.INTERNAL_NAME);
                                boolean modifies = getBoolean(operationValueNode, context,
                                        DMROperationAttributes.MODIFIES);
                                Operation<DMRNodeLocation> op = new Operation<>(
                                        ID.NULL_ID,
                                        new Name(name),
                                        new DMRNodeLocation(pathAddress),
                                        internalName,
                                        modifies,
                                        params);
                                resourceTypeBuilder.operation(op);
                            }
                        }

                        // get resource config properties
                        ModelNode configModelNode = resourceTypeValueNode
                                .get(DMRResourceConfigDefinition.RESOURCE_CONFIG);
                        if (configModelNode != null && configModelNode.isDefined()) {
                            List<Property> configList = configModelNode.asPropertyList();
                            for (Property configProperty : configList) {
                                ModelNode configValueNode = configProperty.getValue();
                                String configName = configProperty.getName();
                                String attributeString = getString(configValueNode, context,
                                        DMRResourceConfigAttributes.ATTRIBUTE);
                                PathAddress pathAddress = getPath(configValueNode, context,
                                        DMRResourceConfigAttributes.PATH);
                                boolean re = getBoolean(configValueNode, context,
                                        DMRResourceConfigAttributes.RESOLVE_EXPRESSIONS);
                                boolean id = getBoolean(configValueNode, context,
                                        DMRResourceConfigAttributes.INCLUDE_DEFAULTS);

                                ResourceConfigurationPropertyType<DMRNodeLocation> configType = new ResourceConfigurationPropertyType<>(
                                        ID.NULL_ID, new Name(configName),
                                        new AttributeLocation<DMRNodeLocation>(
                                                new DMRNodeLocation(pathAddress, re, id),
                                                attributeString));
                                resourceTypeBuilder.resourceConfigurationPropertyType(configType);
                            }
                        }

                        populateMetricAndAvailTypesForResourceType(resourceTypeBuilder, typeSetsBuilder);

                        ResourceType<DMRNodeLocation> resourceType = resourceTypeBuilder.build();
                        typeSetBuilder.type(resourceType);

                    }
                }

                TypeSet<ResourceType<DMRNodeLocation>> typeSet = typeSetBuilder.build();
                enabled = enabled || !typeSet.isDisabledOrEmpty();
                typeSetsBuilder.resourceTypeSet(typeSet);

            }

            // can we build a graph of the full type hierarchy just to test to make sure it all is valid?
        }

        if (!enabled) {
            log.infoNoEnabledResourceTypesConfigured("DMR");
        }
    }

    private static void determineResourceTypeSetJmx(ModelNode config,
            OperationContext context, TypeSets.Builder<JMXNodeLocation> typeSetsBuilder)
            throws OperationFailedException {
        boolean enabled = false;

        if (config.hasDefined(JMXResourceTypeSetDefinition.RESOURCE_TYPE_SET)) {
            List<Property> resourceTypeSetsList = config.get(JMXResourceTypeSetDefinition.RESOURCE_TYPE_SET)
                    .asPropertyList();
            for (Property resourceTypeSetProperty : resourceTypeSetsList) {
                String resourceTypeSetName = resourceTypeSetProperty.getName();
                ModelNode resourceTypeSetValueNode = resourceTypeSetProperty.getValue();
                TypeSetBuilder<ResourceType<JMXNodeLocation>> typeSetBuilder = TypeSet
                        .<ResourceType<JMXNodeLocation>> builder()
                        .name(new Name(resourceTypeSetName))
                        .enabled(getBoolean(resourceTypeSetValueNode, context,
                                JMXResourceTypeSetAttributes.ENABLED));
                if (resourceTypeSetName.indexOf(',') > -1) {
                    log.warnCommaInName(resourceTypeSetName);
                }
                if (resourceTypeSetValueNode.hasDefined(JMXResourceTypeDefinition.RESOURCE_TYPE)) {
                    List<Property> resourceTypesList = resourceTypeSetValueNode.get(
                            JMXResourceTypeDefinition.RESOURCE_TYPE).asPropertyList();
                    for (Property resourceTypeProperty : resourceTypesList) {
                        ModelNode resourceTypeValueNode = resourceTypeProperty.getValue();

                        String resourceTypeName = resourceTypeProperty.getName();

                        String objectName = getObjectName(resourceTypeValueNode, context,
                                JMXResourceTypeAttributes.OBJECT_NAME);
                        try {
                            Builder<?, JMXNodeLocation> resourceTypeBuilder = ResourceType.<JMXNodeLocation> builder()
                                    .id(ID.NULL_ID)
                                    .name(new Name(resourceTypeName))
                                    .location(new JMXNodeLocation(objectName))
                                    .resourceNameTemplate(getString(resourceTypeValueNode, context,
                                            JMXResourceTypeAttributes.RESOURCE_NAME_TEMPLATE))
                                    .parents(getNameListFromString(resourceTypeValueNode, context,
                                            JMXResourceTypeAttributes.PARENTS));

                            List<Name> metricSets = getNameListFromString(resourceTypeValueNode, context,
                                    JMXResourceTypeAttributes.METRIC_SETS);
                            List<Name> availSets = getNameListFromString(resourceTypeValueNode, context,
                                    JMXResourceTypeAttributes.AVAIL_SETS);

                            resourceTypeBuilder.metricSetNames(metricSets)
                                    .availSetNames(availSets);

                            // get operations
                            ModelNode opModelNode = resourceTypeValueNode.get(JMXOperationDefinition.OPERATION);
                            if (opModelNode != null && opModelNode.isDefined()) {
                                List<Property> operationList = opModelNode.asPropertyList();
                                for (Property operationProperty : operationList) {
                                    ModelNode operationValueNode = operationProperty.getValue();
                                    String operationName = operationProperty.getName();

                                    Operation<JMXNodeLocation> op = new Operation<>(ID.NULL_ID,
                                            new Name(operationName),
                                            new JMXNodeLocation(getObjectName(operationValueNode, context,
                                                    JMXOperationAttributes.OBJECT_NAME)),
                                            getString(operationValueNode, context,
                                                    JMXOperationAttributes.INTERNAL_NAME),
                                            getBoolean(operationValueNode, context,
                                                    JMXOperationAttributes.MODIFIES),
                                            null);
                                    resourceTypeBuilder.operation(op);
                                }
                            }

                            // get resource config properties
                            ModelNode configModelNode = resourceTypeValueNode
                                    .get(JMXResourceConfigDefinition.RESOURCE_CONFIG);
                            if (configModelNode != null && configModelNode.isDefined()) {
                                List<Property> configList = configModelNode.asPropertyList();
                                for (Property configProperty : configList) {
                                    ModelNode configValueNode = configProperty.getValue();
                                    String configName = configProperty.getName();

                                    String on = getObjectName(configValueNode, context,
                                            JMXResourceConfigAttributes.OBJECT_NAME);
                                    String attr = getString(configValueNode, context,
                                            JMXResourceConfigAttributes.ATTRIBUTE);
                                    AttributeLocation<JMXNodeLocation> attribLoc = new AttributeLocation<JMXNodeLocation>(
                                            new JMXNodeLocation(on), attr);
                                    ResourceConfigurationPropertyType<JMXNodeLocation> configType = new ResourceConfigurationPropertyType<>(
                                            ID.NULL_ID, new Name(configName), attribLoc);
                                    resourceTypeBuilder.resourceConfigurationPropertyType(configType);

                                }
                            }

                            populateMetricAndAvailTypesForResourceType(resourceTypeBuilder, typeSetsBuilder);

                            ResourceType<JMXNodeLocation> resourceType = resourceTypeBuilder.build();
                            typeSetBuilder.type(resourceType);
                        } catch (MalformedObjectNameException e) {
                            log.warnMalformedJMXObjectName(objectName, e);
                        }

                    }
                }
                TypeSet<ResourceType<JMXNodeLocation>> typeSet = typeSetBuilder.build();
                enabled = enabled || !typeSet.isDisabledOrEmpty();
                typeSetsBuilder.resourceTypeSet(typeSet);
            }

            // can we build a graph of the full type hierarchy just to test to make sure it all is valid?
        }

        if (!enabled) {
            log.infoNoEnabledResourceTypesConfigured("JMX");
        }
    }

    private void determineManagedServers(ModelNode config, OperationContext context)
            throws OperationFailedException {
        if (config.hasDefined(ManagedServersDefinition.MANAGED_SERVERS)) {
            List<Property> asPropertyList = config.get(ManagedServersDefinition.MANAGED_SERVERS).asPropertyList();
            if (asPropertyList.size() > 1) {
                throw new IllegalArgumentException("Can only have one <managed-resources>: "
                        + config.toJSONString(true));
            }

            ModelNode managedServersValueNode = asPropertyList.get(0).getValue();

            // DMR

            if (managedServersValueNode.hasDefined(RemoteDMRDefinition.REMOTE_DMR)) {
                List<Property> remoteDMRsList = managedServersValueNode.get(RemoteDMRDefinition.REMOTE_DMR)
                        .asPropertyList();
                for (Property remoteDMRProperty : remoteDMRsList) {
                    String name = remoteDMRProperty.getName();
                    ModelNode remoteDMRValueNode = remoteDMRProperty.getValue();
                    boolean enabled = getBoolean(remoteDMRValueNode, context, RemoteDMRAttributes.ENABLED);
                    String setAvailOnShutdownStr = getString(remoteDMRValueNode, context,
                            RemoteDMRAttributes.SET_AVAIL_ON_SHUTDOWN);
                    Avail setAvailOnShutdown = (setAvailOnShutdownStr == null) ? null
                            : Avail.valueOf(setAvailOnShutdownStr);
                    String protocol = getString(remoteDMRValueNode, context, RemoteDMRAttributes.PROTOCOL);
                    String host = getString(remoteDMRValueNode, context, RemoteDMRAttributes.HOST);
                    int port = getInt(remoteDMRValueNode, context, RemoteDMRAttributes.PORT);
                    String username = getString(remoteDMRValueNode, context, RemoteDMRAttributes.USERNAME);
                    String password = getString(remoteDMRValueNode, context, RemoteDMRAttributes.PASSWORD);
                    boolean useSsl = getBoolean(remoteDMRValueNode, context, RemoteDMRAttributes.USE_SSL);
                    String securityRealm = getString(remoteDMRValueNode, context, RemoteDMRAttributes.SECURITY_REALM);
                    List<Name> resourceTypeSets = getNameListFromString(remoteDMRValueNode, context,
                            RemoteDMRAttributes.RESOURCE_TYPE_SETS);
                    String tenantId = getString(remoteDMRValueNode, context, RemoteDMRAttributes.TENANT_ID);
                    String metricIdTemplate = getString(remoteDMRValueNode, context,
                            RemoteDMRAttributes.METRIC_ID_TEMPLATE);
                    Map<String, String> metricTags = getMapFromString(remoteDMRValueNode, context,
                            RemoteDMRAttributes.METRIC_TAGS);

                    if (useSsl && securityRealm == null) {
                        log.debugf("Using SSL with no security realm - will rely on the JVM truststore: " + name);
                    }

                    if (protocol == null) {
                        protocol = useSsl ? "https-remoting" : "http-remoting";
                    }

                    List<WaitFor> waitFor = new ArrayList<WaitFor>();
                    if (remoteDMRValueNode.hasDefined(RemoteDMRWaitForDefinition.WAIT_FOR)) {
                        List<Property> waitForList = remoteDMRValueNode.get(RemoteDMRWaitForDefinition.WAIT_FOR)
                                .asPropertyList();
                        for (Property waitForProperty : waitForList) {
                            //ModelNode waitForValueNode = waitForProperty.getValue();
                            String resource = waitForProperty.getName();
                            try {
                                if (!"/".equals(resource)) {
                                    WildflyCompatibilityUtils.parseCLIStyleAddress(resource);
                                }
                            } catch (Exception e) {
                                throw new OperationFailedException(
                                        "Invalid wait-for name - not a valid DMR path: " + resource, e);
                            }
                            waitFor.add(new WaitFor(resource));
                        }
                    }

                    ConnectionData connectionData = new ConnectionData(protocol, host, port, username, password);
                    EndpointConfiguration endpoint = new EndpointConfiguration(name, enabled, resourceTypeSets,
                            connectionData, securityRealm, setAvailOnShutdown, tenantId, metricIdTemplate, metricTags,
                            null, waitFor);

                    dmrConfigBuilder.endpoint(endpoint);
                }
            }

            if (managedServersValueNode.hasDefined(LocalDMRDefinition.LOCAL_DMR)) {
                List<Property> localDMRsList = managedServersValueNode.get(LocalDMRDefinition.LOCAL_DMR)
                        .asPropertyList();
                if (localDMRsList.size() > 1) {
                    throw new IllegalArgumentException("Can only have one <local-dmr>: " + config.toJSONString(true));
                }

                Property localDMRProperty = localDMRsList.get(0);
                String name = localDMRProperty.getName();
                ModelNode localDMRValueNode = localDMRProperty.getValue();
                boolean enabled = getBoolean(localDMRValueNode, context, LocalDMRAttributes.ENABLED);
                String setAvailOnShutdownStr = getString(localDMRValueNode, context,
                        LocalDMRAttributes.SET_AVAIL_ON_SHUTDOWN);
                Avail setAvailOnShutdown = (setAvailOnShutdownStr == null) ? null
                        : Avail.valueOf(setAvailOnShutdownStr);
                List<Name> resourceTypeSets = getNameListFromString(localDMRValueNode, context,
                        LocalDMRAttributes.RESOURCE_TYPE_SETS);
                String tenantId = getString(localDMRValueNode, context, LocalDMRAttributes.TENANT_ID);
                String metricIdTemplate = getString(localDMRValueNode, context, LocalDMRAttributes.METRIC_ID_TEMPLATE);
                Map<String, String> metricTags = getMapFromString(localDMRValueNode, context,
                        LocalDMRAttributes.METRIC_TAGS);

                List<WaitFor> waitFor = new ArrayList<WaitFor>();
                if (localDMRValueNode.hasDefined(LocalDMRWaitForDefinition.WAIT_FOR)) {
                    List<Property> waitForList = localDMRValueNode.get(LocalDMRWaitForDefinition.WAIT_FOR)
                            .asPropertyList();
                    for (Property waitForProperty : waitForList) {
                        //ModelNode waitForValueNode = waitForProperty.getValue();
                        String resource = waitForProperty.getName();
                        try {
                            if (!"/".equals(resource)) {
                                WildflyCompatibilityUtils.parseCLIStyleAddress(resource);
                            }
                        } catch (Exception e) {
                            throw new OperationFailedException(
                                    "Invalid wait-for name - not a valid DMR path: " + resource, e);
                        }
                        waitFor.add(new WaitFor(resource));
                    }
                }

                EndpointConfiguration endpoint = new EndpointConfiguration(name, enabled, resourceTypeSets, null, null,
                        setAvailOnShutdown, tenantId, metricIdTemplate, metricTags, null, waitFor);
                dmrConfigBuilder.endpoint(endpoint);
            }

            // JMX

            if (managedServersValueNode.hasDefined(RemoteJMXDefinition.REMOTE_JMX)) {
                List<Property> remoteJMXsList = managedServersValueNode.get(RemoteJMXDefinition.REMOTE_JMX)
                        .asPropertyList();
                for (Property remoteJMXProperty : remoteJMXsList) {
                    String name = remoteJMXProperty.getName();
                    ModelNode remoteJMXValueNode = remoteJMXProperty.getValue();
                    boolean enabled = getBoolean(remoteJMXValueNode, context, RemoteJMXAttributes.ENABLED);
                    String setAvailOnShutdownStr = getString(remoteJMXValueNode, context,
                            RemoteJMXAttributes.SET_AVAIL_ON_SHUTDOWN);
                    Avail setAvailOnShutdown = (setAvailOnShutdownStr == null) ? null
                            : Avail.valueOf(setAvailOnShutdownStr);
                    String urlStr = getString(remoteJMXValueNode, context, RemoteJMXAttributes.URL);
                    String username = getString(remoteJMXValueNode, context, RemoteJMXAttributes.USERNAME);
                    String password = getString(remoteJMXValueNode, context, RemoteJMXAttributes.PASSWORD);
                    String securityRealm = getString(remoteJMXValueNode, context, RemoteJMXAttributes.SECURITY_REALM);
                    List<Name> resourceTypeSets = getNameListFromString(remoteJMXValueNode, context,
                            RemoteJMXAttributes.RESOURCE_TYPE_SETS);
                    String tenantId = getString(remoteJMXValueNode, context, RemoteJMXAttributes.TENANT_ID);
                    String metricIdTemplate = getString(remoteJMXValueNode, context,
                            RemoteDMRAttributes.METRIC_ID_TEMPLATE);
                    Map<String, String> metricTags = getMapFromString(remoteJMXValueNode, context,
                            RemoteDMRAttributes.METRIC_TAGS);

                    // make sure the URL is at least syntactically valid
                    URI url;
                    try {
                        url = new URI(urlStr);
                    } catch (Exception e) {
                        throw new OperationFailedException("Invalid remote JMX URL: " + urlStr, e);
                    }

                    if (url.getScheme().equalsIgnoreCase("https") && securityRealm == null) {
                        log.debugf("Using SSL with no security realm - will rely on the JVM truststore: " + name);
                    }

                    List<WaitFor> waitFor = new ArrayList<WaitFor>();
                    if (remoteJMXValueNode.hasDefined(RemoteJMXWaitForDefinition.WAIT_FOR)) {
                        List<Property> waitForList = remoteJMXValueNode.get(RemoteJMXWaitForDefinition.WAIT_FOR)
                                .asPropertyList();
                        for (Property waitForProperty : waitForList) {
                            //ModelNode waitForValueNode = waitForProperty.getValue();
                            String resource = waitForProperty.getName();
                            try {
                                new ObjectName(resource);
                            } catch (Exception e) {
                                throw new OperationFailedException(
                                        "Invalid wait-for name - not a valid JMX ObjectName: " + resource, e);
                            }
                            waitFor.add(new WaitFor(resource));
                        }
                    }

                    ConnectionData connectionData = new ConnectionData(url, username, password);
                    EndpointConfiguration endpoint = new EndpointConfiguration(name, enabled, resourceTypeSets,
                            connectionData, securityRealm, setAvailOnShutdown, tenantId, metricIdTemplate, metricTags,
                            null, waitFor);

                    jmxConfigBuilder.endpoint(endpoint);
                }
            }

            if (managedServersValueNode.hasDefined(LocalJMXDefinition.LOCAL_JMX)) {
                List<Property> localJMXsList = managedServersValueNode.get(LocalJMXDefinition.LOCAL_JMX)
                        .asPropertyList();
                for (Property localJMXProperty : localJMXsList) {
                    String name = localJMXProperty.getName();
                    ModelNode localJMXValueNode = localJMXProperty.getValue();
                    boolean enabled = getBoolean(localJMXValueNode, context, LocalJMXAttributes.ENABLED);
                    String setAvailOnShutdownStr = getString(localJMXValueNode, context,
                            LocalJMXAttributes.SET_AVAIL_ON_SHUTDOWN);
                    Avail setAvailOnShutdown = (setAvailOnShutdownStr == null) ? null
                            : Avail.valueOf(setAvailOnShutdownStr);
                    String mbsNameStr = getString(localJMXValueNode, context, LocalJMXAttributes.MBEAN_SERVER_NAME);
                    List<Name> resourceTypeSets = getNameListFromString(localJMXValueNode, context,
                            LocalJMXAttributes.RESOURCE_TYPE_SETS);
                    String tenantId = getString(localJMXValueNode, context, LocalJMXAttributes.TENANT_ID);
                    String metricIdTemplate = getString(localJMXValueNode, context,
                            RemoteDMRAttributes.METRIC_ID_TEMPLATE);
                    Map<String, String> metricTags = getMapFromString(localJMXValueNode, context,
                            RemoteDMRAttributes.METRIC_TAGS);

                    List<WaitFor> waitFor = new ArrayList<WaitFor>();
                    if (localJMXValueNode.hasDefined(LocalJMXWaitForDefinition.WAIT_FOR)) {
                        List<Property> waitForList = localJMXValueNode.get(LocalJMXWaitForDefinition.WAIT_FOR)
                                .asPropertyList();
                        for (Property waitForProperty : waitForList) {
                            //ModelNode waitForValueNode = waitForProperty.getValue();
                            String resource = waitForProperty.getName();
                            try {
                                new ObjectName(resource);
                            } catch (Exception e) {
                                throw new OperationFailedException(
                                        "Invalid wait-for name - not a valid JMX ObjectName: " + resource, e);
                            }
                            waitFor.add(new WaitFor(resource));
                        }
                    }

                    EndpointConfiguration endpoint = new EndpointConfiguration(name, enabled, resourceTypeSets,
                            null, null, setAvailOnShutdown, tenantId, metricIdTemplate, metricTags,
                            Collections.singletonMap(JMXEndpointService.MBEAN_SERVER_NAME_KEY, mbsNameStr), waitFor);

                    jmxConfigBuilder.endpoint(endpoint);
                }
            }

        }
    }

    private static boolean getBoolean(ModelNode modelNode, OperationContext context, SimpleAttributeDefinition attrib)
            throws OperationFailedException {
        ModelNode value = attrib.resolveModelAttribute(context, modelNode);
        return (value.isDefined()) ? value.asBoolean() : false;
    }

    private static String getString(ModelNode modelNode, OperationContext context, SimpleAttributeDefinition attrib)
            throws OperationFailedException {
        ModelNode value = attrib.resolveModelAttribute(context, modelNode);
        return (value.isDefined()) ? value.asString() : null;
    }

    private static PathAddress getPath(ModelNode modelNode, OperationContext context, SimpleAttributeDefinition attrib)
            throws OperationFailedException {
        String path = getString(modelNode, context, attrib);
        if (path == null) {
            return null;
        } else if ("/".equals(path)) {
            return PathAddress.EMPTY_ADDRESS;
        } else {
            return WildflyCompatibilityUtils.parseCLIStyleAddress(path);
        }
    }

    private static int getInt(ModelNode modelNode, OperationContext context, SimpleAttributeDefinition attrib)
            throws OperationFailedException {
        ModelNode value = attrib.resolveModelAttribute(context, modelNode);
        return (value.isDefined()) ? value.asInt() : 0;
    }

    private static String getObjectName(ModelNode modelNode, OperationContext context,
            SimpleAttributeDefinition attrib)
            throws OperationFailedException {
        String value = getString(modelNode, context, attrib);
        if (value != null && !value.isEmpty()) {
            // just make sure it follows valid object name syntax rules
            try {
                new ObjectName(value);
            } catch (MalformedObjectNameException e) {
                throw new OperationFailedException(
                        String.format("Attribute [%s] is an invalid object name [%s]", attrib.getName(), value), e);
            }
        }
        return value;
    }

    private static List<Name> getNameListFromString(ModelNode modelNode, OperationContext context,
            SimpleAttributeDefinition attrib) throws OperationFailedException {
        ModelNode value = attrib.resolveModelAttribute(context, modelNode);
        if (value.isDefined()) {
            String commaSeparatedList = value.asString();
            String[] stringArray = commaSeparatedList.split(",");
            ArrayList<Name> names = new ArrayList<Name>(stringArray.length);
            for (String str : stringArray) {
                names.add(new Name(str));
            }
            return names;
        } else {
            return Collections.emptyList();
        }
    }

    private static List<OperationParam> getOpParamListFromOpNode(ModelNode modelNode, OperationContext context)
            throws OperationFailedException {

        List<OperationParam> ret = new ArrayList<>();

        ModelNode params = modelNode.get(DMROperationParamDefinition.PARAM);
        if (params == null || !params.isDefined()) {
            return Collections.emptyList();
        }

        List<Property> propList = params.asPropertyList();
        for (Property prop : propList) {
            String paramName = prop.getName();
            ModelNode paramModelNode = prop.getValue();
            String paramType = getString(paramModelNode, context, DMROperationParamAttributes.TYPE);
            String paramDesc = getString(paramModelNode, context, DMROperationParamAttributes.DESCRIPTION);
            String paramDefaultValue = getString(paramModelNode, context, DMROperationParamAttributes.DEFAULT_VALUE);
            Boolean paramRequired = getBoolean(paramModelNode, context, DMROperationParamAttributes.REQUIRED);
            OperationParam operationParam = new OperationParam(paramName, paramType, paramDesc, paramDefaultValue,
                    paramRequired);
            ret.add(operationParam);
        }
        return ret;
    }

    private static Map<String, String> getMapFromString(ModelNode modelNode, OperationContext context,
            SimpleAttributeDefinition attrib) throws OperationFailedException {
        ModelNode value = attrib.resolveModelAttribute(context, modelNode);
        if (value.isDefined()) {
            Map<String, String> map = new HashMap<>();
            String commaSeparatedList = value.asString();
            StringTokenizer strtok = new StringTokenizer(commaSeparatedList, ",");
            while (strtok.hasMoreTokens()) {
                String nameValueToken = strtok.nextToken().trim();
                String[] nameValueArr = nameValueToken.split("=");
                if (nameValueArr.length != 2) {
                    throw new OperationFailedException("missing '=' in name-value pair: " + commaSeparatedList);
                }
                map.put(nameValueArr[0].trim(), nameValueArr[1].trim());
            }
            return map;
        } else {
            return Collections.emptyMap();
        }
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
}
