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
package org.hawkular.agent.monitor.storage;

import org.hawkular.agent.monitor.api.Avail;
import org.hawkular.agent.monitor.api.AvailDataPayloadBuilder;

/**
 * Allows one to build up a payload request to send to Hawkular by adding
 * data points one by one.
 */
public class HawkularAvailDataPayloadBuilder implements AvailDataPayloadBuilder {

    private MetricsOnlyAvailDataPayloadBuilder metricsOnlyPayloadBuilder =
            new MetricsOnlyAvailDataPayloadBuilder();

    @Override
    public void addDataPoint(String key, long timestamp, Avail value) {
        metricsOnlyPayloadBuilder.addDataPoint(key, timestamp, value);
    }

    @Override
    public int getNumberDataPoints() {
        return metricsOnlyPayloadBuilder.getNumberDataPoints();
    }

    @Override
    public String toPayload() {
        return metricsOnlyPayloadBuilder.toPayload();
    }
}
