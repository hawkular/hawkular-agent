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
public class Subsystem implements Validatable {

    @JsonProperty
    private BooleanExpression enabled = new BooleanExpression(Boolean.TRUE);

    @JsonProperty("auto-discovery-scan-period-secs")
    private IntegerExpression autoDiscoveryScanPeriodSecs = new IntegerExpression(600);

    @JsonProperty("min-collection-interval-secs")
    private IntegerExpression minCollectionIntervalSecs = new IntegerExpression(30);

    @JsonProperty
    private BooleanExpression immutable = new BooleanExpression(Boolean.FALSE);

    @JsonProperty("in-container")
    private BooleanExpression inContainer = new BooleanExpression(Boolean.FALSE);

    @JsonProperty("metric-dispatcher-buffer-size")
    private Integer metricDispatcherBufferSize = 1000;

    @JsonProperty("metric-dispatcher-max-batch-size")
    private Integer metricDispatcherMaxBatchSize = 100;

    @JsonProperty("avail-dispatcher-buffer-size")
    private Integer availDispatcherBufferSize = 500;

    @JsonProperty("avail-dispatcher-max-batch-size")
    private Integer availDispatcherMaxBatchSize = 50;

    @JsonProperty("ping-period-secs")
    private IntegerExpression pingPeriodSecs = new IntegerExpression(60);

    public Subsystem() {
    }

    public Subsystem(Subsystem original) {
        this.enabled = original.enabled == null ? null : new BooleanExpression(original.enabled);
        this.autoDiscoveryScanPeriodSecs = original.autoDiscoveryScanPeriodSecs == null ? null
                : new IntegerExpression(original.autoDiscoveryScanPeriodSecs);
        this.minCollectionIntervalSecs = original.minCollectionIntervalSecs == null ? null
                : new IntegerExpression(original.minCollectionIntervalSecs);
        this.immutable = original.immutable == null ? null : new BooleanExpression(original.immutable);
        this.inContainer = original.inContainer == null ? null : new BooleanExpression(original.inContainer);
        this.metricDispatcherBufferSize = original.metricDispatcherBufferSize;
        this.metricDispatcherMaxBatchSize = original.metricDispatcherMaxBatchSize;
        this.availDispatcherBufferSize = original.availDispatcherBufferSize;
        this.availDispatcherMaxBatchSize = original.availDispatcherMaxBatchSize;
        this.pingPeriodSecs = original.pingPeriodSecs == null ? null
                : new IntegerExpression(original.pingPeriodSecs);
    }

    @Override
    public void validate() throws Exception {
        if (autoDiscoveryScanPeriodSecs != null && autoDiscoveryScanPeriodSecs.get() <= 0) {
            throw new Exception("subsystem auto-discovery-scan-period-secs must be greater than 0");
        }
        if (minCollectionIntervalSecs != null && minCollectionIntervalSecs.get() <= 0) {
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
        if (pingPeriodSecs != null && pingPeriodSecs.get() < 0) {
            throw new Exception("subsystem ping-period-secs must be greater than or equal to 0");
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

    public Integer getAutoDiscoveryScanPeriodSecs() {
        return autoDiscoveryScanPeriodSecs == null ? null : autoDiscoveryScanPeriodSecs.get();
    }

    public void setAutoDiscoveryScanPeriodSecs(Integer autoDiscoveryScanPeriodSecs) {
        if (this.autoDiscoveryScanPeriodSecs != null) {
            this.autoDiscoveryScanPeriodSecs.set(autoDiscoveryScanPeriodSecs);
        } else {
            this.autoDiscoveryScanPeriodSecs = new IntegerExpression(autoDiscoveryScanPeriodSecs);
        }
    }

    public Integer getMinCollectionIntervalSecs() {
        return minCollectionIntervalSecs == null ? null : minCollectionIntervalSecs.get();
    }

    public void setMinCollectionIntervalSecs(Integer minCollectionIntervalSecs) {
        if (this.minCollectionIntervalSecs != null) {
            this.minCollectionIntervalSecs.set(minCollectionIntervalSecs);
        } else {
            this.minCollectionIntervalSecs = new IntegerExpression(minCollectionIntervalSecs);
        }
    }

    public Boolean getImmutable() {
        return immutable == null ? null : immutable.get();
    }

    public void setImmutable(Boolean immutable) {
        if (this.immutable != null) {
            this.immutable.set(immutable);
        } else {
            this.immutable = new BooleanExpression(immutable);
        }
    }

    public Boolean getInContainer() {
        return inContainer == null ? null : inContainer.get();
    }

    public void setInContainer(Boolean inContainer) {
        if (this.inContainer != null) {
            this.inContainer.set(inContainer);
        } else {
            this.inContainer = new BooleanExpression(inContainer);
        }
    }

    public Integer getMetricDispatcherBufferSize() {
        return metricDispatcherBufferSize;
    }

    public void setMetricDispatcherBufferSize(Integer metricDispatcherBufferSize) {
        this.metricDispatcherBufferSize = metricDispatcherBufferSize;
    }

    public Integer getMetricDispatcherMaxBatchSize() {
        return metricDispatcherMaxBatchSize;
    }

    public void setMetricDispatcherMaxBatchSize(Integer metricDispatcherMaxBatchSize) {
        this.metricDispatcherMaxBatchSize = metricDispatcherMaxBatchSize;
    }

    public Integer getAvailDispatcherBufferSize() {
        return availDispatcherBufferSize;
    }

    public void setAvailDispatcherBufferSize(Integer availDispatcherBufferSize) {
        this.availDispatcherBufferSize = availDispatcherBufferSize;
    }

    public Integer getAvailDispatcherMaxBatchSize() {
        return availDispatcherMaxBatchSize;
    }

    public void setAvailDispatcherMaxBatchSize(Integer availDispatcherMaxBatchSize) {
        this.availDispatcherMaxBatchSize = availDispatcherMaxBatchSize;
    }

    public Integer getPingPeriodSecs() {
        return pingPeriodSecs == null ? null : pingPeriodSecs.get();
    }

    public void setPingPeriodSecs(Integer pingPeriodSecs) {
        if (this.pingPeriodSecs != null) {
            this.pingPeriodSecs.set(pingPeriodSecs);
        } else {
            this.pingPeriodSecs = new IntegerExpression(pingPeriodSecs);
        }
    }
}
