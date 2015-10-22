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

import java.net.URL;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import javax.management.MalformedObjectNameException;
import javax.management.ObjectName;

import org.hawkular.agent.monitor.extension.MonitorServiceConfiguration.Diagnostics;
import org.hawkular.agent.monitor.extension.MonitorServiceConfiguration.DiagnosticsReportTo;
import org.hawkular.agent.monitor.extension.MonitorServiceConfiguration.GlobalConfiguration;
import org.hawkular.agent.monitor.extension.MonitorServiceConfiguration.StorageAdapter;
import org.hawkular.agent.monitor.extension.MonitorServiceConfiguration.StorageReportTo;
import org.hawkular.agent.monitor.inventory.ID;
import org.hawkular.agent.monitor.inventory.ManagedServer;
import org.hawkular.agent.monitor.inventory.Name;
import org.hawkular.agent.monitor.inventory.ResourceTypeManager;
import org.hawkular.agent.monitor.inventory.TypeSet;
import org.hawkular.agent.monitor.inventory.TypeSet.TypeSetBuilder;
import org.hawkular.agent.monitor.inventory.TypeSets;
import org.hawkular.agent.monitor.inventory.dmr.DMRAvailType;
import org.hawkular.agent.monitor.inventory.dmr.DMRMetricType;
import org.hawkular.agent.monitor.inventory.dmr.DMROperation;
import org.hawkular.agent.monitor.inventory.dmr.DMRResourceConfigurationPropertyType;
import org.hawkular.agent.monitor.inventory.dmr.DMRResourceType;
import org.hawkular.agent.monitor.inventory.dmr.LocalDMRManagedServer;
import org.hawkular.agent.monitor.inventory.dmr.RemoteDMRManagedServer;
import org.hawkular.agent.monitor.inventory.jmx.JMXAvailType;
import org.hawkular.agent.monitor.inventory.jmx.JMXMetricType;
import org.hawkular.agent.monitor.inventory.jmx.JMXOperation;
import org.hawkular.agent.monitor.inventory.jmx.JMXResourceConfigurationPropertyType;
import org.hawkular.agent.monitor.inventory.jmx.JMXResourceType;
import org.hawkular.agent.monitor.inventory.jmx.RemoteJMXManagedServer;
import org.hawkular.agent.monitor.inventory.platform.Constants;
import org.hawkular.agent.monitor.inventory.platform.PlatformAvailType;
import org.hawkular.agent.monitor.inventory.platform.PlatformMetricType;
import org.hawkular.agent.monitor.inventory.platform.PlatformResourceType;
import org.hawkular.agent.monitor.log.AgentLoggers;
import org.hawkular.agent.monitor.log.MsgLogger;
import org.hawkular.metrics.client.common.MetricType;
import org.jboss.as.controller.OperationContext;
import org.jboss.as.controller.OperationFailedException;
import org.jboss.as.controller.SimpleAttributeDefinition;
import org.jboss.as.controller.client.helpers.MeasurementUnit;
import org.jboss.dmr.ModelNode;
import org.jboss.dmr.Property;

/**
 * Builds a {@link MonitorServiceConfiguration} object from the service's model.
 */
public class MonitorServiceConfigurationBuilder {
    private static final MsgLogger log = AgentLoggers.getLogger(MonitorServiceConfigurationBuilder.class);

    private TypeSets<DMRResourceType, DMRMetricType, DMRAvailType> dmrTypeSets;
    private TypeSets<JMXResourceType, JMXMetricType, JMXAvailType> jmxTypeSets;
    private TypeSets<PlatformResourceType, PlatformMetricType, PlatformAvailType> platformTypeSets;

    private Diagnostics diagnostics;
    private StorageAdapter storageAdapter;

    private Map<Name, ManagedServer> managedServersMap;
    private GlobalConfiguration globalConfiguration;

    public MonitorServiceConfigurationBuilder(ModelNode config, OperationContext context)
            throws OperationFailedException {
        this.globalConfiguration = determineGlobalConfig(config, context);
        this.storageAdapter = determineStorageAdapterConfig(config, context);

        this.diagnostics = determineDiagnosticsConfig(config, context);

        this.platformTypeSets = determinePlatformConfig(config, context);

        Map<Name, TypeSet<DMRMetricType>> metricsDmr = determineMetricSetDmr(config, context);
        Map<Name, TypeSet<DMRAvailType>> availsDmr = determineAvailSetDmr(config, context);

        Map<Name, TypeSet<JMXMetricType>> metricsJmx = determineMetricSetJmx(config, context);
        Map<Name, TypeSet<JMXAvailType>> availsJmx = determineAvailSetJmx(config, context);

        // make sure to call this AFTER the metric sets and avail sets have been determined
        Map<Name, TypeSet<DMRResourceType>> resourceTypesDmr = determineResourceTypeSetDmr(config, context, metricsDmr,
                availsDmr);
        Map<Name, TypeSet<JMXResourceType>> resourceTypesJmx = determineResourceTypeSetJmx(config, context, metricsJmx,
                availsJmx);

        this.dmrTypeSets = new TypeSets<>(resourceTypesDmr, metricsDmr, availsDmr, true);
        this.jmxTypeSets = new TypeSets<>(resourceTypesJmx, metricsJmx, availsJmx, true);

        // make sure to call this AFTER the resource type sets have been determined
        this.managedServersMap = this.determineManagedServers(config, context);

    }

    public MonitorServiceConfiguration build() {

        return new MonitorServiceConfiguration(globalConfiguration,
                diagnostics, storageAdapter, dmrTypeSets, jmxTypeSets,
                platformTypeSets, managedServersMap);
    }

    private Map<Name, TypeSet<DMRMetricType>> determineMetricSetDmr(ModelNode config, OperationContext context)
            throws OperationFailedException {

        boolean enabled = false;
        Map<Name, TypeSet<DMRMetricType>> result = new HashMap<>();

        if (config.hasDefined(DMRMetricSetDefinition.METRIC_SET)) {
            List<Property> metricSetsList = config.get(DMRMetricSetDefinition.METRIC_SET).asPropertyList();
            for (Property metricSetProperty : metricSetsList) {
                String metricSetName = metricSetProperty.getName();
                if (metricSetName.indexOf(',') > -1) {
                    log.warnCommaInName(metricSetName);
                }
                ModelNode metricSetValueNode = metricSetProperty.getValue();

                TypeSetBuilder<DMRMetricType> typeSetBuilder = TypeSet.<DMRMetricType> builder() //
                        .name(new Name(metricSetName)) //
                        .enabled(getBoolean(metricSetValueNode, context, DMRMetricSetAttributes.ENABLED));

                if (metricSetValueNode.hasDefined(DMRMetricDefinition.METRIC)) {
                    List<Property> metricsList = metricSetValueNode.get(DMRMetricDefinition.METRIC).asPropertyList();
                    for (Property metricProperty : metricsList) {
                        String metricName = metricSetName + "~" + metricProperty.getName();
                        DMRMetricType metric = new DMRMetricType(ID.NULL_ID, new Name(metricName));
                        typeSetBuilder.type(metric);
                        ModelNode metricValueNode = metricProperty.getValue();
                        metric.setPath(getString(metricValueNode, context, DMRMetricAttributes.PATH));
                        metric.setAttribute(getString(metricValueNode, context, DMRMetricAttributes.ATTRIBUTE));
                        String metricTypeStr = getString(metricValueNode, context, DMRMetricAttributes.METRIC_TYPE);
                        if (metricTypeStr == null) {
                            metric.setMetricType(MetricType.GAUGE);
                        } else {
                            metric.setMetricType(MetricType.valueOf(metricTypeStr.toUpperCase(Locale.ENGLISH)));
                        }
                        String metricUnitsStr = getString(metricValueNode, context, DMRMetricAttributes.METRIC_UNITS);
                        if (metricUnitsStr == null) {
                            metric.setMetricUnits(MeasurementUnit.NONE);
                        } else {
                            metric.setMetricUnits(MeasurementUnit.valueOf(metricUnitsStr.toUpperCase(Locale.ENGLISH)));
                        }
                        metric.setInterval(getInt(metricValueNode, context, DMRMetricAttributes.INTERVAL));
                        String metricTimeUnitsStr = getString(metricValueNode, context,
                                DMRMetricAttributes.TIME_UNITS);
                        metric.setTimeUnits(TimeUnit.valueOf(metricTimeUnitsStr.toUpperCase()));
                    }
                }
                TypeSet<DMRMetricType> typeSet = typeSetBuilder.build();
                enabled = enabled || !typeSet.isDisabledOrEmpty();
                result.put(typeSet.getName(), typeSet);
            }
        }
        if (!enabled) {
            log.infoNoEnabledMetricsConfigured("DMR");
        }

        return result;
    }

    private Map<Name, TypeSet<DMRAvailType>> determineAvailSetDmr(ModelNode config, OperationContext context)
            throws OperationFailedException {
        boolean enabled = false;

        Map<Name, TypeSet<DMRAvailType>> result = new LinkedHashMap<>();
        if (config.hasDefined(DMRAvailSetDefinition.AVAIL_SET)) {
            List<Property> availSetsList = config.get(DMRAvailSetDefinition.AVAIL_SET).asPropertyList();
            for (Property availSetProperty : availSetsList) {
                String availSetName = availSetProperty.getName();
                if (availSetName.indexOf(',') > -1) {
                    log.warnCommaInName(availSetName);
                }
                ModelNode availSetValueNode = availSetProperty.getValue();
                TypeSetBuilder<DMRAvailType> typeSetBuilder = TypeSet.<DMRAvailType> builder() //
                        .name(new Name(availSetName)) //
                        .enabled(getBoolean(availSetValueNode, context, DMRAvailSetAttributes.ENABLED));

                if (availSetValueNode.hasDefined(DMRAvailDefinition.AVAIL)) {
                    List<Property> availsList = availSetValueNode.get(DMRAvailDefinition.AVAIL).asPropertyList();
                    for (Property availProperty : availsList) {
                        String availName = availSetName + "~" + availProperty.getName();
                        DMRAvailType avail = new DMRAvailType(ID.NULL_ID, new Name(availName));
                        typeSetBuilder.type(avail);
                        ModelNode availValueNode = availProperty.getValue();
                        avail.setPath(getString(availValueNode, context, DMRAvailAttributes.PATH));
                        avail.setAttribute(getString(availValueNode, context, DMRAvailAttributes.ATTRIBUTE));
                        avail.setInterval(getInt(availValueNode, context, DMRAvailAttributes.INTERVAL));
                        String availTimeUnitsStr = getString(availValueNode, context, DMRAvailAttributes.TIME_UNITS);
                        avail.setTimeUnits(TimeUnit.valueOf(availTimeUnitsStr.toUpperCase()));
                        avail.setUpRegex(getString(availValueNode, context, DMRAvailAttributes.UP_REGEX));
                    }
                    TypeSet<DMRAvailType> typeSet = typeSetBuilder.build();
                    enabled = enabled || !typeSet.isDisabledOrEmpty();
                    result.put(typeSet.getName(), typeSet);
                }
            }
        }
        if (!enabled) {
            log.infoNoEnabledAvailsConfigured("DMR");
        }

        return result;
    }

    private Map<Name, TypeSet<JMXMetricType>> determineMetricSetJmx(ModelNode config, OperationContext context)
            throws OperationFailedException {

        boolean enabled = false;

        Map<Name, TypeSet<JMXMetricType>> result = new LinkedHashMap<>();
        if (config.hasDefined(JMXMetricSetDefinition.METRIC_SET)) {
            List<Property> metricSetsList = config.get(JMXMetricSetDefinition.METRIC_SET).asPropertyList();
            for (Property metricSetProperty : metricSetsList) {
                String metricSetName = metricSetProperty.getName();
                if (metricSetName.indexOf(',') > -1) {
                    log.warnCommaInName(metricSetName);
                }
                ModelNode metricSetValueNode = metricSetProperty.getValue();
                TypeSetBuilder<JMXMetricType> typeSetBuilder = TypeSet.<JMXMetricType> builder() //
                        .name(new Name(metricSetName)) //
                        .enabled(getBoolean(metricSetValueNode, context, JMXMetricSetAttributes.ENABLED));
                if (metricSetValueNode.hasDefined(JMXMetricDefinition.METRIC)) {
                    List<Property> metricsList = metricSetValueNode.get(JMXMetricDefinition.METRIC).asPropertyList();
                    for (Property metricProperty : metricsList) {
                        String metricName = metricSetName + "~" + metricProperty.getName();
                        JMXMetricType metric = new JMXMetricType(ID.NULL_ID, new Name(metricName));
                        typeSetBuilder.type(metric);
                        ModelNode metricValueNode = metricProperty.getValue();
                        metric.setObjectName(getObjectName(metricValueNode, context, JMXMetricAttributes.OBJECT_NAME));
                        metric.setAttribute(getString(metricValueNode, context, JMXMetricAttributes.ATTRIBUTE));
                        String metricTypeStr = getString(metricValueNode, context, JMXMetricAttributes.METRIC_TYPE);
                        if (metricTypeStr == null) {
                            metric.setMetricType(MetricType.GAUGE);
                        } else {
                            metric.setMetricType(MetricType.valueOf(metricTypeStr.toUpperCase(Locale.ENGLISH)));
                        }
                        String metricUnitsStr = getString(metricValueNode, context, JMXMetricAttributes.METRIC_UNITS);
                        if (metricUnitsStr == null) {
                            metric.setMetricUnits(MeasurementUnit.NONE);
                        } else {
                            metric.setMetricUnits(MeasurementUnit.valueOf(metricUnitsStr.toUpperCase(Locale.ENGLISH)));
                        }
                        metric.setInterval(getInt(metricValueNode, context, JMXMetricAttributes.INTERVAL));
                        String metricTimeUnitsStr = getString(metricValueNode, context,
                                JMXMetricAttributes.TIME_UNITS);
                        metric.setTimeUnits(TimeUnit.valueOf(metricTimeUnitsStr.toUpperCase()));
                    }
                    TypeSet<JMXMetricType> typeSet = typeSetBuilder.build();
                    enabled = enabled || !typeSet.isDisabledOrEmpty();
                    result.put(typeSet.getName(), typeSet);
                }
            }
        }
        if (!enabled) {
            log.infoNoEnabledMetricsConfigured("JMX");
        }

        return result;
    }

    private Map<Name, TypeSet<JMXAvailType>> determineAvailSetJmx(ModelNode config, OperationContext context)
            throws OperationFailedException {
        boolean enabled = false;

        Map<Name, TypeSet<JMXAvailType>> result = new LinkedHashMap<>();
        if (config.hasDefined(JMXAvailSetDefinition.AVAIL_SET)) {
            List<Property> availSetsList = config.get(JMXAvailSetDefinition.AVAIL_SET).asPropertyList();
            for (Property availSetProperty : availSetsList) {
                String availSetName = availSetProperty.getName();
                if (availSetName.indexOf(',') > -1) {
                    log.warnCommaInName(availSetName);
                }
                ModelNode availSetValueNode = availSetProperty.getValue();
                TypeSetBuilder<JMXAvailType> typeSetBuilder = TypeSet.<JMXAvailType> builder() //
                        .name(new Name(availSetName)) //
                        .enabled(getBoolean(availSetValueNode, context, JMXAvailSetAttributes.ENABLED));
                if (availSetValueNode.hasDefined(JMXAvailDefinition.AVAIL)) {
                    List<Property> availsList = availSetValueNode.get(JMXAvailDefinition.AVAIL).asPropertyList();
                    for (Property availProperty : availsList) {
                        String availName = availSetName + "~" + availProperty.getName();
                        JMXAvailType avail = new JMXAvailType(ID.NULL_ID, new Name(availName));
                        typeSetBuilder.type(avail);
                        ModelNode availValueNode = availProperty.getValue();
                        avail.setObjectName(getObjectName(availValueNode, context, JMXAvailAttributes.OBJECT_NAME));
                        avail.setAttribute(getString(availValueNode, context, JMXAvailAttributes.ATTRIBUTE));
                        avail.setInterval(getInt(availValueNode, context, JMXAvailAttributes.INTERVAL));
                        String availTimeUnitsStr = getString(availValueNode, context, JMXAvailAttributes.TIME_UNITS);
                        avail.setTimeUnits(TimeUnit.valueOf(availTimeUnitsStr.toUpperCase()));
                        avail.setUpRegex(getString(availValueNode, context, JMXAvailAttributes.UP_REGEX));
                    }
                    TypeSet<JMXAvailType> typeSet = typeSetBuilder.build();
                    enabled = enabled || !typeSet.isDisabledOrEmpty();
                    result.put(typeSet.getName(), typeSet);
                }
            }
        }

        if (!enabled) {
            log.infoNoEnabledAvailsConfigured("JMX");
        }
        return result;
    }

    private TypeSets<PlatformResourceType, PlatformMetricType, PlatformAvailType> determinePlatformConfig(
            ModelNode config, OperationContext context)
                    throws OperationFailedException {

        // assume they are disabled unless configured otherwise
        Map<Name, TypeSet<PlatformResourceType>> resourceTypeSetMap = new HashMap<>();
        Map<Name, TypeSet<PlatformMetricType>> metricTypeSetMap = new HashMap<>();
        Map<Name, TypeSet<PlatformAvailType>> availTypeSetMap = new HashMap<>();

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
        if (typeSetsEnabled == false) {
            log.debugf("Platform monitoring is disabled");
            return TypeSets.empty();
        }

        // all the type metadata is dependent upon the capabilities of the oshi SystemInfo API

        // since platform monitoring is enabled, we will always have at least the root OS type
        final Name osName = Constants.PlatformResourceType.OPERATING_SYSTEM.getName();

        PlatformResourceType rootType = new PlatformResourceType(null, osName);
        rootType.setResourceNameTemplate("%s");

        // the root metric set will be empty to start - we'll add some below as they are enabled
        TypeSetBuilder<PlatformMetricType> rootMetricsBuilder = TypeSet.<PlatformMetricType> builder().name(osName);
        rootType.setMetricSets(Collections.singletonList(osName));

        TypeSet<PlatformResourceType> rootTypeSet = TypeSet.<PlatformResourceType> builder()
                .enabled(true)
                .name(osName)
                .type(rootType)
                .build();

        resourceTypeSetMap.put(rootTypeSet.getName(), rootTypeSet);

        // now add children types if they are enabled

        if (platformValueNode.hasDefined(FileStoresDefinition.FILE_STORES)) {
            asPropertyList = platformValueNode.get(FileStoresDefinition.FILE_STORES).asPropertyList();
            if (asPropertyList.size() == 1) {
                ModelNode fileStoresNode = asPropertyList.get(0).getValue();
                boolean enabled = getBoolean(fileStoresNode, context, FileStoresAttributes.ENABLED);
                if (enabled) {
                    int interval = getInt(fileStoresNode, context, FileStoresAttributes.INTERVAL);
                    TimeUnit timeUnit = TimeUnit.valueOf(getString(fileStoresNode, context,
                            FileStoresAttributes.TIME_UNITS).toUpperCase());

                    PlatformMetricType usableSpace = new PlatformMetricType(null, Constants.FILE_STORE_USABLE_SPACE);
                    usableSpace.setInterval(interval);
                    usableSpace.setTimeUnits(timeUnit);
                    usableSpace.setMetricUnits(MeasurementUnit.BYTES);
                    usableSpace.setMetricType(MetricType.GAUGE);

                    PlatformMetricType totalSpace = new PlatformMetricType(null, Constants.FILE_STORE_TOTAL_SPACE);
                    totalSpace.setInterval(interval);
                    totalSpace.setTimeUnits(timeUnit);
                    totalSpace.setMetricUnits(MeasurementUnit.BYTES);
                    totalSpace.setMetricType(MetricType.GAUGE);

                    TypeSet<PlatformMetricType> fileStoreMetrics = TypeSet.<PlatformMetricType> builder() //
                            .name(Constants.PlatformResourceType.FILE_STORE.getName()) //
                            .type(usableSpace) //
                            .type(totalSpace) //
                            .build();

                    metricTypeSetMap.put(fileStoreMetrics.getName(), fileStoreMetrics);

                    PlatformResourceType fileStore = new PlatformResourceType(null,
                            Constants.PlatformResourceType.FILE_STORE.getName());
                    fileStore.setParents(Collections.singletonList(rootType.getName()));
                    fileStore.setMetricSets(Collections.singletonList(fileStoreMetrics.getName()));
                    fileStore.setResourceNameTemplate(
                            Constants.PlatformResourceType.FILE_STORE.getName().getNameString() + " [%s]");

                    TypeSet<PlatformResourceType> typeSet = TypeSet.<PlatformResourceType> builder() //
                            .name(Constants.PlatformResourceType.FILE_STORE.getName())
                            .type(fileStore)
                            .build();

                    resourceTypeSetMap.put(typeSet.getName(), typeSet);
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
                    int interval = getInt(memoryNode, context, MemoryAttributes.INTERVAL);
                    TimeUnit timeUnit = TimeUnit.valueOf(getString(memoryNode, context,
                            MemoryAttributes.TIME_UNITS).toUpperCase());

                    PlatformMetricType available = new PlatformMetricType(null, Constants.MEMORY_AVAILABLE);
                    available.setInterval(interval);
                    available.setTimeUnits(timeUnit);
                    available.setMetricUnits(MeasurementUnit.BYTES);
                    available.setMetricType(MetricType.GAUGE);

                    PlatformMetricType total = new PlatformMetricType(null, Constants.MEMORY_TOTAL);
                    total.setInterval(interval);
                    total.setTimeUnits(timeUnit);
                    total.setMetricUnits(MeasurementUnit.BYTES);
                    total.setMetricType(MetricType.GAUGE);

                    TypeSet<PlatformMetricType> memoryMetrics = TypeSet.<PlatformMetricType> builder() //
                            .name(Constants.PlatformResourceType.MEMORY.getName()) //
                            .type(available)
                            .type(total)
                            .build();

                    metricTypeSetMap.put(memoryMetrics.getName(), memoryMetrics);

                    PlatformResourceType memory = new PlatformResourceType(null,
                            Constants.PlatformResourceType.MEMORY.getName());
                    memory.setParents(Collections.singletonList(rootType.getName()));
                    memory.setMetricSets(Collections.singletonList(memoryMetrics.getName()));
                    memory.setResourceNameTemplate(
                            Constants.PlatformResourceType.MEMORY.getName().getNameString());

                    TypeSet<PlatformResourceType> typeSet = TypeSet.<PlatformResourceType> builder() //
                            .name(Constants.PlatformResourceType.MEMORY.getName()) //
                            .type(memory) //
                            .build();

                    resourceTypeSetMap.put(typeSet.getName(), typeSet);
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
                    int interval = getInt(processorsNode, context, ProcessorsAttributes.INTERVAL);
                    TimeUnit timeUnit = TimeUnit.valueOf(getString(processorsNode, context,
                            ProcessorsAttributes.TIME_UNITS).toUpperCase());

                    // this is the Processor.getProcessorCpuLoadBetweenTicks value
                    PlatformMetricType cpuUsage = new PlatformMetricType(null, Constants.PROCESSOR_CPU_USAGE);
                    cpuUsage.setInterval(interval);
                    cpuUsage.setTimeUnits(timeUnit);
                    cpuUsage.setMetricUnits(MeasurementUnit.PERCENTAGE);
                    cpuUsage.setMetricType(MetricType.GAUGE);

                    TypeSet<PlatformMetricType> processorMetrics = TypeSet.<PlatformMetricType> builder()
                            .name(Constants.PlatformResourceType.PROCESSOR.getName()) //
                            .type(cpuUsage)
                            .build();

                    metricTypeSetMap.put(processorMetrics.getName(), processorMetrics);

                    PlatformResourceType processor = new PlatformResourceType(null,
                            Constants.PlatformResourceType.PROCESSOR.getName());
                    processor.setParents(Collections.singletonList(rootType.getName()));
                    processor.setMetricSets(Collections.singletonList(processorMetrics.getName()));
                    processor.setResourceNameTemplate(
                            Constants.PlatformResourceType.PROCESSOR.getName().getNameString() + " [%s]");

                    TypeSet<PlatformResourceType> typeSet = TypeSet.<PlatformResourceType> builder() //
                            .name(Constants.PlatformResourceType.PROCESSOR.getName()) //
                            .type(processor) //
                            .build();

                    resourceTypeSetMap.put(typeSet.getName(), typeSet);

                    // We want to also collect system CPU load and system load average. Because these
                    // are processor-related metrics, we only want to collect them if "processors" is enabled
                    // in our configuration; however, because they are aggregates across ALL CPUs (and not
                    // just individual metrics per CPU) they aren't really metrics associated with any
                    // one particular CPU. Therefore, we want to attach these metrics to the parent
                    // "operating system" root resource.
                    PlatformMetricType cpuLoad = new PlatformMetricType(null, Constants.OPERATING_SYSTEM_SYS_CPU_LOAD);
                    cpuLoad.setInterval(interval);
                    cpuLoad.setTimeUnits(timeUnit);
                    cpuLoad.setMetricUnits(MeasurementUnit.PERCENTAGE);
                    cpuLoad.setMetricType(MetricType.GAUGE);
                    rootMetricsBuilder.type(cpuLoad);

                    PlatformMetricType sysLoad = new PlatformMetricType(null, Constants.OPERATING_SYSTEM_SYS_LOAD_AVG);
                    sysLoad.setInterval(interval);
                    sysLoad.setTimeUnits(timeUnit);
                    sysLoad.setMetricUnits(MeasurementUnit.NONE);
                    sysLoad.setMetricType(MetricType.GAUGE);
                    rootMetricsBuilder.type(sysLoad);
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
                    int interval = getInt(powerSourcesNode, context, PowerSourcesAttributes.INTERVAL);
                    TimeUnit timeUnit = TimeUnit.valueOf(getString(powerSourcesNode, context,
                            PowerSourcesAttributes.TIME_UNITS).toUpperCase());

                    PlatformMetricType remainingCap = new PlatformMetricType(null,
                            Constants.POWER_SOURCE_REMAINING_CAPACITY);
                    remainingCap.setInterval(interval);
                    remainingCap.setTimeUnits(timeUnit);
                    remainingCap.setMetricUnits(MeasurementUnit.PERCENTAGE);
                    remainingCap.setMetricType(MetricType.GAUGE);

                    PlatformMetricType timeRemaining = new PlatformMetricType(null,
                            Constants.POWER_SOURCE_TIME_REMAINING);
                    timeRemaining.setInterval(interval);
                    timeRemaining.setTimeUnits(timeUnit);
                    timeRemaining.setMetricUnits(MeasurementUnit.SECONDS);
                    timeRemaining.setMetricType(MetricType.GAUGE);

                    TypeSet<PlatformMetricType> powerSourceMetrics = TypeSet.<PlatformMetricType> builder() //
                            .name(Constants.PlatformResourceType.POWER_SOURCE.getName()) //
                            .type(remainingCap)
                            .type(timeRemaining)
                            .build();

                    metricTypeSetMap.put(powerSourceMetrics.getName(), powerSourceMetrics);

                    PlatformResourceType powerSource = new PlatformResourceType(null,
                            Constants.PlatformResourceType.POWER_SOURCE.getName());
                    powerSource.setParents(Collections.singletonList(rootType.getName()));
                    powerSource.setMetricSets(Collections.singletonList(powerSourceMetrics.getName()));
                    powerSource.setResourceNameTemplate(
                            Constants.PlatformResourceType.POWER_SOURCE.getName().getNameString() + " [%s]");

                    TypeSet<PlatformResourceType> typeSet = TypeSet.<PlatformResourceType> builder() //
                            .name(Constants.PlatformResourceType.POWER_SOURCE.getName())
                            .type(powerSource)
                            .build();

                    resourceTypeSetMap.put(typeSet.getName(), typeSet);
                }
            } else if (asPropertyList.size() > 1) {
                throw new IllegalArgumentException("Only one platform.power-sources config allowed: "
                        + platformValueNode.toJSONString(true));
            }
        }

        // our root metrics should be ready to be built now
        TypeSet<PlatformMetricType> rootMetrics = rootMetricsBuilder.build();
        metricTypeSetMap.put(rootMetrics.getName(), rootMetrics);

        TypeSets<PlatformResourceType, PlatformMetricType, PlatformAvailType> result = new TypeSets<>(
                Collections.unmodifiableMap(resourceTypeSetMap),
                Collections.unmodifiableMap(metricTypeSetMap),
                Collections.unmodifiableMap(availTypeSetMap),
                typeSetsEnabled);

        return result;

    }

    private Diagnostics determineDiagnosticsConfig(ModelNode config, OperationContext context)
            throws OperationFailedException {
        if (!config.hasDefined(DiagnosticsDefinition.DIAGNOSTICS)) {
            log.infoNoDiagnosticsConfig();
            return Diagnostics.EMPTY;
        }

        List<Property> asPropertyList = config.get(DiagnosticsDefinition.DIAGNOSTICS).asPropertyList();
        if (asPropertyList.size() == 0) {
            log.infoNoDiagnosticsConfig();
            return Diagnostics.EMPTY;
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
        return new Diagnostics(enabled, reportTo, interval, timeUnits);
    }

    private StorageAdapter determineStorageAdapterConfig(ModelNode config, OperationContext context)
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
        String securityRealm = getString(storageAdapterConfig, context,
                StorageAttributes.SECURITY_REALM);
        String keystorePath = getString(storageAdapterConfig, context,
                StorageAttributes.KEYSTORE_PATH);
        String keystorePassword = getString(storageAdapterConfig, context,
                StorageAttributes.KEYSTORE_PASSWORD);
        String serverOutboundSocketBindingRef = getString(storageAdapterConfig, context,
                StorageAttributes.SERVER_OUTBOUND_SOCKET_BINDING_REF);
        String tenantId = getString(storageAdapterConfig, context, StorageAttributes.TENANT_ID);
        String accountsContext = getString(storageAdapterConfig, context,
                StorageAttributes.ACCOUNTS_CONTEXT);
        String inventoryContext = getString(storageAdapterConfig, context,
                StorageAttributes.INVENTORY_CONTEXT);
        String metricsContext = getString(storageAdapterConfig, context,
                StorageAttributes.METRICS_CONTEXT);
        String feedcommContext = getString(storageAdapterConfig, context,
                StorageAttributes.FEEDCOMM_CONTEXT);
        String username = getString(storageAdapterConfig, context, StorageAttributes.USERNAME);
        String password = getString(storageAdapterConfig, context, StorageAttributes.PASSWORD);
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
        return new StorageAdapter(type, username, password, tenantId, url, useSSL,
                serverOutboundSocketBindingRef,
                accountsContext, inventoryContext, metricsContext, feedcommContext, keystorePath, keystorePassword,
                securityRealm);
    }

    private GlobalConfiguration determineGlobalConfig(ModelNode config, OperationContext context)
            throws OperationFailedException {
        boolean subsystemEnabled = getBoolean(config, context, SubsystemAttributes.ENABLED);
        String apiJndi = getString(config, context, SubsystemAttributes.API_JNDI);
        int numMetricSchedulerThreads = getInt(config, context,
                SubsystemAttributes.NUM_METRIC_SCHEDULER_THREADS);
        int numAvailSchedulerThreads = getInt(config, context,
                SubsystemAttributes.NUM_AVAIL_SCHEDULER_THREADS);
        int numDmrSchedulerThreads = getInt(config, context,
                SubsystemAttributes.NUM_DMR_SCHEDULER_THREADS);
        int metricDispatcherBufferSize = getInt(config, context,
                SubsystemAttributes.METRIC_DISPATCHER_BUFFER_SIZE);
        int metricDispatcherMaxBatchSize = getInt(config, context,
                SubsystemAttributes.METRIC_DISPATCHER_MAX_BATCH_SIZE);
        int availDispatcherBufferSize = getInt(config, context,
                SubsystemAttributes.AVAIL_DISPATCHER_BUFFER_SIZE);
        int availDispatcherMaxBatchSize = getInt(config, context,
                SubsystemAttributes.AVAIL_DISPATCHER_MAX_BATCH_SIZE);

        return new GlobalConfiguration(subsystemEnabled, apiJndi, numMetricSchedulerThreads, numAvailSchedulerThreads,
                numDmrSchedulerThreads, metricDispatcherBufferSize, metricDispatcherMaxBatchSize,
                availDispatcherBufferSize, availDispatcherMaxBatchSize);
    }

    private Map<Name, TypeSet<DMRResourceType>> determineResourceTypeSetDmr(ModelNode config, OperationContext context,
            Map<Name, TypeSet<DMRMetricType>> metrics, Map<Name, TypeSet<DMRAvailType>> avails)
                    throws OperationFailedException {
        boolean enabled = false;

        Map<Name, TypeSet<DMRResourceType>> result = new LinkedHashMap<>();
        if (config.hasDefined(DMRResourceTypeSetDefinition.RESOURCE_TYPE_SET)) {
            List<Property> resourceTypeSetsList = config.get(DMRResourceTypeSetDefinition.RESOURCE_TYPE_SET)
                    .asPropertyList();
            for (Property resourceTypeSetProperty : resourceTypeSetsList) {
                String resourceTypeSetName = resourceTypeSetProperty.getName();
                ModelNode resourceTypeSetValueNode = resourceTypeSetProperty.getValue();
                TypeSetBuilder<DMRResourceType> typeSetBuilder = TypeSet.<DMRResourceType> builder() //
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
                        DMRResourceType resourceType = new DMRResourceType(ID.NULL_ID, new Name(resourceTypeName));
                        typeSetBuilder.type(resourceType);
                        resourceType.setResourceNameTemplate(getString(resourceTypeValueNode, context,
                                DMRResourceTypeAttributes.RESOURCE_NAME_TEMPLATE));
                        resourceType.setPath(getString(resourceTypeValueNode, context,
                                DMRResourceTypeAttributes.PATH));
                        resourceType.setParents(getNameListFromString(resourceTypeValueNode, context,
                                DMRResourceTypeAttributes.PARENTS));

                        List<Name> metricSets = getNameListFromString(resourceTypeValueNode, context,
                                DMRResourceTypeAttributes.METRIC_SETS);
                        List<Name> availSets = getNameListFromString(resourceTypeValueNode, context,
                                DMRResourceTypeAttributes.AVAIL_SETS);

                        // verify that the metric sets and avail sets exist
                        for (Name metricSetName : metricSets) {
                            if (!metrics.containsKey(metricSetName)) {
                                log.warnMetricSetDoesNotExist(resourceTypeName.toString(),
                                        metricSetName.toString());
                            }
                        }
                        for (Name availSetName : availSets) {
                            if (!avails.containsKey(availSetName)) {
                                log.warnAvailSetDoesNotExist(resourceTypeName.toString(),
                                        availSetName.toString());
                            }
                        }

                        resourceType.setMetricSets(metricSets);
                        resourceType.setAvailSets(availSets);

                        // get operations
                        ModelNode opModelNode = resourceTypeValueNode.get(DMROperationDefinition.OPERATION);
                        if (opModelNode != null && opModelNode.isDefined()) {
                            List<Property> operationList = opModelNode.asPropertyList();
                            for (Property operationProperty : operationList) {
                                ModelNode operationValueNode = operationProperty.getValue();
                                String operationName = operationProperty.getName();
                                DMROperation op = new DMROperation(ID.NULL_ID, new Name(operationName));
                                op.setPath(getString(operationValueNode, context, DMROperationAttributes.PATH));
                                op.setOperationName(getString(operationValueNode, context,
                                        DMROperationAttributes.OPERATION_NAME));
                                resourceType.addOperation(op);
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
                                DMRResourceConfigurationPropertyType configType = //
                                new DMRResourceConfigurationPropertyType(
                                        ID.NULL_ID, new Name(configName));
                                configType.setPath(getString(configValueNode, context,
                                        DMRResourceConfigAttributes.PATH));
                                configType.setAttribute(getString(configValueNode, context,
                                        DMRResourceConfigAttributes.ATTRIBUTE));
                                resourceType.addResourceConfigurationPropertyType(configType);
                            }
                        }
                    }
                }

                TypeSet<DMRResourceType> typeSet = typeSetBuilder.build();
                enabled = enabled || !typeSet.isDisabledOrEmpty();
                result.put(typeSet.getName(), typeSet);

            }

            // build a graph of the full type hierarchy just to test to make sure it all is valid
            try {
                ResourceTypeManager<DMRResourceType> rtm;
                rtm = new ResourceTypeManager<>(result);
                if (!rtm.getAllResourceTypes().isEmpty()) {
                    enabled = true;
                }
            } catch (Exception e) {
                throw new OperationFailedException(e);
            }

        }

        if (!enabled) {
            log.infoNoEnabledResourceTypesConfigured("DMR");
        }

        return result;
    }

    private Map<Name, TypeSet<JMXResourceType>> determineResourceTypeSetJmx(ModelNode config, OperationContext context,
            Map<Name, TypeSet<JMXMetricType>> metrics, Map<Name, TypeSet<JMXAvailType>> avails)
                    throws OperationFailedException {
        boolean enabled = false;
        Map<Name, TypeSet<JMXResourceType>> result = new LinkedHashMap<>();

        if (config.hasDefined(JMXResourceTypeSetDefinition.RESOURCE_TYPE_SET)) {
            List<Property> resourceTypeSetsList = config.get(JMXResourceTypeSetDefinition.RESOURCE_TYPE_SET)
                    .asPropertyList();
            for (Property resourceTypeSetProperty : resourceTypeSetsList) {
                String resourceTypeSetName = resourceTypeSetProperty.getName();
                ModelNode resourceTypeSetValueNode = resourceTypeSetProperty.getValue();
                TypeSetBuilder<JMXResourceType> typeSetBuilder = TypeSet.<JMXResourceType> builder() //
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
                        JMXResourceType resourceType = new JMXResourceType(ID.NULL_ID, new Name(resourceTypeName));
                        typeSetBuilder.type(resourceType);
                        resourceType.setResourceNameTemplate(getString(resourceTypeValueNode, context,
                                JMXResourceTypeAttributes.RESOURCE_NAME_TEMPLATE));
                        resourceType.setObjectName(getObjectName(resourceTypeValueNode, context,
                                JMXResourceTypeAttributes.OBJECT_NAME));
                        resourceType.setParents(getNameListFromString(resourceTypeValueNode, context,
                                JMXResourceTypeAttributes.PARENTS));

                        List<Name> metricSets = getNameListFromString(resourceTypeValueNode, context,
                                JMXResourceTypeAttributes.METRIC_SETS);
                        List<Name> availSets = getNameListFromString(resourceTypeValueNode, context,
                                JMXResourceTypeAttributes.AVAIL_SETS);

                        // verify that the metric sets and avail sets exist
                        for (Name metricSetName : metricSets) {
                            if (!metrics.containsKey(metricSetName)) {
                                log.warnMetricSetDoesNotExist(resourceTypeName.toString(),
                                        metricSetName.toString());
                            }
                        }
                        for (Name availSetName : availSets) {
                            if (!avails.containsKey(availSetName)) {
                                log.warnAvailSetDoesNotExist(resourceTypeName.toString(),
                                        availSetName.toString());
                            }
                        }

                        resourceType.setMetricSets(metricSets);
                        resourceType.setAvailSets(availSets);

                        // get operations
                        ModelNode opModelNode = resourceTypeValueNode.get(JMXOperationDefinition.OPERATION);
                        if (opModelNode != null && opModelNode.isDefined()) {
                            List<Property> operationList = opModelNode.asPropertyList();
                            for (Property operationProperty : operationList) {
                                ModelNode operationValueNode = operationProperty.getValue();
                                String operationName = operationProperty.getName();
                                JMXOperation op = new JMXOperation(ID.NULL_ID, new Name(operationName));
                                op.setObjectName(getObjectName(operationValueNode, context,
                                        JMXOperationAttributes.OBJECT_NAME));
                                op.setOperationName(getString(operationValueNode, context,
                                        JMXOperationAttributes.OPERATION_NAME));
                                resourceType.addOperation(op);
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
                                JMXResourceConfigurationPropertyType configType = //
                                new JMXResourceConfigurationPropertyType(
                                        ID.NULL_ID, new Name(configName));
                                configType.setObjectName(getObjectName(configValueNode, context,
                                        JMXResourceConfigAttributes.OBJECT_NAME));
                                configType.setAttribute(getString(configValueNode, context,
                                        JMXResourceConfigAttributes.ATTRIBUTE));
                                resourceType.addResourceConfigurationPropertyType(configType);
                            }
                        }
                    }
                }
                TypeSet<JMXResourceType> typeSet = typeSetBuilder.build();
                enabled = enabled || !typeSet.isDisabledOrEmpty();
                result.put(typeSet.getName(), typeSet);
            }

            // build a graph of the full type hierarchy just to test to make sure it all is valid
            try {
                ResourceTypeManager<JMXResourceType> rtm;
                rtm = new ResourceTypeManager<>(result);
                if (!rtm.getAllResourceTypes().isEmpty()) {
                    enabled = true;
                }
            } catch (Exception e) {
                throw new OperationFailedException(e);
            }
        }

        if (enabled) {
            log.infoNoEnabledResourceTypesConfigured("JMX");
        }

        return result;
    }

    private Map<Name, ManagedServer> determineManagedServers(ModelNode config, OperationContext context)
            throws OperationFailedException {
        Map<Name, ManagedServer> result = new LinkedHashMap<>();
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
                    String host = getString(remoteDMRValueNode, context, RemoteDMRAttributes.HOST);
                    int port = getInt(remoteDMRValueNode, context, RemoteDMRAttributes.PORT);
                    String username = getString(remoteDMRValueNode, context, RemoteDMRAttributes.USERNAME);
                    String password = getString(remoteDMRValueNode, context, RemoteDMRAttributes.PASSWORD);
                    boolean useSsl = getBoolean(remoteDMRValueNode, context, RemoteDMRAttributes.USE_SSL);
                    String securityRealm = getString(remoteDMRValueNode, context, RemoteDMRAttributes.SECURITY_REALM);
                    List<Name> resourceTypeSets = getNameListFromString(remoteDMRValueNode, context,
                            RemoteDMRAttributes.RESOURCE_TYPE_SETS);

                    // verify that the resource type sets exist
                    for (Name resourceTypeSetName : resourceTypeSets) {
                        if (!dmrTypeSets.getResourceTypeSets().containsKey(resourceTypeSetName)) {
                            log.warnResourceTypeSetDoesNotExist(name.toString(),
                                    resourceTypeSetName.toString());
                        }
                    }

                    if (useSsl && securityRealm == null) {
                        throw new OperationFailedException("If using SSL, you must define a security realm: " + name);
                    }

                    RemoteDMRManagedServer res = new RemoteDMRManagedServer(ID.NULL_ID, new Name(name));
                    res.setEnabled(enabled);
                    res.setHost(host);
                    res.setPort(port);
                    res.setUsername(username);
                    res.setPassword(password);
                    res.setUseSSL(useSsl);
                    res.setSecurityRealm(securityRealm);
                    res.getResourceTypeSets().addAll(resourceTypeSets);
                    result.put(res.getName(), res);
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
                List<Name> resourceTypeSets = getNameListFromString(localDMRValueNode, context,
                        LocalDMRAttributes.RESOURCE_TYPE_SETS);

                // verify that the metric sets and avail sets exist
                for (Name resourceTypeSetName : resourceTypeSets) {
                    if (!dmrTypeSets.getResourceTypeSets().containsKey(resourceTypeSetName)) {
                        log.warnResourceTypeSetDoesNotExist(name.toString(), resourceTypeSetName.toString());
                    }
                }

                LocalDMRManagedServer res = new LocalDMRManagedServer(ID.NULL_ID, new Name(name));
                res.setEnabled(enabled);
                res.getResourceTypeSets().addAll(resourceTypeSets);
                result.put(res.getName(), res);
            }

            // JMX

            if (managedServersValueNode.hasDefined(RemoteJMXDefinition.REMOTE_JMX)) {
                List<Property> remoteJMXsList = managedServersValueNode.get(RemoteJMXDefinition.REMOTE_JMX)
                        .asPropertyList();
                for (Property remoteJMXProperty : remoteJMXsList) {
                    String name = remoteJMXProperty.getName();
                    ModelNode remoteJMXValueNode = remoteJMXProperty.getValue();
                    boolean enabled = getBoolean(remoteJMXValueNode, context, RemoteJMXAttributes.ENABLED);
                    String urlStr = getString(remoteJMXValueNode, context, RemoteJMXAttributes.URL);
                    String username = getString(remoteJMXValueNode, context, RemoteJMXAttributes.USERNAME);
                    String password = getString(remoteJMXValueNode, context, RemoteJMXAttributes.PASSWORD);
                    String securityRealm = getString(remoteJMXValueNode, context, RemoteJMXAttributes.SECURITY_REALM);
                    List<Name> resourceTypeSets = getNameListFromString(remoteJMXValueNode, context,
                            RemoteJMXAttributes.RESOURCE_TYPE_SETS);

                    // verify that the resource type sets exist
                    for (Name resourceTypeSetName : resourceTypeSets) {
                        if (!jmxTypeSets.getResourceTypeSets().containsKey(resourceTypeSetName)) {
                            log.warnResourceTypeSetDoesNotExist(name.toString(),
                                    resourceTypeSetName.toString());
                        }
                    }

                    // make sure the URL is at least syntactically valid
                    URL url;
                    try {
                        url = new URL(urlStr);
                    } catch (Exception e) {
                        throw new OperationFailedException("Invalid remote JMX URL: " + urlStr, e);
                    }

                    if (url.getProtocol().equalsIgnoreCase("https") && securityRealm == null) {
                        throw new OperationFailedException("If using SSL, you must define a security realm: " + name);
                    }

                    RemoteJMXManagedServer res = new RemoteJMXManagedServer(ID.NULL_ID, new Name(name));
                    res.setEnabled(enabled);
                    res.setURL(url);
                    res.setUsername(username);
                    res.setPassword(password);
                    res.setSecurityRealm(securityRealm);
                    res.getResourceTypeSets().addAll(resourceTypeSets);
                    result.put(res.getName(), res);
                }
            }

        }
        return result;
    }

    private boolean getBoolean(ModelNode modelNode, OperationContext context, SimpleAttributeDefinition attrib)
            throws OperationFailedException {
        ModelNode value = attrib.resolveModelAttribute(context, modelNode);
        return (value.isDefined()) ? value.asBoolean() : false;
    }

    private String getString(ModelNode modelNode, OperationContext context, SimpleAttributeDefinition attrib)
            throws OperationFailedException {
        ModelNode value = attrib.resolveModelAttribute(context, modelNode);
        return (value.isDefined()) ? value.asString() : null;
    }

    private int getInt(ModelNode modelNode, OperationContext context, SimpleAttributeDefinition attrib)
            throws OperationFailedException {
        ModelNode value = attrib.resolveModelAttribute(context, modelNode);
        return (value.isDefined()) ? value.asInt() : 0;
    }

    private String getObjectName(ModelNode modelNode, OperationContext context, SimpleAttributeDefinition attrib)
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

    private List<Name> getNameListFromString(ModelNode modelNode, OperationContext context,
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

}
