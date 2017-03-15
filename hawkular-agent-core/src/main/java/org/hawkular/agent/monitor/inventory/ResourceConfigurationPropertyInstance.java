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

/**
 * @author John Mazzitelli
 *
 * @param <L> the type of the protocol specific location, typically a subclass of {@link NodeLocation}
 */
public final class ResourceConfigurationPropertyInstance<L> extends Instance<L, ResourceConfigurationPropertyType<L>> {

    private final String value;

    public ResourceConfigurationPropertyInstance(ID id, Name name, AttributeLocation<L> attributeLocation,
            ResourceConfigurationPropertyType<L> type, String value) {
        super(id, name, attributeLocation, type);
        this.value = value;
    }

    // copy-constructor
    public ResourceConfigurationPropertyInstance(ResourceConfigurationPropertyInstance<L> original, boolean disown) {
        super(original, disown);
        this.value = original.value;
    }

    public String getValue() {
        return value;
    }

    @Override
    public String toString() {
        return String.format("%s[value=%s]", super.toString(), getValue());
    }
}
