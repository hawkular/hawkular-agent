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
package org.hawkular.agent.monitor.inventory;

import java.util.Collections;
import java.util.Map;

import org.hawkular.agent.monitor.config.AgentCoreEngineConfiguration.EndpointConfiguration;

/**
 * A superclass for {@link MetricType} and any other types of metrics that might be needed
 * (perhaps an "availability metric" type in the future?).
 *
 * @param <L> the type of the protocol specific location typically a subclass of {@link NodeLocation}
 */
public class MeasurementType<L> extends AttributeLocationProvider<L> {

    private final SupportedMetricType metricType;
    private final String metricFamily;
    private final Map<String, String> metricLabels;
    private final String metricExpression;

    public MeasurementType(
            ID id,
            Name name,
            SupportedMetricType metricType,
            AttributeLocation<L> location,
            String metricFamily,
            Map<String, String> metricLabels,
            String metricExpression) {
        super(id, name, location);
        this.metricType = metricType;
        this.metricFamily = metricFamily;
        this.metricLabels = (metricLabels != null) ? Collections.unmodifiableMap(metricLabels)
                : Collections.emptyMap();
        this.metricExpression = metricExpression;
    }

    public SupportedMetricType getMetricType() {
        return metricType;
    }

    /**
     * @return this name of the metric family of all metrics of this measurement type
     *
     * @see MeasurementInstance#getMetricFamily()
     */
    public String getMetricFamily() {
        return metricFamily;
    }

    /**
     * @return Defines what the labels are associated for all instances of this measurement type. May be empty.
     *
     * @see EndpointConfiguration#getMetricLabels()
     * @see MeasurementInstance#getMetricLabels()
     */
    public Map<String, String> getMetricLabels() {
        return metricLabels;
    }

    /**
     * This is an optional metric expression that is to be used to evaluate the metric value.
     *
     * An expression should include the token "$metric" which is meant to be replaced with
     * the true expression "family{labels...}" or just "family" if there are no labels associated
     * with this metric. See {@link MeasurementInstance#resolveExpression()} for the method that
     * can do this for you.
     *
     * If not specified (i.e. if null is returned), caller should assume "$metric".
     *
     * @return an optional metric expression that is to be used to evaluate the metric value.
     */
    public String getMetricExpression() {
        return metricExpression;
    }
}
