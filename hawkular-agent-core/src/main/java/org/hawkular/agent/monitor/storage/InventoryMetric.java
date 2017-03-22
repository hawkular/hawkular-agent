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

import java.util.Collections;
import java.util.Set;
import java.util.stream.Collectors;

import org.hawkular.agent.monitor.util.Util;

/**
 * @author Joel Takvorian
 */
class InventoryMetric {

    // FIXME: what retention? deactivate?
    private static final int DATA_RETENTION = 90;

    private final String feed;
    private final String type;
    private final String id;
    private final Set<String> resourceTypes;

    private InventoryMetric(String feed, String type, String id, Set<String> resourceTypes) {
        this.feed = feed;
        this.type = type;
        this.id = id;
        this.resourceTypes = resourceTypes;
    }

    static InventoryMetric resource(String feed, String id, Set<String> resourceTypes) {
        return new InventoryMetric(feed, "r", id, resourceTypes);
    }

    static InventoryMetric resourceType(String feed, String id) {
        return new InventoryMetric(feed, "rt", id, null);
    }

    static InventoryMetric metricType(String feed, String id) {
        return new InventoryMetric(feed, "mt", id, null);
    }

    String getFeed() {
        return feed;
    }

    String getType() {
        return type;
    }

    String getId() {
        return id;
    }

    @Override public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        InventoryMetric that = (InventoryMetric) o;

        if (feed != null ? !feed.equals(that.feed) : that.feed != null) return false;
        if (type != null ? !type.equals(that.type) : that.type != null) return false;
        return id != null ? id.equals(that.id) : that.id == null;
    }

    @Override public int hashCode() {
        int result = feed != null ? feed.hashCode() : 0;
        result = 31 * result + (type != null ? type.hashCode() : 0);
        result = 31 * result + (id != null ? id.hashCode() : 0);
        return result;
    }

    @Override public String toString() {
        return baseName();
    }

    public String baseName() {
        return "inventory." + feed + "." + type + "." + id;
    }

    public InventoryMetricChunk chunk(InventoryStringDataPoint chunk) {
        return new InventoryMetricChunk(chunk);
    }

    public class InventoryMetricChunk {
        private final String chunkId;
        private final InventoryStringData payload;

        private InventoryMetricChunk(final InventoryStringDataPoint chunk) {
            this.chunkId = chunk.getTags().get("chunk");
            payload = new InventoryStringData(name(), Collections.singletonList(chunk));
        }

        public String name() {
            if (chunkId.equals("0")) {
                return baseName();
            }
            return baseName() + "." + chunkId;
        }

        public String encodedName() {
            return Util.urlEncode(name());
        }

        public InventoryStringData getPayload() {
            return payload;
        }

        @Override public String toString() {
            return name();
        }

        public MetricDefinition toMetricDefinition() {
            MetricDefinition def = new MetricDefinition(name(), DATA_RETENTION);
            def.addTag("module", "inventory");
            def.addTag("feed", feed);
            def.addTag("type", type);
            def.addTag("id", id);
            def.addTag("chunk", chunkId);
            if (resourceTypes != null) {
                def.addTag("restypes",
                        "|" + resourceTypes.stream().collect(Collectors.joining("|")) + "|");
            }
            return def;
        }
    }
}
