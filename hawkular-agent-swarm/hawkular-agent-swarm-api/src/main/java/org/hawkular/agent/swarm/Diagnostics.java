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

public class Diagnostics {
    private boolean enabled;
    private String reportTo = "LOG"; // LOG or STORAGE
    private int interval = 5;
    private String timeUnits = "minutes"; // e.g. minutes | seconds

    public Diagnostics() {
    }

    public boolean enabled() {
        return enabled;
    }

    public Diagnostics enabled(boolean enabled) {
        this.enabled = enabled;
        return this;
    }

    public String reportTo() {
        return reportTo;
    }

    public Diagnostics reportTo(String reportTo) {
        this.reportTo = reportTo;
        return this;
    }

    public int interval() {
        return interval;
    }

    public Diagnostics interval(int interval) {
        this.interval = interval;
        return this;
    }

    public String timeUnits() {
        return timeUnits;
    }

    public Diagnostics timeUnits(String timeUnits) {
        this.timeUnits = timeUnits;
        return this;
    }
}
