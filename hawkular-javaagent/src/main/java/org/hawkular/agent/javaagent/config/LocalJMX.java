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

import org.hawkular.agent.javaagent.config.StringExpression.StringValue;
import org.hawkular.agent.monitor.api.Avail;

import com.fasterxml.jackson.annotation.JsonAutoDetect;
import com.fasterxml.jackson.annotation.JsonAutoDetect.Visibility;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonAutoDetect( //
        fieldVisibility = Visibility.NONE, //
        getterVisibility = Visibility.NONE, //
        setterVisibility = Visibility.NONE, //
        isGetterVisibility = Visibility.NONE)
public class LocalJMX implements Validatable {

    @JsonProperty(required = true)
    private String name;

    @JsonProperty
    private BooleanExpression enabled = new BooleanExpression(Boolean.TRUE);

    @JsonProperty("tenant-id")
    private StringExpression tenantId;

    @JsonProperty("resource-type-sets")
    private String[] resourceTypeSets;

    @JsonProperty("metric-id-template")
    private String metricIdTemplate;

    @JsonProperty("metric-tags")
    private Map<String, String> metricTags;

    @JsonProperty("set-avail-on-shutdown")
    private Avail setAvailOnShutdown;

    @JsonProperty("mbean-server-name")
    private String mbeanServerName;

    public LocalJMX() {
    }

    public LocalJMX(LocalJMX original) {
        this.name = original.name;
        this.enabled = original.enabled == null ? null : new BooleanExpression(original.enabled);
        this.tenantId = original.tenantId == null ? null : new StringExpression(original.tenantId);
        this.resourceTypeSets = original.resourceTypeSets == null ? null
                : Arrays.copyOf(original.resourceTypeSets, original.resourceTypeSets.length);
        this.metricIdTemplate = original.metricIdTemplate;
        this.metricTags = original.metricTags == null ? null : new HashMap<>(original.metricTags);
        this.setAvailOnShutdown = original.setAvailOnShutdown;
        this.mbeanServerName = original.mbeanServerName;
    }

    @Override
    public void validate() throws Exception {
        if (name == null || name.trim().isEmpty()) {
            throw new Exception("local-jmx name must be specified");
        }
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public Boolean getEnabled() {
        return enabled == null ? null : enabled.get();
    }

    public void setEnabled(Boolean enabled) {
        if (this.enabled != null) {
            this.enabled.set(enabled);
        } else {
            this.enabled = new BooleanExpression(enabled);
        }
    }

    public String getTenantId() {
        return tenantId == null ? null : tenantId.get().toString();
    }

    public void setTenantId(String tenantId) {
        if (this.tenantId != null) {
            this.tenantId.set(new StringValue(tenantId));
        } else {
            this.tenantId = new StringExpression(tenantId);
        }
    }

    public String[] getResourceTypeSets() {
        return resourceTypeSets;
    }

    public void setResourceTypeSets(String[] resourceTypeSets) {
        this.resourceTypeSets = resourceTypeSets;
    }

    public String getMetricIdTemplate() {
        return metricIdTemplate;
    }

    public void setMetricIdTemplate(String metricIdTemplate) {
        this.metricIdTemplate = metricIdTemplate;
    }

    public Map<String, String> getMetricTags() {
        return metricTags;
    }

    public void setMetricTags(Map<String, String> metricTags) {
        this.metricTags = metricTags;
    }

    public Avail getSetAvailOnShutdown() {
        return setAvailOnShutdown;
    }

    public void setSetAvailOnShutdown(Avail setAvailOnShutdown) {
        this.setAvailOnShutdown = setAvailOnShutdown;
    }

    public String getMbeanServerName() {
        return mbeanServerName;
    }

    public void setMbeanServerName(String mbeanServerName) {
        this.mbeanServerName = mbeanServerName;
    }
}
