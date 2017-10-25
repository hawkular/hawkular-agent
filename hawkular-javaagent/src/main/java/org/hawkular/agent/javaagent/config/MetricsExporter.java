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

import org.hawkular.agent.javaagent.config.StringExpression.StringValue;

import com.fasterxml.jackson.annotation.JsonAutoDetect;
import com.fasterxml.jackson.annotation.JsonAutoDetect.Visibility;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonAutoDetect( //
        fieldVisibility = Visibility.NONE, //
        getterVisibility = Visibility.NONE, //
        setterVisibility = Visibility.NONE, //
        isGetterVisibility = Visibility.NONE)
public class MetricsExporter implements Validatable {

    @JsonProperty
    private BooleanExpression enabled = new BooleanExpression(Boolean.FALSE);

    @JsonProperty
    private StringExpression host = new StringExpression("127.0.0.1");

    @JsonProperty
    private IntegerExpression port = new IntegerExpression(9779);

    @JsonProperty("config-file")
    private StringExpression configFile = new StringExpression("");

    public MetricsExporter() {
    }

    public MetricsExporter(MetricsExporter original) {
        this.enabled = original.enabled == null ? null : new BooleanExpression(original.enabled);
        this.host = original.host == null ? null : new StringExpression(original.host);
        this.port = original.port == null ? null : new IntegerExpression(original.port);
        this.configFile = original.configFile == null ? null : new StringExpression(original.configFile);
    }

    @Override
    public void validate() throws Exception {
        if (enabled != null && enabled.get()) {
            if (host == null || host.get().toString().trim().isEmpty()) {
                throw new Exception("metrics-exporter host must be specified");
            }
            if (port == null || port.get() <= 0) {
                throw new Exception("metrics-exporter port must be specified");
            }
            if (configFile == null || configFile.get().toString().trim().isEmpty()) {
                throw new Exception("metrics-exporter config-file must be specified");
            }
        }
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

    public String getConfigFile() {
        return configFile == null ? null : configFile.get().toString();
    }

    public void setConfigFile(String configFile) {
        if (this.configFile != null) {
            this.configFile.set(new StringValue(configFile));
        } else {
            this.configFile = new StringExpression(new StringValue(configFile));
        }
    }

}
