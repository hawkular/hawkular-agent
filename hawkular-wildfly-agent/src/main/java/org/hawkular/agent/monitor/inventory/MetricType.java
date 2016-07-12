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
package org.hawkular.agent.monitor.inventory;

import java.util.Map;

import org.jboss.as.controller.client.helpers.MeasurementUnit;

/**
 * @author John Mazzitelli
 *
 * @param <L> the type of the protocol specific location typically a subclass of {@link NodeLocation}
 */
public final class MetricType<L> extends MeasurementType<L> {

    private final MeasurementUnit metricUnits;

    private final org.hawkular.metrics.client.common.MetricType metricType;

    public MetricType(ID id, Name name, AttributeLocation<L> location, Interval interval, MeasurementUnit metricUnits,
            org.hawkular.metrics.client.common.MetricType metricType, String metricIdTemplate,
            Map<String, String> metricTags) {
        super(id, name, location, interval, metricIdTemplate, metricTags);
        this.metricUnits = metricUnits;
        this.metricType = metricType;
    }

    public org.hawkular.metrics.client.common.MetricType getMetricType() {
        return metricType;
    }

    public MeasurementUnit getMetricUnits() {
        return metricUnits;
    }

}
