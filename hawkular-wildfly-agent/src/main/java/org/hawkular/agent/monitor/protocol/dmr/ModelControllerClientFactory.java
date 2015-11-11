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
import java.net.URI;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;

import javax.security.auth.callback.Callback;
import javax.security.auth.callback.CallbackHandler;
import javax.security.auth.callback.NameCallback;
import javax.security.auth.callback.PasswordCallback;
import javax.security.auth.callback.UnsupportedCallbackException;
import javax.security.sasl.RealmCallback;

import org.hawkular.agent.monitor.inventory.ConnectionData;
import org.hawkular.agent.monitor.inventory.MonitoredEndpoint;
import org.hawkular.agent.monitor.util.ThreadFactoryGenerator;
import org.jboss.as.controller.ModelController;
import org.jboss.as.controller.client.ModelControllerClient;
import org.jboss.as.controller.client.ModelControllerClientConfiguration;

/**
 * @author John Mazzitelli
 */
public abstract class ModelControllerClientFactory {
    private static class LocalModelControllerClientFactory extends ModelControllerClientFactory {

        private final ModelController modelController;
        private Executor executor;

        public LocalModelControllerClientFactory(ModelController modelController) {
            super();
            this.modelController = modelController;
            final ThreadFactory threadFactory = ThreadFactoryGenerator.generateFactory(true,
                    "Hawkular-Monitor-LocalMgmtClient");
            this.executor = Executors.newCachedThreadPool(threadFactory);
        }

        /** @see org.hawkular.agent.monitor.protocol.dmr.ModelControllerClientFactory#createClient() */
        @Override
        public ModelControllerClient createClient() {
            return modelController.createClient(executor);
        }

    }

    private static class RemoteModelControllerClientFactory extends ModelControllerClientFactory  {

        private final MonitoredEndpoint defaultEndpoint;

        public RemoteModelControllerClientFactory(MonitoredEndpoint endpoint) {
            this.defaultEndpoint = endpoint;
        }

        @Override
        public ModelControllerClient createClient() {
            return createClient(defaultEndpoint);
        }

        protected ModelControllerClient createClient(final MonitoredEndpoint endpoint) {
            final ConnectionData cnData = endpoint.getConnectionData();
            final CallbackHandler callbackHandler = new CallbackHandler() {
                public void handle(Callback[] callbacks) throws IOException, UnsupportedCallbackException {
                    for (Callback current : callbacks) {
                        if (current instanceof NameCallback) {
                            NameCallback ncb = (NameCallback) current;
                            ncb.setName(cnData.getUsername());
                        } else if (current instanceof PasswordCallback) {
                            PasswordCallback pcb = (PasswordCallback) current;
                            pcb.setPassword(cnData.getPassword().toCharArray());
                        } else if (current instanceof RealmCallback) {
                            RealmCallback rcb = (RealmCallback) current;
                            rcb.setText(rcb.getDefaultText());
                        } else {
                            throw new UnsupportedCallbackException(current);
                        }
                    }
                }
            };
            final URI uri = cnData.getUri();
            try {
                ModelControllerClientConfiguration config = new ModelControllerClientConfiguration.Builder()
                        .setProtocol(uri.getScheme())
                        .setHostName(uri.getHost())
                        .setPort(uri.getPort())
                        .setSslContext(endpoint.getSSLContext())
                        .setHandler(callbackHandler)
                        .build();

                return ModelControllerClient.Factory.create(config);
            } catch (Exception e) {
                throw new RuntimeException("Failed to create management client", e);
            }
        }
    }


    public static ModelControllerClientFactory createLocal(ModelController modelController) {
        return new LocalModelControllerClientFactory(modelController);
    }

    public static ModelControllerClientFactory createRemote(MonitoredEndpoint endpoint) {
        return new RemoteModelControllerClientFactory(endpoint);
    }

    public abstract ModelControllerClient createClient();
}
