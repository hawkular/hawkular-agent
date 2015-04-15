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

import java.io.IOException;
import java.net.InetAddress;

import javax.security.auth.callback.Callback;
import javax.security.auth.callback.CallbackHandler;
import javax.security.auth.callback.NameCallback;
import javax.security.auth.callback.PasswordCallback;
import javax.security.auth.callback.UnsupportedCallbackException;
import javax.security.sasl.RealmCallback;

import org.hawkular.agent.monitor.scheduler.config.DMREndpoint;
import org.jboss.as.controller.client.ModelControllerClient;

/**
 * Can create clients to remote servers.
 */
public class ModelControllerClientFactoryImpl implements ModelControllerClientFactory  {

    private final DMREndpoint defaultEndpoint;

    public ModelControllerClientFactoryImpl(DMREndpoint endpoint) {
        this.defaultEndpoint = endpoint;
    }

    @Override
    public ModelControllerClient createClient() {
        return createClient(defaultEndpoint);
    }

    protected ModelControllerClient createClient(final DMREndpoint endpoint) {

        final CallbackHandler callbackHandler = new CallbackHandler() {
            public void handle(Callback[] callbacks) throws IOException, UnsupportedCallbackException {
                for (Callback current : callbacks) {
                    if (current instanceof NameCallback) {
                        NameCallback ncb = (NameCallback) current;
                        ncb.setName(endpoint.getUsername());
                    } else if (current instanceof PasswordCallback) {
                        PasswordCallback pcb = (PasswordCallback) current;
                        pcb.setPassword(endpoint.getPassword().toCharArray());
                    } else if (current instanceof RealmCallback) {
                        RealmCallback rcb = (RealmCallback) current;
                        rcb.setText(rcb.getDefaultText());
                    } else {
                        throw new UnsupportedCallbackException(current);
                    }
                }
            }
        };

        try {
            InetAddress inetAddr = InetAddress.getByName(endpoint.getHost());
            return ModelControllerClient.Factory.create(inetAddr, endpoint.getPort(), callbackHandler);
        } catch (Exception e) {
            throw new RuntimeException("Failed to create management client", e);
        }
    }
}
