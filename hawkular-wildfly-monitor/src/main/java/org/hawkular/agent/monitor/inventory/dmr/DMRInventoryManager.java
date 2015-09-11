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
package org.hawkular.agent.monitor.inventory.dmr;

import org.hawkular.agent.monitor.inventory.InventoryManager;
import org.hawkular.agent.monitor.inventory.ManagedServer;
import org.hawkular.agent.monitor.inventory.MetadataManager;
import org.hawkular.agent.monitor.inventory.ResourceManager;
import org.hawkular.agent.monitor.log.MsgLogger;
import org.hawkular.agent.monitor.scheduler.ModelControllerClientFactory;
import org.hawkular.agent.monitor.scheduler.config.DMREndpoint;
import org.jgrapht.event.VertexSetListener;

public class DMRInventoryManager extends InventoryManager
        <DMRResourceType,
        DMRResourceTypeSet,
        DMRMetricType,
        DMRMetricTypeSet,
        DMRAvailType,
        DMRAvailTypeSet,
        DMROperation,
        DMRResourceConfigurationPropertyType,
        DMRResource,
        DMREndpoint> {

    private final ModelControllerClientFactory dmrClientFactory;

    public DMRInventoryManager(String feedId,
            MetadataManager<DMRResourceType, DMRResourceTypeSet, DMRMetricType, DMRMetricTypeSet,
            DMRAvailType, DMRAvailTypeSet, DMROperation, DMRResourceConfigurationPropertyType> metadataManager,
            ResourceManager<DMRResource> resourceManager,
            ManagedServer managedServer,
            DMREndpoint dmrEndpoint,
            ModelControllerClientFactory dmrClientFactory) {
        super(feedId, metadataManager, resourceManager, managedServer, dmrEndpoint);
        this.dmrClientFactory = dmrClientFactory;
    }

    @Override
    public void discoverResources(VertexSetListener<DMRResource> listener) {
        try {
            DMRDiscovery discovery = new DMRDiscovery(this);
            discovery.discoverAllResources(listener);
        } catch (Exception e) {
            MsgLogger.LOG.errorDiscoveryFailed(e, getEndpoint());
        }
    }

    /**
     * @return factory that can be used to build clients that can talk to the server
     */
    public ModelControllerClientFactory getModelControllerClientFactory() {
        return dmrClientFactory;
    }
}
