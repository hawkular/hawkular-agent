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

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.hawkular.agent.monitor.api.Avail;
import org.hawkular.agent.monitor.api.AvailDataPayloadBuilder;
import org.hawkular.agent.monitor.service.Util;

/**
 * Allows one to build up a payload request to send to Hawkular by adding
 * data points one by one.
 */
public class HawkularAvailDataPayloadBuilder implements AvailDataPayloadBuilder {

    private MetricsOnlyAvailDataPayloadBuilder metricsOnlyPayloadBuilder =
            new MetricsOnlyAvailDataPayloadBuilder();
    private String tenantId = null;

    public void setTenantId(String tenantId) {
        this.tenantId = tenantId;
    }

    public MetricsOnlyAvailDataPayloadBuilder toMetricsOnlyAvailDataPayloadBuilder() {
        return metricsOnlyPayloadBuilder;
    }

    @Override
    public void addDataPoint(String key, long timestamp, Avail value) {
        // delegate
        metricsOnlyPayloadBuilder.addDataPoint(key, timestamp, value);
    }

    @Override
    public int getNumberDataPoints() {
        return metricsOnlyPayloadBuilder.getNumberDataPoints();
    }

    @Override
    public String toPayload() {
        final String jsonPayload = Util.toJson(toMessageBusObject());
        return jsonPayload;
    }

    public Map<String, Object> toMessageBusObject() {
        if (tenantId == null) {
            throw new IllegalStateException("Do not know the tenant ID");
        }

        List<Availability> availabilityList = new ArrayList<>();

        // list of maps where map is keyed on avail ID ("id") and value is "data"
        // where "data" is another List<Map<String, Object>> that is the list of avail data.
        // That inner map is keyed on either "timestamp" or "value".
        List<Map<String, Object>> allAvails = metricsOnlyPayloadBuilder.toObjectPayload();
        for (Map<String, Object> avail : allAvails) {
            String availId = (String) avail.get("id");
            List<Map<String, Object>> availListData = (List<Map<String, Object>>) avail.get("data");
            for (Map<String, Object> singleAvailData : availListData) {
                long timestamp = ((Long) singleAvailData.get("timestamp")).longValue();
                String value = (String) singleAvailData.get("value");
                availabilityList.add(new Availability(availId, timestamp, value.toString()));
            }
        }

        Map<String, Object> data = new HashMap<>(2);
        data.put("tenantId", tenantId);
        data.put("data", availabilityList);

        Map<String, Object> outerBusObject = new HashMap<>(1);
        outerBusObject.put("availData", data);

        return outerBusObject;
    }

    public static class Availability {
        public final String id;
        public final long timestamp;
        public final String value;

        public Availability(String id, long timestamp, String value) {
            this.id = id;
            this.timestamp = timestamp;
            this.value = value;
        }
    }
}
