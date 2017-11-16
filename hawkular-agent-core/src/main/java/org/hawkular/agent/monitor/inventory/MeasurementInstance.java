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

import java.util.HashMap;
import java.util.Map;

import org.hawkular.agent.monitor.config.AgentCoreEngineConfiguration.EndpointConfiguration;

/**
 * A measurement instance that can be used to represent either numeric metric data or availability data.
 *
 * @param <L> the type of the protocol specific location typically a subclass of {@link NodeLocation}
 * @param <T> the measurement type
 */
public final class MeasurementInstance<L, T extends MeasurementType<L>> extends Instance<L, T> {

    private String metricFamily;
    private Map<String, String> metricLabels;

    public MeasurementInstance(ID id, Name name, AttributeLocation<L> attributeLocation, T type) {
        super(id, name, attributeLocation, type);
    }

    // copy-constructor
    public MeasurementInstance(MeasurementInstance<L, T> copy, boolean disown) {
        super(copy, disown);
    }

    /**
     * @return this name of the metric family of this metric - paired with the {@link #getMetricLabels() labels}
     *         the unique timeseries can be identified.
     *
     * @see EndpointConfiguration#getMetricFamily()
     * @see MeasurementInstance#getUniqueMetricId()
     */
    public String getMetricFamily() {
        return metricFamily;
    }

    public void setMetricFamily(String metricFamily) {
        this.metricFamily = metricFamily;
    }

    /**
     * @return labels that are associated with this metric instance. May be empty.
     *
     * @see EndpointConfiguration#getMetricLabels()
     * @see MeasurementInstance#getUniqueMetricId()
     */
    public Map<String, String> getMetricLabels() {
        return metricLabels;
    }

    public void setMetricLabels(Map<String, String> metricLabels) {
        if (metricLabels == null) {
            this.metricLabels = new HashMap<>();
        } else {
            this.metricLabels = new HashMap<>(metricLabels);
        }
    }

    /**
     * Returns the actual expression that is to be used to evaluate the metric value.
     * This takes the optional {@link MeasurementType#getMetricExpression()} and returns
     * a non-null expression with the $metric token replaced appropriately. Note that if
     * {@link MeasurementType#getMetricExpression()} returns null, then it will be
     * assumed "$metric" and this method will return that expression resolved.
     *
     * $metric is resolved as: family{labelName1="labelValue1", ...}
     * If this metric instance has no labels, $metric is resolved simply as the family name.
     *
     * @return the resolved expression used to evaluate the metric value.
     */
    public String resolveExpression() {
        String expr = getType().getMetricExpression();
        if (expr == null || expr.isEmpty()) {
            expr = "$metric";
        }
        if (expr.contains("$metric")) {
            StringBuilder metricString = new StringBuilder();
            if (getMetricFamily() != null) {
                metricString.append(getMetricFamily());
            }
            if (getMetricLabels() != null && !getMetricLabels().isEmpty()) {
                String comma = "";
                metricString.append("{");
                for (Map.Entry<String, String> label : getMetricLabels().entrySet()) {
                    metricString.append(comma)
                            .append(label.getKey())
                            .append("=\"")
                            .append(label.getValue())
                            .append("\"");
                    comma = ",";
                }
                metricString.append("}");
            }
            expr = expr.replace("$metric", metricString.toString());
        }

        return expr;
    }
}
