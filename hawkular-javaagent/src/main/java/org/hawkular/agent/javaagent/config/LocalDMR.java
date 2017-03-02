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

import org.hawkular.agent.monitor.api.Avail;

import com.fasterxml.jackson.annotation.JsonProperty;

public class LocalDMR implements Validatable {

    @JsonProperty(required = true)
    public String name;

    @JsonProperty
    public Boolean enabled = Boolean.TRUE;

    @JsonProperty("tenant-id")
    public String tenantId;

    @JsonProperty("resource-type-sets")
    public String[] resourceTypeSets;

    @JsonProperty("metric-id-template")
    public String metricIdTemplate;

    @JsonProperty("metric-tags")
    public Map<String, String> metricTags;

    @JsonProperty("set-avail-on-shutdown")
    public Avail setAvailOnShutdown;

    public LocalDMR() {
    }

    public LocalDMR(LocalDMR original) {
        this.name = original.name;
        this.enabled = original.enabled;
        this.tenantId = original.tenantId;
        this.resourceTypeSets = original.resourceTypeSets == null ? null
                : Arrays.copyOf(original.resourceTypeSets, original.resourceTypeSets.length);
        this.metricIdTemplate = original.metricIdTemplate;
        this.metricTags = original.metricTags == null ? null : new HashMap<>(original.metricTags);
        this.setAvailOnShutdown = original.setAvailOnShutdown;
    }

    @Override
    public void validate() throws Exception {
        if (name == null) {
            throw new Exception("local-dmr name must be specified");
        }
    }
}
