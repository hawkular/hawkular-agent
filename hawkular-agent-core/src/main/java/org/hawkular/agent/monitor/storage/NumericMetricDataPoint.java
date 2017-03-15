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
package org.hawkular.agent.monitor.storage;

import org.hawkular.metrics.client.common.MetricType;

/**
 * Numeric metric data that was collected.
 */
public class NumericMetricDataPoint extends MetricDataPoint {

    public NumericMetricDataPoint(String key, long timestamp, double value, MetricType metricType, String tenantId) {
        super(key, timestamp, value, metricType, tenantId);
        if ((metricType != MetricType.COUNTER) && (metricType != MetricType.GAUGE)) {
            throw new IllegalArgumentException(
                    "Numeric metric data point must be a counter or gauge but was [" + metricType + "]. Key=" + key);
        }
    }

    /**
     * The metric data point value as a Double. If the underlying value was provided as a non-Number object,
     * it is assumed that object's string representation can be parsed as a number. If the string
     * representation cannot be parsed as a numeric value, a runtime exception is thrown.
     *
     * If the underlying data point was null, a 0.0 is returned.
     *
     * @return non-null double value for the metric data point.
     */
    public Double getMetricValue() {
        Object v = super.getMetricValue();
        if (v instanceof Number) {
            return ((Number) v).doubleValue();
        } else {
            return (v != null) ? (Double.valueOf(v.toString())) : Double.valueOf(0.0);
        }
    }
}
