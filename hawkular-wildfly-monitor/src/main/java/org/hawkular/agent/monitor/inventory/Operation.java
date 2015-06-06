/*
 * Copyright 2015 Red Hat, Inc. and/or its affiliates
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

public abstract class Operation<RT extends ResourceType<?, ?, ?>> extends NamedObject {

    private final RT resourceType;

    public Operation(ID id, Name name, RT resourceType) {
        super(id, name);
        this.resourceType = resourceType;
    }

    public RT getResourceType() {
        return resourceType;
    }

    private static final String OP_NAME_PROP = "operationName";

    public String getOperationName() {
        return (String) getProperties().get(OP_NAME_PROP);
    }

    public void setOperationName(String operationName) {
        getProperties().put(OP_NAME_PROP, operationName);
    }
}