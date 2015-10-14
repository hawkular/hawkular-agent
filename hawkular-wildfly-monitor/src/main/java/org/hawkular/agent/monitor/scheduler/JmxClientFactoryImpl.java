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
package org.hawkular.agent.monitor.scheduler;

import javax.net.ssl.SSLContext;

import org.hawkular.agent.monitor.scheduler.config.JMXEndpoint;
import org.jolokia.client.J4pClient;

/**
 * Can create clients to remote JMX servers.
 */
public class JmxClientFactoryImpl implements JmxClientFactory {

    private final JMXEndpoint defaultEndpoint;
    private final SSLContext sslContext;

    public JmxClientFactoryImpl(JMXEndpoint endpoint, SSLContext sslContext) {
        this.defaultEndpoint = endpoint;
        this.sslContext = sslContext;
    }

    @Override
    public J4pClient createClient() {
        return createClient(defaultEndpoint, sslContext);
    }

    protected J4pClient createClient(final JMXEndpoint endpoint, SSLContext sslContext) {
        try {
            J4pClient client = endpoint.getJmxClient(sslContext);
            return client;
        } catch (Exception e) {
            throw new RuntimeException("Failed to create JMX client", e);
        }
    }
}
