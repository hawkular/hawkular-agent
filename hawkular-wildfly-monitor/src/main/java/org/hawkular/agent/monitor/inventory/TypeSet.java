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

public class TypeSet<T extends NamedObject> extends NamedObject {

    public static class TypeSetBuilder<BT extends NamedObject> {
        private boolean enabled = true;
        private ID id;
        private Name name;
        private Map<Name, BT> typeMap = new HashMap<>();

        private TypeSetBuilder() {
            super();
        }

        public TypeSet<BT> build() {
            return new TypeSet<BT>(id, name, enabled, Collections.unmodifiableMap(typeMap));
        }

        public TypeSetBuilder<BT> enabled(boolean enabled) {
            this.enabled = enabled;
            return this;
        }

        public TypeSetBuilder<BT> id(ID id) {
            this.id = id;
            return this;
        }

        public TypeSetBuilder<BT> name(Name name) {
            this.name = name;
            return this;
        }

        public TypeSetBuilder<BT> type(BT type) {
            this.typeMap.put(type.getName(), type);
            return this;
        }
    }

    private static final TypeSet<NamedObject> EMPTY = new TypeSet<NamedObject>(ID.NULL_ID,
            new Name(TypeSet.class.getSimpleName() + ".EMPTY"), false, Collections.emptyMap());

    public static <T extends NamedObject> TypeSetBuilder<T> builder() {
        return new TypeSetBuilder<T>();
    }

    @SuppressWarnings("unchecked")
    public static <T extends NamedObject> TypeSet<T> empty() {
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
