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
package org.hawkular.agent.monitor.protocol.jmx;

import javax.net.ssl.SSLContext;

import org.apache.http.conn.ssl.SSLConnectionSocketFactory;
import org.apache.http.impl.client.HttpClientBuilder;
import org.hawkular.agent.monitor.inventory.ConnectionData;
import org.hawkular.agent.monitor.inventory.MonitoredEndpoint;
import org.jolokia.client.BasicAuthenticator;
import org.jolokia.client.J4pClient;

/**
 * Can create clients to remote JMX servers.
 *
 * @author John Mazzitelli
 */
public class JmxClientFactory {

    private static class SecureBasicAuthenticator extends BasicAuthenticator {
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

    private final MonitoredEndpoint endpoint;

    public JmxClientFactory(MonitoredEndpoint endpoint) {
        this.endpoint = endpoint;
    }

    public J4pClient createClient() {
        BasicAuthenticator authenticator;

        ConnectionData cnData = endpoint.getConnectionData();
        SSLContext sslContext = endpoint.getSSLContext();
        if (sslContext != null && cnData.getUri().getScheme().equalsIgnoreCase("https")) {
            authenticator = new SecureBasicAuthenticator(sslContext);
        } else {
            authenticator = new BasicAuthenticator();
        }

        J4pClient client = J4pClient
                .url(cnData.getUri().toString())
                .user(cnData.getUsername())
                .password(cnData.getPassword())
                .authenticator(authenticator.preemptive())
                .connectionTimeout(60000)
                .build();
        return client;
    }


}
