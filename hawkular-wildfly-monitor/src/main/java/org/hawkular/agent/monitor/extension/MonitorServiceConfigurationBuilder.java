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
import java.util.List;
import java.util.Locale;
import java.util.concurrent.TimeUnit;

import javax.management.MalformedObjectNameException;
import javax.management.ObjectName;

import org.hawkular.agent.monitor.inventory.ID;
import org.hawkular.agent.monitor.inventory.Name;
import org.hawkular.agent.monitor.inventory.ResourceTypeManager;
import org.hawkular.agent.monitor.inventory.dmr.DMRAvailType;
import org.hawkular.agent.monitor.inventory.dmr.DMRAvailTypeSet;
import org.hawkular.agent.monitor.inventory.dmr.DMRMetricType;
import org.hawkular.agent.monitor.inventory.dmr.DMRMetricTypeSet;
import org.hawkular.agent.monitor.inventory.dmr.DMROperation;
import org.hawkular.agent.monitor.inventory.dmr.DMRResourceConfigurationPropertyType;
import org.hawkular.agent.monitor.inventory.dmr.DMRResourceType;
import org.hawkular.agent.monitor.inventory.dmr.DMRResourceTypeSet;
import org.hawkular.agent.monitor.inventory.dmr.LocalDMRManagedServer;
import org.hawkular.agent.monitor.inventory.dmr.RemoteDMRManagedServer;
import org.hawkular.agent.monitor.inventory.jmx.JMXAvailType;
import org.hawkular.agent.monitor.inventory.jmx.JMXAvailTypeSet;
import org.hawkular.agent.monitor.inventory.jmx.JMXMetricType;
import org.hawkular.agent.monitor.inventory.jmx.JMXMetricTypeSet;
import org.hawkular.agent.monitor.inventory.jmx.JMXOperation;
import org.hawkular.agent.monitor.inventory.jmx.JMXResourceConfigurationPropertyType;
import org.hawkular.agent.monitor.inventory.jmx.JMXResourceType;
import org.hawkular.agent.monitor.inventory.jmx.JMXResourceTypeSet;
import org.hawkular.agent.monitor.inventory.jmx.RemoteJMXManagedServer;
import org.hawkular.agent.monitor.inventory.platform.Constants;
import org.hawkular.agent.monitor.inventory.platform.PlatformMetricType;
import org.hawkular.agent.monitor.inventory.platform.PlatformMetricTypeSet;
import org.hawkular.agent.monitor.inventory.platform.PlatformResourceType;
import org.hawkular.agent.monitor.inventory.platform.PlatformResourceTypeSet;
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
    private final MonitorServiceConfiguration theConfig = new MonitorServiceConfiguration();

    public MonitorServiceConfiguration build() {
        return theConfig;
    }

    public MonitorServiceConfigurationBuilder(ModelNode config, OperationContext context)
            throws OperationFailedException {
        determineGlobalConfig(config, context);
        determineStorageAdapterConfig(config, context);
        determineDiagnosticsConfig(config, context);
        determinePlatformConfig(config, context);

        boolean hasEnabledMetricsDmr = determineMetricSetDmr(config, context);
        boolean hasEnabledAvailsDmr = determineAvailSetDmr(config, context);

        boolean hasEnabledMetricsJmx = determineMetricSetJmx(config, context);
        boolean hasEnabledAvailsJmx = determineAvailSetJmx(config, context);

        if (!hasEnabledMetricsDmr) {
            log.infoNoEnabledMetricsConfigured("DMR");
        }
        if (!hasEnabledAvailsDmr) {
            log.infoNoEnabledAvailsConfigured("DMR");
        }
        if (!hasEnabledMetricsJmx) {
            log.infoNoEnabledMetricsConfigured("JMX");
        }
        if (!hasEnabledAvailsJmx) {
            log.infoNoEnabledAvailsConfigured("JMX");
        }

        // make sure to call this AFTER the metric sets and avail sets have been determined
        boolean hasEnabledResourceTypesDmr = determineResourceTypeSetDmr(config, context);
        boolean hasEnabledResourceTypesJmx = determineResourceTypeSetJmx(config, context);

        if (!hasEnabledResourceTypesDmr) {
            log.infoNoEnabledResourceTypesConfigured("DMR");
        }
        if (!hasEnabledResourceTypesJmx) {
            log.infoNoEnabledResourceTypesConfigured("JMX");
        }

        // make sure to call this AFTER the resource type sets have been determined
        determineManagedServers(config, context);

        return;
    }

    private boolean determineMetricSetDmr(ModelNode config, OperationContext context)
            throws OperationFailedException {

        boolean hasEnabledMetrics = false;

        if (config.hasDefined(DMRMetricSetDefinition.METRIC_SET)) {
            List<Property> metricSetsList = config.get(DMRMetricSetDefinition.METRIC_SET).asPropertyList();
            for (Property metricSetProperty : metricSetsList) {
                String metricSetName = metricSetProperty.getName();
                if (metricSetName.indexOf(',') > -1) {
                    log.warnCommaInName(metricSetName);
                }
                DMRMetricTypeSet metricSet = new DMRMetricTypeSet(ID.NULL_ID, new Name(metricSetName));
                theConfig.dmrMetricTypeSetMap.put(metricSet.getName(), metricSet);
                ModelNode metricSetValueNode = metricSetProperty.getValue();
                metricSet.setEnabled(getBoolean(metricSetValueNode, context, DMRMetricSetAttributes.ENABLED));
                if (metricSetValueNode.hasDefined(DMRMetricDefinition.METRIC)) {
                    List<Property> metricsList = metricSetValueNode.get(DMRMetricDefinition.METRIC).asPropertyList();
                    for (Property metricProperty : metricsList) {
                        String metricName = metricSet.getName() + "~" + metricProperty.getName();
                        DMRMetricType metric = new DMRMetricType(ID.NULL_ID, new Name(metricName));
                        metricSet.getMetricTypeMap().put(metric.getName(), metric);
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
                    if (metricSet.isEnabled() && !metricSet.getMetricTypeMap().isEmpty()) {
                        hasEnabledMetrics = true;
                    }
                }
            }
        }

        return hasEnabledMetrics;
    }

    private boolean determineAvailSetDmr(ModelNode config, OperationContext context) throws OperationFailedException {
        boolean hasEnabledAvails = false;

        if (config.hasDefined(DMRAvailSetDefinition.AVAIL_SET)) {
            List<Property> availSetsList = config.get(DMRAvailSetDefinition.AVAIL_SET).asPropertyList();
            for (Property availSetProperty : availSetsList) {
                String availSetName = availSetProperty.getName();
                if (availSetName.indexOf(',') > -1) {
                    log.warnCommaInName(availSetName);
                }
                DMRAvailTypeSet availSet = new DMRAvailTypeSet(ID.NULL_ID, new Name(availSetName));
                theConfig.dmrAvailTypeSetMap.put(availSet.getName(), availSet);
                ModelNode availSetValueNode = availSetProperty.getValue();
                availSet.setEnabled(getBoolean(availSetValueNode, context, DMRAvailSetAttributes.ENABLED));
                if (availSetValueNode.hasDefined(DMRAvailDefinition.AVAIL)) {
                    List<Property> availsList = availSetValueNode.get(DMRAvailDefinition.AVAIL).asPropertyList();
                    for (Property availProperty : availsList) {
                        String availName = availSet.getName() + "~" + availProperty.getName();
                        DMRAvailType avail = new DMRAvailType(ID.NULL_ID, new Name(availName));
                        availSet.getAvailTypeMap().put(avail.getName(), avail);
                        ModelNode availValueNode = availProperty.getValue();
                        avail.setPath(getString(availValueNode, context, DMRAvailAttributes.PATH));
                        avail.setAttribute(getString(availValueNode, context, DMRAvailAttributes.ATTRIBUTE));
                        avail.setInterval(getInt(availValueNode, context, DMRAvailAttributes.INTERVAL));
                        String availTimeUnitsStr = getString(availValueNode, context, DMRAvailAttributes.TIME_UNITS);
                        avail.setTimeUnits(TimeUnit.valueOf(availTimeUnitsStr.toUpperCase()));
                        avail.setUpRegex(getString(availValueNode, context, DMRAvailAttributes.UP_REGEX));
                    }
                    if (availSet.isEnabled() && !availSet.getAvailTypeMap().isEmpty()) {
                        hasEnabledAvails = true;
                    }
                }
            }
        }

        return hasEnabledAvails;
    }

    private boolean determineMetricSetJmx(ModelNode config, OperationContext context)
            throws OperationFailedException {

        boolean hasEnabledMetrics = false;

        if (config.hasDefined(JMXMetricSetDefinition.METRIC_SET)) {
            List<Property> metricSetsList = config.get(JMXMetricSetDefinition.METRIC_SET).asPropertyList();
            for (Property metricSetProperty : metricSetsList) {
                String metricSetName = metricSetProperty.getName();
                if (metricSetName.indexOf(',') > -1) {
                    log.warnCommaInName(metricSetName);
                }
                JMXMetricTypeSet metricSet = new JMXMetricTypeSet(ID.NULL_ID, new Name(metricSetName));
                theConfig.jmxMetricTypeSetMap.put(metricSet.getName(), metricSet);
                ModelNode metricSetValueNode = metricSetProperty.getValue();
                metricSet.setEnabled(getBoolean(metricSetValueNode, context, JMXMetricSetAttributes.ENABLED));
                if (metricSetValueNode.hasDefined(JMXMetricDefinition.METRIC)) {
                    List<Property> metricsList = metricSetValueNode.get(JMXMetricDefinition.METRIC).asPropertyList();
                    for (Property metricProperty : metricsList) {
                        String metricName = metricSet.getName() + "~" + metricProperty.getName();
                        JMXMetricType metric = new JMXMetricType(ID.NULL_ID, new Name(metricName));
                        metricSet.getMetricTypeMap().put(metric.getName(), metric);
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
                    if (metricSet.isEnabled() && !metricSet.getMetricTypeMap().isEmpty()) {
                        hasEnabledMetrics = true;
                    }
                }
            }
        }

        return hasEnabledMetrics;
    }

    private boolean determineAvailSetJmx(ModelNode config, OperationContext context) throws OperationFailedException {
        boolean hasEnabledAvails = false;

        if (config.hasDefined(JMXAvailSetDefinition.AVAIL_SET)) {
            List<Property> availSetsList = config.get(JMXAvailSetDefinition.AVAIL_SET).asPropertyList();
            for (Property availSetProperty : availSetsList) {
                String availSetName = availSetProperty.getName();
                if (availSetName.indexOf(',') > -1) {
                    log.warnCommaInName(availSetName);
                }
                JMXAvailTypeSet availSet = new JMXAvailTypeSet(ID.NULL_ID, new Name(availSetName));
                theConfig.jmxAvailTypeSetMap.put(availSet.getName(), availSet);
                ModelNode availSetValueNode = availSetProperty.getValue();
                availSet.setEnabled(getBoolean(availSetValueNode, context, JMXAvailSetAttributes.ENABLED));
                if (availSetValueNode.hasDefined(JMXAvailDefinition.AVAIL)) {
                    List<Property> availsList = availSetValueNode.get(JMXAvailDefinition.AVAIL).asPropertyList();
                    for (Property availProperty : availsList) {
                        String availName = availSet.getName() + "~" + availProperty.getName();
                        JMXAvailType avail = new JMXAvailType(ID.NULL_ID, new Name(availName));
                        availSet.getAvailTypeMap().put(avail.getName(), avail);
                        ModelNode availValueNode = availProperty.getValue();
                        avail.setObjectName(getObjectName(availValueNode, context, JMXAvailAttributes.OBJECT_NAME));
                        avail.setAttribute(getString(availValueNode, context, JMXAvailAttributes.ATTRIBUTE));
                        avail.setInterval(getInt(availValueNode, context, JMXAvailAttributes.INTERVAL));
                        String availTimeUnitsStr = getString(availValueNode, context, JMXAvailAttributes.TIME_UNITS);
                        avail.setTimeUnits(TimeUnit.valueOf(availTimeUnitsStr.toUpperCase()));
                        avail.setUpRegex(getString(availValueNode, context, JMXAvailAttributes.UP_REGEX));
                    }
                    if (availSet.isEnabled() && !availSet.getAvailTypeMap().isEmpty()) {
                        hasEnabledAvails = true;
                    }
                }
            }
        }

        return hasEnabledAvails;
    }

    private void determinePlatformConfig(ModelNode config, OperationContext context)
            throws OperationFailedException {

        // assume they are disabled unless configured otherwise
        theConfig.platform.allEnabled = false;
        theConfig.platform.resourceTypeSetMap.clear();
        theConfig.platform.metricTypeSetMap.clear();

        if (!config.hasDefined(PlatformDefinition.PLATFORM)) {
            log.infoNoPlatformConfig();
            return;
        }

        List<Property> asPropertyList = config.get(PlatformDefinition.PLATFORM).asPropertyList();
        if (asPropertyList.size() == 0) {
            log.infoNoPlatformConfig();
            return;
        } else if (asPropertyList.size() > 1) {
            throw new IllegalArgumentException("Only one platform config allowed: " + config.toJSONString(true));
        }

        ModelNode platformValueNode = asPropertyList.get(0).getValue();
        theConfig.platform.allEnabled = getBoolean(platformValueNode, context, PlatformAttributes.ENABLED);
        if (theConfig.platform.allEnabled == false) {
            log.debugf("Platform monitoring is disabled");
            return;
        }

        // all the type metadata is dependent upon the capabilities of the oshi SystemInfo API

        // since platform monitoring is enabled, we will always have at least the root OS type

        PlatformResourceTypeSet rootTypeSet = new PlatformResourceTypeSet(null,
                Constants.PlatformResourceType.OPERATING_SYSTEM.getName());
        rootTypeSet.setEnabled(true);
        PlatformResourceType rootType = new PlatformResourceType(null,
                Constants.PlatformResourceType.OPERATING_SYSTEM.getName());
        rootType.setResourceNameTemplate("%s");
        rootTypeSet.getResourceTypeMap().put(rootType.getName(), rootType);
        theConfig.platform.resourceTypeSetMap.put(rootTypeSet.getName(), rootTypeSet);

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

                    PlatformMetricTypeSet fileStoreMetrics = new PlatformMetricTypeSet(null,
                            Constants.PlatformResourceType.FILE_STORE.getName());
                    theConfig.platform.metricTypeSetMap.put(fileStoreMetrics.getName(), fileStoreMetrics);

                    PlatformMetricType usableSpace = new PlatformMetricType(null, Constants.FILE_STORE_USABLE_SPACE);
                    usableSpace.setInterval(interval);
                    usableSpace.setTimeUnits(timeUnit);
                    usableSpace.setMetricUnits(MeasurementUnit.BYTES);
                    usableSpace.setMetricType(MetricType.GAUGE);
                    fileStoreMetrics.getMetricTypeMap().put(usableSpace.getName(), usableSpace);

                    PlatformMetricType totalSpace = new PlatformMetricType(null, Constants.FILE_STORE_TOTAL_SPACE);
                    totalSpace.setInterval(interval);
                    totalSpace.setTimeUnits(timeUnit);
                    totalSpace.setMetricUnits(MeasurementUnit.BYTES);
                    totalSpace.setMetricType(MetricType.GAUGE);
                    fileStoreMetrics.getMetricTypeMap().put(totalSpace.getName(), totalSpace);

                    PlatformResourceTypeSet typeSet = new PlatformResourceTypeSet(null,
                            Constants.PlatformResourceType.FILE_STORE.getName());
                    typeSet.setEnabled(true);
                    PlatformResourceType type = new PlatformResourceType(null,
                            Constants.PlatformResourceType.FILE_STORE.getName());
                    type.setParents(Collections.singletonList(rootType.getName()));
                    type.setMetricSets(Collections.singletonList(fileStoreMetrics.getName()));
                    type.setResourceNameTemplate(
                            Constants.PlatformResourceType.FILE_STORE.getName().getNameString() + " [%s]");
                    typeSet.getResourceTypeMap().put(type.getName(), type);
                    theConfig.platform.resourceTypeSetMap.put(typeSet.getName(), typeSet);
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

                    PlatformMetricTypeSet memoryMetrics = new PlatformMetricTypeSet(null,
                            Constants.PlatformResourceType.MEMORY.getName());
                    theConfig.platform.metricTypeSetMap.put(memoryMetrics.getName(), memoryMetrics);

                    PlatformMetricType available = new PlatformMetricType(null, Constants.MEMORY_AVAILABLE);
                    available.setInterval(interval);
                    available.setTimeUnits(timeUnit);
                    available.setMetricUnits(MeasurementUnit.BYTES);
                    available.setMetricType(MetricType.GAUGE);
                    memoryMetrics.getMetricTypeMap().put(available.getName(), available);

                    PlatformMetricType total = new PlatformMetricType(null, Constants.MEMORY_TOTAL);
                    total.setInterval(interval);
                    total.setTimeUnits(timeUnit);
                    total.setMetricUnits(MeasurementUnit.BYTES);
                    total.setMetricType(MetricType.GAUGE);
                    memoryMetrics.getMetricTypeMap().put(total.getName(), total);

                    PlatformResourceTypeSet typeSet = new PlatformResourceTypeSet(null,
                            Constants.PlatformResourceType.MEMORY.getName());
                    typeSet.setEnabled(true);
                    PlatformResourceType type = new PlatformResourceType(null,
                            Constants.PlatformResourceType.MEMORY.getName());
                    type.setParents(Collections.singletonList(rootType.getName()));
                    type.setMetricSets(Collections.singletonList(memoryMetrics.getName()));
                    type.setResourceNameTemplate(
                            Constants.PlatformResourceType.MEMORY.getName().getNameString());
                    typeSet.getResourceTypeMap().put(type.getName(), type);
                    theConfig.platform.resourceTypeSetMap.put(typeSet.getName(), typeSet);
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

                    PlatformMetricTypeSet processorMetrics = new PlatformMetricTypeSet(null,
                            Constants.PlatformResourceType.PROCESSOR.getName());
                    theConfig.platform.metricTypeSetMap.put(processorMetrics.getName(), processorMetrics);

                    // this is the Processor.getProcessorCpuLoadBetweenTicks value
                    PlatformMetricType cpuUsage = new PlatformMetricType(null, Constants.PROCESSOR_CPU_USAGE);
                    cpuUsage.setInterval(interval);
                    cpuUsage.setTimeUnits(timeUnit);
                    cpuUsage.setMetricUnits(MeasurementUnit.PERCENTAGE);
                    cpuUsage.setMetricType(MetricType.GAUGE);
                    processorMetrics.getMetricTypeMap().put(cpuUsage.getName(), cpuUsage);

                    PlatformResourceTypeSet typeSet = new PlatformResourceTypeSet(null,
                            Constants.PlatformResourceType.PROCESSOR.getName());
                    typeSet.setEnabled(true);
                    PlatformResourceType type = new PlatformResourceType(null,
                            Constants.PlatformResourceType.PROCESSOR.getName());
                    type.setParents(Collections.singletonList(rootType.getName()));
                    type.setMetricSets(Collections.singletonList(processorMetrics.getName()));
                    type.setResourceNameTemplate(
                            Constants.PlatformResourceType.PROCESSOR.getName().getNameString() + " [%s]");
                    typeSet.getResourceTypeMap().put(type.getName(), type);
                    theConfig.platform.resourceTypeSetMap.put(typeSet.getName(), typeSet);
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

                    PlatformMetricTypeSet powerSourceMetrics = new PlatformMetricTypeSet(null,
                            Constants.PlatformResourceType.POWER_SOURCE.getName());
                    theConfig.platform.metricTypeSetMap.put(powerSourceMetrics.getName(), powerSourceMetrics);

                    PlatformMetricType remainingCap = new PlatformMetricType(null,
                            Constants.POWER_SOURCE_REMAINING_CAPACITY);
                    remainingCap.setInterval(interval);
                    remainingCap.setTimeUnits(timeUnit);
                    remainingCap.setMetricUnits(MeasurementUnit.PERCENTAGE);
                    remainingCap.setMetricType(MetricType.GAUGE);
                    powerSourceMetrics.getMetricTypeMap().put(remainingCap.getName(), remainingCap);

                    PlatformMetricType timeRemaining = new PlatformMetricType(null,
                            Constants.POWER_SOURCE_TIME_REMAINING);
                    timeRemaining.setInterval(interval);
                    timeRemaining.setTimeUnits(timeUnit);
                    timeRemaining.setMetricUnits(MeasurementUnit.SECONDS);
                    timeRemaining.setMetricType(MetricType.GAUGE);
                    powerSourceMetrics.getMetricTypeMap().put(timeRemaining.getName(), timeRemaining);

                    PlatformResourceTypeSet typeSet = new PlatformResourceTypeSet(null,
                            Constants.PlatformResourceType.POWER_SOURCE.getName());
                    typeSet.setEnabled(true);
                    PlatformResourceType type = new PlatformResourceType(null,
                            Constants.PlatformResourceType.POWER_SOURCE.getName());
                    type.setParents(Collections.singletonList(rootType.getName()));
                    type.setMetricSets(Collections.singletonList(powerSourceMetrics.getName()));
                    type.setResourceNameTemplate(
                            Constants.PlatformResourceType.POWER_SOURCE.getName().getNameString() + " [%s]");
                    typeSet.getResourceTypeMap().put(type.getName(), type);
                    theConfig.platform.resourceTypeSetMap.put(typeSet.getName(), typeSet);
                }
            } else if (asPropertyList.size() > 1) {
                throw new IllegalArgumentException("Only one platform.power-sources config allowed: "
                        + platformValueNode.toJSONString(true));
            }
        }
    }

    private void determineDiagnosticsConfig(ModelNode config, OperationContext context)
            throws OperationFailedException {
        if (!config.hasDefined(DiagnosticsDefinition.DIAGNOSTICS)) {
            log.infoNoDiagnosticsConfig();
            theConfig.diagnostics.enabled = false;
            return;
        }

        List<Property> asPropertyList = config.get(DiagnosticsDefinition.DIAGNOSTICS).asPropertyList();
        if (asPropertyList.size() == 0) {
            log.infoNoDiagnosticsConfig();
            theConfig.diagnostics.enabled = false;
            return;
        } else if (asPropertyList.size() > 1) {
            throw new IllegalArgumentException("Only one diagnostics config allowed: " + config.toJSONString(true));
        }

        ModelNode diagnosticsValueNode = asPropertyList.get(0).getValue();

        String reportToStr = getString(diagnosticsValueNode, context, DiagnosticsAttributes.REPORT_TO);
        theConfig.diagnostics.reportTo = MonitorServiceConfiguration.DiagnosticsReportTo.valueOf(reportToStr
                .toUpperCase());
        theConfig.diagnostics.enabled = getBoolean(diagnosticsValueNode, context, DiagnosticsAttributes.ENABLED);
        theConfig.diagnostics.interval = getInt(diagnosticsValueNode, context, DiagnosticsAttributes.INTERVAL);
        String diagnosticsTimeUnitsStr = getString(diagnosticsValueNode, context, DiagnosticsAttributes.TIME_UNITS);
        theConfig.diagnostics.timeUnits = TimeUnit.valueOf(diagnosticsTimeUnitsStr.toUpperCase());
    }

    private void determineStorageAdapterConfig(ModelNode config, OperationContext context)
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

        theConfig.storageAdapter.url = getString(storageAdapterConfig, context, StorageAttributes.URL);
        if (theConfig.storageAdapter.url != null) {
            theConfig.storageAdapter.useSSL = theConfig.storageAdapter.url.startsWith("https");
            log.infoUsingSSL(theConfig.storageAdapter.url, theConfig.storageAdapter.useSSL);
        } else {
            theConfig.storageAdapter.useSSL = getBoolean(storageAdapterConfig, context, StorageAttributes.USE_SSL);
        }
        theConfig.storageAdapter.securityRealm = getString(storageAdapterConfig, context,
                StorageAttributes.SECURITY_REALM);
        theConfig.storageAdapter.keystorePath = getString(storageAdapterConfig, context,
                StorageAttributes.KEYSTORE_PATH);
        theConfig.storageAdapter.keystorePassword = getString(storageAdapterConfig, context,
                StorageAttributes.KEYSTORE_PASSWORD);
        theConfig.storageAdapter.serverOutboundSocketBindingRef = getString(storageAdapterConfig, context,
                StorageAttributes.SERVER_OUTBOUND_SOCKET_BINDING_REF);
        theConfig.storageAdapter.tenantId = getString(storageAdapterConfig, context, StorageAttributes.TENANT_ID);
        theConfig.storageAdapter.accountsContext = getString(storageAdapterConfig, context,
                StorageAttributes.ACCOUNTS_CONTEXT);
        theConfig.storageAdapter.inventoryContext = getString(storageAdapterConfig, context,
                StorageAttributes.INVENTORY_CONTEXT);
        theConfig.storageAdapter.metricsContext = getString(storageAdapterConfig, context,
                StorageAttributes.METRICS_CONTEXT);
        theConfig.storageAdapter.feedcommContext = getString(storageAdapterConfig, context,
                StorageAttributes.FEEDCOMM_CONTEXT);
        theConfig.storageAdapter.username = getString(storageAdapterConfig, context, StorageAttributes.USERNAME);
        theConfig.storageAdapter.password = getString(storageAdapterConfig, context, StorageAttributes.PASSWORD);
        String typeStr = getString(storageAdapterConfig, context, StorageAttributes.TYPE);
        theConfig.storageAdapter.type = MonitorServiceConfiguration.StorageReportTo.valueOf(typeStr.toUpperCase());

        if (theConfig.storageAdapter.useSSL) {
            if (theConfig.storageAdapter.securityRealm == null) {
                if (theConfig.storageAdapter.keystorePath == null) {
                    throw new IllegalArgumentException(
                            "In order to use SSL, a securityRealm or keystorePath must be specified");
                }
                if (theConfig.storageAdapter.keystorePassword == null) {
                    throw new IllegalArgumentException(
                            "In order to use SSL, a securityRealm or keystorePassword must be specified");
                }
            }
        }
    }

    private void determineGlobalConfig(ModelNode config, OperationContext context) throws OperationFailedException {
        theConfig.subsystemEnabled = getBoolean(config, context, SubsystemAttributes.ENABLED);
        theConfig.apiJndi = getString(config, context, SubsystemAttributes.API_JNDI);
        theConfig.numMetricSchedulerThreads = getInt(config, context,
                SubsystemAttributes.NUM_METRIC_SCHEDULER_THREADS);
        theConfig.numAvailSchedulerThreads = getInt(config, context,
                SubsystemAttributes.NUM_AVAIL_SCHEDULER_THREADS);
        theConfig.numDmrSchedulerThreads = getInt(config, context,
                SubsystemAttributes.NUM_DMR_SCHEDULER_THREADS);
        theConfig.metricDispatcherBufferSize = getInt(config, context,
                SubsystemAttributes.METRIC_DISPATCHER_BUFFER_SIZE);
        theConfig.metricDispatcherMaxBatchSize = getInt(config, context,
                SubsystemAttributes.METRIC_DISPATCHER_MAX_BATCH_SIZE);
        theConfig.availDispatcherBufferSize = getInt(config, context,
                SubsystemAttributes.AVAIL_DISPATCHER_BUFFER_SIZE);
        theConfig.availDispatcherMaxBatchSize = getInt(config, context,
                SubsystemAttributes.AVAIL_DISPATCHER_MAX_BATCH_SIZE);
    }

    private boolean determineResourceTypeSetDmr(ModelNode config, OperationContext context)
            throws OperationFailedException {
        boolean hasEnabledResourceTypes = false;

        if (config.hasDefined(DMRResourceTypeSetDefinition.RESOURCE_TYPE_SET)) {
            List<Property> resourceTypeSetsList = config.get(DMRResourceTypeSetDefinition.RESOURCE_TYPE_SET)
                    .asPropertyList();
            for (Property resourceTypeSetProperty : resourceTypeSetsList) {
                String resourceTypeSetName = resourceTypeSetProperty.getName();
                DMRResourceTypeSet resourceTypeSet = new DMRResourceTypeSet(ID.NULL_ID, new Name(resourceTypeSetName));
                if (resourceTypeSetName.indexOf(',') > -1) {
                    log.warnCommaInName(resourceTypeSetName);
                }
                theConfig.dmrResourceTypeSetMap.put(resourceTypeSet.getName(), resourceTypeSet);
                ModelNode resourceTypeSetValueNode = resourceTypeSetProperty.getValue();
                resourceTypeSet.setEnabled(getBoolean(resourceTypeSetValueNode, context,
                        DMRResourceTypeSetAttributes.ENABLED));
                if (resourceTypeSetValueNode.hasDefined(DMRResourceTypeDefinition.RESOURCE_TYPE)) {
                    List<Property> resourceTypesList = resourceTypeSetValueNode.get(
                            DMRResourceTypeDefinition.RESOURCE_TYPE).asPropertyList();
                    for (Property resourceTypeProperty : resourceTypesList) {
                        ModelNode resourceTypeValueNode = resourceTypeProperty.getValue();

                        String resourceTypeName = resourceTypeProperty.getName();
                        DMRResourceType resourceType = new DMRResourceType(ID.NULL_ID, new Name(resourceTypeName));
                        resourceTypeSet.getResourceTypeMap().put(resourceType.getName(), resourceType);
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
                            if (!theConfig.dmrMetricTypeSetMap.containsKey(metricSetName)) {
                                log.warnMetricSetDoesNotExist(resourceTypeName.toString(),
                                        metricSetName.toString());
                            }
                        }
                        for (Name availSetName : availSets) {
                            if (!theConfig.dmrAvailTypeSetMap.containsKey(availSetName)) {
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
                                DMROperation op = new DMROperation(ID.NULL_ID, new Name(operationName), resourceType);
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
                                DMRResourceConfigurationPropertyType configType =
                                        new DMRResourceConfigurationPropertyType(ID.NULL_ID, new Name(configName),
                                                resourceType);
                                configType.setPath(getString(configValueNode, context,
                                        DMRResourceConfigAttributes.PATH));
                                configType.setAttribute(getString(configValueNode, context,
                                        DMRResourceConfigAttributes.ATTRIBUTE));
                                resourceType.addResourceConfigurationPropertyType(configType);
                            }
                        }
                    }
                }
            }

            // build a graph of the full type hierarchy just to test to make sure it all is valid
            try {
                ResourceTypeManager<DMRResourceType, DMRResourceTypeSet> rtm;
                rtm = new ResourceTypeManager<>(theConfig.dmrResourceTypeSetMap);
                if (!rtm.getAllResourceTypes().isEmpty()) {
                    hasEnabledResourceTypes = true;
                }
            } catch (Exception e) {
                throw new OperationFailedException(e);
            }
        }

        return hasEnabledResourceTypes;
    }

    private boolean determineResourceTypeSetJmx(ModelNode config, OperationContext context)
            throws OperationFailedException {
        boolean hasEnabledResourceTypes = false;

        if (config.hasDefined(JMXResourceTypeSetDefinition.RESOURCE_TYPE_SET)) {
            List<Property> resourceTypeSetsList = config.get(JMXResourceTypeSetDefinition.RESOURCE_TYPE_SET)
                    .asPropertyList();
            for (Property resourceTypeSetProperty : resourceTypeSetsList) {
                String resourceTypeSetName = resourceTypeSetProperty.getName();
                JMXResourceTypeSet resourceTypeSet = new JMXResourceTypeSet(ID.NULL_ID, new Name(resourceTypeSetName));
                if (resourceTypeSetName.indexOf(',') > -1) {
                    log.warnCommaInName(resourceTypeSetName);
                }
                theConfig.jmxResourceTypeSetMap.put(resourceTypeSet.getName(), resourceTypeSet);
                ModelNode resourceTypeSetValueNode = resourceTypeSetProperty.getValue();
                resourceTypeSet.setEnabled(getBoolean(resourceTypeSetValueNode, context,
                        JMXResourceTypeSetAttributes.ENABLED));
                if (resourceTypeSetValueNode.hasDefined(JMXResourceTypeDefinition.RESOURCE_TYPE)) {
                    List<Property> resourceTypesList = resourceTypeSetValueNode.get(
                            JMXResourceTypeDefinition.RESOURCE_TYPE).asPropertyList();
                    for (Property resourceTypeProperty : resourceTypesList) {
                        ModelNode resourceTypeValueNode = resourceTypeProperty.getValue();

                        String resourceTypeName = resourceTypeProperty.getName();
                        JMXResourceType resourceType = new JMXResourceType(ID.NULL_ID, new Name(resourceTypeName));
                        resourceTypeSet.getResourceTypeMap().put(resourceType.getName(), resourceType);
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
                            if (!theConfig.jmxMetricTypeSetMap.containsKey(metricSetName)) {
                                log.warnMetricSetDoesNotExist(resourceTypeName.toString(),
                                        metricSetName.toString());
                            }
                        }
                        for (Name availSetName : availSets) {
                            if (!theConfig.jmxAvailTypeSetMap.containsKey(availSetName)) {
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
                                JMXOperation op = new JMXOperation(ID.NULL_ID, new Name(operationName), resourceType);
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
                                JMXResourceConfigurationPropertyType configType =
                                        new JMXResourceConfigurationPropertyType(ID.NULL_ID, new Name(configName),
                                                resourceType);
                                configType.setObjectName(getObjectName(configValueNode, context,
                                        JMXResourceConfigAttributes.OBJECT_NAME));
                                configType.setAttribute(getString(configValueNode, context,
                                        JMXResourceConfigAttributes.ATTRIBUTE));
                                resourceType.addResourceConfigurationPropertyType(configType);
                            }
                        }
                    }
                }
            }

            // build a graph of the full type hierarchy just to test to make sure it all is valid
            try {
                ResourceTypeManager<JMXResourceType, JMXResourceTypeSet> rtm;
                rtm = new ResourceTypeManager<>(theConfig.jmxResourceTypeSetMap);
                if (!rtm.getAllResourceTypes().isEmpty()) {
                    hasEnabledResourceTypes = true;
                }
            } catch (Exception e) {
                throw new OperationFailedException(e);
            }
        }

        return hasEnabledResourceTypes;
    }

    private void determineManagedServers(ModelNode config, OperationContext context) throws OperationFailedException {
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
                        if (!theConfig.dmrResourceTypeSetMap.containsKey(resourceTypeSetName)) {
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
                    theConfig.managedServersMap.put(res.getName(), res);
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
                    if (!theConfig.dmrResourceTypeSetMap.containsKey(resourceTypeSetName)) {
                        log.warnResourceTypeSetDoesNotExist(name.toString(), resourceTypeSetName.toString());
                    }
                }

                LocalDMRManagedServer res = new LocalDMRManagedServer(ID.NULL_ID, new Name(name));
                res.setEnabled(enabled);
                res.getResourceTypeSets().addAll(resourceTypeSets);
                theConfig.managedServersMap.put(res.getName(), res);
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
                        if (!theConfig.jmxResourceTypeSetMap.containsKey(resourceTypeSetName)) {
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
                    theConfig.managedServersMap.put(res.getName(), res);
                }
            }

        }
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
