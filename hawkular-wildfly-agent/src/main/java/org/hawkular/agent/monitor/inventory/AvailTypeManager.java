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

public class AvailTypeManager<T extends AvailType> {

    private final Map<Name, TypeSet<T>> availTypeSetMap = new HashMap<>();

    /**
     * Adds the given types to the manager. If a set is not enabled, it will be ignored.
     *
     * @param availTypeSetMap a full set of types
     * @param setsToUse optional set of type names that the manager cares about - it will ignore others it finds.
     *                  If null, then the full set is used (by "full set" it means the metricTypeSetMap param).
     */
    public void addAvailTypes(Map<Name, TypeSet<T>> availTypeSetMap, Collection<Name> setsToUse) {
        // If setsToUse is null, that means we need to use all the ones in the incoming map.
        // If setsToUse is not null, just use those named sets and ignore the others.
        if (setsToUse == null) {
            this.availTypeSetMap.putAll(availTypeSetMap);
        } else {
            for (Name setToUse : setsToUse) {
                if (availTypeSetMap.containsKey(setToUse)) {
                    TypeSet<T> availSet = availTypeSetMap.get(setToUse);
                    if (availSet.isEnabled()) {
                        this.availTypeSetMap.put(setToUse, availSet);
                    }
                }
            }
        }
    }

    /**
     * Returns the named avail set. If the avail set was disabled, this will return null.
     *
     * @param availSetName the name of the avail set to be returned
     *
     * @return the avail set, or null if the set was disabled
     */
    public TypeSet<T> getAvailSet(Name availSetName) {
        return this.availTypeSetMap.get(availSetName);
    }

    public Collection<TypeSet<T>> getAllAvailTypes() {
        Collection<TypeSet<T>> availTypes = new HashSet<>();
        for (TypeSet<T> availType : this.availTypeSetMap.values()) {
            availTypes.add(availType);
        }
        return availTypes;
    }
}
