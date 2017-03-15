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

import com.fasterxml.jackson.annotation.JsonProperty;

public class Diagnostics implements Validatable {

    public enum ReportTo {
        LOG, STORAGE
    };

    @JsonProperty
    public Boolean enabled = Boolean.TRUE;

    @JsonProperty
    public Integer interval = 5;

    @JsonProperty("time-units")
    public TimeUnits timeUnits = TimeUnits.minutes;

    @JsonProperty("report-to")
    public ReportTo reportTo = ReportTo.LOG;

    public Diagnostics() {
    }

    public Diagnostics(Diagnostics original) {
        this.enabled = original.enabled;
        this.interval = original.interval;
        this.timeUnits = original.timeUnits;
        this.reportTo = original.reportTo;
    }

    @Override
    public void validate() throws Exception {
        if (interval == null || interval.intValue() < 0) {
            throw new Exception("diagnostics interval must be greater than or equal to 0");
        }
    }
}
