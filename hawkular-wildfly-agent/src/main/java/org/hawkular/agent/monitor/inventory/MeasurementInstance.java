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

/**
 * A measurement instance that can be used to represent either numeric metric data or availability data.
 *
 * @author <a href="https://github.com/ppalaga">Peter Palaga</a>
 *
 * @param <L> the type of the protocol specific location typically a subclass of {@link NodeLocation}
 * @param <T> the measurement type
 */
public final class MeasurementInstance<L, T extends MeasurementType<L>> extends Instance<L, T> {

    /**
     * If this property exists in {@link #getProperties()} then this is the metric ID that represents
     * the data for this measurement instance as it is found in Hawkular Metrics storage. If this
     * property doesn't exist, you can assume the metric ID is the same as the ID of this measurement instance.
     */
    private static final String METRIC_ID_PROPERTY = "hawkular-metric-id";

    public MeasurementInstance(ID id, Name name, AttributeLocation<L> attributeLocation, T type) {
        super(id, name, attributeLocation, type);
    }

    // copy-constructor
    public MeasurementInstance(MeasurementInstance<L, T> copy, boolean disown) {
        super(copy, disown);

        if (copy.getProperties().containsKey(METRIC_ID_PROPERTY)) {
            this.addProperty(METRIC_ID_PROPERTY, copy.getProperties().get(METRIC_ID_PROPERTY));
        }
    }

    /**
     * This returns this instance's associated metric ID. A metric ID is that ID which is used
     * to store the metric data associated with this measurement instance into Hawkular Metrics.
     *
     * There is an explicit rule that all clients must follow - if a measurement instance has a
     * property called {@value #METRIC_ID_PROPERTY} then that ID must be used when storing
     * and retrieving metric data associated with this measurement instance. If there is no such
     * property, then the implicit rule takes effect, and that is the metric ID to be used will
     * be the same as the {@link #getID() id} of this measurement instance.
     *
     * This method follows those rules - if there is such a property, its value is returned;
     * otherwise this instance's ID string is returned.
     *
     * @return the metric ID that should be used to read/write metric data from/to Hawkular Metrics
     */
    public String getAssociatedMetricId() {
        Object property = getProperties().get(METRIC_ID_PROPERTY);
        if (property != null) {
            return property.toString();
        } else {
            return getID().getIDString();
        }
    }

    /**
     * This tells this instance what its metric ID should be. A metric ID is that ID which is used
     * to store the metric data associated with this measurement instance into Hawkular Metrics.
     * See {@link #getAssociatedMetricId()} for more details.
     *
     * @param metricId the metric ID to be associated with this measurement instance. If this is
     *                 null or empty, the metric ID will be assumed to be the same as this instance's ID.
     */
    public void setAssociatedMetricId(String metricId) {
        // Note that if the metric ID is the same as this instance's ID, then the implicit rule is in force,
        // so there is no need to actually add the metric ID property.
        if (metricId == null || metricId.isEmpty() || metricId.equals(getID().getIDString())) {
            removeProperty(METRIC_ID_PROPERTY);
        } else {
            addProperty(METRIC_ID_PROPERTY, metricId);
        }
    }
}
