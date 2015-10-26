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

import javax.net.ssl.SSLContext;

import org.hawkular.agent.monitor.inventory.MonitoredEndpoint;
import org.hawkular.agent.monitor.log.AgentLoggers;
import org.hawkular.agent.monitor.log.MsgLogger;

/**
 * A remote endpoint that can process DMR requests.
 *
 * @author John Mazzitelli
 */
public class DMREndpoint extends MonitoredEndpoint {

    @SuppressWarnings("unused")
    private static final MsgLogger log = AgentLoggers.getLogger(DMREndpoint.class);

    public static LocalDMREndpoint of(LocalDMRManagedServer dmrServer) {
        return new LocalDMREndpoint(dmrServer.getName().toString());
    }

    public static DMREndpoint of(RemoteDMRManagedServer dmrServer, SSLContext sslContext) {
        return new DMREndpoint(dmrServer.getName().toString(),
                dmrServer.getHost(),
                dmrServer.getPort(),
                dmrServer.getUsername(),
                dmrServer.getPassword(),
                dmrServer.getUseSSL(),
                sslContext);
    }
    private final String host;
    private final String password;
    private final int port;
    private final SSLContext sslContext;
    private final String username;
    private final boolean useSSL;

    protected DMREndpoint(String name, String host, int port, String username, String password, boolean useSSL,
            SSLContext sslContext) {
        super(name);
        this.host = host;
        this.port = port;
        this.username = username;
        this.password = password;
        this.useSSL = useSSL;
        this.sslContext = sslContext;
    }

    public String getHost() {
        return host;
    }

    public String getPassword() {
        return password;
    }

    public int getPort() {
        return port;
    }

    public SSLContext getSSLContext() {
        return sslContext;
    }

    public String getUsername() {
        return username;
    }

    public boolean getUseSSL() {
        return useSSL;
    }

    @Override
    public String toString() {
        return "DMREndpoint[name=" + getName() + ", host=" + host + ", port=" + port + ", username=" + username
                + ", useSSL=" + useSSL + "]";
    }

}
