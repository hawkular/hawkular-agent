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
package org.hawkular.agent.monitor.storage;

import java.io.Serializable;
import java.util.Map;
import java.util.TreeMap;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * For h*metrics "Metric" json
 * @author Joel Takvorian
 */
class MetricDefinition implements Serializable {
    @JsonProperty("id")
    private final String id;
    @JsonProperty("dataRetention")
    private final int dataRetention;
    @JsonProperty("tags")
    private final Map<String, String> tags = new TreeMap<>();

    MetricDefinition(String id, int dataRetention) {
        this.id = id;
        this.dataRetention = dataRetention;
    }

    void addTag(String key, String value) {
        tags.put(key, value);
    }

    public String getId() {
        return id;
    }

    public int getDataRetention() {
        return dataRetention;
    }

    public Map<String, String> getTags() {
        return tags;
    }
}
