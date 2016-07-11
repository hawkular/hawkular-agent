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

package org.hawkular.agent.monitor.dynamicprotocol;

import java.util.HashSet;
import java.util.Set;

import org.hawkular.agent.monitor.inventory.Name;

public class MetricSetMetadata {

    public static class Builder {
        private Name name;
        private boolean enabled;
        private Set<MetricMetadata> metrics = new HashSet<>();

        public Builder name(Name name) {
            this.name = name;
            return this;
        }

        public Builder enabled(boolean enabled) {
            this.enabled = enabled;
            return this;
        }

        public Builder metric(MetricMetadata metric) {
            this.metrics.add(metric);
            return this;
        }

        public MetricSetMetadata build() {
            return new MetricSetMetadata(name, enabled, metrics);
        }
    }

    public static MetricSetMetadata.Builder builder() {
        return new MetricSetMetadata.Builder();
    }

    private final Name name;
    private final boolean enabled;
    private final Set<MetricMetadata> metrics;

    private MetricSetMetadata(Name name, boolean enabled, Set<MetricMetadata> metrics) {
        this.name = name;
        this.enabled = enabled;
        this.metrics = metrics;
    }

    public Name getName() {
        return name;
    }

    public boolean isEnabled() {
        return this.enabled;
    }

    public Set<MetricMetadata> getMetrics() {
        return metrics;
    }
}
