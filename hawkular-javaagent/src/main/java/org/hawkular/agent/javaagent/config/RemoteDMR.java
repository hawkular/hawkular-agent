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
public class RemoteDMR implements Validatable {

    @JsonProperty(required = true)
    private String name;

    @JsonProperty
    private BooleanExpression enabled = new BooleanExpression(Boolean.TRUE);

    @JsonProperty
    private StringExpression protocol;

    @JsonProperty(required = true)
    private StringExpression host;

    @JsonProperty(required = true)
    private IntegerExpression port;

    @JsonProperty
    private StringExpression username;

    @JsonProperty
    private StringExpression password;

    @JsonProperty("use-ssl")
    private Boolean useSsl = Boolean.FALSE;

    @JsonProperty("security-realm")
    private String securityRealmName;

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

    public RemoteDMR() {
    }

    public RemoteDMR(RemoteDMR original) {
        this.name = original.name;
        this.enabled = original.enabled == null ? null : new BooleanExpression(original.enabled);
        this.protocol = original.protocol == null ? null : new StringExpression(original.protocol);
        this.host = original.host == null ? null : new StringExpression(original.host);
        this.port = original.port == null ? null : new IntegerExpression(original.port);
        this.username = original.username == null ? null : new StringExpression(original.username);
        this.password = original.password == null ? null : new StringExpression(original.password);
        this.useSsl = original.useSsl;
        this.securityRealmName = original.securityRealmName;
        this.tenantId = original.tenantId == null ? null : new StringExpression(original.tenantId);
        this.resourceTypeSets = original.resourceTypeSets == null ? null
                : Arrays.copyOf(original.resourceTypeSets, original.resourceTypeSets.length);
        this.metricIdTemplate = original.metricIdTemplate;
        this.metricTags = original.metricTags == null ? null : new HashMap<>(original.metricTags);
        this.setAvailOnShutdown = original.setAvailOnShutdown;
    }

    @Override
    public void validate() throws Exception {
        if (name == null || name.trim().isEmpty()) {
            throw new Exception("remote-dmr name must be specified");
        }
        if (host == null || host.get().toString().trim().isEmpty()) {
            throw new Exception("remote-dmr host must be specified");
        }
        if (port == null || port.get() <= 0) {
            throw new Exception("remote-dmr port must be specified");
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

    public String getProtocol() {
        return protocol == null ? null : protocol.get().toString();
    }

    public void setProtocol(String protocol) {
        if (this.protocol != null) {
            this.protocol.set(new StringValue(protocol));
        } else {
            this.protocol = new StringExpression(new StringValue(protocol));
        }
    }

    public String getHost() {
        return host == null ? null : host.get().toString();
    }

    public void setHost(String host) {
        if (this.host != null) {
            this.host.set(new StringValue(host));
        } else {
            this.host = new StringExpression(new StringValue(host));
        }
    }

    public Integer getPort() {
        return port == null ? null : port.get();
    }

    public void setPort(Integer port) {
        if (this.port != null) {
            this.port.set(port);
        } else {
            this.port = new IntegerExpression(port);
        }
    }

    public String getUsername() {
        return username == null ? null : username.get().toString();
    }

    public void setUsername(String username) {
        if (this.username != null) {
            this.username.set(new StringValue(username));
        } else {
            this.username = new StringExpression(new StringValue(username));
        }
    }

    public String getPassword() {
        return password == null ? null : password.get().toString();
    }

    public void setPassword(String password) {
        if (this.password != null) {
            this.password.set(new StringValue(password));
        } else {
            this.password = new StringExpression(new StringValue(password));
        }
    }

    public Boolean getUseSsl() {
        return useSsl;
    }

    public void setUseSsl(Boolean useSsl) {
        this.useSsl = useSsl;
    }

    public String getSecurityRealmName() {
        return securityRealmName;
    }

    public void setSecurityRealmName(String securityRealmName) {
        this.securityRealmName = securityRealmName;
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
}
