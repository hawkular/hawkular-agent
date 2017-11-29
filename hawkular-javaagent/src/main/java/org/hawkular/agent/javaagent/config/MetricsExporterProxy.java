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

import org.hawkular.agent.javaagent.config.StringExpression.StringValue;

import com.fasterxml.jackson.annotation.JsonAutoDetect;
import com.fasterxml.jackson.annotation.JsonAutoDetect.Visibility;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonAutoDetect( //
        fieldVisibility = Visibility.NONE, //
        getterVisibility = Visibility.NONE, //
        setterVisibility = Visibility.NONE, //
        isGetterVisibility = Visibility.NONE)
public class MetricsExporterProxy implements Validatable {

    public enum Mode {
        disabled, master, slave
    };

    @JsonProperty(required = true)
    private Mode mode = Mode.disabled;

    @JsonProperty("data-dir")
    private StringExpression dataDir;

    @JsonProperty("metric-labels-expression")
    private StringExpression metricLabelsExpression = new StringExpression(
            "jboss.node.name|([^:]+)[:]?(.*)?|domain_host,domain_server");

    public MetricsExporterProxy() {
    }

    public MetricsExporterProxy(MetricsExporterProxy original) {
        this.mode = original.mode == null ? null : original.mode;
        this.dataDir = original.dataDir == null ? null : new StringExpression(original.dataDir);
        this.metricLabelsExpression = original.metricLabelsExpression == null ? null
                : new StringExpression(original.metricLabelsExpression);
    }

    @Override
    public void validate() throws Exception {
        if (mode == null) {
            throw new Exception("metrics-exporter proxy mode must be one of: " + Arrays.toString(Mode.values()));
        }

        if (mode != Mode.disabled) {
            if (dataDir == null || dataDir.get().toString().trim().isEmpty()) {
                throw new Exception("metrics-exporter proxy data-dir must be specified");
            }
        }
    }

    public Mode getMode() {
        return mode == null ? Mode.disabled : mode;
    }

    public void setMode(Mode mode) {
        this.mode = mode;
    }

    public String getDataDir() {
        return dataDir == null ? null : dataDir.get().toString();
    }

    public void setDataDir(String dataDir) {
        if (this.dataDir != null) {
            this.dataDir.set(new StringValue(dataDir));
        } else {
            this.dataDir = new StringExpression(new StringValue(dataDir));
        }
    }

    public String getMetricLabelsExpression() {
        return metricLabelsExpression == null ? null : metricLabelsExpression.get().toString();
    }

    public void setMetricLabelsExpression(String metricLabelsExpression) {
        if (this.metricLabelsExpression != null) {
            this.metricLabelsExpression.set(new StringValue(metricLabelsExpression));
        } else {
            this.metricLabelsExpression = new StringExpression(new StringValue(metricLabelsExpression));
        }
    }
}
