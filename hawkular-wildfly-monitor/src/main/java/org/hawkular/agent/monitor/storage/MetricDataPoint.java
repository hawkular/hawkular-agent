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

import java.util.Date;

import org.hawkular.agent.monitor.scheduler.polling.Task;
import org.hawkular.metrics.client.common.MetricType;

/**
 * Metric data that was collected.
 */
public class MetricDataPoint {
    private final Task task;
    private final long timestamp;
    private final double value;
    private final MetricType metricType;

    public MetricDataPoint(Task task, double value, MetricType metricType) {
        this.task = task;
        this.timestamp = System.currentTimeMillis();
        this.value = value;
        this.metricType = metricType;
    }

    /**
     * @return object that identifies the metric that was collected.
     */
    public Task getTask() {
        return task;
    }

    /**
     * @return when the metric was collected
     */
    public long getTimestamp() {
        return timestamp;
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
        StringBuilder str = new StringBuilder("MetricDataPoint: ");
        str.append("task=[").append(task).append("]");
        str.append(", timestamp=[").append(new Date(timestamp)).append("]");
        str.append(", value=[").append(value).append("]");
        str.append(", metricType=[").append(metricType).append("]");
        return str.toString();
    }
}
