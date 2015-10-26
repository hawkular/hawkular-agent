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
package org.hawkular.agent.monitor.storage;

import org.hawkular.metrics.client.common.MetricType;

/**
 * Metric data that was collected.
 */
public class MetricDataPoint extends DataPoint {

    private final double value;
    private final MetricType metricType;

    public MetricDataPoint(String key, long timestamp, double value, MetricType metricType) {
        super(key, timestamp);
        this.value = value;
        this.metricType = metricType;
    }

    /**
     * @return the actual data that was collected
     */
    public double getValue() {
        return value;
    }

    /**
     * @return the type of metric
     */
    public MetricType getMetricType() {
        return metricType;
    }

    @Override
    public String toString() {
        return "MetricDataPoint [value=" + value + ", metricType=" + metricType + ", key=" + key + ", timestamp="
                + timestamp + "]";
    }

}
