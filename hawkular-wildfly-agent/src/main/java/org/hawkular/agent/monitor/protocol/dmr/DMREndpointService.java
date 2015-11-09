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
package org.hawkular.agent.monitor.protocol.dmr;

import java.io.IOException;
import java.util.List;

import org.hawkular.agent.monitor.diagnostics.Diagnostics;
import org.hawkular.agent.monitor.inventory.ResourceTypeManager;
import org.hawkular.agent.monitor.protocol.Driver;
import org.hawkular.agent.monitor.protocol.EndpointService;
import org.hawkular.dmr.api.OperationBuilder;
import org.jboss.as.controller.client.ModelControllerClient;
import org.jboss.dmr.ModelNode;
import org.jboss.dmr.Property;

/**
 * @author <a href="https://github.com/ppalaga">Peter Palaga</a>
 * @see EndpointService
 */
public class DMREndpointService
        extends EndpointService<DMRNodeLocation, DMREndpoint, DMRSession> {

    public static String lookupServerIdentifier(ModelControllerClient client) throws IOException {
        ModelNode rootNode = OperationBuilder.readResource().includeRuntime().execute(client).assertSuccess()
                .getResultNode();
        String lauchType = rootNode.get("launch-type").asString();
        boolean isDomainMode = "domain".equalsIgnoreCase(lauchType);
        String hostName = (isDomainMode) ? rootNode.get("host").asString() : null;
        String serverName = rootNode.get("name").asString();
        // this is a new attribute that only exists in Wildfly 10 and up. If we can't get it, just use null.
        String uuid = rootNode.has("uuid") ? rootNode.get("uuid").asString() : null;

        List<Property> sysprops = OperationBuilder.readAttribute() //
                .address().segment("core-service", "platform-mbean").segment("type", "runtime").parentBuilder() //
                .name("system-properties")
                .execute(client).assertSuccess().getResultNode().asPropertyList();

        String nodeName = null;
        for (Property prop : sysprops) {
            if ("jboss.node.name".equals(prop.getName())) {
                nodeName = prop.getValue().asString();
                break;
            }
        }

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
    private final Diagnostics diagnostics;

    public DMREndpointService(String feedId, DMREndpoint endpoint,
            ResourceTypeManager<DMRNodeLocation> resourceTypeManager,
            ModelControllerClientFactory modelControllerClientFactory, Diagnostics diagnostics) {
        super(feedId, endpoint, resourceTypeManager, new DMRLocationResolver());
        this.modelControllerClientFactory = modelControllerClientFactory;
        this.diagnostics = diagnostics;
    }

    @Override
    public DMRSession openSession() {
        ModelControllerClient client = modelControllerClientFactory.createClient();
        Driver<DMRNodeLocation> driver = new DMRDriver(client, endpoint, diagnostics);
        return new DMRSession(feedId, endpoint, resourceTypeManager, driver, locationResolver, client);
    }

}
