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
package org.hawkular.agent.javaagent;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.hawkular.agent.monitor.config.AgentCoreEngineConfiguration.EndpointConfiguration;
import org.hawkular.agent.monitor.inventory.ConnectionData;
import org.hawkular.agent.monitor.inventory.MonitoredEndpoint;
import org.hawkular.agent.monitor.inventory.Resource;
import org.hawkular.agent.monitor.inventory.ResourceConfigurationPropertyInstance;
import org.hawkular.agent.monitor.inventory.ResourceManager;
import org.hawkular.agent.monitor.protocol.EndpointService;
import org.hawkular.agent.monitor.protocol.ProtocolService;
import org.hawkular.agent.monitor.service.AgentCoreEngine;
import org.hawkular.agent.monitor.service.ServiceStatus;
import org.jboss.dmr.ModelNode;

public class InventoryReport {
    public static String getInventoryReport(AgentCoreEngine agent) throws Exception {
        ServiceStatus status = agent.getStatus();
        if (status == ServiceStatus.RUNNING) {
            ModelNode result = new ModelNode();
            List<ProtocolService<?, ?>> protocolServices = agent.getProtocolServices().getServices();
            for (ProtocolService<?, ?> protocolService : protocolServices) {
                ModelNode protocolServiceNode = result.get(protocolService.getName());
                for (EndpointService<?, ?> endpointService : protocolService.getEndpointServices().values()) {
                    MonitoredEndpoint<EndpointConfiguration> endpoint = endpointService.getMonitoredEndpoint();
                    EndpointConfiguration endpointConfig = endpoint.getEndpointConfiguration();
                    ConnectionData endpointConnectionData = endpointConfig.getConnectionData();
                    Map<String, ? extends Object> endpointCustomData = endpointConfig.getCustomData();

                    ModelNode endpointNode = protocolServiceNode.get(endpoint.getName());

                    endpointNode.get("Feed ID").set(agent.getFeedId());

                    if (endpointConnectionData != null) {
                        ModelNode connDataNode = endpointNode.get("Connection Data");
                        connDataNode.get("URL").set(String.valueOf(endpointConnectionData.getUri()));
                        connDataNode.get("Username").set(String.valueOf(endpointConnectionData.getUsername()));
                    }
                    if (endpointCustomData != null && !endpointCustomData.isEmpty()) {
                        ModelNode customDataNode = endpointNode.get("Custom Data");
                        for (Map.Entry<String, ? extends Object> entry : endpointCustomData.entrySet()) {
                            customDataNode.get(entry.getKey()).set(String.valueOf(entry.getValue()));
                        }
                    }

                    buildEndpointNode(endpointNode.get("Resources"), endpointService);
                }
            }
            return result.toJSONString(false);
        } else {
            throw new Exception("Agent is not running - status is [" + status + "]");
        }
    }

    private static void buildEndpointNode(ModelNode endpointNode, EndpointService<?, ?> endpointService) {
        ResourceManager<?> resourceManager = endpointService.getResourceManager();
        Collection<?> resources = resourceManager.getRootResources();
        for (Object resourceObj : resources) {
            ModelNode rootResourceNode = endpointNode.add();
            processResource(resourceManager, rootResourceNode, (Resource<?>) resourceObj);
        }
    }

    private static void processResource(ResourceManager<?> manager, ModelNode resourceNode, Resource<?> resource) {
        resourceNode = resourceNode.get(resource.getID().getIDString());
        resourceNode.get("Name").set(resource.getName().getNameString());
        resourceNode.get("Type ID").set(resource.getResourceType().getID().getIDString());
        if (!resource.getResourceConfigurationProperties().isEmpty()) {
            ModelNode resourceConfigNode = resourceNode.get("Resource Configuration");
            for (ResourceConfigurationPropertyInstance<?> config : resource.getResourceConfigurationProperties()) {
                resourceConfigNode.add(config.getName().getNameString(), String.valueOf(config.getValue()));
            }
        }

        Set<?> children = manager.getChildren((Resource) resource);
        if (!children.isEmpty()) {
            ModelNode childrenNode = resourceNode.get("Children");
            for (Object childObj : children) {
                processResource(manager, childrenNode, (Resource<?>) childObj);
            }
        }
    }
}
