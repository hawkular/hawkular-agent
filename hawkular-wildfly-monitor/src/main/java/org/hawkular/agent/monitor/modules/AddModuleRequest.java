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

package org.hawkular.agent.monitor.modules;

import java.util.Collections;
import java.util.Map;
import java.util.Set;

/**
 * Data necessary for the creation of a new JBoss Module. See
 * https://docs.jboss.org/author/display/MODULES/Module+descriptors
 *
 */
public class AddModuleRequest {

    /**
     * The name of the JBoss Module to create, such as "org.example.jdbc.driver" (Required)
     *
     */
    private final java.lang.String moduleName;
    /**
     * The version slot of the module. If not specified, defaults to "main".
     *
     */
    private final java.lang.String slot;
    /**
     * The main class of this module, if any.
     *
     */
    private final java.lang.String mainClass;
    /**
     * List of resource paths relative to module folder on the server. (Required)
     *
     */
    private final Set<java.lang.String> resources;
    /**
     * List of the dependencies for this module.
     *
     */
    private final Set<java.lang.String> dependencies;
    /**
     * A simple map of named parameters.
     *
     */
    private final Map<String, String> properties;

    public AddModuleRequest(String moduleName, String slot, String mainClass, Set<String> resources,
            Set<String> dependencies, Map<String, String> properties) {
        super();
        this.moduleName = moduleName;
        this.slot = slot;
        this.mainClass = mainClass;
        this.resources = resources == null ? Collections.emptySet() : resources;
        this.dependencies = dependencies == null ? Collections.emptySet() : dependencies;
        this.properties = properties == null ? Collections.emptyMap() : properties;
    }

    /**
     * The name of the JBoss Module to create, such as "org.example.jdbc.driver" (Required)
     *
     * @return The moduleName
     */
    public java.lang.String getModuleName() {
        return moduleName;
    }

    /**
     * The version slot of the module. If not specified, defaults to "main".
     *
     * @return The slot
     */
    public java.lang.String getSlot() {
        return slot;
    }

    /**
     * The main class of this module, if any.
     *
     * @return The mainClass
     */
    public java.lang.String getMainClass() {
        return mainClass;
    }

    /**
     * List of resource paths relative to module folder on the server. (Required)
     *
     * @return The resources
     */
    public Set<java.lang.String> getResources() {
        return resources;
    }

    /**
     * List of the dependencies for this module.
     *
     * @return The dependencies
     */
    public Set<java.lang.String> getDependencies() {
        return dependencies;
    }

    /**
     * A simple map of named parameters.
     *
     * @return The properties
     */
    public Map<String, String> getProperties() {
        return properties;
    }

}
