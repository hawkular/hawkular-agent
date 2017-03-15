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
 */
public class Name implements Comparable<Name> {
    private final String name;

    public Name(String name) {
        this.name = name;
    }

    public String getNameString() {
        return this.name;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }

        if (obj == null) {
            return false;
        }

        if (!(obj instanceof Name)) {
            return false;
        }
        String thisName = this.name;
        String thatName = ((Name) obj).name;
        if (thisName == null) {
            return thatName == null;
        }
        return thisName.equals(thatName);
    }

    @Override
    public int hashCode() {
        String n = getNameString();
        return (n != null) ? n.hashCode() : 0;
    }

    /**
     * @return the same as {@link #getNameString()}
     */
    @Override
    public String toString() {
        return getNameString();
    }

    @Override
    public int compareTo(Name other) {
        return this.name.compareTo(other.name);
    }
}
