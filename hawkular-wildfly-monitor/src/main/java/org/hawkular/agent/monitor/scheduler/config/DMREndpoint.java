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
package org.hawkular.agent.monitor.scheduler.config;

import java.util.Properties;

import org.hawkular.agent.monitor.log.MsgLogger;
import org.hawkular.agent.monitor.scheduler.ModelControllerClientFactory;
import org.hawkular.agent.monitor.scheduler.ModelControllerClientFactoryImpl;
import org.hawkular.agent.monitor.service.ServerIdentifiers;
import org.hawkular.dmrclient.Address;
import org.hawkular.dmrclient.CoreJBossASClient;

/**
 * Represents a remote endpoint that can process DMR requests.
 */
public class DMREndpoint extends MonitoredEndpoint {

    private final String host;
    private final int port;
    private final String username;
    private final String password;
    private ServerIdentifiers serverId;

    public DMREndpoint(String name, String host, int port, String username, String password) {
        super(name);
        this.host = host;
        this.port = port;
        this.username = username;
        this.password = password;
    }

    public String getHost() {
        return host;
    }

    public int getPort() {
        return port;
    }

    public String getUsername() {
        return username;
    }

    public String getPassword() {
        return password;
    }

    /**
     * Returns the server identification, connecting to the endpoint if it
     * has not been obtained yet. This will be null if the endpoint could not
     * be connected to or the identification could not be obtained for some reason.
     *
     * @return the endpoint's identification, or null if unable to determine
     *
     * @see #getServerIdentifiers()
     */
    public ServerIdentifiers getServerIdentifiers() {
        if (this.serverId == null) {
            try (CoreJBossASClient client = new CoreJBossASClient(getModelControllerClientFactory().createClient())) {
                Address rootResource = Address.root();
                boolean isDomainMode = client.getStringAttribute("launch-type", rootResource)
                        .equalsIgnoreCase("domain");
                String hostName = (isDomainMode) ? client.getStringAttribute("host", rootResource) : null;
                String serverName = client.getStringAttribute("name", rootResource);
                Properties sysprops = client.getSystemProperties();
                String nodeName = sysprops.getProperty("jboss.node.name");

                // this is a new attribute that only exists in Wildfly 10 and up. If we can't get it, just use null.
                String uuid;
                try {
                    uuid = client.getStringAttribute("uuid", rootResource);
                } catch (Exception ignore) {
                    uuid = null;
                }

                this.serverId = new ServerIdentifiers(hostName, serverName, nodeName, uuid);
            } catch (Exception e) {
                MsgLogger.LOG.warnCannotObtainServerIdentifiersForDMREndpoint(this.toString(), e.toString());
            }
        }

        return this.serverId;
    }

    protected ModelControllerClientFactory getModelControllerClientFactory() {
        return new ModelControllerClientFactoryImpl(this);
    }

    @Override
    public String toString() {
        return "DMREndpoint[name=" + getName() + ", host=" + host + ", port=" + port + ", username=" + username
                + ", serverId=" + serverId + "]";
    }
}
