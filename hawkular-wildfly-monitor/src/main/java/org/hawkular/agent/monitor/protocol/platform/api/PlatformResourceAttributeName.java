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
package org.hawkular.agent.monitor.protocol.platform.api;

import static org.hawkular.agent.monitor.protocol.platform.api.PlatformResourceAttributeName.toEnumMap;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

public interface PlatformResourceAttributeName {
    enum FileStoreAttribute implements PlatformResourceAttributeName {
        description,
        name,
        totalSpace,
        usableSpace
        ;
        public static Set<String> stringSet() {
            return VALUE_MAP.keySet();
        }
        private static final Map<String, FileStoreAttribute> VALUE_MAP = toEnumMap(values());
        public static FileStoreAttribute valueOfOrFail(String name) {
            FileStoreAttribute result = VALUE_MAP.get(name);
            if (result == null) {
                throw new IllegalStateException("No such name in "+ FileStoreAttribute.class.getName());
            }
            return result;
        }
    }
    enum MemoryAttribute implements PlatformResourceAttributeName {
        available,
        total;

        public static Set<String> stringSet() {
            return VALUE_MAP.keySet();
        }
        private static final Map<String, MemoryAttribute> VALUE_MAP = toEnumMap(values());
        public static MemoryAttribute valueOfOrFail(String name) {
            MemoryAttribute result = VALUE_MAP.get(name);
            if (result == null) {
                throw new IllegalStateException("No such name in "+ MemoryAttribute.class.getName());
            }
            return result;
        }

    }
    enum PlatformAttribute implements PlatformResourceAttributeName {
        description;

        public static Set<String> stringSet() {
            return VALUE_MAP.keySet();
        }
        private static final Map<String, PlatformAttribute> VALUE_MAP = toEnumMap(values());
        public static PlatformAttribute valueOfOrFail(String name) {
            PlatformAttribute result = VALUE_MAP.get(name);
            if (result == null) {
                throw new IllegalStateException("No such name in "+ PlatformAttribute.class.getName());
            }
            return result;
        }

    }
    enum PowerSourceAttribute implements PlatformResourceAttributeName {
        name,remainingCapacity, timeRemaining;

        public static Set<String> stringSet() {
            return VALUE_MAP.keySet();
        }
        private static final Map<String, PowerSourceAttribute> VALUE_MAP = toEnumMap(values());
        public static PowerSourceAttribute valueOfOrFail(String name) {
            PowerSourceAttribute result = VALUE_MAP.get(name);
            if (result == null) {
                throw new IllegalStateException("No such name in "+ PowerSourceAttribute.class.getName());
            }
            return result;
        }

    }

    enum ProcessorAttribute implements PlatformResourceAttributeName {
        name, processorNumber, load;

        public static Set<String> stringSet() {
            return VALUE_MAP.keySet();
        }
        private static final Map<String, ProcessorAttribute> VALUE_MAP = toEnumMap(values());
        public static ProcessorAttribute valueOfOrFail(String name) {
            ProcessorAttribute result = VALUE_MAP.get(name);
            if (result == null) {
                throw new IllegalStateException("No such name in "+ ProcessorAttribute.class.getName());
            }
            return result;
        }
    }

    String NAME = "name";

    static <E extends PlatformResourceAttributeName> Map<String, E> toEnumMap(E[] values) {
        Map<String, E> m = new HashMap<String, E>();
        for (E e : values) {
            m.put(e.toString(), e);
        }
        return Collections.unmodifiableMap(m);
    }

    String toString();
}