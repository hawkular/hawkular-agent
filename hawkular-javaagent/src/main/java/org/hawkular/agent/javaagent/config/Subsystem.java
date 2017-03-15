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

public class Subsystem implements Validatable {

    @JsonProperty
    public Boolean enabled = Boolean.TRUE;

    @JsonProperty("auto-discovery-scan-period-secs")
    public Integer autoDiscoveryScanPeriodSecs = 600;

    @JsonProperty("min-collection-interval-secs")
    public Integer minCollectionIntervalSecs = 30;

    @JsonProperty
    public Boolean immutable = Boolean.FALSE;

    @JsonProperty("in-container")
    public Boolean inContainer = Boolean.FALSE;

    @JsonProperty("metric-dispatcher-buffer-size")
    public Integer metricDispatcherBufferSize = 1000;

    @JsonProperty("metric-dispatcher-max-batch-size")
    public Integer metricDispatcherMaxBatchSize = 100;

    @JsonProperty("avail-dispatcher-buffer-size")
    public Integer availDispatcherBufferSize = 500;

    @JsonProperty("avail-dispatcher-max-batch-size")
    public Integer availDispatcherMaxBatchSize = 50;

    @JsonProperty("ping-period-secs")
    public Integer pingPeriodSecs = 60;

    public Subsystem() {
    }

    public Subsystem(Subsystem subsystem) {
        this.enabled = subsystem.enabled;
        this.autoDiscoveryScanPeriodSecs = subsystem.autoDiscoveryScanPeriodSecs;
        this.minCollectionIntervalSecs = subsystem.minCollectionIntervalSecs;
        this.immutable = subsystem.immutable;
        this.inContainer = subsystem.inContainer;
        this.metricDispatcherBufferSize = subsystem.metricDispatcherBufferSize;
        this.metricDispatcherMaxBatchSize = subsystem.metricDispatcherMaxBatchSize;
        this.availDispatcherBufferSize = subsystem.availDispatcherBufferSize;
        this.availDispatcherMaxBatchSize = subsystem.availDispatcherMaxBatchSize;
        this.pingPeriodSecs = subsystem.pingPeriodSecs;
    }

    @Override
    public void validate() throws Exception {
        if (autoDiscoveryScanPeriodSecs != null && autoDiscoveryScanPeriodSecs <= 0) {
            throw new Exception("subsystem auto-discovery-scan-period-secs must be greater than 0");
        }
        if (minCollectionIntervalSecs != null && minCollectionIntervalSecs <= 0) {
            throw new Exception("subsystem min-collection-interval-secs must be greater than 0");
        }
        if (metricDispatcherBufferSize != null && metricDispatcherBufferSize <= 0) {
            throw new Exception("subsystem metric-dispatcher-buffer-size must be greater than 0");
        }
        if (metricDispatcherMaxBatchSize != null && metricDispatcherMaxBatchSize <= 0) {
            throw new Exception("subsystem metric-dispatcher-max-batch-size must be greater than 0");
        }
        if (availDispatcherBufferSize != null && availDispatcherBufferSize <= 0) {
            throw new Exception("subsystem avail-dispatcher-buffer-size must be greater than 0");
        }
        if (availDispatcherMaxBatchSize != null && availDispatcherMaxBatchSize <= 0) {
            throw new Exception("subsystem avail-dispatcher-max-batch-size must be greater than 0");
        }
        if (pingPeriodSecs != null && pingPeriodSecs < 0) {
            throw new Exception("subsystem ping-period-secs must be greater than or equal to 0");
        }
    }
}
