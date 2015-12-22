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
 * A comnination of an attribute name (in {@link #attribute}) and node location (in {@link #location}).
 *
 * @author <a href="https://github.com/ppalaga">Peter Palaga</a>
 *
 * @param <L> the type of the location of the node this attribute belongs to
 */
public final class AttributeLocation<L> implements Comparable<AttributeLocation<L>> {
    private final String attribute;
    private final String fullPath;
    private final L location;

    public AttributeLocation(L location, String attribute) {
        super();
        this.location = location;
        this.attribute = attribute;
        this.fullPath = location.toString() + "#" + attribute;
    }

    /**
     * @return the name of the attribute
     */
    public String getAttribute() {
        return attribute;
    }

    /**
     * @return the location of the node this attribute belongs to
     */
    public L getLocation() {
        return location;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj)
            return true;
        if (obj == null)
            return false;
        if (getClass() != obj.getClass())
            return false;
        AttributeLocation<?> other = (AttributeLocation<?>) obj;
        if (attribute == null) {
            if (other.attribute != null)
                return false;
        } else if (!attribute.equals(other.attribute))
            return false;
        if (location == null) {
            if (other.location != null)
                return false;
        } else if (!location.equals(other.location))
            return false;
        return true;
    }

    @Override
    public int hashCode() {
        final int prime = 31;
        int result = 1;
        result = prime * result + ((attribute == null) ? 0 : attribute.hashCode());
        result = prime * result + ((location == null) ? 0 : location.hashCode());
        return result;
    }

    /**
     * @param newLocation the location to use when creating a new {@link AttributeLocation} returned by this method
     * @return a new {@link AttributeLocation} created with the given {@code newLocation} and {@link #attribute} taken
     *         from {@code this}
     */
    public AttributeLocation<L> rebase(L newLocation) {
        return new AttributeLocation<L>(newLocation, this.attribute);
    }

    @Override
    public String toString() {
        return fullPath;
    }

    @Override
    public int compareTo(AttributeLocation<L> other) {
        L thisLocation = this.getLocation();
        L otherLocation = other.getLocation();
        if (thisLocation == null) {
            if (otherLocation != null) {
                return -1;
            }
        } else if (otherLocation == null) {
            return 1;
        } else {
            int c = thisLocation.toString().compareTo(otherLocation.toString());
            if (c != 0) {
                return c;
            }
        }

        String thisAttribute = this.getAttribute();
        String otherAttribute = other.getAttribute();
        if (thisAttribute == null) {
            if (otherAttribute != null) {
                return -1;
            }
        } else if (otherAttribute == null) {
            return 1;
        } else {
            int c = thisAttribute.compareTo(otherAttribute);
            if (c != 0) {
                return c;
            }
        }

        return 0;
    }

}
