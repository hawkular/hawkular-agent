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

import javax.management.ObjectName;

import org.hawkular.agent.javaagent.Util;

import com.fasterxml.jackson.annotation.JsonAutoDetect;
import com.fasterxml.jackson.annotation.JsonAutoDetect.Visibility;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonAutoDetect( //
        fieldVisibility = Visibility.NONE, //
        getterVisibility = Visibility.NONE, //
        setterVisibility = Visibility.NONE, //
        isGetterVisibility = Visibility.NONE)
public class JMXOperation implements Validatable {

    @JsonProperty(required = true)
    private String name;

    @JsonProperty("object-name")
    private String objectName;

    @JsonProperty("internal-name")
    private String internalName;

    @JsonProperty
    private Boolean modifies = Boolean.FALSE;

    @JsonProperty("params")
    private JMXOperationParam[] jmxOperationParams;

    public JMXOperation() {
    }

    public JMXOperation(JMXOperation original) {
        this.name = original.name;
        this.objectName = original.objectName;
        this.internalName = original.internalName;
        this.modifies = original.modifies;
        this.jmxOperationParams = Util.cloneArray(original.jmxOperationParams);
    }

    @Override
    public void validate() throws Exception {
        if (name == null || name.trim().isEmpty()) {
            throw new Exception("operation-jmx name must be specified");
        }

        if (objectName != null) {
            try {
                new ObjectName(objectName);
            } catch (Exception e) {
                throw new Exception("operation-jmx [" + name + "] object-name [" + objectName + "] is invalid",
                        e);
            }
        }

        if (jmxOperationParams != null) {
            for (JMXOperationParam o : jmxOperationParams) {
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

    public String getObjectName() {
        return objectName;
    }

    public void setObjectName(String objectName) {
        this.objectName = objectName;
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

    public JMXOperationParam[] getJmxOperationParams() {
        return jmxOperationParams;
    }

    public void setJmxOperationParams(JMXOperationParam[] jmxOperationParams) {
        this.jmxOperationParams = jmxOperationParams;
    }
}
