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

import org.hawkular.agent.javaagent.Util;
import org.hawkular.agent.monitor.util.WildflyCompatibilityUtils;

import com.fasterxml.jackson.annotation.JsonAutoDetect;
import com.fasterxml.jackson.annotation.JsonAutoDetect.Visibility;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonAutoDetect( //
        fieldVisibility = Visibility.NONE, //
        getterVisibility = Visibility.NONE, //
        setterVisibility = Visibility.NONE, //
        isGetterVisibility = Visibility.NONE)
public class DMROperation implements Validatable {

    @JsonProperty(required = true)
    private String name;

    @JsonProperty
    private String path = "/";

    @JsonProperty("internal-name")
    private String internalName;

    @JsonProperty
    private Boolean modifies = Boolean.FALSE;

    @JsonProperty("params")
    private DMROperationParam[] dmrOperationParams;

    public DMROperation() {
    }

    public DMROperation(DMROperation original) {
        this.name = original.name;
        this.path = original.path;
        this.internalName = original.internalName;
        this.modifies = original.modifies;
        this.dmrOperationParams = Util.cloneArray(original.dmrOperationParams);
    }

    @Override
    public void validate() throws Exception {
        if (name == null || name.trim().isEmpty()) {
            throw new Exception("operation-dmr name must be specified");
        }

        try {
            if (!"/".equals(path)) {
                WildflyCompatibilityUtils.parseCLIStyleAddress(path);
            }
        } catch (Exception e) {
            throw new Exception("operation-dmr [" + name + "] path [" + path + "] is invalid", e);
        }

        if (dmrOperationParams != null) {
            for (DMROperationParam o : dmrOperationParams) {
                o.validate();
            }
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

    public String getInternalName() {
        return internalName;
    }

    public void setInternalName(String internalName) {
        this.internalName = internalName;
    }

    public Boolean getModifies() {
        return modifies;
    }

    public void setModifies(Boolean modifies) {
        this.modifies = modifies;
    }

    public DMROperationParam[] getDmrOperationParams() {
        return dmrOperationParams;
    }

    public void setDmrOperationParams(DMROperationParam[] dmrOperationParams) {
        this.dmrOperationParams = dmrOperationParams;
    }
}
