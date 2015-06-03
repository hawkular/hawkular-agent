/*
 * Copyright 2015 Red Hat, Inc. and/or its affiliates
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
package org.hawkular.agent.swarm;

public class DMRMetric {

    private final String name;
    private int interval = 5;
    private String timeUnits = "minutes"; // e.g. seconds | minutes
    private String path;
    private String attribute;
    private String metricUnits; // e.g. bytes

    public DMRMetric(String name) {
        this.name = name;
    }

    public String name() {
        return name;
    }

    public int interval() {
        return interval;
    }

    public DMRMetric interval(int interval) {
        this.interval = interval;
        return this;
    }

    public String timeUnits() {
        return timeUnits;
    }

    public DMRMetric timeUnits(String timeUnits) {
        this.timeUnits = timeUnits;
        return this;
    }

    public String path() {
        return path;
    }

    public DMRMetric path(String path) {
        this.path = path;
        return this;
    }

    public String attribute() {
        return attribute;
    }

    public DMRMetric attribute(String attribute) {
        this.attribute = attribute;
        return this;
    }

    public String metricUnits() {
        return metricUnits;
    }

    public DMRMetric metricUnits(String metricUnits) {
        this.metricUnits = metricUnits;
        return this;
    }
}
