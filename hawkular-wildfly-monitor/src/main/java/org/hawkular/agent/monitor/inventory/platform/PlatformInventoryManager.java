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
package org.hawkular.agent.monitor.inventory.platform;

import org.hawkular.agent.monitor.inventory.InventoryManager;
import org.hawkular.agent.monitor.inventory.ManagedServer;
import org.hawkular.agent.monitor.inventory.MetadataManager;
import org.hawkular.agent.monitor.inventory.ResourceManager;
import org.hawkular.agent.monitor.log.AgentLoggers;
import org.hawkular.agent.monitor.log.MsgLogger;
import org.hawkular.agent.monitor.scheduler.config.PlatformEndpoint;
import org.jgrapht.event.VertexSetListener;

import oshi.SystemInfo;

public class PlatformInventoryManager extends InventoryManager
        <PlatformResourceType,
        PlatformMetricType,
        PlatformAvailType,
        PlatformOperation,
        PlatformResourceConfigurationPropertyType,
        PlatformResource,
        PlatformEndpoint> {

    private static final MsgLogger log = AgentLoggers.getLogger(PlatformInventoryManager.class);
    private final SystemInfo systemInfo;

    public PlatformInventoryManager(String feedId,
            MetadataManager<PlatformResourceType, PlatformMetricType,
            PlatformAvailType, PlatformOperation,
            PlatformResourceConfigurationPropertyType> metadataManager,
            ResourceManager<PlatformResource> resourceManager,
            ManagedServer managedServer,
            PlatformEndpoint endpoint) {
        super(feedId, metadataManager, resourceManager, managedServer, endpoint);
        systemInfo = new SystemInfo();
    }

    @Override
    public void discoverResources(VertexSetListener<PlatformResource> listener) {
        try {
            PlatformDiscovery discovery = new PlatformDiscovery(this);
            discovery.discoverAllResources(listener);
        } catch (Exception e) {
            log.errorDiscoveryFailed(e, getEndpoint());
        }
    }

    /**
     * @return object that can be used to obtain platform data
     */
    public SystemInfo getSystemInfo() {
        return this.systemInfo;
    }
}
