/*
 * Copyright 2015-2016 Red Hat, Inc. and/or its affiliates
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
package org.hawkular.agent.monitor.inventory;

/**
 * Immutable object that describes a single operation parameter.
 *
 * @author hrupp
 */
public class OperationParam {

    private final String name;
    private final String type;
    private final String description;
    private final String defaultValue;
    private final Boolean required;

    public OperationParam(String name, String type, String description, String defaultValue, Boolean required) {
        this.name = name;
        this.type = type;
        this.description = description;
        this.defaultValue = defaultValue;
        this.required = required;
    }

    public String getName() {
        return name;
    }

    public String getType() {
        return type;
    }

    public String getDescription() {
        return description;
    }

    public String getDefaultValue() {
        return defaultValue;
    }

    @Override
    public String toString() {
        StringBuilder str = new StringBuilder("OperationParam: ");
        str.append("name=[").append(this.name);
        str.append("], type=[").append(this.type);
        str.append("], description=[").append(this.description);
        str.append("], defaultValue=[").append(this.defaultValue);
        str.append("], required=[").append(this.required);
        str.append("]");
        return str.toString();
    }
}
