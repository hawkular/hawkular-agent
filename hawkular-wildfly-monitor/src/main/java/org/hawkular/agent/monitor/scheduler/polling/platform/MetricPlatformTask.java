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
package org.hawkular.agent.monitor.scheduler.polling.platform;

import org.hawkular.agent.monitor.inventory.platform.PlatformMetricInstance;
import org.hawkular.agent.monitor.scheduler.config.Interval;
import org.hawkular.agent.monitor.scheduler.config.PlatformEndpoint;
import org.hawkular.agent.monitor.scheduler.polling.KeyGenerator;

/**
 * Represents a platform task that is to be used to collect a metric.
 */
public class MetricPlatformTask extends PlatformTask {

    private final PlatformMetricInstance metricInstance;

    public MetricPlatformTask(
            Interval interval,
            PlatformEndpoint endpoint,
            PlatformMetricInstance metricInstance) {

        super(Type.METRIC, interval, endpoint);
        this.metricInstance = metricInstance;
    }

    /**
     * If this task is collecting a metric from an inventoried resource,
     * this will be the metric instance of that resource.
     * If there is no inventoried resource behind this collected metric, this will be null.
     *
     * @return the metric instance or null if no inventoried resource backs this metric
     */
    public PlatformMetricInstance getMetricInstance() {
        return metricInstance;
    }

    @Override
    public KeyGenerator getKeyGenerator() {
        return new MetricPlatformTaskKeyGenerator();
    }

    @Override
    public String toString() {
        StringBuilder str = new StringBuilder("MetricPlatformTask: ");
        str.append(super.toString());
        return str.toString();
    }
}
