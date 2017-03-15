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

import javax.management.ObjectName;

import org.hawkular.agent.javaagent.Util;

import com.fasterxml.jackson.annotation.JsonProperty;

public class JMXResourceType implements Validatable {

    @JsonProperty(required = true)
    public String name;

    @JsonProperty(value = "object-name", required = true)
    public String objectName;

    @JsonProperty(value = "resource-name-template", required = true)
    public String resourceNameTemplate;

    @JsonProperty
    public String[] parents;

    @JsonProperty("metric-sets")
    public String[] metricSets;

    @JsonProperty("avail-sets")
    public String[] availSets;

    @JsonProperty("resource-config-jmx")
    public JMXResourceConfig[] jmxResourceConfigs;

    @JsonProperty("operation-jmx")
    public JMXOperation[] jmxOperations;

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
        this.availSets = original.availSets == null ? null
                : Arrays.copyOf(original.availSets, original.availSets.length);
        this.jmxResourceConfigs = Util.cloneArray(original.jmxResourceConfigs);
        this.jmxOperations = Util.cloneArray(original.jmxOperations);
    }

    @Override
    public void validate() throws Exception {
        if (name == null) {
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

        if (resourceNameTemplate == null) {
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
    }
}
