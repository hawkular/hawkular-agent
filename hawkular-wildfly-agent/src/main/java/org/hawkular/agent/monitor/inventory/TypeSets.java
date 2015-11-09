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
package org.hawkular.agent.monitor.inventory;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

import org.hawkular.agent.monitor.log.AgentLoggers;
import org.hawkular.agent.monitor.log.MsgLogger;

/**
 * A bundle of various {@link TypeSet}s.
 *
 * @author <a href="https://github.com/ppalaga">Peter Palaga</a>
 *
 * @param <L> the type of the protocol specific location, typically a subclass of {@link NodeLocation}
 */
public class TypeSets<L> {
    private static final MsgLogger log = AgentLoggers.getLogger(TypeSets.class);

    public static <L> Builder<L> builder() {
        return new Builder<L>();
    }

    public static class Builder<L> {
        private Map<Name, TypeSet<AvailType<L>>> availTypeSets = new LinkedHashMap<>();
        private boolean enabled = true;
        private Map<Name, TypeSet<MetricType<L>>> metricTypeSets = new LinkedHashMap<>();
        private Map<Name, TypeSet<ResourceType<L>>> resourceTypeSets = new LinkedHashMap<>();

        public TypeSets<L> build() {

            /* warn if there is something suspicious */
            for (TypeSet<ResourceType<L>> resourceTypeSet : resourceTypeSets.values()) {
                for (ResourceType<L> type : resourceTypeSet.getTypeMap().values()) {
                    for (Name metricSetName : type.getMetricSets()) {
                        if (!metricTypeSets.containsKey(metricSetName)) {
                            log.warnMetricSetDoesNotExist(type.getName().toString(), metricSetName.toString());
                        }
                    }
                    for (Name availSetName : type.getAvailSets()) {
                        if (!availTypeSets.containsKey(availSetName)) {
                            log.warnAvailSetDoesNotExist(type.getName().toString(),
                                    availSetName.toString());
                        }
                    }
                }
            }

            return new TypeSets<>(Collections.unmodifiableMap(resourceTypeSets),
                    Collections.unmodifiableMap(metricTypeSets), Collections.unmodifiableMap(availTypeSets),
                    enabled);
        }

        public Builder<L> enabled(boolean enabled) {
            this.enabled = enabled;
            return this;
        }

        public Builder<L> availTypeSet(TypeSet<AvailType<L>> typeSet) {
            availTypeSets.put(typeSet.getName(), typeSet);
            return this;
        }

        public Builder<L> metricTypeSet(TypeSet<MetricType<L>> typeSet) {
            metricTypeSets.put(typeSet.getName(), typeSet);
            return this;
        }

        public Builder<L> resourceTypeSet(TypeSet<ResourceType<L>> typeSet) {
            resourceTypeSets.put(typeSet.getName(), typeSet);
            return this;
        }
    }

    private static final TypeSets<?> EMPTY = new TypeSets<>(
            Collections.emptyMap(), Collections.emptyMap(), Collections.emptyMap(), false);

    @SuppressWarnings("unchecked")
    public static <L> TypeSets<L> empty() {
        return (TypeSets<L>) EMPTY;
    }

    private final Map<Name, TypeSet<AvailType<L>>> availTypeSets;
    private final boolean enabled;
    private final Map<Name, TypeSet<MetricType<L>>> metricTypeSets;
    private final Map<Name, TypeSet<ResourceType<L>>> resourceTypeSets;

    private TypeSets(Map<Name, TypeSet<ResourceType<L>>> resourceTypeSets,
            Map<Name, TypeSet<MetricType<L>>> metricTypeSets,
            Map<Name, TypeSet<AvailType<L>>> availTypeSets, boolean enabled) {
        super();
        this.resourceTypeSets = resourceTypeSets;
        this.metricTypeSets = metricTypeSets;
        this.availTypeSets = availTypeSets;
        this.enabled = enabled;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public Map<Name, TypeSet<AvailType<L>>> getAvailTypeSets() {
        return availTypeSets;
    }

    public Map<Name, TypeSet<MetricType<L>>> getMetricTypeSets() {
        return metricTypeSets;
    }

    public Map<Name, TypeSet<ResourceType<L>>> getResourceTypeSets() {
        return resourceTypeSets;
    }
}
