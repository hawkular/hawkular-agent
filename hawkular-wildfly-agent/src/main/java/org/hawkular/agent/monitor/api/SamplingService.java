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
package org.hawkular.agent.monitor.api;

import java.util.Collection;

import org.hawkular.agent.monitor.inventory.AvailType;
import org.hawkular.agent.monitor.inventory.MeasurementInstance;
import org.hawkular.agent.monitor.inventory.MetricType;
import org.hawkular.agent.monitor.inventory.MonitoredEndpoint;
import org.hawkular.agent.monitor.inventory.NodeLocation;
import org.hawkular.agent.monitor.storage.AvailDataPoint;
import org.hawkular.agent.monitor.storage.MetricDataPoint;
import org.hawkular.agent.monitor.util.Consumer;

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
    MonitoredEndpoint getEndpoint();

    /**
     * @return the {@code feedId} associated with the present endpoint
     */
    String getFeedId();

    /**
     * Checks the availabilities defined by {@code instances} and reports them back to the given {@code consumer}.
     *
     * @param instances the availabilities to check
     * @param consumer the consumer to send the results to
     */
    void measureAvails(Collection<MeasurementInstance<L, AvailType<L>>> instances, Consumer<AvailDataPoint> consumer);

    /**
     * Collects the metrics defined by {@code instances} and reports them back to the given {@code consumer}.
     *
     * @param instances the metrics to check
     * @param consumer the consumer to send the results to
     */
    void measureMetrics(Collection<MeasurementInstance<L, MetricType<L>>> instances,
            Consumer<MetricDataPoint> consumer);

}
