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

import org.hawkular.agent.monitor.util.Util;

/**
 * @author Joel Takvorian
 */
public class InventoryMetric {
    private final String feed;
    private final String type;
    private final String id;

    public InventoryMetric(String feed, String type, String id) {
        this.feed = feed;
        this.type = type;
        this.id = Util.urlEncode(id);
    }

    public static InventoryMetric resource(String feed, String id) {
        return new InventoryMetric(feed, "r", id);
    }

    public static InventoryMetric resourceType(String feed, String id) {
        return new InventoryMetric(feed, "rt", id);
    }

    public static InventoryMetric metricType(String feed, String id) {
        return new InventoryMetric(feed, "mt", id);
    }

    public String getFeed() {
        return feed;
    }

    public String getType() {
        return type;
    }

    public String getId() {
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
        return name();
    }

    public String name() {
        return "inventory." + feed + "." + type + "." + id;
    }
}
