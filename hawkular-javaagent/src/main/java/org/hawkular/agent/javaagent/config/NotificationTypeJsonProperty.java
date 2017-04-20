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

public class NotificationTypeJsonProperty extends AbstractStringifiedProperty<NotificationType> {

    public NotificationTypeJsonProperty() {
        super();
    }

    public NotificationTypeJsonProperty(NotificationType initialValue) {
        super(initialValue);
    }

    public NotificationTypeJsonProperty(String valueAsString) {
        super(valueAsString);
    }

    public NotificationTypeJsonProperty(NotificationTypeJsonProperty original) {
        super(original);
    }

    @Override
    protected NotificationType deserialize(String valueAsString) {
        if (valueAsString != null) {
            return NotificationType.valueOf(valueAsString.trim().replace("-", "_").toUpperCase());
        } else {
            throw new IllegalArgumentException("Notification type is not specified");
        }
    }

    @Override
    protected String serialize(NotificationType value) {
        if (value != null) {
            return value.name().toLowerCase().replace("_", "-");
        } else {
            throw new IllegalArgumentException("Notification type is not specified");
        }
    }
}
