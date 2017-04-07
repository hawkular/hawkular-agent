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

import org.hawkular.client.api.NotificationType;

import com.fasterxml.jackson.annotation.JsonAutoDetect;
import com.fasterxml.jackson.annotation.JsonAutoDetect.Visibility;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * The Notification name should map to a defined NotificationType using the transform:
 * <pre>trim().replace("-","_").toUpperCase()</pre>
 *
 * <p>For example, "resource-added" -> NotificationType.RESOURCE_ADDED.</p>
 *
 * @author jay shaughnessy
 */
@JsonAutoDetect( //
        fieldVisibility = Visibility.NONE, //
        getterVisibility = Visibility.NONE, //
        setterVisibility = Visibility.NONE, //
        isGetterVisibility = Visibility.NONE)
public class DMRNotification implements Validatable {

    @JsonProperty(required = true)
    private String name;

    @JsonIgnore
    NotificationType notificationType;

    public DMRNotification() {
    }

    public DMRNotification(DMRNotification original) {
        setName(original.name);
    }

    @Override
    public void validate() throws Exception {
        if (name == null || name.trim().isEmpty()) {
            throw new Exception("notification name must be specified");
        }

        if (null == notificationType) {
            throw new Exception("notification name [" + name + "] is an unknown notification type");
        }
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
        try {
            this.notificationType = NotificationType.valueOf(name.trim().replace("-", "_").toUpperCase());
        } catch (Exception e) {
            // ignore, will be reported in validate
        }
    }

    public NotificationType getNotificationType() {
        return notificationType;
    }
}
