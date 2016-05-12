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
import org.hawkular.agent.monitor.extension.MonitorServiceConfiguration.DiagnosticsConfiguration;
import org.hawkular.agent.monitor.extension.MonitorServiceConfiguration.DiagnosticsReportTo;
import org.hawkular.agent.monitor.extension.MonitorServiceConfiguration.DynamicEndpointConfiguration;
import org.hawkular.agent.monitor.extension.MonitorServiceConfiguration.DynamicProtocolConfiguration;
import org.hawkular.agent.monitor.extension.MonitorServiceConfiguration.EndpointConfiguration;
import org.hawkular.agent.monitor.extension.MonitorServiceConfiguration.GlobalConfiguration;
import org.hawkular.agent.monitor.extension.MonitorServiceConfiguration.ProtocolConfiguration;
import org.hawkular.agent.monitor.extension.MonitorServiceConfiguration.StorageAdapterConfiguration;
import org.hawkular.agent.monitor.extension.MonitorServiceConfiguration.StorageReportTo;
import org.hawkular.agent.monitor.inventory.AttributeLocation;
import org.hawkular.agent.monitor.inventory.AvailType;
import org.hawkular.agent.monitor.inventory.ConnectionData;
import org.hawkular.agent.monitor.inventory.ID;
import org.hawkular.agent.monitor.inventory.Interval;
import org.hawkular.agent.monitor.inventory.MetricType;
import org.hawkular.agent.monitor.inventory.Name;
import org.hawkular.agent.monitor.inventory.NameSet;
import org.hawkular.agent.monitor.inventory.NameSet.NameSetBuilder;
import org.hawkular.agent.monitor.inventory.Operation;
import org.hawkular.agent.monitor.inventory.ResourceConfigurationPropertyType;
import org.hawkular.agent.monitor.inventory.ResourceType;
import org.hawkular.agent.monitor.inventory.ResourceType.Builder;
import org.hawkular.agent.monitor.inventory.TypeSet;
import org.hawkular.agent.monitor.inventory.TypeSet.TypeSetBuilder;
import org.hawkular.agent.monitor.inventory.TypeSets;
import org.hawkular.agent.monitor.log.AgentLoggers;
import org.hawkular.agent.monitor.log.MsgLogger;
import org.hawkular.agent.monitor.protocol.dmr.DMRNodeLocation;
import org.hawkular.agent.monitor.protocol.jmx.JMXNodeLocation;
import org.hawkular.agent.monitor.protocol.platform.Constants;
import org.hawkular.agent.monitor.protocol.platform.Constants.PlatformResourceType;
import org.hawkular.agent.monitor.protocol.platform.PlatformNodeLocation;
import org.hawkular.agent.monitor.protocol.platform.PlatformPath;
import org.jboss.as.controller.OperationContext;
import org.jboss.as.controller.OperationFailedException;
import org.jboss.as.controller.PathAddress;
import org.jboss.as.controller.SimpleAttributeDefinition;
import org.jboss.as.controller.client.helpers.MeasurementUnit;
import org.jboss.dmr.ModelNode;
import org.jboss.dmr.Property;

/**
 * Builds a {@link MonitorServiceConfiguration} object from the service's model.
 */
public class MonitorServiceConfigurationBuilder {
    private static final MsgLogger log = AgentLoggers.getLogger(MonitorServiceConfigurationBuilder.class);

    private ProtocolConfiguration.Builder<DMRNodeLocation> dmrConfigBuilder;
    private ProtocolConfiguration.Builder<JMXNodeLocation> jmxConfigBuilder;
    private ProtocolConfiguration.Builder<PlatformNodeLocation> platformConfigBuilder;
    private DynamicProtocolConfiguration.Builder prometheusConfigBuilder;

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
        prometheusConfigBuilder = DynamicProtocolConfiguration.builder();

        TypeSets.Builder<DMRNodeLocation> dmrTypeSetsBuilder = TypeSets.builder();
        TypeSets.Builder<JMXNodeLocation> jmxTypeSetsBuilder = TypeSets.builder();
        Map<Name, NameSet> prometheusMetricSets = new HashMap<>();

        determineMetricSetDmr(config, context, dmrTypeSetsBuilder);
        determineAvailSetDmr(config, context, dmrTypeSetsBuilder);

        determineMetricSetJmx(config, context, jmxTypeSetsBuilder);
        determineAvailSetJmx(config, context, jmxTypeSetsBuilder);

        determineMetricSetPrometheus(config, context, prometheusMetricSets);

        // make sure to call this AFTER the metric sets and avail sets have been determined
        determineResourceTypeSetDmr(config, context, dmrTypeSetsBuilder);

        determineResourceTypeSetJmx(config, context, jmxTypeSetsBuilder);

        dmrConfigBuilder.typeSets(dmrTypeSetsBuilder.build());
        jmxConfigBuilder.typeSets(jmxTypeSetsBuilder.build());
        prometheusConfigBuilder.metricSets(prometheusMetricSets);

        TypeSets<PlatformNodeLocation> platformTypeSets = buildPlatformTypeSets(config, context);
        platformConfigBuilder.typeSets(platformTypeSets);
        if (!platformTypeSets.isDisabledOrEmpty()) {
            EndpointConfiguration endpoint = new EndpointConfiguration("platform", true, null, null, null, Avail.DOWN,
                    null);
            platformConfigBuilder.endpoint(endpoint);
        }

        // make sure to call this AFTER the resource type sets have been determined
        this.determineManagedServers(config, context);

    }

    public MonitorServiceConfiguration build() {

        return new MonitorServiceConfiguration(globalConfiguration,
                diagnostics, storageAdapter, dmrConfigBuilder.build(), jmxConfigBuilder.build(),
                platformConfigBuilder.build(), prometheusConfigBuilder.build());
    }

    private static void determineMetricSetDmr(ModelNode config,
            OperationContext context,
            org.hawkular.agent.monitor.inventory.TypeSets.Builder<DMRNodeLocation>//
            typeSetsBuilder)
                    throws OperationFailedException {

        boolean enabled = false;

        if (config.hasDefined(DMRMetricSetDefinition.METRIC_SET)) {
            List<Property> metricSetsList = config.get(DMRMetricSetDefinition.METRIC_SET).asPropertyList();
            for (Property metricSetProperty : metricSetsList) {
                String metricSetName = metricSetProperty.getName();
                if (metricSetName.indexOf(',') > -1) {
                    log.warnCommaInName(metricSetName);
                }
                ModelNode metricSetValueNode = metricSetProperty.getValue();

                TypeSetBuilder<MetricType<DMRNodeLocation>> typeSetBuilder =
                        TypeSet.<MetricType<DMRNodeLocation>> builder()
                                .name(new Name(metricSetName))
                                .enabled(getBoolean(metricSetValueNode, context, DMRMetricSetAttributes.ENABLED));

                if (metricSetValueNode.hasDefined(DMRMetricDefinition.METRIC)) {
                    List<Property> metricsList = metricSetValueNode.get(DMRMetricDefinition.METRIC).asPropertyList();
                    for (Property metricProperty : metricsList) {
                        String metricName = metricSetName + "~" + metricProperty.getName();
                        ModelNode metricValueNode = metricProperty.getValue();
                        String attributeString = getString(metricValueNode, context, DMRMetricAttributes.ATTRIBUTE);
                        PathAddress pathAddress = getPath(metricValueNode, context, DMRMetricAttributes.PATH);
                        boolean re = getBoolean(metricValueNode, context, DMRMetricAttributes.RESOLVE_EXPRESSIONS);
                        boolean id = getBoolean(metricValueNode, context, DMRMetricAttributes.INCLUDE_DEFAULTS);
                        AttributeLocation<DMRNodeLocation> location = new AttributeLocation<>(
                                new DMRNodeLocation(pathAddress, re, id),
                                attributeString);
                        MetricType<DMRNodeLocation> metric = new MetricType<>(
                                ID.NULL_ID,
                                new Name(metricName),
                                location,
                                new Interval(getInt(metricValueNode, context, DMRMetricAttributes.INTERVAL),
                                        getTimeUnit(metricValueNode, context, DMRMetricAttributes.TIME_UNITS)),
                                getMeasurementUnit(metricValueNode, context, DMRMetricAttributes.METRIC_UNITS),
                                getMetricType(metricValueNode, context, DMRMetricAttributes.METRIC_TYPE));
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
            TypeSets.Builder<DMRNodeLocation> typeSetsBuilder)
                    throws OperationFailedException {
        boolean enabled = false;

        if (config.hasDefined(DMRAvailSetDefinition.AVAIL_SET)) {
            List<Property> availSetsList = config.get(DMRAvailSetDefinition.AVAIL_SET).asPropertyList();
            for (Property availSetProperty : availSetsList) {
                String availSetName = availSetProperty.getName();
                if (availSetName.indexOf(',') > -1) {
                    log.warnCommaInName(availSetName);
                }
                ModelNode availSetValueNode = availSetProperty.getValue();
                TypeSetBuilder<AvailType<DMRNodeLocation>> typeSetBuilder =
                        TypeSet.<AvailType<DMRNodeLocation>> builder()
                                .name(new Name(availSetName))
                                .enabled(getBoolean(availSetValueNode, context, DMRAvailSetAttributes.ENABLED));

                if (availSetValueNode.hasDefined(DMRAvailDefinition.AVAIL)) {
                    List<Property> availsList = availSetValueNode.get(DMRAvailDefinition.AVAIL).asPropertyList();
                    for (Property availProperty : availsList) {
                        String availName = availSetName + "~" + availProperty.getName();
                        ModelNode availValueNode = availProperty.getValue();
                        String attributeString = getString(availValueNode, context, DMRAvailAttributes.ATTRIBUTE);
                        PathAddress pathAddress = getPath(availValueNode, context, DMRAvailAttributes.PATH);
                        boolean re = getBoolean(availValueNode, context, DMRAvailAttributes.RESOLVE_EXPRESSIONS);
                        boolean id = getBoolean(availValueNode, context, DMRAvailAttributes.INCLUDE_DEFAULTS);
                        AttributeLocation<DMRNodeLocation> location = new AttributeLocation<>(
                                new DMRNodeLocation(pathAddress, re, id),
                                attributeString);

                        AvailType<DMRNodeLocation> avail = new AvailType<DMRNodeLocation>(ID.NULL_ID,
                                new Name(availName),
                                location,
                                new Interval(getInt(availValueNode, context, DMRAvailAttributes.INTERVAL),
                                        getTimeUnit(availValueNode, context, DMRAvailAttributes.TIME_UNITS)),
                                Pattern.compile(getString(availValueNode, context, DMRAvailAttributes.UP_REGEX)));
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
            TypeSets.Builder<JMXNodeLocation> typeSetsBuilder)
                    throws OperationFailedException {

        boolean enabled = false;

        if (config.hasDefined(JMXMetricSetDefinition.METRIC_SET)) {
            List<Property> metricSetsList = config.get(JMXMetricSetDefinition.METRIC_SET).asPropertyList();
            for (Property metricSetProperty : metricSetsList) {
                String metricSetName = metricSetProperty.getName();
                if (metricSetName.indexOf(',') > -1) {
                    log.warnCommaInName(metricSetName);
                }
                ModelNode metricSetValueNode = metricSetProperty.getValue();
                TypeSetBuilder<MetricType<JMXNodeLocation>> typeSetBuilder =
                        TypeSet.<MetricType<JMXNodeLocation>> builder()
                                .name(new Name(metricSetName))
                                .enabled(getBoolean(metricSetValueNode, context, JMXMetricSetAttributes.ENABLED));
                if (metricSetValueNode.hasDefined(JMXMetricDefinition.METRIC)) {
                    List<Property> metricsList = metricSetValueNode.get(JMXMetricDefinition.METRIC).asPropertyList();
                    for (Property metricProperty : metricsList) {
                        String metricName = metricSetName + "~" + metricProperty.getName();

                        ModelNode metricValueNode = metricProperty.getValue();
                        String objectName = getString(metricValueNode, context, JMXMetricAttributes.OBJECT_NAME);
                        try {
                            AttributeLocation<JMXNodeLocation> location = new AttributeLocation<>(
                                    new JMXNodeLocation(objectName),
                                    getString(metricValueNode, context, JMXMetricAttributes.ATTRIBUTE));

                            MetricType<JMXNodeLocation> metric = new MetricType<JMXNodeLocation>(ID.NULL_ID,
                                    new Name(metricName),
                                    location,
                                    new Interval(getInt(metricValueNode, context, JMXMetricAttributes.INTERVAL),
                                            getTimeUnit(metricValueNode, context, JMXMetricAttributes.TIME_UNITS)),
                                    getMeasurementUnit(metricValueNode, context, JMXMetricAttributes.METRIC_UNITS),
                                    getMetricType(metricValueNode, context, JMXMetricAttributes.METRIC_TYPE));
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
            TypeSets.Builder<JMXNodeLocation> typeSetsBuilder)
                    throws OperationFailedException {

        boolean enabled = false;

        if (config.hasDefined(JMXAvailSetDefinition.AVAIL_SET)) {
            List<Property> availSetsList = config.get(JMXAvailSetDefinition.AVAIL_SET).asPropertyList();
            for (Property availSetProperty : availSetsList) {
                String availSetName = availSetProperty.getName();
                if (availSetName.indexOf(',') > -1) {
                    log.warnCommaInName(availSetName);
                }
                ModelNode availSetValueNode = availSetProperty.getValue();
                TypeSetBuilder<AvailType<JMXNodeLocation>> typeSetBuilder =
                        TypeSet.<AvailType<JMXNodeLocation>> builder() //
                                .name(new Name(availSetName)) //
                                .enabled(getBoolean(availSetValueNode, context, JMXAvailSetAttributes.ENABLED));
                if (availSetValueNode.hasDefined(JMXAvailDefinition.AVAIL)) {
                    List<Property> availsList = availSetValueNode.get(JMXAvailDefinition.AVAIL).asPropertyList();
                    for (Property availProperty : availsList) {
                        String availName = availSetName + "~" + availProperty.getName();
                        ModelNode availValueNode = availProperty.getValue();

                        String objectName = getString(availValueNode, context, JMXAvailAttributes.OBJECT_NAME);
                        try {
                            AttributeLocation<JMXNodeLocation> location = new AttributeLocation<>(
                                    new JMXNodeLocation(objectName),
                                    getString(availValueNode, context, JMXAvailAttributes.ATTRIBUTE));

                            AvailType<JMXNodeLocation> avail = new AvailType<JMXNodeLocation>(ID.NULL_ID,
                                    new Name(availName), location,
                                    new Interval(getInt(availValueNode, context, JMXAvailAttributes.INTERVAL),
                                            getTimeUnit(availValueNode, context, JMXAvailAttributes.TIME_UNITS)),
                                    Pattern.compile(getString(availValueNode, context, JMXAvailAttributes.UP_REGEX)));
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

    private static void determineMetricSetPrometheus(ModelNode config,
            OperationContext context,
            Map<Name, NameSet> namedMetricSets)
                    throws OperationFailedException {

        boolean enabled = false;

        if (config.hasDefined(PrometheusMetricSetDefinition.METRIC_SET)) {
            List<Property> metricSetsList = config.get(PrometheusMetricSetDefinition.METRIC_SET).asPropertyList();
            for (Property metricSetProperty : metricSetsList) {
                NameSetBuilder nameSetBuilder = NameSet.builder();

                String metricSetName = metricSetProperty.getName();
                if (metricSetName.indexOf(',') > -1) {
                    log.warnCommaInName(metricSetName);
                }
                nameSetBuilder.nameOfSet(new Name(metricSetName));

                ModelNode metricSetValueNode = metricSetProperty.getValue();

                enabled = getBoolean(metricSetValueNode, context, PrometheusMetricSetAttributes.ENABLED);
                nameSetBuilder.enabled(enabled);

                if (metricSetValueNode.hasDefined(PrometheusMetricDefinition.METRIC)) {
                    List<Property> metricsList = metricSetValueNode.get(PrometheusMetricDefinition.METRIC)
                            .asPropertyList();
                    for (Property metricProperty : metricsList) {
                        String metricName = metricProperty.getName();
                        try {
                            Pattern.compile(metricName); // metric name can be a regex, make sure it can compile
                            nameSetBuilder.name(new Name(metricName));
                        } catch (Exception e) {
                            throw new OperationFailedException("Metric name is an invalid regex: " + metricName);
                        }

                        // there are no attributes (other than 'name')to process
                        //ModelNode metricValueNode = metricProperty.getValue();
                    }

                    NameSet nameSet = nameSetBuilder.build();
                    namedMetricSets.put(nameSet.getName(), nameSet);

                    enabled = enabled || !nameSet.isDisabledOrEmpty();
                }
            }
        }
        if (!enabled) {
            log.infoNoEnabledMetricsConfigured("Prometheus");
        }

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
        final Name osName = PlatformResourceType.OPERATING_SYSTEM.getName();

        Builder<?, PlatformNodeLocation> rootTypeBldr = ResourceType.<PlatformNodeLocation> builder()
                .name(osName)
                .location(new PlatformNodeLocation(
                        PlatformPath.builder().any(PlatformResourceType.OPERATING_SYSTEM).build()))
                .resourceNameTemplate("%s");

        ResourceConfigurationPropertyType<PlatformNodeLocation> machineIdConfigType =
                new ResourceConfigurationPropertyType<> (
                        ID.NULL_ID,
                        Constants.MACHINE_ID,
                        new AttributeLocation<>(
                                new PlatformNodeLocation(PlatformPath.empty()), Constants.MACHINE_ID.getNameString()
                        )
                );
        rootTypeBldr.resourceConfigurationPropertyType(machineIdConfigType);

        populateMetricAndAvailTypesForResourceType(rootTypeBldr, typeSetsBuilder);
        ResourceType<PlatformNodeLocation> rootType = rootTypeBldr.build();

        TypeSet<ResourceType<PlatformNodeLocation>> rootTypeSet =
                TypeSet.<ResourceType<PlatformNodeLocation>> builder()
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
                            TimeUnit.valueOf(getString(fileStoresNode, context,
                                    FileStoresAttributes.TIME_UNITS).toUpperCase()));

                    MetricType<PlatformNodeLocation> usableSpace = new MetricType<PlatformNodeLocation>(null,
                            Constants.FILE_STORE_USABLE_SPACE,
                            new AttributeLocation<>(
                                    new PlatformNodeLocation(PlatformPath.empty()),
                                    Constants.FILE_STORE_USABLE_SPACE.getNameString()),
                            interval,
                            MeasurementUnit.BYTES,
                            org.hawkular.metrics.client.common.MetricType.GAUGE);

                    MetricType<PlatformNodeLocation> totalSpace = new MetricType<PlatformNodeLocation>(null,
                            Constants.FILE_STORE_TOTAL_SPACE,
                            new AttributeLocation<>(
                                    new PlatformNodeLocation(PlatformPath.empty()),
                                    Constants.FILE_STORE_TOTAL_SPACE.getNameString()),
                            interval,
                            MeasurementUnit.BYTES,
                            org.hawkular.metrics.client.common.MetricType.GAUGE);

                    TypeSet<MetricType<PlatformNodeLocation>> fileStoreMetrics =
                            TypeSet.<MetricType<PlatformNodeLocation>> builder()
                            .name(PlatformResourceType.FILE_STORE.getName())
                                    .type(usableSpace)
                                    .type(totalSpace)
                                    .build();

                    typeSetsBuilder.metricTypeSet(fileStoreMetrics);

                    PlatformNodeLocation fileStoreLocation = new PlatformNodeLocation(
                            PlatformPath.builder().any(PlatformResourceType.FILE_STORE).build());
                    Builder<?, PlatformNodeLocation> fileStoreBldr = ResourceType.<PlatformNodeLocation> builder()
                            .name(PlatformResourceType.FILE_STORE.getName())
                            .location(fileStoreLocation)
                            .resourceNameTemplate(
                                    PlatformResourceType.FILE_STORE.getName().getNameString() + " [%s]")
                            .parent(rootType.getName())
                            .metricSetName(fileStoreMetrics.getName());

                    populateMetricAndAvailTypesForResourceType(fileStoreBldr, typeSetsBuilder);

                    ResourceType<PlatformNodeLocation> fileStore = fileStoreBldr.build();
                    TypeSet<ResourceType<PlatformNodeLocation>> typeSet =
                            TypeSet.<ResourceType<PlatformNodeLocation>> builder()
                            .name(PlatformResourceType.FILE_STORE.getName())
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
                            TimeUnit.valueOf(getString(memoryNode, context,
                                    MemoryAttributes.TIME_UNITS).toUpperCase()));

                    MetricType<PlatformNodeLocation> available = new MetricType<PlatformNodeLocation>(null,
                            Constants.MEMORY_AVAILABLE,
                            new AttributeLocation<PlatformNodeLocation>(
                                    new PlatformNodeLocation(PlatformPath.empty()),
                                    Constants.MEMORY_AVAILABLE.getNameString()),
                            interval,
                            MeasurementUnit.BYTES,
                            org.hawkular.metrics.client.common.MetricType.GAUGE);

                    MetricType<PlatformNodeLocation> total = new MetricType<PlatformNodeLocation>(null,
                            Constants.MEMORY_TOTAL,
                            new AttributeLocation<PlatformNodeLocation>(
                                    new PlatformNodeLocation(PlatformPath.empty()),
                                    Constants.MEMORY_TOTAL.getNameString()),
                            interval,
                            MeasurementUnit.BYTES,
                            org.hawkular.metrics.client.common.MetricType.GAUGE);

                    TypeSet<MetricType<PlatformNodeLocation>> memoryMetrics =
                            TypeSet.<MetricType<PlatformNodeLocation>> builder()
                            .name(PlatformResourceType.MEMORY.getName())
                                    .type(available)
                                    .type(total)
                                    .build();

                    typeSetsBuilder.metricTypeSet(memoryMetrics);

                    PlatformNodeLocation memoryLocation = new PlatformNodeLocation(
                            PlatformPath.builder().any(PlatformResourceType.MEMORY).build());
                    Builder<?, PlatformNodeLocation> memoryBldr = ResourceType.<PlatformNodeLocation> builder()
                            .name(PlatformResourceType.MEMORY.getName())
                            .parent(rootType.getName())
                            .location(memoryLocation)
                            .metricSetName(memoryMetrics.getName())
                            .resourceNameTemplate(PlatformResourceType.MEMORY.getName().getNameString());

                    populateMetricAndAvailTypesForResourceType(memoryBldr, typeSetsBuilder);

                    ResourceType<PlatformNodeLocation> memory = memoryBldr.build();
                    TypeSet<ResourceType<PlatformNodeLocation>> typeSet =
                            TypeSet.<ResourceType<PlatformNodeLocation>> builder()
                            .name(PlatformResourceType.MEMORY.getName())
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
                            TimeUnit.valueOf(getString(processorsNode, context,
                                    ProcessorsAttributes.TIME_UNITS).toUpperCase()));

                    // this is the Processor.getProcessorCpuLoadBetweenTicks value
                    MetricType<PlatformNodeLocation> cpuUsage = new MetricType<PlatformNodeLocation>(null,
                            Constants.PROCESSOR_CPU_USAGE,
                            new AttributeLocation<PlatformNodeLocation>(
                                    new PlatformNodeLocation(PlatformPath.empty()),
                                    Constants.PROCESSOR_CPU_USAGE.getNameString()),
                            interval,
                            MeasurementUnit.PERCENTAGE,
                            org.hawkular.metrics.client.common.MetricType.GAUGE);

                    TypeSet<MetricType<PlatformNodeLocation>> processorMetrics =
                            TypeSet.<MetricType<PlatformNodeLocation>> builder()
                            .name(PlatformResourceType.PROCESSOR.getName())
                                    .type(cpuUsage)
                                    .build();

                    typeSetsBuilder.metricTypeSet(processorMetrics);

                    PlatformNodeLocation processorsLocation = new PlatformNodeLocation(
                            PlatformPath.builder().any(PlatformResourceType.PROCESSOR).build());
                    Builder<?, PlatformNodeLocation> processorBldr = ResourceType.<PlatformNodeLocation> builder()
                            .name(PlatformResourceType.PROCESSOR.getName())
                            .parent(rootType.getName())
                            .location(processorsLocation)
                            .metricSetName(processorMetrics.getName())
                            .resourceNameTemplate(PlatformResourceType.PROCESSOR.getName().getNameString() + " [%s]");

                    populateMetricAndAvailTypesForResourceType(processorBldr, typeSetsBuilder);

                    ResourceType<PlatformNodeLocation> processor = processorBldr.build();
                    TypeSet<ResourceType<PlatformNodeLocation>> typeSet =
                            TypeSet.<ResourceType<PlatformNodeLocation>> builder()
                            .name(PlatformResourceType.PROCESSOR.getName())
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
                            TimeUnit.valueOf(getString(powerSourcesNode, context,
                                    PowerSourcesAttributes.TIME_UNITS).toUpperCase()));

                    MetricType<PlatformNodeLocation> remainingCap = new MetricType<PlatformNodeLocation>(null,
                            Constants.POWER_SOURCE_REMAINING_CAPACITY,
                            new AttributeLocation<PlatformNodeLocation>(
                                    new PlatformNodeLocation(PlatformPath.empty()),
                                    Constants.POWER_SOURCE_REMAINING_CAPACITY.getNameString()),
                            interval,
                            MeasurementUnit.PERCENTAGE,
                            org.hawkular.metrics.client.common.MetricType.GAUGE);

                    MetricType<PlatformNodeLocation> timeRemaining = new MetricType<PlatformNodeLocation>(null,
                            Constants.POWER_SOURCE_TIME_REMAINING,
                            new AttributeLocation<PlatformNodeLocation>(
                                    new PlatformNodeLocation(PlatformPath.empty()),
                                    Constants.POWER_SOURCE_TIME_REMAINING.getNameString()),
                            interval,
                            MeasurementUnit.SECONDS,
                            org.hawkular.metrics.client.common.MetricType.GAUGE);

                    TypeSet<MetricType<PlatformNodeLocation>> powerSourceMetrics =
                            TypeSet.<MetricType<PlatformNodeLocation>> builder()
                            .name(PlatformResourceType.POWER_SOURCE.getName())
                                    .type(remainingCap)
                                    .type(timeRemaining)
                                    .build();

                    typeSetsBuilder.metricTypeSet(powerSourceMetrics);

                    PlatformNodeLocation powerSourcesLocation = new PlatformNodeLocation(
                            PlatformPath.builder().any(PlatformResourceType.POWER_SOURCE).build());
                    Builder<?, PlatformNodeLocation> powerSourceBldr = ResourceType.<PlatformNodeLocation> builder()
                            .name(PlatformResourceType.POWER_SOURCE.getName())
                            .parent(rootType.getName())
                            .location(powerSourcesLocation)
                            .metricSetName(powerSourceMetrics.getName())
                            .resourceNameTemplate(
                                    PlatformResourceType.POWER_SOURCE.getName().getNameString() + " [%s]");

                    populateMetricAndAvailTypesForResourceType(powerSourceBldr, typeSetsBuilder);

                    ResourceType<PlatformNodeLocation> powerSource = powerSourceBldr.build();
                    TypeSet<ResourceType<PlatformNodeLocation>> typeSet =
                            TypeSet.<ResourceType<PlatformNodeLocation>> builder()
                            .name(PlatformResourceType.POWER_SOURCE.getName())
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
        DiagnosticsReportTo reportTo = MonitorServiceConfiguration.DiagnosticsReportTo.valueOf(reportToStr
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
        String accountsContext = getString(storageAdapterConfig, context, StorageAttributes.ACCOUNTS_CONTEXT);
        String inventoryContext = getString(storageAdapterConfig, context, StorageAttributes.INVENTORY_CONTEXT);
        String metricsContext = getString(storageAdapterConfig, context, StorageAttributes.METRICS_CONTEXT);
        String feedcommContext = getString(storageAdapterConfig, context, StorageAttributes.FEEDCOMM_CONTEXT);
        String username = getString(storageAdapterConfig, context, StorageAttributes.USERNAME);
        String password = getString(storageAdapterConfig, context, StorageAttributes.PASSWORD);
        String key = getString(storageAdapterConfig, context, StorageAttributes.SECURITY_KEY);
        String secret = getString(storageAdapterConfig, context, StorageAttributes.SECURITY_SECRET);
        String typeStr = getString(storageAdapterConfig, context, StorageAttributes.TYPE);
        StorageReportTo type = MonitorServiceConfiguration.StorageReportTo.valueOf(typeStr.toUpperCase());

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

        if ((username == null || password == null) && (key == null || secret == null)) {
            throw new IllegalArgumentException("Must have a username/password or security key/secret");
        }

        return new StorageAdapterConfiguration(type, username, password, key, secret, tenantId, feedId, url, useSSL,
                serverOutboundSocketBindingRef, accountsContext, inventoryContext, metricsContext, feedcommContext,
                keystorePath, keystorePassword, securityRealm);
    }

    private static GlobalConfiguration determineGlobalConfig(ModelNode config, OperationContext context)
            throws OperationFailedException {
        boolean subsystemEnabled = getBoolean(config, context, SubsystemAttributes.ENABLED);
        String apiJndi = getString(config, context, SubsystemAttributes.API_JNDI);
        int autoDiscoveryScanPeriodSecs = getInt(config, context, SubsystemAttributes.AUTO_DISCOVERY_SCAN_PERIOD_SECS);
        int numDmrSchedulerThreads = getInt(config, context, SubsystemAttributes.NUM_DMR_SCHEDULER_THREADS);
        int metricDispatcherBufferSize = getInt(config, context, SubsystemAttributes.METRIC_DISPATCHER_BUFFER_SIZE);
        int metricDispatcherMaxBatchSize = getInt(config, context,
                SubsystemAttributes.METRIC_DISPATCHER_MAX_BATCH_SIZE);
        int availDispatcherBufferSize = getInt(config, context, SubsystemAttributes.AVAIL_DISPATCHER_BUFFER_SIZE);
        int availDispatcherMaxBatchSize = getInt(config, context, SubsystemAttributes.AVAIL_DISPATCHER_MAX_BATCH_SIZE);

        return new GlobalConfiguration(subsystemEnabled, apiJndi, autoDiscoveryScanPeriodSecs,
                numDmrSchedulerThreads,
                metricDispatcherBufferSize, metricDispatcherMaxBatchSize, availDispatcherBufferSize,
                availDispatcherMaxBatchSize);
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
                TypeSetBuilder<ResourceType<DMRNodeLocation>> typeSetBuilder =
                        TypeSet.<ResourceType<DMRNodeLocation>> builder()
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
                                String operationName = operationProperty.getName();

                                PathAddress pathAddress = getPath(operationValueNode, context,
                                        DMROperationAttributes.PATH);
                                Operation<DMRNodeLocation> op = new Operation<>(ID.NULL_ID, new Name(operationName),
                                        new DMRNodeLocation(pathAddress),
                                        getString(operationValueNode, context, DMROperationAttributes.OPERATION_NAME));
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

                                ResourceConfigurationPropertyType<DMRNodeLocation> configType =
                                new ResourceConfigurationPropertyType<>(
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
                TypeSetBuilder<ResourceType<JMXNodeLocation>> typeSetBuilder =
                        TypeSet.<ResourceType<JMXNodeLocation>> builder()
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
                            Builder<?, JMXNodeLocation> resourceTypeBuilder =
                                    ResourceType.<JMXNodeLocation> builder()
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
                                                    JMXOperationAttributes.OPERATION_NAME));
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
                                    AttributeLocation<JMXNodeLocation> attribLoc =
                                            new AttributeLocation<JMXNodeLocation>(new JMXNodeLocation(on), attr);
                                    ResourceConfigurationPropertyType<JMXNodeLocation> configType =
                                            new ResourceConfigurationPropertyType<>(
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
                    String host = getString(remoteDMRValueNode, context, RemoteDMRAttributes.HOST);
                    int port = getInt(remoteDMRValueNode, context, RemoteDMRAttributes.PORT);
                    String username = getString(remoteDMRValueNode, context, RemoteDMRAttributes.USERNAME);
                    String password = getString(remoteDMRValueNode, context, RemoteDMRAttributes.PASSWORD);
                    boolean useSsl = getBoolean(remoteDMRValueNode, context, RemoteDMRAttributes.USE_SSL);
                    String securityRealm = getString(remoteDMRValueNode, context, RemoteDMRAttributes.SECURITY_REALM);
                    List<Name> resourceTypeSets = getNameListFromString(remoteDMRValueNode, context,
                            RemoteDMRAttributes.RESOURCE_TYPE_SETS);
                    String tenantId = getString(remoteDMRValueNode, context, RemoteDMRAttributes.TENANT_ID);

                    if (useSsl && securityRealm == null) {
                        log.debugf("Using SSL with no security realm - will rely on the JVM truststore: " + name);
                    }

                    String protocol = useSsl ? "https-remoting" : "http-remoting";
                    ConnectionData connectionData = new ConnectionData(protocol, host, port, username, password);
                    EndpointConfiguration endpoint = new EndpointConfiguration(name, enabled, resourceTypeSets,
                            connectionData, securityRealm, setAvailOnShutdown, tenantId);

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

                EndpointConfiguration endpoint = new EndpointConfiguration(name, enabled, resourceTypeSets, null, null,
                        setAvailOnShutdown, tenantId);
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

                    ConnectionData connectionData = new ConnectionData(url, username, password);
                    EndpointConfiguration endpoint = new EndpointConfiguration(name, enabled, resourceTypeSets,
                            connectionData, securityRealm, setAvailOnShutdown, tenantId);

                    jmxConfigBuilder.endpoint(endpoint);
                }
            }

            // PROMETHEUS

            if (managedServersValueNode.hasDefined(RemotePrometheusDefinition.REMOTE_PROMETHEUS)) {
                List<Property> remotePromsList = managedServersValueNode
                        .get(RemotePrometheusDefinition.REMOTE_PROMETHEUS)
                        .asPropertyList();
                for (Property remotePromProperty : remotePromsList) {
                    String name = remotePromProperty.getName();
                    ModelNode remotePromValueNode = remotePromProperty.getValue();
                    boolean enabled = getBoolean(remotePromValueNode, context, RemotePrometheusAttributes.ENABLED);
                    String urlStr = getString(remotePromValueNode, context, RemotePrometheusAttributes.URL);
                    String username = getString(remotePromValueNode, context, RemotePrometheusAttributes.USERNAME);
                    String password = getString(remotePromValueNode, context, RemotePrometheusAttributes.PASSWORD);
                    String securityRealm = getString(remotePromValueNode, context,
                            RemotePrometheusAttributes.SECURITY_REALM);
                    List<Name> metricSets = getNameListFromString(remotePromValueNode, context,
                            RemotePrometheusAttributes.METRIC_SETS);
                    int interval = getInt(remotePromValueNode, context, RemotePrometheusAttributes.INTERVAL);
                    String timeUnitsStr = getString(remotePromValueNode, context,
                            RemotePrometheusAttributes.TIME_UNITS);
                    TimeUnit timeUnits = TimeUnit.valueOf(timeUnitsStr.toUpperCase());
                    String tenandId = getString(remotePromValueNode, context, RemotePrometheusAttributes.TENANT_ID);

                    // make sure the URL is at least syntactically valid
                    URI url;
                    try {
                        url = new URI(urlStr);
                    } catch (Exception e) {
                        throw new OperationFailedException("Invalid remote Prometheus URL: " + urlStr, e);
                    }

                    if (url.getScheme().equalsIgnoreCase("https") && securityRealm == null) {
                        log.debugf("Using SSL with no security realm - will rely on the JVM truststore: " + name);
                    }

                    ConnectionData connectionData = new ConnectionData(url, username, password);
                    DynamicEndpointConfiguration endpoint = new DynamicEndpointConfiguration(name, enabled,
                            metricSets, connectionData, securityRealm, interval, timeUnits, tenandId);

                    prometheusConfigBuilder.endpoint(endpoint);
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
            return PathAddress.parseCLIStyleAddress(path);
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
