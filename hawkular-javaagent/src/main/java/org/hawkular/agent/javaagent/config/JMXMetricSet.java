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

import org.hawkular.agent.javaagent.Util;

import com.fasterxml.jackson.annotation.JsonProperty;

public class JMXMetricSet implements Validatable {

    @JsonProperty(required = true)
    public String name;

    @JsonProperty
    public Boolean enabled = Boolean.TRUE;

    @JsonProperty("metric-jmx")
    public JMXMetric[] jmxMetrics;

    public JMXMetricSet() {
    }

    public JMXMetricSet(JMXMetricSet original) {
        this.name = original.name;
        this.enabled = original.enabled;
        this.jmxMetrics = Util.cloneArray(original.jmxMetrics);
    }

    @Override
    public void validate() throws Exception {
        if (name == null) {
            throw new Exception("metric-set-jmx name must be specified");
        }

        if (jmxMetrics != null) {
            for (JMXMetric o : jmxMetrics) {
                o.validate();
            }
        }
    }
}
