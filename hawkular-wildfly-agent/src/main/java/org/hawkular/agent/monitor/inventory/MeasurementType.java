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

import java.util.Collections;
import java.util.Map;

import org.hawkular.agent.monitor.extension.MonitorServiceConfiguration.EndpointConfiguration;

/**
 * A common superclass for {@link AvailType} and {@link MetricType}.
 *
 * @author <a href="https://github.com/ppalaga">Peter Palaga</a>
 * @param <L> the type of the protocol specific location typically a subclass of {@link NodeLocation}
 */
public class MeasurementType<L> extends AttributeLocationProvider<L> {

    private final Interval interval;
    private final String metricIdTemplate;
    private final Map<String, String> metricTags;

    public MeasurementType(ID id, Name name, AttributeLocation<L> location, Interval interval, String metricIdTemplate,
            Map<String, String> metricTags) {
        super(id, name, location);
        this.interval = interval;
        this.metricIdTemplate = metricIdTemplate;
        this.metricTags = (metricTags != null) ? Collections.unmodifiableMap(metricTags) : Collections.emptyMap();

        if (interval.seconds() < 1) {
            throw new IllegalArgumentException("Interval is too small: " + interval);
        }
    }

    /**
     * @return how often should instances of this type be measured
     */
    public Interval getInterval() {
        return interval;
    }

    /**
     * @return if not null, this should be used to generate the Hawkular Metrics metric ID
     *         for all instances of this measurement type.
     *
     * @see EndpointConfiguration#getMetricIdTemplate()
     * @see MeasurementInstance#getAssociatedMetricId()
     */
    public String getMetricIdTemplate() {
        return metricIdTemplate;
    }

    /**
     * @return Defines what Hawkular Metrics tags should be generated for all instances of this measurement type.
     *         May be empty.
     *
     * @see EndpointConfiguration#getMetricTags()
     */
    public Map<String, String> getMetricTags() {
        return metricTags;
    }

}
