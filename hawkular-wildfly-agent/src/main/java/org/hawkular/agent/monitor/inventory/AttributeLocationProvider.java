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
 * This abstract class simply is here as a common way for subclasses to be provided
 * an attribute location. Extending subclasses become {@link NamedObject}s as well.
 *
 * @author <a href="https://github.com/ppalaga">Peter Palaga</a>
 */
public abstract class AttributeLocationProvider<L> extends NamedObject {
    private final AttributeLocation<L> attributeLocation;

    public AttributeLocationProvider(ID id, Name name, AttributeLocation<L> attributeLocation) {
        super(id, name);
        if (attributeLocation == null) {
            throw new IllegalArgumentException(
                    "Cannot create a new [" + getClass().getName() + "] with a null attributeLocation");
        }
        this.attributeLocation = attributeLocation;
    }

    public AttributeLocation<L> getAttributeLocation() {
        return attributeLocation;
    }
}
