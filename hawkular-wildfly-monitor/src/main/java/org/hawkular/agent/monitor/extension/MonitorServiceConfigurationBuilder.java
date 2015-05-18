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

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.TimeUnit;

import org.hawkular.agent.monitor.inventory.Name;
import org.hawkular.agent.monitor.inventory.ResourceTypeManager;
import org.hawkular.agent.monitor.inventory.dmr.DMRAvailType;
import org.hawkular.agent.monitor.inventory.dmr.DMRAvailTypeSet;
import org.hawkular.agent.monitor.inventory.dmr.DMRMetricType;
import org.hawkular.agent.monitor.inventory.dmr.DMRMetricTypeSet;
import org.hawkular.agent.monitor.inventory.dmr.DMRResourceType;
import org.hawkular.agent.monitor.inventory.dmr.DMRResourceTypeSet;
import org.hawkular.agent.monitor.inventory.dmr.LocalDMRManagedServer;
import org.hawkular.agent.monitor.inventory.dmr.RemoteDMRManagedServer;
import org.hawkular.agent.monitor.log.MsgLogger;
import org.hawkular.agent.monitor.scheduler.config.SchedulerConfiguration;
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

    private final MonitorServiceConfiguration theConfig = new MonitorServiceConfiguration();

    public MonitorServiceConfiguration build() {
        return theConfig;
    }

    public MonitorServiceConfigurationBuilder(ModelNode config, OperationContext context)
            throws OperationFailedException {
        determineGlobalConfig(config, context);
        determineStorageAdapterConfig(config, context);
        determineDiagnosticsConfig(config, context);

        boolean hasEnabledMetrics = determineMetricSetDmr(config, context);
        if (!hasEnabledMetrics) {
            MsgLogger.LOG.infoNoEnabledMetricsConfigured();
        }

        boolean hasEnabledAvails = determineAvailSetDmr(config, context);
        if (!hasEnabledAvails) {
            MsgLogger.LOG.infoNoEnabledAvailsConfigured();
        }

        // make sure to call this AFTER the metric sets and avail sets have been determined
        boolean hasEnabledResourceTypes = determineResourceTypeSetDmr(config, context);
        if (!hasEnabledResourceTypes) {
            MsgLogger.LOG.infoNoEnabledResourceTypesConfigured();
        }

        // make sure to call this AFTER the resource type sets have been determined
        determineManagedServers(config, context);

        return;
    }

    private boolean determineMetricSetDmr(ModelNode config, OperationContext context) throws OperationFailedException {
        boolean hasEnabledMetrics = false;

        if (config.hasDefined(DMRMetricSetDefinition.METRIC_SET)) {
            List<Property> metricSetsList = config.get(DMRMetricSetDefinition.METRIC_SET).asPropertyList();
            for (Property metricSetProperty : metricSetsList) {
                String metricSetName = metricSetProperty.getName();
                if (metricSetName.indexOf(',') > -1) {
                    MsgLogger.LOG.warnCommaInName(metricSetName);
                }
                DMRMetricTypeSet metricSet = new DMRMetricTypeSet(metricSetName);
                theConfig.dmrMetricTypeSetMap.put(metricSet.getName(), metricSet);
                ModelNode metricSetValueNode = metricSetProperty.getValue();
                metricSet.setEnabled(getBoolean(metricSetValueNode, context, DMRMetricSetDefinition.ENABLED));
                if (metricSetValueNode.hasDefined(DMRMetricDefinition.METRIC)) {
                    List<Property> metricsList = metricSetValueNode.get(DMRMetricDefinition.METRIC).asPropertyList();
                    for (Property metricProperty : metricsList) {
                        String metricName = metricProperty.getName();
                        DMRMetricType metric = new DMRMetricType(metricName);
                        metricSet.getMetricTypeMap().put(metric.getName(), metric);
                        ModelNode metricValueNode = metricProperty.getValue();
                        metric.setPath(getString(metricValueNode, context, DMRMetricDefinition.PATH));
                        metric.setAttribute(getString(metricValueNode, context, DMRMetricDefinition.ATTRIBUTE));
                        String metricUnitsStr = getString(metricValueNode, context, DMRMetricDefinition.METRIC_UNITS);
                        if (metricUnitsStr == null) {
                            metric.setMetricUnits(MeasurementUnit.NONE);
                        } else {
                            metric.setMetricUnits(MeasurementUnit.valueOf(metricUnitsStr.toUpperCase(Locale.ENGLISH)));
                        }
                        metric.setInterval(getInt(metricValueNode, context, DMRMetricDefinition.INTERVAL));
                        String metricTimeUnitsStr = getString(metricValueNode, context, DMRMetricDefinition.TIME_UNITS);
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
                    MsgLogger.LOG.warnCommaInName(availSetName);
                }
                DMRAvailTypeSet availSet = new DMRAvailTypeSet(availSetName);
                theConfig.dmrAvailTypeSetMap.put(availSet.getName(), availSet);
                ModelNode availSetValueNode = availSetProperty.getValue();
                availSet.setEnabled(getBoolean(availSetValueNode, context, DMRAvailSetDefinition.ENABLED));
                if (availSetValueNode.hasDefined(DMRAvailDefinition.AVAIL)) {
                    List<Property> availsList = availSetValueNode.get(DMRAvailDefinition.AVAIL).asPropertyList();
                    for (Property availProperty : availsList) {
                        String availName = availProperty.getName();
                        DMRAvailType avail = new DMRAvailType(availName);
                        availSet.getAvailTypeMap().put(avail.getName(), avail);
                        ModelNode availValueNode = availProperty.getValue();
                        avail.setPath(getString(availValueNode, context, DMRAvailDefinition.PATH));
                        avail.setAttribute(getString(availValueNode, context, DMRAvailDefinition.ATTRIBUTE));
                        avail.setInterval(getInt(availValueNode, context, DMRAvailDefinition.INTERVAL));
                        String availTimeUnitsStr = getString(availValueNode, context, DMRAvailDefinition.TIME_UNITS);
                        avail.setTimeUnits(TimeUnit.valueOf(availTimeUnitsStr.toUpperCase()));
                        avail.setUpRegex(getString(availValueNode, context, DMRAvailDefinition.UP_REGEX));
                    }
                    if (availSet.isEnabled() && !availSet.getAvailTypeMap().isEmpty()) {
                        hasEnabledAvails = true;
                    }
                }
            }
        }

        return hasEnabledAvails;
    }

    private void determineDiagnosticsConfig(ModelNode config, OperationContext context)
            throws OperationFailedException {
        if (!config.hasDefined(DiagnosticsDefinition.DIAGNOSTICS)) {
            MsgLogger.LOG.infoNoDiagnosticsConfig();
            theConfig.diagnostics.enabled = false;
            return;
        }

        List<Property> asPropertyList = config.get(DiagnosticsDefinition.DIAGNOSTICS).asPropertyList();
        if (asPropertyList.size() == 0) {
            MsgLogger.LOG.infoNoDiagnosticsConfig();
            theConfig.diagnostics.enabled = false;
            return;
        } else if (asPropertyList.size() > 1) {
            throw new IllegalArgumentException("Only one diagnostics config allowed: " + config.toJSONString(true));
        }

        ModelNode diagnosticsValueNode = asPropertyList.get(0).getValue();

        String reportToStr = getString(diagnosticsValueNode, context, DiagnosticsDefinition.REPORT_TO);
        theConfig.diagnostics.reportTo = SchedulerConfiguration.DiagnosticsReportTo.valueOf(reportToStr.toUpperCase());
        theConfig.diagnostics.enabled = getBoolean(diagnosticsValueNode, context, DiagnosticsDefinition.ENABLED);
        theConfig.diagnostics.interval = getInt(diagnosticsValueNode, context, DiagnosticsDefinition.INTERVAL);
        String diagnosticsTimeUnitsStr = getString(diagnosticsValueNode, context, DiagnosticsDefinition.TIME_UNITS);
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

        theConfig.storageAdapter.url = getString(storageAdapterConfig, context, StorageDefinition.URL);
        theConfig.storageAdapter.context = getString(storageAdapterConfig, context, StorageDefinition.CONTEXT);
        theConfig.storageAdapter.restContext = getString(storageAdapterConfig, context, StorageDefinition.REST_CONTEXT);
        theConfig.storageAdapter.user = getString(storageAdapterConfig, context, StorageDefinition.USER);
        theConfig.storageAdapter.password = getString(storageAdapterConfig, context, StorageDefinition.PASSWORD);
        String typeStr = getString(storageAdapterConfig, context, StorageDefinition.TYPE);
        theConfig.storageAdapter.type = SchedulerConfiguration.StorageReportTo.valueOf(typeStr.toUpperCase());
    }

    private void determineGlobalConfig(ModelNode config, OperationContext context) throws OperationFailedException {
        theConfig.subsystemEnabled = getBoolean(config, context, SubsystemDefinition.ENABLED);
        theConfig.apiJndi = getString(config, context, SubsystemDefinition.API_JNDI);
        theConfig.numMetricSchedulerThreads = getInt(config, context, SubsystemDefinition.NUM_METRIC_SCHEDULER_THREADS);
        theConfig.numAvailSchedulerThreads = getInt(config, context, SubsystemDefinition.NUM_AVAIL_SCHEDULER_THREADS);
        theConfig.numDmrSchedulerThreads = getInt(config, context, SubsystemDefinition.NUM_DMR_SCHEDULER_THREADS);
        theConfig.metricDispatcherBufferSize = getInt(config, context,
                SubsystemDefinition.METRIC_DISPATCHER_BUFFER_SIZE);
        theConfig.metricDispatcherMaxBatchSize = getInt(config, context,
                SubsystemDefinition.METRIC_DISPATCHER_MAX_BATCH_SIZE);
        theConfig.availDispatcherBufferSize = getInt(config, context,
                SubsystemDefinition.AVAIL_DISPATCHER_BUFFER_SIZE);
        theConfig.availDispatcherMaxBatchSize = getInt(config, context,
                SubsystemDefinition.AVAIL_DISPATCHER_MAX_BATCH_SIZE);
    }

    private boolean determineResourceTypeSetDmr(ModelNode config, OperationContext context)
            throws OperationFailedException {
        boolean hasEnabledResourceTypes = false;

        if (config.hasDefined(DMRResourceTypeSetDefinition.RESOURCE_TYPE_SET)) {
            List<Property> resourceTypeSetsList = config.get(DMRResourceTypeSetDefinition.RESOURCE_TYPE_SET)
                    .asPropertyList();
            for (Property resourceTypeSetProperty : resourceTypeSetsList) {
                String resourceTypeSetName = resourceTypeSetProperty.getName();
                DMRResourceTypeSet resourceTypeSet = new DMRResourceTypeSet(resourceTypeSetName);
                if (resourceTypeSetName.indexOf(',') > -1) {
                    MsgLogger.LOG.warnCommaInName(resourceTypeSetName);
                }
                theConfig.dmrResourceTypeSetMap.put(resourceTypeSet.getName(), resourceTypeSet);
                ModelNode resourceTypeSetValueNode = resourceTypeSetProperty.getValue();
                resourceTypeSet.setEnabled(getBoolean(resourceTypeSetValueNode, context,
                        DMRResourceTypeSetDefinition.ENABLED));
                if (resourceTypeSetValueNode.hasDefined(DMRResourceTypeDefinition.RESOURCE_TYPE)) {
                    List<Property> resourceTypesList = resourceTypeSetValueNode.get(
                            DMRResourceTypeDefinition.RESOURCE_TYPE).asPropertyList();
                    for (Property resourceTypeProperty : resourceTypesList) {
                        ModelNode resourceTypeValueNode = resourceTypeProperty.getValue();

                        String resourceTypeName = resourceTypeProperty.getName();
                        DMRResourceType resourceType = new DMRResourceType(resourceTypeName);
                        resourceTypeSet.getResourceTypeMap().put(resourceType.getName(), resourceType);
                        resourceType.setResourceNameTemplate(getString(resourceTypeValueNode, context,
                                DMRResourceTypeDefinition.RESOURCE_NAME_TEMPLATE));
                        resourceType.setPath(getString(resourceTypeValueNode, context,
                                DMRResourceTypeDefinition.PATH));
                        resourceType.setParents(getNameListFromString(resourceTypeValueNode, context,
                                DMRResourceTypeDefinition.PARENTS));

                        List<Name> metricSets = getNameListFromString(resourceTypeValueNode, context,
                                DMRResourceTypeDefinition.METRIC_SETS);
                        List<Name> availSets = getNameListFromString(resourceTypeValueNode, context,
                                DMRResourceTypeDefinition.AVAIL_SETS);

                        // verify that the metric sets and avail sets exist
                        for (Name metricSetName : metricSets) {
                            if (!theConfig.dmrMetricTypeSetMap.containsKey(metricSetName)) {
                                MsgLogger.LOG.warnMetricSetDoesNotExist(resourceTypeName.toString(),
                                        metricSetName.toString());
                            }
                        }
                        for (Name availSetName : availSets) {
                            if (!theConfig.dmrAvailTypeSetMap.containsKey(availSetName)) {
                                MsgLogger.LOG.warnAvailSetDoesNotExist(resourceTypeName.toString(),
                                        availSetName.toString());
                            }
                        }

                        resourceType.setMetricSets(metricSets);
                        resourceType.setAvailSets(availSets);
                    }
                }
            }

            // build a graph of the full type hierarchy just to test to make sure it all is valid
            try {
                ResourceTypeManager<DMRResourceType, DMRResourceTypeSet> rtm;
                rtm = new ResourceTypeManager<>(theConfig.dmrResourceTypeSetMap);
                if (!rtm.getResourceTypesGraph().vertexSet().isEmpty()) {
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

            if (managedServersValueNode.hasDefined(RemoteDMRDefinition.REMOTE_DMR)) {
                List<Property> remoteDMRsList = managedServersValueNode.get(RemoteDMRDefinition.REMOTE_DMR)
                        .asPropertyList();
                for (Property remoteDMRProperty : remoteDMRsList) {
                    String name = remoteDMRProperty.getName();
                    ModelNode remoteDMRValueNode = remoteDMRProperty.getValue();
                    boolean enabled = getBoolean(remoteDMRValueNode, context, RemoteDMRDefinition.ENABLED);
                    String host = getString(remoteDMRValueNode, context, RemoteDMRDefinition.HOST);
                    int port = getInt(remoteDMRValueNode, context, RemoteDMRDefinition.PORT);
                    String username = getString(remoteDMRValueNode, context, RemoteDMRDefinition.USERNAME);
                    String password = getString(remoteDMRValueNode, context, RemoteDMRDefinition.PASSWORD);
                    List<Name> resourceTypeSets = getNameListFromString(remoteDMRValueNode, context,
                            RemoteDMRDefinition.RESOURCE_TYPE_SETS);

                    // verify that the resource type sets exist
                    for (Name resourceTypeSetName : resourceTypeSets) {
                        if (!theConfig.dmrResourceTypeSetMap.containsKey(resourceTypeSetName)) {
                            MsgLogger.LOG.warnResourceTypeSetDoesNotExist(name.toString(),
                                    resourceTypeSetName.toString());
                        }
                    }

                    RemoteDMRManagedServer res = new RemoteDMRManagedServer(name);
                    res.setEnabled(enabled);
                    res.setHost(host);
                    res.setPort(port);
                    res.setUsername(username);
                    res.setPassword(password);
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
                boolean enabled = getBoolean(localDMRValueNode, context, LocalDMRDefinition.ENABLED);
                List<Name> resourceTypeSets = getNameListFromString(localDMRValueNode, context,
                        LocalDMRDefinition.RESOURCE_TYPE_SETS);

                // verify that the metric sets and avail sets exist
                for (Name resourceTypeSetName : resourceTypeSets) {
                    if (!theConfig.dmrResourceTypeSetMap.containsKey(resourceTypeSetName)) {
                        MsgLogger.LOG.warnResourceTypeSetDoesNotExist(name.toString(), resourceTypeSetName.toString());
                    }
                }

                LocalDMRManagedServer res = new LocalDMRManagedServer(name);
                res.setEnabled(enabled);
                res.getResourceTypeSets().addAll(resourceTypeSets);
                theConfig.managedServersMap.put(res.getName(), res);
            }

            // TODO get remote JMX entries now
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
