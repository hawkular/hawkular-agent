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

import org.hawkular.agent.monitor.api.InventoryEvent;
import org.hawkular.agent.monitor.api.InventoryListener;
import org.hawkular.agent.monitor.api.NotificationPayloadBuilder;
import org.hawkular.agent.monitor.config.AgentCoreEngineConfiguration.EndpointConfiguration;
import org.hawkular.agent.monitor.inventory.MonitoredEndpoint;
import org.hawkular.client.api.NotificationType;
import org.hawkular.inventory.paths.CanonicalPath;

/**
 * @author Jay Shaughnessy
 */
public class NotificationDispatcher implements InventoryListener {

    StorageAdapter storageAdapter;
    String feedId;

    public NotificationDispatcher(StorageAdapter storageAdapter, String feedId) {
        this.storageAdapter = storageAdapter;
        this.feedId = feedId;
    }

    @Override public <L> void receivedEvent(InventoryEvent<L> event) {
        MonitoredEndpoint<EndpointConfiguration> endpoint = event.getSamplingService().getMonitoredEndpoint();
        String endpointTenantId = endpoint.getEndpointConfiguration().getTenantId();
        String tenantId = (null != endpointTenantId) ? endpointTenantId
                : storageAdapter.getStorageAdapterConfiguration().getTenantId();

        event.getAddedOrModified().stream()
                .filter(r -> r.getResourceType().getNotifications().contains(NotificationType.RESOURCE_ADDED))
                .forEach(r -> {
                    CanonicalPath cp = CanonicalPath.of()
                            .tenant(tenantId)
                            .feed(feedId)
                            .resource(r.getID().getIDString())
                            .get();
                    try {
                        NotificationPayloadBuilder b = storageAdapter.createNotificationPayloadBuilder();
                        b.addNotificationType(NotificationType.RESOURCE_ADDED);
                        b.addProperty("resourceType", r.getResourceType().getName().getNameString());
                        b.addProperty("resourcePath", cp.toString());
                        storageAdapter.store(b, 0);
                    } catch (Exception e) {

                    }
                });
    }
}
