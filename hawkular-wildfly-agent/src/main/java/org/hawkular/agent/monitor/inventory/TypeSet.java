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
import java.util.HashMap;
import java.util.Map;

/**
 * A named, disbaleable collection of types.
 *
 * @author <a href="https://github.com/ppalaga">Peter Palaga</a>
 *
 * @param <T> the type of the values in {@link #typeMap}, one of {@link ResourceType}, {@link MetricType} or
 *            {@link AvailType}
 */
public class TypeSet<T extends NamedObject> extends NamedObject {

    public static class TypeSetBuilder<T extends NamedObject> {
        private boolean enabled = true;
        private ID id;
        private Name name;
        private Map<Name, T> typeMap = new HashMap<>();

        private TypeSetBuilder() {
            super();
        }

        public TypeSet<T> build() {
            return new TypeSet<>(id, name, enabled, Collections.unmodifiableMap(typeMap));
        }

        public TypeSetBuilder<T> enabled(boolean enabled) {
            this.enabled = enabled;
            return this;
        }

        public TypeSetBuilder<T> id(ID id) {
            this.id = id;
            return this;
        }

        public TypeSetBuilder<T> name(Name name) {
            this.name = name;
            return this;
        }

        public TypeSetBuilder<T> type(T type) {
            this.typeMap.put(type.getName(), type);
            return this;
        }
    }

    private static final TypeSet<NodeLocationProvider<?>> EMPTY = new TypeSet<NodeLocationProvider<?>>(
            ID.NULL_ID,
            new Name(TypeSet.class.getSimpleName() + ".EMPTY"), false, Collections.emptyMap());

    public static <T extends NamedObject> TypeSetBuilder<T> builder() {
        return new TypeSetBuilder<>();
    }

    @SuppressWarnings("unchecked")
    public static <T extends NodeLocationProvider<?>> TypeSet<T> empty() {
        return (TypeSet<T>) EMPTY;
    }

    private final boolean enabled;

    private final Map<Name, T> typeMap;

    private TypeSet(ID id, Name name, boolean enabled, Map<Name, T> typeMap) {
        super(id, name);
        this.enabled = enabled;
        this.typeMap = typeMap;
    }

    public Map<Name, T> getTypeMap() {
        return typeMap;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public boolean isDisabledOrEmpty() {
        return !isEnabled() || typeMap == null || typeMap.isEmpty();
    }

}
