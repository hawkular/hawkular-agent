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

import org.hawkular.agent.javaagent.Util;
import org.hawkular.agent.monitor.util.WildflyCompatibilityUtils;

import com.fasterxml.jackson.annotation.JsonAutoDetect;
import com.fasterxml.jackson.annotation.JsonAutoDetect.Visibility;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonAutoDetect( //
        fieldVisibility = Visibility.NONE, //
        getterVisibility = Visibility.NONE, //
        setterVisibility = Visibility.NONE, //
        isGetterVisibility = Visibility.NONE)
public class DMRResourceType implements Validatable {

    @JsonProperty(required = true)
    private String name;

    @JsonProperty
    private String path = "/";

    @JsonProperty(value = "resource-name-template", required = true)
    private String resourceNameTemplate;

    @JsonProperty
    private String[] parents;

    @JsonProperty("metric-sets")
    private String[] metricSets;

    @JsonProperty("notification-dmr")
    private DMRNotification[] dmrNotifications;

    @JsonProperty("resource-config-dmr")
    private DMRResourceConfig[] dmrResourceConfigs;

    @JsonProperty("operation-dmr")
    private DMROperation[] dmrOperations;

    @JsonProperty("metric-labels")
    private Map<String, String> metricLabels;

    public DMRResourceType() {
    }

    public DMRResourceType(DMRResourceType original) {
        this.name = original.name;
        this.path = original.path;
        this.resourceNameTemplate = original.resourceNameTemplate;
        this.parents = original.parents == null ? null
                : Arrays.copyOf(original.parents, original.parents.length);
        this.metricSets = original.metricSets == null ? null
                : Arrays.copyOf(original.metricSets, original.metricSets.length);
        this.dmrNotifications = original.dmrNotifications == null ? null
                : Arrays.copyOf(original.dmrNotifications, original.dmrNotifications.length);
        this.dmrResourceConfigs = Util.cloneArray(original.dmrResourceConfigs);
        this.dmrOperations = Util.cloneArray(original.dmrOperations);
        this.metricLabels = original.metricLabels == null ? null : new HashMap<>(original.metricLabels);
    }

    @Override
    public void validate() throws Exception {
        if (name == null || name.trim().isEmpty()) {
            throw new Exception("resource-type-dmr name must be specified");
        }

        try {
            if (!"/".equals(path)) {
                WildflyCompatibilityUtils.parseCLIStyleAddress(path);
            }
        } catch (Exception e) {
            throw new Exception("resource-type-dmr [" + name + "] path [" + path + "] is invalid", e);
        }

        if (dmrResourceConfigs != null) {
            for (DMRResourceConfig o : dmrResourceConfigs) {
                o.validate();
            }
        }

        if (dmrOperations != null) {
            for (DMROperation o : dmrOperations) {
                o.validate();
            }
        }

        if (dmrNotifications != null) {
            for (DMRNotification o : dmrNotifications) {
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

    public String getPath() {
        return path;
    }

    public void setPath(String path) {
        this.path = path;
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

    public DMRResourceConfig[] getDmrResourceConfigs() {
        return dmrResourceConfigs;
    }

    public void setDmrResourceConfigs(DMRResourceConfig[] dmrResourceConfigs) {
        this.dmrResourceConfigs = dmrResourceConfigs;
    }

    public DMROperation[] getDmrOperations() {
        return dmrOperations;
    }

    public void setDmrOperations(DMROperation[] dmrOperations) {
        this.dmrOperations = dmrOperations;
    }

    public DMRNotification[] getDmrNotifications() {
        return dmrNotifications;
    }

    public void setDmrNotifications(DMRNotification[] dmrNotifications) {
        this.dmrNotifications = dmrNotifications;
    }

    public Map<String, String> getMetricLabels() {
        return metricLabels;
    }

    public void setMetricLabels(Map<String, String> metricLabels) {
        this.metricLabels = metricLabels;
    }
}
