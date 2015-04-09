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

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import org.hawkular.agent.monitor.log.MsgLogger;
import org.hawkular.agent.monitor.scheduler.config.SchedulerConfiguration;
import org.jboss.as.controller.OperationContext;
import org.jboss.as.controller.OperationFailedException;
import org.jboss.as.controller.SimpleAttributeDefinition;
import org.jboss.dmr.ModelNode;
import org.jboss.dmr.Property;

public class MonitorServiceConfiguration {

    public boolean subsystemEnabled;
    public String apiJndi;
    public int numSchedulerThreads;
    public StorageAdapter storageAdapter = new StorageAdapter();
    public Diagnostics diagnostics = new Diagnostics();
    public Map<String, MetricSetDMR> metricSetDmrMap = new HashMap<>();
    public Map<String, AvailSetDMR> availSetDmrMap = new HashMap<>();

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
        return;
    }

    private boolean determineMetricSetDmr(ModelNode config, OperationContext context) throws OperationFailedException {
        boolean hasEnabledMetrics = false;

        if (config.hasDefined(DMRMetricSetDefinition.METRIC_SET)) {
            List<Property> metricSetsList = config.get(DMRMetricSetDefinition.METRIC_SET).asPropertyList();
            for (Property metricSetProperty : metricSetsList) {
                MetricSetDMR metricSet = new MetricSetDMR();
                String metricSetName = metricSetProperty.getName();
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
                        metric.resource = getString(metricValueNode, context, DMRMetricDefinition.RESOURCE);
                        metric.attribute = getString(metricValueNode, context, DMRMetricDefinition.ATTRIBUTE);
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
                        avail.resource = getString(availValueNode, context, DMRAvailDefinition.RESOURCE);
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
        numSchedulerThreads = getInt(config, context, SubsystemDefinition.NUM_SCHEDULER_THREADS);
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

    public class StorageAdapter {
        public SchedulerConfiguration.StorageReportTo type;
        public String url;
        public String context;
        public String restContext;
        public String user;
        public String password;
    }

    public class Diagnostics {
        public SchedulerConfiguration.DiagnosticsReportTo reportTo;
        public boolean enabled;
        public int interval;
        public TimeUnit timeUnits;
    }

    public class MetricDMR {
        public String name;
        public String resource;
        public String attribute;
        public int interval;
        public TimeUnit timeUnits;
    }

    public class MetricSetDMR {
        public String name;
        public boolean enabled;
        public Map<String, MetricDMR> metricDmrMap = new HashMap<>();
    }

    public class AvailDMR {
        public String name;
        public String resource;
        public String attribute;
        public int interval;
        public TimeUnit timeUnits;
        public String upRegex;
    }

    public class AvailSetDMR {
        public String name;
        public boolean enabled;
        public Map<String, AvailDMR> availDmrMap = new HashMap<>();
    }
}
