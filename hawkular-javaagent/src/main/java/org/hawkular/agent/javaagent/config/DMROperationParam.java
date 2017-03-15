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

import com.fasterxml.jackson.annotation.JsonProperty;

public class DMROperationParam implements Validatable {

    @JsonProperty(required = true)
    public String name;

    @JsonProperty
    public String type = "string";

    @JsonProperty("default-value")
    public String defaultValue;

    @JsonProperty
    public String description;

    @JsonProperty
    public Boolean required = Boolean.FALSE;

    public DMROperationParam() {
    }

    public DMROperationParam(DMROperationParam original) {
        this.name = original.name;
        this.type = original.type;
        this.defaultValue = original.defaultValue;
        this.description = original.description;
        this.required = original.required;
    }

    @Override
    public void validate() throws Exception {
        if (name == null) {
            throw new Exception("DMR operation parameter name must be specified");
        }
    }
}
