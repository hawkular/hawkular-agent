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

import java.util.HashMap;
import java.util.Map;

import javax.management.ObjectName;

import org.hawkular.agent.monitor.inventory.SupportedMetricType;
import org.hawkular.inventory.api.model.MetricUnit;

import com.fasterxml.jackson.annotation.JsonAutoDetect;
import com.fasterxml.jackson.annotation.JsonAutoDetect.Visibility;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonAutoDetect( //
        fieldVisibility = Visibility.NONE, //
        getterVisibility = Visibility.NONE, //
        setterVisibility = Visibility.NONE, //
        isGetterVisibility = Visibility.NONE)
public class JMXMetric implements Validatable {

    @JsonProperty(required = true)
    private String name;

    @JsonProperty("object-name")
    private String objectName;

    @JsonProperty(required = true)
    private String attribute;

    @JsonProperty("metric-units")
    private MetricUnitJsonProperty metricUnits = new MetricUnitJsonProperty(MetricUnit.NONE);

    @JsonProperty("metric-type")
    private MetricTypeJsonProperty metricType = new MetricTypeJsonProperty(SupportedMetricType.GAUGE);

    @JsonProperty("metric-family")
    private String metricFamily;

    @JsonProperty("metric-labels")
    private Map<String, String> metricLabels;

    @JsonProperty("metric-expression")
    private String metricExpression;

    public JMXMetric() {
    }

    public JMXMetric(JMXMetric original) {
        this.name = original.name;
        this.objectName = original.objectName;
        this.attribute = original.attribute;
        this.metricUnits = original.metricUnits == null ? null : new MetricUnitJsonProperty(original.metricUnits);
        this.metricType = original.metricType == null ? null : new MetricTypeJsonProperty(original.metricType);
        this.metricFamily = original.metricFamily;
        this.metricLabels = original.metricLabels == null ? null : new HashMap<>(original.metricLabels);
        this.metricExpression = original.metricExpression;
    }

    @Override
    public void validate() throws Exception {
        if (name == null || name.trim().isEmpty()) {
            throw new Exception("metric-jmx name must be specified");
        }

        if (metricFamily == null || metricFamily.trim().isEmpty()) {
            throw new Exception("metric-jmx [" + name + "] metric-family must be specified");
        }

        if (attribute == null || attribute.trim().isEmpty()) {
            throw new Exception("metric-jmx [" + name + "] attribute must be specified");
        }

        if (objectName != null) {
            try {
                new ObjectName(objectName);
            } catch (Exception e) {
                throw new Exception("metric-jmx [" + name + "] object-name [" + objectName + "] is invalid", e);
            }
        }
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getObjectName() {
        return objectName;
    }

    public void setObjectName(String objectName) {
        this.objectName = objectName;
    }

    public String getAttribute() {
        return attribute;
    }

    public void setAttribute(String attribute) {
        this.attribute = attribute;
    }

    public MetricUnit getMetricUnits() {
        return metricUnits == null ? null : metricUnits.get();
    }

    public void setMetricUnits(MetricUnit mu) {
        if (metricUnits != null) {
            metricUnits.set(mu);
        } else {
            metricUnits = new MetricUnitJsonProperty(mu);
        }
    }

    public SupportedMetricType getMetricType() {
        return metricType == null ? null : metricType.get();
    }

    public void setMetricType(SupportedMetricType mt) {
        if (metricType != null) {
            metricType.set(mt);
        } else {
            metricType = new MetricTypeJsonProperty(mt);
        }
    }

    public String getMetricFamily() {
        return metricFamily;
    }

    public void setMetricFamily(String metricFamily) {
        this.metricFamily = metricFamily;
    }

    public Map<String, String> getMetricLabels() {
        return metricLabels;
    }

    public void setMetricLabels(Map<String, String> metricLabels) {
        this.metricLabels = metricLabels;
    }

    public String getMetricExpression() {
        return metricExpression;
    }

    public void setMetricExpression(String metricExpression) {
        this.metricExpression = metricExpression;
    }
}
