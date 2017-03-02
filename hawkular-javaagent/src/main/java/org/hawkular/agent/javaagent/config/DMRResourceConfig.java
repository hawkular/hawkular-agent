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
package org.hawkular.agent.javaagent.config;

import org.hawkular.agent.monitor.util.WildflyCompatibilityUtils;

import com.fasterxml.jackson.annotation.JsonProperty;

public class DMRResourceConfig implements Validatable {

    @JsonProperty(required = true)
    public String name;

    @JsonProperty
    public String path = "/";

    @JsonProperty(required = true)
    public String attribute;

    @JsonProperty("resolve-expressions")
    public Boolean resolveExpressions = Boolean.FALSE;

    @JsonProperty("include-defaults")
    public Boolean includeDefaults = Boolean.TRUE;

    public DMRResourceConfig() {
    }

    public DMRResourceConfig(DMRResourceConfig original) {
        this.name = original.name;
        this.path = original.path;
        this.attribute = original.attribute;
        this.resolveExpressions = original.resolveExpressions;
        this.includeDefaults = original.includeDefaults;
    }

    @Override
    public void validate() throws Exception {
        if (name == null) {
            throw new Exception("resource-config-dmr name must be specified");
        }

        if (attribute == null) {
            throw new Exception("resource-config-dmr [" + name + "] attribute must be specified");
        }

        try {
            if (!"/".equals(path)) {
                WildflyCompatibilityUtils.parseCLIStyleAddress(path);
            }
        } catch (Exception e) {
            throw new Exception("resource-config-dmr [" + name + "] path [" + path + "] is invalid", e);
        }
    }
}
