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
    private final Name name;

    public NamedObject(String id, String name) {
        super(id);

        if (name == null) {
            throw new IllegalArgumentException("name cannot be null");
        }
        this.name = new Name(name);
    }

    public NamedObject(ID id, Name name) {
        super(id);

        if (name == null) {
            throw new IllegalArgumentException("name cannot be null");
        }
        this.name = name;
    }

    public Name getName() {
        return this.name;
    }

    /**
     * IDs are checked for equality between named objects.
     * However, if neither this object nor the object passed in has a
     * non-null ID, name will be checked for equality.
     *
     * @param object to test for equality with this object
     * @return equality based on ID or name if ID is null
     */
    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }

        if (obj == null) {
            return false;
        }

        if (!(obj instanceof NamedObject)) {
            return false;
        }

        if (!super.equals(obj)) {
            return false;
        }

        if (super.getID().getIDString() != null) {
            // If our ID is not null, then obj's ID must also be non-null since it was equal to our ID.
            // Thus both objects being compared have non-null IDs and they are equal,
            // so we consider both objects equal as well.
            return true;
        }

        // both IDs were null, so let's fall back and check name for equality
        Name thisName = name;
        Name thatName = ((NamedObject) obj).name;
        return thisName.equals(thatName);
    }

    /**
     * Returns the hash code of this object's ID. If this object's ID is a null ID
     * then the hash code will be that of the name.
     *
     * @return hash code
     */
    @Override
    public int hashCode() {
        if (super.getID().getIDString() == null) {
            return name.hashCode();
        } else {
            return super.hashCode();
        }
    }

    @Override
    public String toString() {
        return String.format("%s[name=%s]", super.toString(), getName());
    }
}
