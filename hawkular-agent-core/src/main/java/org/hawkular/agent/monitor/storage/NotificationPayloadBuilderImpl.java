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
package org.hawkular.agent.monitor.storage;

import java.util.HashMap;
import java.util.Map;

import org.hawkular.agent.monitor.api.NotificationPayloadBuilder;
import org.hawkular.client.api.Notification;
import org.hawkular.client.api.NotificationType;

/**
 * Allows one to build up a payload request to send to metric storage by adding
 * data points one by one. After all data points are added, you can get the payload in
 * either an {@link #toObjectPayload() object} format or a {@link #toPayload() JSON} format.
 */
public class NotificationPayloadBuilderImpl implements NotificationPayloadBuilder {

    private NotificationType notificationType;
    private Map<String, String> properties = new HashMap<>();

    @Override
    public void addNotificationType(NotificationType notificationType) {
        this.notificationType = notificationType;

    }

    @Override
    public void addProperty(String name, String value) {
        properties.put(name, value);
    }

    @Override
    public Notification toPayload() {
        return new Notification(notificationType, properties);
    }
}