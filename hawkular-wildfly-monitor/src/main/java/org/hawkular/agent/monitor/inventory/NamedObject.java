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
 * An object that has an associated name as well as an ID.
 */
public abstract class NamedObject extends IDObject {
    public static final String NAME = "name"; // the property name where the object's name is stored

    /**
     * Creates a named object.
     *
     * @param id the id of the object; if null, name will be used as its ID
     * @param name the name of the object; must not be null
     */
    public NamedObject(ID id, Name name) {
        super((id != null && !id.equals(ID.NULL_ID)) ? id : (name != null) ? new ID(name.getNameString()) : null);

        if (name == null) {
            throw new IllegalArgumentException("name cannot be null");
        }
        if (name.getNameString() == null) {
            throw new IllegalArgumentException("name string cannot be null");
        }
        addProperty(NAME, name.getNameString());
    }

    public Name getName() {
        return new Name(getProperties().get(NAME).toString());
    }
}
