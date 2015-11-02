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
import java.util.Map;

public class TypeSets< //
RT extends ResourceType<MT, AT, ? extends Operation, ? extends ResourceConfigurationPropertyType>, //
MT extends MetricType, //
AT extends AvailType> {

    private static final TypeSets<ResourceType<MetricType, AvailType, Operation, ResourceConfigurationPropertyType>, //
    MetricType, AvailType> EMPTY = new TypeSets<>(
            Collections.emptyMap(), Collections.emptyMap(), Collections.emptyMap(), false);

    @SuppressWarnings("unchecked")
    public static < //
    RT extends ResourceType<MT, AT, ? extends Operation, ? extends ResourceConfigurationPropertyType>, //
    MT extends MetricType, //
    AT extends AvailType> TypeSets<RT, MT, AT> empty() {
        return (TypeSets<RT, MT, AT>) EMPTY;
    }

    private final Map<Name, TypeSet<AT>> availTypeSets;
    private final boolean enabled;
    private final Map<Name, TypeSet<MT>> metricTypeSets;
    private final Map<Name, TypeSet<RT>> resourceTypeSets;

    public TypeSets(Map<Name, TypeSet<RT>> resourceTypeSets, Map<Name, TypeSet<MT>> metricTypeSets,
            Map<Name, TypeSet<AT>> availTypeSets, boolean enabled) {
        super();
        this.resourceTypeSets = resourceTypeSets;
        this.metricTypeSets = metricTypeSets;
        this.availTypeSets = availTypeSets;
        this.enabled = enabled;
    }

    public Map<Name, TypeSet<AT>> getAvailTypeSets() {
        return availTypeSets;
    }

    public Map<Name, TypeSet<MT>> getMetricTypeSets() {
        return metricTypeSets;
    }

    public Map<Name, TypeSet<RT>> getResourceTypeSets() {
        return resourceTypeSets;
    }

    public boolean isEnabled() {
        return enabled;
    }
}
