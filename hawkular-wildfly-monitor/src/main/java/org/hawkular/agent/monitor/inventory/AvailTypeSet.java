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

import java.util.HashMap;
import java.util.Map;

public abstract class AvailTypeSet<T extends AvailType> extends NamedObject {

    public AvailTypeSet(ID id, Name name) {
        super(id, name);
    }

    private boolean enabled;
    private Map<Name, T> availTypeMap = new HashMap<>();

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public Map<Name, T> getAvailTypeMap() {
        return availTypeMap;
    }

    public void setAvailTypeMap(Map<Name, T> map) {
        this.availTypeMap = map;
    }

}
