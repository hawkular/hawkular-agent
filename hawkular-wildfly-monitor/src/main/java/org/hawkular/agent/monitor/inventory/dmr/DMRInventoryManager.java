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

import org.hawkular.agent.monitor.inventory.AvailTypeManager;
import org.hawkular.agent.monitor.inventory.InventoryManager;
import org.hawkular.agent.monitor.inventory.ManagedServer;
import org.hawkular.agent.monitor.inventory.MetricTypeManager;
import org.hawkular.agent.monitor.inventory.ResourceManager;
import org.hawkular.agent.monitor.inventory.ResourceTypeManager;
import org.hawkular.agent.monitor.log.MsgLogger;
import org.hawkular.agent.monitor.scheduler.ModelControllerClientFactory;
import org.hawkular.agent.monitor.scheduler.config.DMREndpoint;

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
            ResourceTypeManager<DMRResourceType, DMRResourceTypeSet> resourceTypeManager,
            MetricTypeManager<DMRMetricType, DMRMetricTypeSet> metricTypeManager,
            AvailTypeManager<DMRAvailType, DMRAvailTypeSet> availTypeManager,
            ResourceManager<DMRResource> resourceManager,
            ManagedServer managedServer,
            DMREndpoint dmrEndpoint,
            ModelControllerClientFactory dmrClientFactory) {
        super(feedId,
                resourceTypeManager,
                metricTypeManager,
                availTypeManager,
                resourceManager,
                managedServer,
                dmrEndpoint);
        this.dmrClientFactory = dmrClientFactory;
    }

    @Override
    public void discoverResources() {
        try {
            DMRDiscovery discovery = new DMRDiscovery(this, dmrClientFactory);
            discovery.discoverAllResources(getResourceManager());
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
