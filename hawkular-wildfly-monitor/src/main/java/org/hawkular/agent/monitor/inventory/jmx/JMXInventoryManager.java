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
package org.hawkular.agent.monitor.inventory.jmx;

import org.hawkular.agent.monitor.inventory.InventoryManager;
import org.hawkular.agent.monitor.inventory.ManagedServer;
import org.hawkular.agent.monitor.inventory.MetadataManager;
import org.hawkular.agent.monitor.inventory.ResourceManager;
import org.hawkular.agent.monitor.log.AgentLoggers;
import org.hawkular.agent.monitor.log.MsgLogger;
import org.hawkular.agent.monitor.scheduler.JmxClientFactory;
import org.hawkular.agent.monitor.scheduler.config.JMXEndpoint;
import org.jgrapht.event.VertexSetListener;

public class JMXInventoryManager extends InventoryManager
        <JMXResourceType,
        JMXMetricType,
        JMXAvailType,
        JMXOperation,
        JMXResourceConfigurationPropertyType,
        JMXResource,
        JMXEndpoint> {
    private static final MsgLogger log = AgentLoggers.getLogger(JMXInventoryManager.class);
    private final JmxClientFactory jmxClientFactory;

    public JMXInventoryManager(String feedId,
            MetadataManager<JMXResourceType, JMXMetricType,
            JMXAvailType, JMXOperation, JMXResourceConfigurationPropertyType> metadataManager,
            ResourceManager<JMXResource> resourceManager,
            ManagedServer managedServer,
            JMXEndpoint dmrEndpoint,
            JmxClientFactory jmxClientFactory) {
        super(feedId, metadataManager, resourceManager, managedServer, dmrEndpoint);
        this.jmxClientFactory = jmxClientFactory;
    }

    @Override
    public void discoverResources(VertexSetListener<JMXResource> listener) {
        try {
            JMXDiscovery discovery = new JMXDiscovery(this);
            discovery.discoverAllResources(listener);
        } catch (Exception e) {
            log.errorDiscoveryFailed(e, getEndpoint());
        }
    }

    /**
     * @return factory that can be used to build clients that can talk to the server
     */
    public JmxClientFactory getJmxClientFactory() {
        return jmxClientFactory;
    }
}
