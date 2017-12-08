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

import com.fasterxml.jackson.annotation.JsonAutoDetect;
import com.fasterxml.jackson.annotation.JsonAutoDetect.Visibility;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonAutoDetect( //
        fieldVisibility = Visibility.NONE, //
        getterVisibility = Visibility.NONE, //
        setterVisibility = Visibility.NONE, //
        isGetterVisibility = Visibility.NONE)
public class JMXResourceTypeSet implements Validatable {

    @JsonProperty(required = true)
    private String name;

    @JsonProperty
    private BooleanExpression enabled = new BooleanExpression(Boolean.TRUE);

    @JsonProperty("resource-type-jmx")
    private JMXResourceType[] jmxResourceTypes;

    public JMXResourceTypeSet() {
    }

    public JMXResourceTypeSet(JMXResourceTypeSet original) {
        this.name = original.name;
        this.enabled = original.enabled == null ? null : new BooleanExpression(original.enabled);
        this.jmxResourceTypes = Util.cloneArray(original.jmxResourceTypes);
    }

    @Override
    public void validate() throws Exception {
        if (name == null || name.trim().isEmpty()) {
            throw new Exception("resource-type-set-jmx name must be specified");
        }

        if (jmxResourceTypes != null) {
            for (JMXResourceType o : jmxResourceTypes) {
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

    public Boolean getEnabled() {
        return enabled == null ? null : enabled.get();
    }

    public void setEnabled(Boolean enabled) {
        if (this.enabled != null) {
            this.enabled.set(enabled);
        } else {
            this.enabled = new BooleanExpression(enabled);
        }
    }

    public JMXResourceType[] getJmxResourceTypes() {
        return jmxResourceTypes;
    }

    public void setJmxResourceTypes(JMXResourceType[] jmxResourceTypes) {
        this.jmxResourceTypes = jmxResourceTypes;
    }
}
