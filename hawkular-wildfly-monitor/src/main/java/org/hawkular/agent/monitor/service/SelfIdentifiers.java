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

public class SelfIdentifiers {
    private final String localHost;
    private final String localServer;
    private final String nodeName;

    public SelfIdentifiers(String localHost, String localServer, String nodeName) {
        this.localHost = (localHost != null) ? localHost : "";
        this.localServer = (localServer != null) ? localServer : "";
        this.nodeName = (nodeName != null) ? nodeName : "";
    }

    public String getLocalHost() {
        return localHost;
    }

    public String getLocalServer() {
        return localServer;
    }

    public String getNodeName() {
        return nodeName;
    }

    public String getFullIdentifier() {
        ArrayList<String> ids = new ArrayList<>();
        if (!localHost.isEmpty()) {
            ids.add(localHost);
        }
        if (!localServer.isEmpty()) {
            ids.add(localServer);
        }
        if (!nodeName.isEmpty() && !nodeName.equals(localServer)) {
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
