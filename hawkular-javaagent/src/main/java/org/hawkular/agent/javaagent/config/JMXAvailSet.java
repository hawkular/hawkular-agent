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
public class JMXAvailSet implements Validatable {

    @JsonProperty(required = true)
    private String name;

    @JsonProperty
    private Boolean enabled = Boolean.TRUE;

    @JsonProperty("avail-jmx")
    private JMXAvail[] jmxAvails;

    public JMXAvailSet() {
    }

    public JMXAvailSet(JMXAvailSet original) {
        this.name = original.name;
        this.enabled = original.enabled;
        this.jmxAvails = Util.cloneArray(original.jmxAvails);
    }

    @Override
    public void validate() throws Exception {
        if (name == null || name.trim().isEmpty()) {
            throw new Exception("avail-set-jmx name must be specified");
        }

        if (jmxAvails != null) {
            for (JMXAvail o : jmxAvails) {
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
        return enabled;
    }

    public void setEnabled(Boolean enabled) {
        this.enabled = enabled;
    }

    public JMXAvail[] getJmxAvails() {
        return jmxAvails;
    }

    public void setJmxAvails(JMXAvail[] jmxAvails) {
        this.jmxAvails = jmxAvails;
    }
}
