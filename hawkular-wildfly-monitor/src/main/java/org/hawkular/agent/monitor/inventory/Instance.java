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

/**
 * An abstract supertype for {@link MeasurementInstance} and {@link ResourceConfigurationPropertyInstance}.
 *
 * @author <a href="https://github.com/ppalaga">Peter Palaga</a>
 */
public abstract class Instance<L, T extends AttributeLocationProvider<L>> //
        extends AttributeLocationProvider<L> {

    private final T type;

    public Instance(ID id, Name name, AttributeLocation<L> attributeLocation, T type) {
        super(id, name, attributeLocation);
        this.type = type;
    }

    /**
     * @return the type of this {@link Instance}
     */
    public T getType() {
        return type;
    }

}
