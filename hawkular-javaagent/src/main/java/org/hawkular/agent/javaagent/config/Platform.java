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

public class Platform implements Validatable {

    public static class PlatformChild implements Validatable {
        @JsonProperty
        public Boolean enabled = Boolean.TRUE;

        @JsonProperty
        public Integer interval = 5;

        @JsonProperty("time-units")
        public TimeUnits timeUnits = TimeUnits.minutes;

        public PlatformChild() {
        }

        public PlatformChild(PlatformChild original) {
            this.enabled = original.enabled;
            this.interval = original.interval;
            this.timeUnits = original.timeUnits;
        }

        public PlatformChild(Boolean e, Integer i, TimeUnits t) {
            this.enabled = e;
            this.interval = i;
            this.timeUnits = t;
        }

        @Override
        public void validate() throws Exception {
            if (interval == null || interval.intValue() < 0) {
                throw new Exception("platform intervals must be greater than or equal to 0");
            }
        }
    }

    @JsonProperty
    public Boolean enabled = Boolean.FALSE;

    @JsonProperty
    public Integer interval = 5;

    @JsonProperty("time-units")
    public TimeUnits timeUnits = TimeUnits.minutes;

    @JsonProperty("machine-id")
    public String machineId;

    @JsonProperty("file-stores")
    public PlatformChild fileStores = new PlatformChild(true, 5, TimeUnits.minutes);

    @JsonProperty
    public PlatformChild memory = new PlatformChild(true, 5, TimeUnits.minutes);

    @JsonProperty
    public PlatformChild processors = new PlatformChild(true, 5, TimeUnits.minutes);

    @JsonProperty("power-sources")
    public PlatformChild powerSources = new PlatformChild(false, 5, TimeUnits.minutes);

    public Platform() {
    }

    public Platform(Platform original) {
        this.enabled = original.enabled;
        this.interval = original.interval;
        this.timeUnits = original.timeUnits;
        this.machineId = original.machineId;
        this.fileStores = new PlatformChild(original.fileStores);
        this.memory = new PlatformChild(original.memory);
        this.processors = new PlatformChild(original.processors);
        this.powerSources = new PlatformChild(original.powerSources);
    }

    @Override
    public void validate() throws Exception {
        if (interval == null || interval.intValue() < 0) {
            throw new Exception("platform intervals must be greater than or equal to 0");
        }
    }
}
