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
 * A service that can be used to sample metrics for the given {@link MonitoredEndpoint}.
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
     * Given a measurement instance, this will generate its family name.
     *
     * @param instance the measurement instance whose family name is to be generated
     * @return the metric family
     */
    String generateMetricFamily(MeasurementInstance<L, ? extends MeasurementType<L>> instance);

    /**
     * Given a measurement instance, this will generate its labels.
     *
     * The service can use the metric tags provided by the user via
     * {@link MonitoredEndpoint#getEndpointConfiguration() the endpoint configuration} which contains
     * {@link AbstractEndpointConfiguration#getMetricLabels() the metric labels}.
     *
     * @param instance the measurement instance whose labels are to be generated
     * @return the measurement labels to be added to the instance definition
     */
    Map<String, String> generateMetricLabels(MeasurementInstance<L, ? extends MeasurementType<L>> instance);
}
