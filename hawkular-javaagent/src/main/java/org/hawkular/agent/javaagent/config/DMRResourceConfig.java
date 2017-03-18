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

import com.fasterxml.jackson.annotation.JsonAutoDetect;
import com.fasterxml.jackson.annotation.JsonAutoDetect.Visibility;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonAutoDetect( //
        fieldVisibility = Visibility.NONE, //
        getterVisibility = Visibility.NONE, //
        setterVisibility = Visibility.NONE, //
        isGetterVisibility = Visibility.NONE)
public class DMRResourceConfig implements Validatable {

    @JsonProperty(required = true)
    private String name;

    @JsonProperty
    private String path = "/";

    @JsonProperty(required = true)
    private String attribute;

    @JsonProperty("resolve-expressions")
    private Boolean resolveExpressions = Boolean.FALSE;

    @JsonProperty("include-defaults")
    private Boolean includeDefaults = Boolean.TRUE;

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
        if (name == null || name.trim().isEmpty()) {
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

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getPath() {
        return path;
    }

    public void setPath(String path) {
        this.path = path;
    }

    public String getAttribute() {
        return attribute;
    }

    public void setAttribute(String attribute) {
        this.attribute = attribute;
    }

    public Boolean getResolveExpressions() {
        return resolveExpressions;
    }

    public void setResolveExpressions(Boolean resolveExpressions) {
        this.resolveExpressions = resolveExpressions;
    }

    public Boolean getIncludeDefaults() {
        return includeDefaults;
    }

    public void setIncludeDefaults(Boolean includeDefaults) {
        this.includeDefaults = includeDefaults;
    }
}
