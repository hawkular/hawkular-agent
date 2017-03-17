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

import com.fasterxml.jackson.annotation.JsonAutoDetect;
import com.fasterxml.jackson.annotation.JsonAutoDetect.Visibility;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonAutoDetect( //
        fieldVisibility = Visibility.NONE, //
        getterVisibility = Visibility.NONE, //
        setterVisibility = Visibility.NONE, //
        isGetterVisibility = Visibility.NONE)
public class Platform implements Validatable {

    @JsonAutoDetect( //
            fieldVisibility = Visibility.NONE, //
            getterVisibility = Visibility.NONE, //
            setterVisibility = Visibility.NONE, //
            isGetterVisibility = Visibility.NONE)
    public static class PlatformChild implements Validatable {
        @JsonProperty
        private BooleanExpression enabled = new BooleanExpression(Boolean.TRUE);

        @JsonProperty
        private Integer interval = 5;

        @JsonProperty("time-units")
        private TimeUnits timeUnits = TimeUnits.minutes;

        public PlatformChild() {
        }

        public PlatformChild(PlatformChild original) {
            this.enabled = original.enabled == null ? null : new BooleanExpression(original.enabled);
            this.interval = original.interval;
            this.timeUnits = original.timeUnits;
        }

        public PlatformChild(Boolean e, Integer i, TimeUnits t) {
            this.enabled = new BooleanExpression(e);
            this.interval = i;
            this.timeUnits = t;
        }

        @Override
        public void validate() throws Exception {
            if (interval == null || interval.intValue() < 0) {
                throw new Exception("platform intervals must be greater than or equal to 0");
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
    }

    @JsonProperty
    private BooleanExpression enabled = new BooleanExpression(Boolean.FALSE);

    @JsonProperty
    private Integer interval = 5;

    @JsonProperty("time-units")
    private TimeUnits timeUnits = TimeUnits.minutes;

    @JsonProperty("machine-id")
    private String machineId;

    @JsonProperty("file-stores")
    private PlatformChild fileStores = new PlatformChild(true, 5, TimeUnits.minutes);

    @JsonProperty
    private PlatformChild memory = new PlatformChild(true, 5, TimeUnits.minutes);

    @JsonProperty
    private PlatformChild processors = new PlatformChild(true, 5, TimeUnits.minutes);

    @JsonProperty("power-sources")
    private PlatformChild powerSources = new PlatformChild(false, 5, TimeUnits.minutes);

    public Platform() {
    }

    public Platform(Platform original) {
        this.enabled = original.enabled == null ? null : new BooleanExpression(original.enabled);
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

    public String getMachineId() {
        return machineId;
    }

    public void setMachineId(String machineId) {
        this.machineId = machineId;
    }

    public PlatformChild getFileStores() {
        return fileStores;
    }

    public void setFileStores(PlatformChild fileStores) {
        this.fileStores = fileStores;
    }

    public PlatformChild getMemory() {
        return memory;
    }

    public void setMemory(PlatformChild memory) {
        this.memory = memory;
    }

    public PlatformChild getProcessors() {
        return processors;
    }

    public void setProcessors(PlatformChild processors) {
        this.processors = processors;
    }

    public PlatformChild getPowerSources() {
        return powerSources;
    }

    public void setPowerSources(PlatformChild powerSources) {
        this.powerSources = powerSources;
    }
}
