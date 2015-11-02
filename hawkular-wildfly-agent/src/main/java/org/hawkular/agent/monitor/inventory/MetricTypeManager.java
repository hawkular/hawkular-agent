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

import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

public class MetricTypeManager<T extends MetricType> {

    private final Map<Name, TypeSet<T>> metricTypeSetMap = new HashMap<Name, TypeSet<T>>();

    /**
     * Adds the given types to the manager. If a set is not enabled, it will be ignored.
     *
     * @param metricTypeSetMap a full set of types
     * @param setsToUse optional set of type names that the manager cares about - it will ignore others it finds.
     *                  If null, then the full set is used (by "full set" it means the metricTypeSetMap param).
     */
    public void addMetricTypes(Map<Name, TypeSet<T>> metricTypeSetMap, Collection<Name> setsToUse) {
        // If setsToUse is null, that means we need to use all the ones in the incoming map.
        // If setsToUse is not null, just use those named sets and ignore the others.
        if (setsToUse == null) {
            this.metricTypeSetMap.putAll(metricTypeSetMap);
        } else {
            for (Name setToUse : setsToUse) {
                if (metricTypeSetMap.containsKey(setToUse)) {
                    TypeSet<T> metricSet = metricTypeSetMap.get(setToUse);
                    if (metricSet.isEnabled()) {
                        this.metricTypeSetMap.put(setToUse, metricSet);
                    }
                }
            }
        }
    }

    /**
     * Returns the named metric set. If the metric set was disabled, this will return null.
     *
     * @param metricSetName the name of the metric set to be returned
     *
     * @return the metric set, or null if the set was disabled
     */
    public TypeSet<T> getMetricSet(Name metricSetName) {
        return this.metricTypeSetMap.get(metricSetName);
    }

    public Collection<TypeSet<T>> getAllMetricTypes() {
        Set<TypeSet<T>> metricTypes = new HashSet<>();
        for (TypeSet<T> metricType : this.metricTypeSetMap.values()) {
            metricTypes.add(metricType);
        }
        return metricTypes;
    }
}
