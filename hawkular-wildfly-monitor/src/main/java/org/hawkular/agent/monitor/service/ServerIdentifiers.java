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
package org.hawkular.agent.monitor.service;

import java.util.ArrayList;

/**
 * Identification strings used to identify a Wildfly server.
 */
public class ServerIdentifiers {
    private final String host;
    private final String server;
    private final String nodeName;
    private final String uuid;

    public ServerIdentifiers(String host, String server, String nodeName, String uuid) {
        this.host = (host != null) ? host : "";
        this.server = (server != null) ? server : "";
        this.nodeName = (nodeName != null) ? nodeName : "";
        this.uuid = uuid;
    }

    /**
     * @return The host name of the WildFly application server, if known.
     *         The host name is only meaningful if the WildFly instance is running in domain mode.
     *         It comes from the DMR attribute "host" in the "/" DMR root resource.
     */
    public String getHost() {
        return host;
    }

    /**
     * @return The server name of the WildFly application server, if known.
     *         It comes from the DMR attribute "name" in the "/" DMR root resource.
     */
    public String getServer() {
        return server;
    }

    /**
     * @return The node name of the WildFly application server, if known.
     *         It comes from the WildFly's system property "jboss.node.name".
     */
    public String getNodeName() {
        return nodeName;
    }

    /**
     * @return The UUID of the WildFly application server. If it did not have a UUID, this will be null.
     */
    public String getUuid() {
        return uuid;
    }

    /**
     * Returns a string that can uniquely identify the WildFly application server.
     * This will be the {@link #getUuid() UUID} if one is available; otherwise, this will
     * return a concatenation of host, server, and node name where the host will be omitted
     * if it is empty and node name will be omitted if it is empty or is the same as server.
     *
     * @return UUID or (if that is null) the string "[host.]server[.nodeName]"
     */
    public String getFullIdentifier() {
        if (uuid != null) {
            return uuid;
        }

        ArrayList<String> ids = new ArrayList<>();
        if (!host.isEmpty()) {
            ids.add(host);
        }
        if (!server.isEmpty()) {
            ids.add(server);
        }
        if (!nodeName.isEmpty() && !nodeName.equals(server)) {
            ids.add(nodeName);
        }
        StringBuilder fullId = new StringBuilder();
        for (String id : ids) {
            if (fullId.length() > 0) {
                fullId.append(".");
            }
            fullId.append(id);
        }
        return fullId.toString();
    }

    @Override
    public String toString() {
        return getFullIdentifier();
    }
}
