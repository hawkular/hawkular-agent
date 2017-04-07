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
package org.hawkular.agent.monitor.api;

import org.hawkular.client.api.Notification;
import org.hawkular.client.api.NotificationType;

/**
 * When the notification data is all added call {@link #toPayload()}
 * to get the payload message that can be used to send to the storage backend via the storage adapter.
 */
public interface NotificationPayloadBuilder {

    /**
     * Add the NotificationType for the notification.
     *
     * @param notificationType Not Null
     */
    void addNotificationType(NotificationType notificationType);

    /**
     * Add a property supported for the given notification type.
     *
     * @param name property name
     * @param value the string value
     */
    void addProperty(String key, String value);

    /**
     * @return the payload in a format suitable for the storage adapter.
     */
    Notification toPayload();
}
