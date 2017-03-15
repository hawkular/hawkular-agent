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
package org.hawkular.agent.monitor.inventory;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

/**
 * A generic named, disableable collection of other names.
 */
public class NameSet {

    public static class NameSetBuilder {
        private boolean enabled = true;
        private Name name;
        private Set<Name> nameSet = new HashSet<>();

        private NameSetBuilder() {
            super();
        }

        public NameSet build() {
            return new NameSet(name, enabled, Collections.unmodifiableSet(nameSet));
        }

        public NameSetBuilder enabled(boolean enabled) {
            this.enabled = enabled;
            return this;
        }

        public NameSetBuilder nameOfSet(Name setName) {
            this.name = setName;
            return this;
        }

        public NameSetBuilder name(Name name) {
            this.nameSet.add(name);
            return this;
        }
    }

    public static NameSetBuilder builder() {
        return new NameSetBuilder();
    }

    private final Name name;
    private final boolean enabled;
    private final Set<Name> nameSet;

    private NameSet(Name name, boolean enabled, Set<Name> nameSet) {
        this.name = name;
        this.enabled = enabled;
        this.nameSet = nameSet;
    }

    public Name getName() {
        return name;
    }

    public Set<Name> getNameSet() {
        return nameSet;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public boolean isDisabledOrEmpty() {
        return !isEnabled() || nameSet == null || nameSet.isEmpty();
    }

}
