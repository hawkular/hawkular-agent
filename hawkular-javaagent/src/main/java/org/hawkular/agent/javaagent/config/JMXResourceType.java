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

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

import javax.management.ObjectName;

import org.hawkular.agent.javaagent.Util;

import com.fasterxml.jackson.annotation.JsonAutoDetect;
import com.fasterxml.jackson.annotation.JsonAutoDetect.Visibility;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonAutoDetect( //
        fieldVisibility = Visibility.NONE, //
        getterVisibility = Visibility.NONE, //
        setterVisibility = Visibility.NONE, //
        isGetterVisibility = Visibility.NONE)
public class JMXResourceType implements Validatable {

    @JsonProperty(required = true)
    private String name;

    @JsonProperty(value = "object-name", required = true)
    private String objectName;

    @JsonProperty(value = "resource-name-template", required = true)
    private String resourceNameTemplate;

    @JsonProperty
    private String[] parents;

    @JsonProperty("metric-sets")
    private String[] metricSets;

    @JsonProperty("resource-config-jmx")
    private JMXResourceConfig[] jmxResourceConfigs;

    @JsonProperty("operation-jmx")
    private JMXOperation[] jmxOperations;

    @JsonProperty("metric-labels")
    private Map<String, String> metricLabels;

    public JMXResourceType() {
    }

    public JMXResourceType(JMXResourceType original) {
        this.name = original.name;
        this.objectName = original.objectName;
        this.resourceNameTemplate = original.resourceNameTemplate;
        this.parents = original.parents == null ? null
                : Arrays.copyOf(original.parents, original.parents.length);
        this.metricSets = original.metricSets == null ? null
                : Arrays.copyOf(original.metricSets, original.metricSets.length);
        this.jmxResourceConfigs = Util.cloneArray(original.jmxResourceConfigs);
        this.jmxOperations = Util.cloneArray(original.jmxOperations);
        this.metricLabels = original.metricLabels == null ? null : new HashMap<>(original.metricLabels);
    }

    @Override
    public void validate() throws Exception {
        if (name == null || name.trim().isEmpty()) {
            throw new Exception("resource-type-jmx name must be specified");
        }

        if (objectName == null) {
            throw new Exception("resource-type-jmx [" + name + "] object-name must be specified");
        }

        if (objectName != null) {
            try {
                new ObjectName(objectName);
            } catch (Exception e) {
                throw new Exception("resource-type-jmx [" + name + "] object-name [" + objectName + "] is invalid",
                        e);
            }
        }

        if (resourceNameTemplate == null || resourceNameTemplate.trim().isEmpty()) {
            throw new Exception("resource-type-jmx [" + name + "] resource-name-template must be specified");
        }

        if (jmxResourceConfigs != null) {
            for (JMXResourceConfig o : jmxResourceConfigs) {
                o.validate();
            }
        }

        if (jmxOperations != null) {
            for (JMXOperation o : jmxOperations) {
                o.validate();
            }
        }

        if (metricLabels == null) {
            metricLabels = new HashMap<>(0);
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

    public String getResourceNameTemplate() {
        return resourceNameTemplate;
    }

    public void setResourceNameTemplate(String resourceNameTemplate) {
        this.resourceNameTemplate = resourceNameTemplate;
    }

    public String[] getParents() {
        return parents;
    }

    public void setParents(String[] parents) {
        this.parents = parents;
    }

    public String[] getMetricSets() {
        return metricSets;
    }

    public void setMetricSets(String[] metricSets) {
        this.metricSets = metricSets;
    }

    public JMXResourceConfig[] getJmxResourceConfigs() {
        return jmxResourceConfigs;
    }

    public void setJmxResourceConfigs(JMXResourceConfig[] jmxResourceConfigs) {
        this.jmxResourceConfigs = jmxResourceConfigs;
    }

    public JMXOperation[] getJmxOperations() {
        return jmxOperations;
    }

    public void setJmxOperations(JMXOperation[] jmxOperations) {
        this.jmxOperations = jmxOperations;
    }

    public Map<String, String> getMetricLabels() {
        return metricLabels;
    }

    public void setMetricLabels(Map<String, String> metricLabels) {
        this.metricLabels = metricLabels;
    }
}
