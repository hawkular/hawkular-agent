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

import java.net.URL;

import javax.net.ssl.SSLContext;

import org.apache.http.conn.ssl.SSLConnectionSocketFactory;
import org.apache.http.impl.client.HttpClientBuilder;
import org.hawkular.agent.monitor.log.AgentLoggers;
import org.hawkular.agent.monitor.log.MsgLogger;
import org.jolokia.client.BasicAuthenticator;
import org.jolokia.client.J4pClient;

/**
 * Represents a remote endpoint that can process JMX requests.
 */
public class JMXEndpoint extends MonitoredEndpoint {
    private static final MsgLogger log = AgentLoggers.getLogger(JMXEndpoint.class);
    private final URL url;
    private final String username;
    private final String password;
    private final String serverId;
    private final SSLContext sslContext;

    public JMXEndpoint(String name, URL url, String username, String password, SSLContext sslContext) {
        super(name);
        this.url = url;
        this.username = username;
        this.password = password;
        this.sslContext = sslContext;
        this.serverId = String.format("%s:%d", url.getHost(), url.getPort());
    }

    public URL getURL() {
        return url;
    }

    public String getUsername() {
        return username;
    }

    public String getPassword() {
        return password;
    }

    public SSLContext getSSLContext() {
        return sslContext;
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
    public String getServerIdentifier() {
        return this.serverId;
    }

    public J4pClient getJmxClient() {
        BasicAuthenticator authenticator;

        if (sslContext != null && getURL().getProtocol().equalsIgnoreCase("https")) {
            authenticator = new SecureBasicAuthenticator(sslContext);
        } else {
            authenticator = new BasicAuthenticator();
        }

        J4pClient client = J4pClient
                .url(getURL().toExternalForm())
                .user(getUsername())
                .password(getPassword())
                .authenticator(authenticator.preemptive())
                .connectionTimeout(60000)
                .build();
        return client;
    }

    @Override
    public String toString() {
        return "JMXEndpoint[name=" + getName() + ", url=" + url + ", username=" + username + ", serverId=" + serverId
                + "]";
    }

    private class SecureBasicAuthenticator extends BasicAuthenticator {
        private final SSLContext sslContext;

        public SecureBasicAuthenticator(SSLContext sslContext) {
            this.sslContext = sslContext;
        }

        @Override
        public void authenticate(HttpClientBuilder pBuilder, String pUser, String pPassword) {
            pBuilder.setSSLSocketFactory(new SSLConnectionSocketFactory(sslContext));
            super.authenticate(pBuilder, pUser, pPassword);
        }
    }
}
