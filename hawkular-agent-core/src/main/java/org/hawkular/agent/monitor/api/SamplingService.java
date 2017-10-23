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
package org.hawkular.agent.monitor.api;

import java.util.Map;

import org.hawkular.agent.monitor.config.AgentCoreEngineConfiguration.AbstractEndpointConfiguration;
import org.hawkular.agent.monitor.config.AgentCoreEngineConfiguration.EndpointConfiguration;
import org.hawkular.agent.monitor.inventory.MeasurementInstance;
import org.hawkular.agent.monitor.inventory.MeasurementType;
import org.hawkular.agent.monitor.inventory.MonitoredEndpoint;
import org.hawkular.agent.monitor.inventory.NodeLocation;

/**
 * A service that can be used to sample metrics and avails for the given {@link MonitoredEndpoint}.
 *
 * @author <a href="https://github.com/ppalaga">Peter Palaga</a>
 *
 * @param <L> the type of the protocol specific location, typically a subclass of {@link NodeLocation}
 */
public interface SamplingService<L> {

    /**
     * @return the endpoint this service is able to sample
     */
    MonitoredEndpoint<EndpointConfiguration> getMonitoredEndpoint();

    /**
     * Given a measurement instance, this will generate the labels associated with the metric.
     *
     * The service can use the metric tags provided by the user via
     * {@link MonitoredEndpoint#getEndpointConfiguration() the endpoint configuration} which contains
     * {@link AbstractEndpointConfiguration#getMetricLabels() the metric labels}.
     *
     * @param instance the measurement instance whose labels are to be generated
     * @return the measurement labels to be added to the metric definition
     */
    Map<String, String> generateAssociatedMetricLabels(MeasurementInstance<L, ? extends MeasurementType<L>> instance);

    /**
     * Given a measurement instance, this will generate the key to be used when
     * storing that measurement instance's collected data to storage. In the Hawkular Metrics
     * REST API, this key is known as the "metric id".
     *
     * The service can generate a default one or can use the metric ID template provided by the user
     * via {@link MonitoredEndpoint#getEndpointConfiguration() the endpoint configuration} which contains a
     * {@link AbstractEndpointConfiguration#getMetricIdTemplate() metric ID template}.
     *
     * If this method is not implemented, the default behavior is to return the ID of
     * the measurement instance itself.
     *
     * @param instance the measurement instance whose key is to be generated
     * @return the measurement key to be used to identify measured data for the given instance
     * @see MeasurementInstance#getAssociatedMetricId(String)
     * @see MeasurementInstance#setAssociatedMetricId(String)
     */
    default String generateAssociatedMetricId(MeasurementInstance<L, ? extends MeasurementType<L>> instance) {
        return instance.getID().getIDString();
    }
}
