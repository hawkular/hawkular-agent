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

import com.fasterxml.jackson.annotation.JsonAutoDetect;
import com.fasterxml.jackson.annotation.JsonAutoDetect.Visibility;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonAutoDetect( //
        fieldVisibility = Visibility.NONE, //
        getterVisibility = Visibility.NONE, //
        setterVisibility = Visibility.NONE, //
        isGetterVisibility = Visibility.NONE)
public class JMXAvail implements Validatable {

    @JsonProperty(required = true)
    private String name;

    @JsonProperty("object-name")
    private String objectName;

    @JsonProperty(required = true)
    private String attribute;

    @JsonProperty("up-regex")
    private String upRegex;

    @JsonProperty
    private Integer interval = 5;

    @JsonProperty("time-units")
    private TimeUnits timeUnits = TimeUnits.minutes;

    @JsonProperty("metric-id-template")
    private String metricIdTemplate;

    @JsonProperty("metric-tags")
    private Map<String, String> metricTags;

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
        if (name == null || name.trim().isEmpty()) {
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

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getObjectName() {
        return objectName;
    }

    public void setObjectName(String objectName) {
        this.objectName = objectName;
    }

    public String getAttribute() {
        return attribute;
    }

    public void setAttribute(String attribute) {
        this.attribute = attribute;
    }

    public String getUpRegex() {
        return upRegex;
    }

    public void setUpRegex(String upRegex) {
        this.upRegex = upRegex;
    }

    public Integer getInterval() {
        return interval;
    }

    public void setInterval(Integer interval) {
        this.interval = interval;
    }

    public TimeUnits getTimeUnits() {
        return timeUnits;
    }

    public void setTimeUnits(TimeUnits timeUnits) {
        this.timeUnits = timeUnits;
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
}
