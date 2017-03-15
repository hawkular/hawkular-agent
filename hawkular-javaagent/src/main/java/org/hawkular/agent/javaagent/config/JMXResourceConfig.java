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

import com.fasterxml.jackson.annotation.JsonProperty;

public class JMXResourceConfig implements Validatable {

    @JsonProperty(required = true)
    public String name;

    @JsonProperty("object-name")
    public String objectName;

    @JsonProperty(required = true)
    public String attribute;

    public JMXResourceConfig() {
    }

    public JMXResourceConfig(JMXResourceConfig original) {
        this.name = original.name;
        this.objectName = original.objectName;
        this.attribute = original.attribute;
    }

    @Override
    public void validate() throws Exception {
        if (name == null) {
            throw new Exception("resource-config-jmx name must be specified");
        }

        if (attribute == null) {
            throw new Exception("resource-config-jmx [" + name + "] attribute must be specified");
        }

        if (objectName != null) {
            try {
                new ObjectName(objectName);
            } catch (Exception e) {
                throw new Exception("resource-config-jmx [" + name + "] object-name [" + objectName + "] is invalid",
                        e);
            }
        }
    }
}
