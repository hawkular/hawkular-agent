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
package org.hawkular.agent.monitor.protocol.dmr;

import java.io.IOException;

import org.hawkular.agent.monitor.config.AgentCoreEngineConfiguration.EndpointConfiguration;
import org.hawkular.agent.monitor.diagnostics.ProtocolDiagnostics;
import org.hawkular.agent.monitor.inventory.MonitoredEndpoint;
import org.hawkular.agent.monitor.inventory.ResourceTypeManager;
import org.hawkular.agent.monitor.protocol.Driver;
import org.hawkular.agent.monitor.protocol.EndpointService;
import org.hawkular.dmr.api.OperationBuilder;
import org.jboss.as.controller.client.ModelControllerClient;
import org.jboss.dmr.ModelNode;

/**
 * @author <a href="https://github.com/ppalaga">Peter Palaga</a>
 * @see EndpointService
 */
public class DMREndpointService
        extends EndpointService<DMRNodeLocation, DMRSession> {

    public static String lookupServerIdentifier(ModelControllerClient client) throws IOException {
        ModelNode rootNode = OperationBuilder.readResource().includeRuntime().execute(client).assertSuccess()
                .getResultNode();

        // Remember we might be running in standalone mode, domain mode, or in a host controller.

        String hostName = null;
        if (rootNode.hasDefined("local-host-name")) {
            hostName = rootNode.get("local-host-name").asString();
        } else if (rootNode.hasDefined("host")) {
            hostName = rootNode.get("host").asString();
        }

        String serverName = null;
        if (rootNode.hasDefined("name")) {
            serverName = rootNode.get("name").asString();
        }

        // this is a new attribute that only exists in Wildfly 10 and up. If we can't get it, just use null.
        String uuid = rootNode.hasDefined("uuid") ? rootNode.get("uuid").asString() : null;

        String nodeName = System.getProperty("jboss.node.name");

        return getServerIdentifier(hostName, serverName, nodeName, uuid);
    }

    static String getServerIdentifier(String hostName, String serverName, String nodeName, String uuid) {
        if (uuid != null) {
            return uuid;
        } else {
            StringBuilder fullId = new StringBuilder();
            if (hostName != null && !hostName.isEmpty()) {
                if (fullId.length() > 0) {
                    fullId.append('.');
                }
                fullId.append(hostName);
            }
            if (serverName != null && !serverName.isEmpty()) {
                if (fullId.length() > 0) {
                    fullId.append('.');
                }
                fullId.append(serverName);
            }
            if (nodeName != null && !nodeName.isEmpty() && !nodeName.equals(serverName)) {
                if (fullId.length() > 0) {
                    fullId.append('.');
                }
                fullId.append(nodeName);
            }
            return fullId.toString();
        }
    }

    private final ModelControllerClientFactory modelControllerClientFactory;

    public DMREndpointService(String feedId, MonitoredEndpoint<EndpointConfiguration> endpoint,
            ResourceTypeManager<DMRNodeLocation> resourceTypeManager,
            ModelControllerClientFactory modelControllerClientFactory, ProtocolDiagnostics diagnostics) {
        super(feedId, endpoint, resourceTypeManager, new DMRLocationResolver(), diagnostics);
        this.modelControllerClientFactory = modelControllerClientFactory;
    }

    @Override
    public DMRSession openSession() {
        ModelControllerClient client = modelControllerClientFactory.createClient();
        Driver<DMRNodeLocation> driver = new DMRDriver(client, getMonitoredEndpoint(), getDiagnostics());
        return new DMRSession(getFeedId(), getMonitoredEndpoint(), getResourceTypeManager(), driver,
                getLocationResolver(), client);
    }

}
