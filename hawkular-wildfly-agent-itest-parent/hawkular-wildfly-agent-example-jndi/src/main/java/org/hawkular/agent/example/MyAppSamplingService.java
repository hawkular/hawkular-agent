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
package org.hawkular.agent.example;

import java.net.URI;
import java.util.Collection;
import java.util.Collections;

import org.hawkular.agent.monitor.api.SamplingService;
import org.hawkular.agent.monitor.extension.MonitorServiceConfiguration.EndpointConfiguration;
import org.hawkular.agent.monitor.inventory.AvailType;
import org.hawkular.agent.monitor.inventory.ConnectionData;
import org.hawkular.agent.monitor.inventory.MeasurementInstance;
import org.hawkular.agent.monitor.inventory.MetricType;
import org.hawkular.agent.monitor.inventory.MonitoredEndpoint;
import org.hawkular.agent.monitor.storage.AvailDataPoint;
import org.hawkular.agent.monitor.storage.MetricDataPoint;
import org.hawkular.agent.monitor.util.Consumer;
import org.jboss.logging.Logger;

/**
 * Provides a way for the Hawkular WildFly Agent to collect metrics and check availabilities
 * for the application's managed resources.
 */
public class MyAppSamplingService implements SamplingService<MyAppNodeLocation> {
    private static final Logger log = Logger.getLogger(MyAppSamplingService.class);

    private final MonitoredEndpoint endpoint;

    public MyAppSamplingService() {
        try {
            // this is our endpoint that Hawkular uses to collect metrics and availabilities for our managed resources
            ConnectionData connectionData = new ConnectionData(new URI("myapp:local-uri"), null, null);
            EndpointConfiguration config = new EndpointConfiguration("My App Endpoint", true, Collections.emptyList(),
                    connectionData, null);
            this.endpoint = MonitoredEndpoint.of(config, null);
        } catch (Exception e) {
            throw new IllegalStateException("Cannot create sampling service", e);
        }
    }

    @Override
    public MonitoredEndpoint getMonitoredEndpoint() {
        return this.endpoint;
    }

    @Override
    public void measureMetrics(
            Collection<MeasurementInstance<MyAppNodeLocation, MetricType<MyAppNodeLocation>>> instances,
            Consumer<MetricDataPoint> consumer) {
        // TODO collect metrics
        log.warnf("Need to collect metrics for these: %s", instances);
    }

    @Override
    public void measureAvails(
            Collection<MeasurementInstance<MyAppNodeLocation, AvailType<MyAppNodeLocation>>> instances,
            Consumer<AvailDataPoint> consumer) {
        // TODO collect availabilities
        log.warnf("Need to check availabilities for these: %s", instances);
    }

}
