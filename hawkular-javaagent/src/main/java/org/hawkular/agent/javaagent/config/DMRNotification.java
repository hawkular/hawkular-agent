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

    @JsonProperty(value="name", required = true)
    private NotificationTypeJsonProperty notificationType;

    public DMRNotification() {
    }

    public DMRNotification(DMRNotification original) {
        this.notificationType = original.notificationType == null? null : new NotificationTypeJsonProperty(original.notificationType);
    }

    @Override
    public void validate() throws Exception {
        // NotificationType validates itself
    }

    public NotificationType getNotificationType() {
        return notificationType == null ? null : notificationType.get();
    }

    public void setNotificationType(NotificationType nt) {
        if (notificationType != null) {
            notificationType.set(nt);
        } else {
            notificationType = new NotificationTypeJsonProperty(nt);
        }
    }

}
