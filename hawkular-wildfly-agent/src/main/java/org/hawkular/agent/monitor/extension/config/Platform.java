/*
 * Copyright 2015-2016 Red Hat, Inc. and/or its affiliates
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
package org.hawkular.agent.monitor.extension.config;

import org.hawkular.agent.monitor.extension.FileStoresAttributes;
import org.hawkular.agent.monitor.extension.MemoryAttributes;
import org.hawkular.agent.monitor.extension.PlatformAttributes;
import org.hawkular.agent.monitor.extension.PowerSourcesAttributes;
import org.hawkular.agent.monitor.extension.ProcessorsAttributes;

import com.fasterxml.jackson.annotation.JsonProperty;

public class Platform {

    public static class PlatformChild {
        public PlatformChild() {
        }

        public PlatformChild(Boolean e, Integer i, TimeUnits t) {
            this.enabled = e;
            this.interval = i;
            this.timeUnits = t;
        }

        @JsonProperty
        public Boolean enabled;

        @JsonProperty
        public Integer interval;

        @JsonProperty("time-units")
        public TimeUnits timeUnits;
    }

    @JsonProperty
    public Boolean enabled = PlatformAttributes.ENABLED.getDefaultValue().asBoolean();

    @JsonProperty
    public Integer interval = PlatformAttributes.INTERVAL.getDefaultValue().asInt();

    @JsonProperty("time-units")
    public TimeUnits timeUnits = TimeUnits
            .valueOf(PlatformAttributes.TIME_UNITS.getDefaultValue().asString().toLowerCase());

    @JsonProperty("machine-id")
    public String machineId;

    @JsonProperty("file-stores")
    public PlatformChild fileStores = new PlatformChild(FileStoresAttributes.ENABLED.getDefaultValue().asBoolean(),
            FileStoresAttributes.INTERVAL.getDefaultValue().asInt(),
            TimeUnits.valueOf(FileStoresAttributes.TIME_UNITS.getDefaultValue().asString().toLowerCase()));

    @JsonProperty
    public PlatformChild memory = new PlatformChild(MemoryAttributes.ENABLED.getDefaultValue().asBoolean(),
            MemoryAttributes.INTERVAL.getDefaultValue().asInt(),
            TimeUnits.valueOf(MemoryAttributes.TIME_UNITS.getDefaultValue().asString().toLowerCase()));

    @JsonProperty
    public PlatformChild processors = new PlatformChild(ProcessorsAttributes.ENABLED.getDefaultValue().asBoolean(),
            ProcessorsAttributes.INTERVAL.getDefaultValue().asInt(),
            TimeUnits.valueOf(ProcessorsAttributes.TIME_UNITS.getDefaultValue().asString().toLowerCase()));

    @JsonProperty("power-sources")
    public PlatformChild powerSources = new PlatformChild(PowerSourcesAttributes.ENABLED.getDefaultValue().asBoolean(),
            PowerSourcesAttributes.INTERVAL.getDefaultValue().asInt(),
            TimeUnits.valueOf(PowerSourcesAttributes.TIME_UNITS.getDefaultValue().asString().toLowerCase()));

}
