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
package org.hawkular.wildfly.agent.installer;

public interface TargetConfigInfo {
    /**
     * The root that all other xpaths are relative to.
     */
    String getRootXPath();

    /**
     * @return the XPath that refers to the security-realms element in the config file.
     */
    String getSecurityRealmsXPath();

    /**
     * @return the XPath that refers to the profile element in the config file.
     */
    String getProfileXPath();

    /**
     * @return the resource type sets that should be associated with the managed server definition
     */
    String getManagedServerResourceTypeSets();

    /**
     * @return relative-to property for the target config
     */
    String getRelativeTo();
}
