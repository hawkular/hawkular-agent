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
package org.hawkular.agent.monitor.scheduler.polling.jmx;

import javax.management.ObjectName;

import org.hawkular.agent.monitor.inventory.jmx.JMXMetricInstance;
import org.hawkular.agent.monitor.scheduler.config.Interval;
import org.hawkular.agent.monitor.scheduler.config.JMXEndpoint;
import org.hawkular.agent.monitor.scheduler.polling.KeyGenerator;

/**
 * Represents a JMX task that is to be used to collect a metric.
 */
public class MetricJMXTask extends JMXTask {

    private final JMXMetricInstance metricInstance;

    public MetricJMXTask(
            Interval interval,
            JMXEndpoint endpoint,
            ObjectName objectName,
            String attribute,
            String subref,
            JMXMetricInstance metricInstance) {

        super(Type.METRIC, interval, endpoint, objectName, attribute, subref);
        this.metricInstance = metricInstance;
    }

    /**
     * If this task is collecting a metric from an inventoried resource,
     * this will be the metric instance of that resource.
     * If there is no inventoried resource behind this collected metric, this will be null.
     *
     * @return the metric instance or null if no inventoried resource backs this metric
     */
    public JMXMetricInstance getMetricInstance() {
        return metricInstance;
    }

    @Override
    public KeyGenerator getKeyGenerator() {
        return new MetricJMXTaskKeyGenerator();
    }

    @Override
    public String toString() {
        StringBuilder str = new StringBuilder("MetricJMXTask: ");
        str.append(super.toString());
        return str.toString();
    }
}
