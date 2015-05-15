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
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import org.hawkular.agent.monitor.log.MsgLogger;
import org.hawkular.agent.monitor.scheduler.config.SchedulerConfiguration;
import org.jboss.as.controller.OperationContext;
import org.jboss.as.controller.OperationFailedException;
import org.jboss.as.controller.SimpleAttributeDefinition;
import org.jboss.as.controller.client.helpers.MeasurementUnit;
import org.jboss.dmr.ModelNode;
import org.jboss.dmr.Property;

/**
 * This represents the monitor service extension's XML configuration in a more consumable form.
 */
public class MonitorServiceConfiguration {

    public boolean subsystemEnabled;
    public String apiJndi;
    public int numMetricSchedulerThreads;
    public int numAvailSchedulerThreads;
    public int numDmrSchedulerThreads;
    public StorageAdapter storageAdapter = new StorageAdapter();
    public Diagnostics diagnostics = new Diagnostics();
    public Map<String, MetricSetDMR> metricSetDmrMap = new HashMap<>();
    public Map<String, AvailSetDMR> availSetDmrMap = new HashMap<>();
    public Map<String, ResourceTypeSetDMR> resourceTypeSetDmrMap = new HashMap<>();
    public Map<String, ManagedServer> managedServersMap = new HashMap<>();

    public MonitorServiceConfiguration(ModelNode config, OperationContext context) throws OperationFailedException {
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
                MetricSetDMR metricSet = new MetricSetDMR();
                String metricSetName = metricSetProperty.getName();
                if (metricSetName.indexOf(',') > -1) {
                    MsgLogger.LOG.warnCommaInName(metricSetName);
                }
                metricSetDmrMap.put(metricSetName, metricSet);
                metricSet.name = metricSetName;
                ModelNode metricSetValueNode = metricSetProperty.getValue();
                metricSet.enabled = getBoolean(metricSetValueNode, context, DMRMetricSetDefinition.ENABLED);
                if (metricSetValueNode.hasDefined(DMRMetricDefinition.METRIC)) {
                    List<Property> metricsList = metricSetValueNode.get(DMRMetricDefinition.METRIC).asPropertyList();
                    for (Property metricProperty : metricsList) {
                        MetricDMR metric = new MetricDMR();
                        String metricName = metricProperty.getName();
                        metricSet.metricDmrMap.put(metricName, metric);
                        metric.name = metricName;
                        ModelNode metricValueNode = metricProperty.getValue();
                        metric.path = getString(metricValueNode, context, DMRMetricDefinition.PATH);
                        metric.attribute = getString(metricValueNode, context, DMRMetricDefinition.ATTRIBUTE);
                        String metricUnitsStr = getString(metricValueNode, context, DMRMetricDefinition.METRIC_UNITS);
                        if (metricUnitsStr == null) {
                            metric.metricUnits = MeasurementUnit.NONE;
                        } else {
                            metric.metricUnits = MeasurementUnit.valueOf(metricUnitsStr.toUpperCase(Locale.ENGLISH));
                        }
                        metric.interval = getInt(metricValueNode, context, DMRMetricDefinition.INTERVAL);
                        String metricTimeUnitsStr = getString(metricValueNode, context, DMRMetricDefinition.TIME_UNITS);
                        metric.timeUnits = TimeUnit.valueOf(metricTimeUnitsStr.toUpperCase());
                    }
                    if (metricSet.enabled && !metricSet.metricDmrMap.isEmpty()) {
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
                AvailSetDMR availSet = new AvailSetDMR();
                String availSetName = availSetProperty.getName();
                if (availSetName.indexOf(',') > -1) {
                    MsgLogger.LOG.warnCommaInName(availSetName);
                }
                availSetDmrMap.put(availSetName, availSet);
                availSet.name = availSetName;
                ModelNode availSetValueNode = availSetProperty.getValue();
                availSet.enabled = getBoolean(availSetValueNode, context, DMRAvailSetDefinition.ENABLED);
                if (availSetValueNode.hasDefined(DMRAvailDefinition.AVAIL)) {
                    List<Property> availsList = availSetValueNode.get(DMRAvailDefinition.AVAIL).asPropertyList();
                    for (Property availProperty : availsList) {
                        AvailDMR avail = new AvailDMR();
                        String availName = availProperty.getName();
                        availSet.availDmrMap.put(availName, avail);
                        avail.name = availName;
                        ModelNode availValueNode = availProperty.getValue();
                        avail.path = getString(availValueNode, context, DMRAvailDefinition.PATH);
                        avail.attribute = getString(availValueNode, context, DMRAvailDefinition.ATTRIBUTE);
                        avail.interval = getInt(availValueNode, context, DMRAvailDefinition.INTERVAL);
                        String availTimeUnitsStr = getString(availValueNode, context, DMRAvailDefinition.TIME_UNITS);
                        avail.timeUnits = TimeUnit.valueOf(availTimeUnitsStr.toUpperCase());
                        avail.upRegex = getString(availValueNode, context, DMRAvailDefinition.UP_REGEX);
                    }
                    if (availSet.enabled && !availSet.availDmrMap.isEmpty()) {
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
            diagnostics.enabled = false;
            return;
        }

        List<Property> asPropertyList = config.get(DiagnosticsDefinition.DIAGNOSTICS).asPropertyList();
        if (asPropertyList.size() == 0) {
            MsgLogger.LOG.infoNoDiagnosticsConfig();
            diagnostics.enabled = false;
            return;
        } else if (asPropertyList.size() > 1) {
            throw new IllegalArgumentException("Only one diagnostics config allowed: " + config.toJSONString(true));
        }

        ModelNode diagnosticsValueNode = asPropertyList.get(0).getValue();

        String reportToStr = getString(diagnosticsValueNode, context, DiagnosticsDefinition.REPORT_TO);
        diagnostics.reportTo = SchedulerConfiguration.DiagnosticsReportTo.valueOf(reportToStr.toUpperCase());
        diagnostics.enabled = getBoolean(diagnosticsValueNode, context, DiagnosticsDefinition.ENABLED);
        diagnostics.interval = getInt(diagnosticsValueNode, context, DiagnosticsDefinition.INTERVAL);
        String diagnosticsTimeUnitsStr = getString(diagnosticsValueNode, context, DiagnosticsDefinition.TIME_UNITS);
        diagnostics.timeUnits = TimeUnit.valueOf(diagnosticsTimeUnitsStr.toUpperCase());
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

        storageAdapter.url = getString(storageAdapterConfig, context, StorageDefinition.URL);
        storageAdapter.context = getString(storageAdapterConfig, context, StorageDefinition.CONTEXT);
        storageAdapter.restContext = getString(storageAdapterConfig, context, StorageDefinition.REST_CONTEXT);
        storageAdapter.user = getString(storageAdapterConfig, context, StorageDefinition.USER);
        storageAdapter.password = getString(storageAdapterConfig, context, StorageDefinition.PASSWORD);
        String typeStr = getString(storageAdapterConfig, context, StorageDefinition.TYPE);
        storageAdapter.type = SchedulerConfiguration.StorageReportTo.valueOf(typeStr.toUpperCase());
    }

    private void determineGlobalConfig(ModelNode config, OperationContext context) throws OperationFailedException {
        subsystemEnabled = getBoolean(config, context, SubsystemDefinition.ENABLED);
        apiJndi = getString(config, context, SubsystemDefinition.API_JNDI);
        numMetricSchedulerThreads = getInt(config, context, SubsystemDefinition.NUM_METRIC_SCHEDULER_THREADS);
        numAvailSchedulerThreads = getInt(config, context, SubsystemDefinition.NUM_AVAIL_SCHEDULER_THREADS);
        numDmrSchedulerThreads = getInt(config, context, SubsystemDefinition.NUM_DMR_SCHEDULER_THREADS);
    }

    private boolean determineResourceTypeSetDmr(ModelNode config, OperationContext context)
            throws OperationFailedException {
        boolean hasEnabledResourceTypes = false;

        if (config.hasDefined(DMRResourceTypeSetDefinition.RESOURCE_TYPE_SET)) {
            List<Property> resourceTypeSetsList = config.get(DMRResourceTypeSetDefinition.RESOURCE_TYPE_SET)
                    .asPropertyList();
            for (Property resourceTypeSetProperty : resourceTypeSetsList) {
                ResourceTypeSetDMR resourceTypeSet = new ResourceTypeSetDMR();
                String resourceTypeSetName = resourceTypeSetProperty.getName();
                if (resourceTypeSetName.indexOf(',') > -1) {
                    MsgLogger.LOG.warnCommaInName(resourceTypeSetName);
                }
                resourceTypeSetDmrMap.put(resourceTypeSetName, resourceTypeSet);
                resourceTypeSet.name = resourceTypeSetName;
                ModelNode resourceTypeSetValueNode = resourceTypeSetProperty.getValue();
                resourceTypeSet.enabled = getBoolean(resourceTypeSetValueNode, context,
                        DMRResourceTypeSetDefinition.ENABLED);
                if (resourceTypeSetValueNode.hasDefined(DMRResourceTypeDefinition.RESOURCE_TYPE)) {
                    List<Property> resourceTypesList = resourceTypeSetValueNode.get(
                            DMRResourceTypeDefinition.RESOURCE_TYPE).asPropertyList();
                    for (Property resourceTypeProperty : resourceTypesList) {
                        ModelNode resourceTypeValueNode = resourceTypeProperty.getValue();

                        ResourceTypeDMR resourceType = new ResourceTypeDMR();
                        String resourceTypeName = resourceTypeProperty.getName();
                        resourceTypeSet.resourceTypeDmrMap.put(resourceTypeName, resourceType);
                        resourceType.name = resourceTypeName;
                        resourceType.path = getString(resourceTypeValueNode, context, DMRResourceTypeDefinition.PATH);
                        resourceType.parents = getListFromString(resourceTypeValueNode, context,
                                DMRResourceTypeDefinition.PARENTS);

                        List<String> metricSets = getListFromString(resourceTypeValueNode, context,
                                DMRResourceTypeDefinition.METRIC_SETS);
                        List<String> availSets = getListFromString(resourceTypeValueNode, context,
                                DMRResourceTypeDefinition.AVAIL_SETS);

                        // verify that the metric sets and avail sets exist
                        for (String metricSetName : metricSets) {
                            if (!metricSetDmrMap.containsKey(metricSetName)) {
                                MsgLogger.LOG.warnMetricSetDoesNotExist(resourceTypeName, metricSetName);
                            }
                        }
                        for (String availSetName : availSets) {
                            if (!availSetDmrMap.containsKey(availSetName)) {
                                MsgLogger.LOG.warnAvailSetDoesNotExist(resourceTypeName, availSetName);
                            }
                        }

                        resourceType.metricSets = metricSets;
                        resourceType.availSets = availSets;
                    }

                    if (resourceTypeSet.enabled && !resourceTypeSet.resourceTypeDmrMap.isEmpty()) {
                        hasEnabledResourceTypes = true;
                    }
                }
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
                    List<String> resourceTypeSets = getListFromString(remoteDMRValueNode, context,
                            RemoteDMRDefinition.RESOURCE_TYPE_SETS);

                    // verify that the resource type sets exist
                    for (String resourceTypeSetName : resourceTypeSets) {
                        if (!resourceTypeSetDmrMap.containsKey(resourceTypeSetName)) {
                            MsgLogger.LOG.warnResourceTypeSetDoesNotExist(name, resourceTypeSetName);
                        }
                    }

                    RemoteDMRManagedServer res = new RemoteDMRManagedServer();
                    res.name = name;
                    res.enabled = enabled;
                    res.host = host;
                    res.port = port;
                    res.username = username;
                    res.password = password;
                    res.resourceTypeSets.addAll(resourceTypeSets);
                    managedServersMap.put(name, res);
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
                List<String> resourceTypeSets = getListFromString(localDMRValueNode, context,
                        LocalDMRDefinition.RESOURCE_TYPE_SETS);

                // verify that the metric sets and avail sets exist
                for (String resourceTypeSetName : resourceTypeSets) {
                    if (!resourceTypeSetDmrMap.containsKey(resourceTypeSetName)) {
                        MsgLogger.LOG.warnResourceTypeSetDoesNotExist(name, resourceTypeSetName);
                    }
                }

                LocalDMRManagedServer res = new LocalDMRManagedServer();
                res.name = name;
                res.enabled = enabled;
                res.resourceTypeSets.addAll(resourceTypeSets);
                managedServersMap.put(name, res);
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

    private List<String> getListFromString(ModelNode modelNode, OperationContext context,
            SimpleAttributeDefinition attrib) throws OperationFailedException {
        ModelNode value = attrib.resolveModelAttribute(context, modelNode);
        if (value.isDefined()) {
            String commaSeparatedList = value.asString();
            String[] array = commaSeparatedList.split(",");
            return Arrays.asList(array);
        } else {
            return Collections.emptyList();
        }
    }

    public static class StorageAdapter {
        public SchedulerConfiguration.StorageReportTo type;
        public String url;
        public String context;
        public String restContext;
        public String user;
        public String password;
    }

    public static class Diagnostics {
        public SchedulerConfiguration.DiagnosticsReportTo reportTo;
        public boolean enabled;
        public int interval;
        public TimeUnit timeUnits;
    }

    public static class MetricDMR {
        public String name;
        public String path;
        public String attribute;
        public int interval;
        public TimeUnit timeUnits;
        public MeasurementUnit metricUnits;
    }

    public static class MetricSetDMR {
        public String name;
        public boolean enabled;
        public Map<String, MetricDMR> metricDmrMap = new HashMap<>();
    }

    public static class AvailDMR {
        public String name;
        public String path;
        public String attribute;
        public int interval;
        public TimeUnit timeUnits;
        public String upRegex;
    }

    public static class AvailSetDMR {
        public String name;
        public boolean enabled;
        public Map<String, AvailDMR> availDmrMap = new HashMap<>();
    }

    public static class ResourceTypeDMR extends NamedObject {
        public String name;
        public String path;
        public List<String> parents;
        public List<String> metricSets = new ArrayList<>();
        public List<String> availSets = new ArrayList<>();

        @Override
        public String getName() {
            return name;
        }
    }

    public static class ResourceTypeSetDMR {
        public String name;
        public boolean enabled;
        public Map<String, ResourceTypeDMR> resourceTypeDmrMap = new HashMap<>();
    }

    public static class ManagedServer {
        public String name;
        public boolean enabled;
        public List<String> resourceTypeSets = new ArrayList<>();
    }

    public static class LocalDMRManagedServer extends ManagedServer {
    }

    public static class RemoteDMRManagedServer extends ManagedServer {
        public String host;
        public int port;
        public String username;
        public String password;
    }

    public abstract static class NamedObject {
        public abstract String getName();

        @Override
        public boolean equals(Object obj) {
            if (this == obj) {
                return true;
            }

            if (obj == null) {
                return false;
            }

            if (!(obj instanceof NamedObject)) {
                return false;
            }
            String thisName = getName();
            String thatName = ((NamedObject) obj).getName();
            if (thisName == null) {
                return thatName == null;
            }
            return thisName.equals(thatName);
        }

        @Override
        public int hashCode() {
            String n = getName();
            return (n != null) ? n.hashCode() : 0;
        }

        @Override
        public String toString() {
            return getName();
        }
    }
}
