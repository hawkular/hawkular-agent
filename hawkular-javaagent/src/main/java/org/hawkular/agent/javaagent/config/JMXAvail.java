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

import java.util.HashMap;
import java.util.Map;

import javax.management.ObjectName;

import com.fasterxml.jackson.annotation.JsonProperty;

public class JMXAvail implements Validatable {

    @JsonProperty(required = true)
    public String name;

    @JsonProperty("object-name")
    public String objectName;

    @JsonProperty(required = true)
    public String attribute;

    @JsonProperty("up-regex")
    public String upRegex;

    @JsonProperty
    public Integer interval = 5;

    @JsonProperty("time-units")
    public TimeUnits timeUnits = TimeUnits.minutes;

    @JsonProperty("metric-id-template")
    public String metricIdTemplate;

    @JsonProperty("metric-tags")
    public Map<String, String> metricTags;

    public JMXAvail() {
    }

    public JMXAvail(JMXAvail original) {
        this.name = original.name;
        this.objectName = original.objectName;
        this.attribute = original.attribute;
        this.upRegex = original.upRegex;
        this.interval = original.interval;
        this.timeUnits = original.timeUnits;
        this.metricIdTemplate = original.metricIdTemplate;
        this.metricTags = original.metricTags == null ? null : new HashMap<>(original.metricTags);
    }

    @Override
    public void validate() throws Exception {
        if (name == null) {
            throw new Exception("avail-jmx name must be specified");
        }

        if (attribute == null) {
            throw new Exception("avail-jmx [" + name + "] attribute must be specified");
        }

        if (interval == null || interval.intValue() < 0) {
            throw new Exception("avail-jmx [" + name + "] interval must be greater than or equal to 0");
        }

        if (objectName != null) {
            try {
                new ObjectName(objectName);
            } catch (Exception e) {
                throw new Exception("avail-jmx [" + name + "] object-name [" + objectName + "] is invalid", e);
            }
        }
    }
}
