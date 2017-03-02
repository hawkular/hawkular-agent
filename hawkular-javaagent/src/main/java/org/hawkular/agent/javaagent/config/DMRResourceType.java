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

import org.hawkular.agent.javaagent.Util;
import org.hawkular.agent.monitor.util.WildflyCompatibilityUtils;

import com.fasterxml.jackson.annotation.JsonProperty;

public class DMRResourceType implements Validatable {

    @JsonProperty(required = true)
    public String name;

    @JsonProperty
    public String path = "/";

    @JsonProperty(value = "resource-name-template", required = true)
    public String resourceNameTemplate;

    @JsonProperty
    public String[] parents;

    @JsonProperty("metric-sets")
    public String[] metricSets;

    @JsonProperty("avail-sets")
    public String[] availSets;

    @JsonProperty("resource-config-dmr")
    public DMRResourceConfig[] dmrResourceConfigs;

    @JsonProperty("operation-dmr")
    public DMROperation[] dmrOperations;

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
        this.availSets = original.availSets == null ? null
                : Arrays.copyOf(original.availSets, original.availSets.length);
        this.dmrResourceConfigs = Util.cloneArray(original.dmrResourceConfigs);
        this.dmrOperations = Util.cloneArray(original.dmrOperations);
    }

    @Override
    public void validate() throws Exception {
        if (name == null) {
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
    }
}
