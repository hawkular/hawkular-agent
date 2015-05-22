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

import java.util.Collection;
import java.util.HashSet;

public abstract class ManagedServer extends NamedObject {

    public ManagedServer(String name) {
        super(name);
    }

    private boolean enabled;
    private Collection<Name> resourceTypeSets = new HashSet<>();

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public Collection<Name> getResourceTypeSets() {
        return resourceTypeSets;
    }

    public void setResourceTypeSets(Collection<Name> resourceTypeSets) {
        this.resourceTypeSets = resourceTypeSets;
    }

}
