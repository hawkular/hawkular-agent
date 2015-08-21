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
 * Can be used to identify object instances.
 */
public class ID {
    public static final ID NULL_ID = new ID(null);

    private final String id;

    public ID(String id) {
        this.id = id != null && id.endsWith("/") ? id.substring(0, id.length() - 1) + '~' : id;
    }

    /**
     * @return the actual ID string, or null if this object represents a null ID.
     */
    public String getIDString() {
        return this.id;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }

        if (obj == null) {
            return false;
        }

        if (!(obj instanceof ID)) {
            return false;
        }
        String thisIDString = getIDString();
        String thatIDString = ((ID) obj).getIDString();
        if (thisIDString == null) {
            return thatIDString == null;
        }
        return thisIDString.equals(thatIDString);
    }

    @Override
    public int hashCode() {
        String i = getIDString();
        return (i != null) ? i.hashCode() : 0;
    }

    /**
     * @return the same string that is returned by {@link #getIDString()}.
     */
    @Override
    public String toString() {
        return getIDString();
    }
}
