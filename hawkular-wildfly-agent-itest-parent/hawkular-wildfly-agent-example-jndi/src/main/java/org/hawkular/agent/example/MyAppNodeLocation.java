/*
 * Copyright 2015-2016 Red Hat, Inc. and/or its affiliates
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
package org.hawkular.agent.example;

import org.hawkular.agent.monitor.inventory.NodeLocation;

/**
 * Provides a way to identify my app's managed resources.
 */
public class MyAppNodeLocation implements NodeLocation {

    private final String location;

    public MyAppNodeLocation(String location) {
        if (location == null) {
            throw new IllegalArgumentException("location cannot be null");
        }
        this.location = location;
    }

    public String getLocation() {
        return location;
    }

    @Override
    public int hashCode() {
        return this.location.hashCode();
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (!(obj instanceof MyAppNodeLocation)) {
            return false;
        }
        MyAppNodeLocation other = (MyAppNodeLocation) obj;
        return this.location.equals(other.location);
    }

    @Override
    public String toString() {
        return this.location;
    }
}
