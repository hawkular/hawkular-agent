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
    public String metricsJndi;
    public int numSchedulerThreads;
    public StorageAdapter storageAdapter = new StorageAdapter();
    public Diagnostics diagnostics = new Diagnostics();
    public Map<String, MetricSet> metricSets = new HashMap<>();

    public MonitorServiceConfiguration(ModelNode config, OperationContext context) throws OperationFailedException {
        determineGlobalConfig(config, context);
        determineStorageAdapterConfig(config, context);
        determineDiagnosticsConfig(config, context);
        boolean hasEnabledMetrics = determineMetricSets(config, context);
        if (!hasEnabledMetrics) {
            MsgLogger.LOG.warnNoEnabledMetricsConfigured();
            subsystemEnabled = false; // no sense enabling subsystem if there is nothing to be monitored
        }
        return;
    }

    private boolean determineMetricSets(ModelNode config, OperationContext context) throws OperationFailedException {
        boolean hasEnabledMetrics = false;

        if (config.hasDefined(MetricSetDefinition.METRIC_SET)) {
            List<Property> metricSetsList = config.get(MetricSetDefinition.METRIC_SET).asPropertyList();
            for (Property metricSetProperty : metricSetsList) {
                MetricSet metricSet = new MetricSet();
                String metricSetName = metricSetProperty.getName();
                metricSets.put(metricSetName, metricSet);
                metricSet.name = metricSetName;
                ModelNode metricSetValueNode = metricSetProperty.getValue();
                metricSet.enabled = getBoolean(metricSetValueNode, context, MetricSetDefinition.ENABLED);
                if (metricSetValueNode.hasDefined(MetricDefinition.METRIC)) {
                    List<Property> metricsList = metricSetValueNode.get(MetricDefinition.METRIC).asPropertyList();
                    for (Property metricProperty : metricsList) {
                        Metric metric = new Metric();
                        String metricName = metricProperty.getName();
                        metricSet.metrics.put(metricName, metric);
                        metric.name = metricName;
                        ModelNode metricValueNode = metricProperty.getValue();
                        metric.resource = getString(metricValueNode, context, MetricDefinition.RESOURCE);
                        metric.attribute = getString(metricValueNode, context, MetricDefinition.ATTRIBUTE);
                        metric.interval = getInt(metricValueNode, context, MetricDefinition.INTERVAL);
                        String metricTimeUnitsStr = getString(metricValueNode, context, MetricDefinition.TIME_UNITS);
                        metric.timeUnits = TimeUnit.valueOf(metricTimeUnitsStr.toUpperCase());
                    }
                    if (metricSet.enabled && !metricSet.metrics.isEmpty()) {
                        hasEnabledMetrics = true;
                    }
                }
            }
        }

        return hasEnabledMetrics;
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
        storageAdapter.user = getString(storageAdapterConfig, context, StorageDefinition.USER);
        storageAdapter.password = getString(storageAdapterConfig, context, StorageDefinition.PASSWORD);
        String typeStr = getString(storageAdapterConfig, context, StorageDefinition.TYPE);
        storageAdapter.type = SchedulerConfiguration.StorageReportTo.valueOf(typeStr.toUpperCase());
    }

    private void determineGlobalConfig(ModelNode config, OperationContext context) throws OperationFailedException {
        subsystemEnabled = getBoolean(config, context, SubsystemDefinition.ENABLED);
        metricsJndi = getString(config, context, SubsystemDefinition.METRICS_JNDI);
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
        public String user;
        public String password;
    }

    public class Diagnostics {
        public SchedulerConfiguration.DiagnosticsReportTo reportTo;
        public boolean enabled;
        public int interval;
        public TimeUnit timeUnits;
    }

    public class Metric {
        public String name;
        public String resource;
        public String attribute;
        public int interval;
        public TimeUnit timeUnits;
    }

    public class MetricSet {
        public String name;
        public boolean enabled;
        public Map<String, Metric> metrics = new HashMap<>();
    }
}
