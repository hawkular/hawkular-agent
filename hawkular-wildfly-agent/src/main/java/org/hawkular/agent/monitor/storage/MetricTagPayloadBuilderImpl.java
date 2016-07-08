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
package org.hawkular.agent.monitor.storage;

import java.util.HashMap;
import java.util.Map;
import java.util.TreeMap;

import org.hawkular.agent.monitor.api.MetricTagPayloadBuilder;
import org.hawkular.agent.monitor.util.Util;
import org.hawkular.metrics.client.common.MetricType;

/**
 * Allows one to build up payload requests to send to metric storage to add tags.
 * After all tags are added to this builder, you can get the payloads in
 * JSON format via {@link #toPayload()}.
 */
public class MetricTagPayloadBuilderImpl implements MetricTagPayloadBuilder {

    // key is metric ID, value is map of name/value pairs (the actual tags)
    private Map<String, Map<String, String>> allGauges = new HashMap<>();
    private Map<String, Map<String, String>> allCounters = new HashMap<>();

    // a running count of the number of tags that have been added
    private int count = 0;

    // if not null, this is the tenant ID to associate all the metrics with (null means used the agent tenant ID)
    private String tenantId = null;

    @Override
    public void addTag(String key, String name, String value, MetricType metricType) {
        Map<String, Map<String, String>> map;

        switch (metricType) {
            case GAUGE: {
                map = allGauges;
                break;
            }
            case COUNTER: {
                map = allCounters;
                break;
            }
            // case AVAILABILITY: {
            //     map = allAvails;
            //     break;
            // }
            default: {
                throw new IllegalArgumentException("Unsupported metric type: " + metricType);
            }
        }

        Map<String, String> allTagsForMetric = map.get(key);
        if (allTagsForMetric == null) {
            // we haven't seen this metric ID before, create a new map of tags
            allTagsForMetric = new TreeMap<String, String>(); // use tree map to sort the tags
            map.put(key, allTagsForMetric);
        }
        allTagsForMetric.put(name, value);
        count++;
    }

    @Override
    public Map<String, String> toPayload() {
        Map<String, Map<String, String>> withMapObject = new HashMap<>();

        for (Map.Entry<String, Map<String, String>> gaugeEntry : allGauges.entrySet()) {
            withMapObject.put("gauges/" + Util.urlEncode(gaugeEntry.getKey()), gaugeEntry.getValue());
        }
        for (Map.Entry<String, Map<String, String>> counterEntry : allCounters.entrySet()) {
            withMapObject.put("counters/" + Util.urlEncode(counterEntry.getKey()), counterEntry.getValue());
        }

        // now convert all the maps of tags to json
        Map<String, String> withJson = new HashMap<>(withMapObject.size());
        for (Map.Entry<String, Map<String, String>> entry : withMapObject.entrySet()) {
            withJson.put(entry.getKey(), Util.toJson(entry.getValue()));
        }

        return withJson;
    }

    @Override
    public int getNumberTags() {
        return count;
    }

    @Override
    public void setTenantId(String tenantId) {
        this.tenantId = tenantId;
    }

    @Override
    public String getTenantId() {
        return this.tenantId;
    }
}