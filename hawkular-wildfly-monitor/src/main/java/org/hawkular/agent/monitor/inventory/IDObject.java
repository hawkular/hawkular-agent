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
 * An object that has an ID that can uniquely identify it.
 *
 * @author John Mazzitelli
 */
public abstract class IDObject {
    private final ID id;

    private boolean persisted = false;

    public IDObject(String id) {
        if (id == null) {
            throw new IllegalArgumentException("id cannot be null");
        }
        this.id = new ID(id);
    }

    public IDObject(ID id) {
        if (id == null) {
            throw new IllegalArgumentException("id cannot be null");
        }
        this.id = id;
    }

    public ID getID() {
        return this.id;
    }

    /**
     * @return if true, this object has been registered or persisted in backend storage
     */
    public boolean isPersisted() {
        return persisted;
    }

    public void setPersisted(boolean persisted) {
        this.persisted = persisted;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }

        if (obj == null) {
            return false;
        }

        if (!(obj instanceof IDObject)) {
            return false;
        }
        ID thisID = this.id;
        ID thatID = ((IDObject) obj).id;
        return thisID.equals(thatID);
    }

    @Override
    public int hashCode() {
        return id.hashCode();
    }

    @Override
    public String toString() {
        return String.format("%s[id=%s]", getClass().getSimpleName(), getID());
    }
}
