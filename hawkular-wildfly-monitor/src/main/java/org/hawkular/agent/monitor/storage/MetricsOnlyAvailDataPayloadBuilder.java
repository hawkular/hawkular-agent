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

import com.google.gson.Gson;
import org.hawkular.agent.monitor.api.Avail;
import org.hawkular.agent.monitor.api.AvailDataPayloadBuilder;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Allows one to build up a payload request to send to availability storage by adding
 * data points one by one. After all data points are added, you can get the payload in
 * either an {@link #toObjectPayload() object} format or a {@link #toPayload() JSON} format.
 */
public class MetricsOnlyAvailDataPayloadBuilder implements AvailDataPayloadBuilder {

    // key is avail ID, value is list of data points where a data point is a map with timestamp and value
    private Map<String, List<Map<String, Object>>> allAvail = new HashMap<>();

    // a running count of the number of data points that have been added
    private int count = 0;

    @Override
    public void addDataPoint(String key, long timestamp, Avail value) {
        List<Map<String, Object>> data = allAvail.get(key);
        if (data == null) {
            // we haven't seen this avail ID before, create a new list of data points
            data = new ArrayList<Map<String, Object>>();
            allAvail.put(key, data);
        }
        Map<String, Object> timestampAndValue = new HashMap<>(2);
        timestampAndValue.put("timestamp", new Long(timestamp));
        timestampAndValue.put("value", value.name().toLowerCase());
        data.add(timestampAndValue);
        count++;
    }

    public List<Map<String, Object>> toObjectPayload() {
        List<Map<String, Object>> fullMessageObject = new ArrayList<>();
        for (Map.Entry<String, List<Map<String, Object>>> availEntry : allAvail.entrySet()) {
            Map<String, Object> availKeyAndData = new HashMap<>(2);
            availKeyAndData.put("id", availEntry.getKey());
            availKeyAndData.put("data", availEntry.getValue());
            fullMessageObject.add(availKeyAndData);
        }
        return fullMessageObject;
    }

    @Override
    public String toPayload() {
        String jsonPayload = new Gson().toJson(toObjectPayload());
        return jsonPayload;
    }

    @Override
    public int getNumberDataPoints() {
        return count;
    }
}