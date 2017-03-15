/*
 * Copyright 2015-2017 Red Hat, Inc. and/or its affiliates
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

import java.io.IOException;

import javax.net.ssl.SSLContext;

import org.apache.http.Header;
import org.apache.http.HttpException;
import org.apache.http.HttpHost;
import org.apache.http.HttpRequest;
import org.apache.http.HttpRequestInterceptor;
import org.apache.http.auth.AuthScheme;
import org.apache.http.auth.AuthScope;
import org.apache.http.auth.AuthState;
import org.apache.http.auth.AuthenticationException;
import org.apache.http.auth.Credentials;
import org.apache.http.auth.UsernamePasswordCredentials;
import org.apache.http.client.CredentialsProvider;
import org.apache.http.client.protocol.HttpClientContext;
import org.apache.http.conn.ssl.SSLConnectionSocketFactory;
import org.apache.http.impl.auth.BasicScheme;
import org.apache.http.impl.client.BasicCredentialsProvider;
import org.apache.http.impl.client.HttpClientBuilder;
import org.apache.http.message.BufferedHeader;
import org.apache.http.protocol.HttpContext;
import org.apache.http.util.Args;
import org.apache.http.util.CharArrayBuffer;
import org.hawkular.agent.monitor.config.AgentCoreEngineConfiguration.EndpointConfiguration;
import org.hawkular.agent.monitor.inventory.ConnectionData;
import org.hawkular.agent.monitor.inventory.MonitoredEndpoint;
import org.jolokia.client.BasicAuthenticator;
import org.jolokia.client.J4pAuthenticator;
import org.jolokia.client.J4pClient;

/**
 * Can create clients to remote JMX servers.
 *
 * @author John Mazzitelli
 */
public class JolokiaClientFactory {

    // if a username matches this, it means we are to use bearer token auth, not basic auth
    private static final String BEARER_TOKEN_USER_ID = "_bearer";

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

    private class SecureBearerAuthenticator extends BearerAuthenticator {
        private final SSLContext sslContext;

        public SecureBearerAuthenticator(SSLContext sslContext) {
            this.sslContext = sslContext;
        }

        @Override
        public void authenticate(HttpClientBuilder pBuilder, String pUser, String pPassword) {
            pBuilder.setSSLSocketFactory(new SSLConnectionSocketFactory(sslContext));
            super.authenticate(pBuilder, pUser, pPassword);
        }
    }

    // should work just like basic authentication except Authentication header has Bearer with the token following
    private class BearerScheme extends BasicScheme {
        @Override
        public String getSchemeName() {
            return "bearer";
        }

        @Override
        public Header authenticate(Credentials credentials, HttpRequest request, HttpContext context)
                throws AuthenticationException {
            Args.notNull(credentials, "Credentials");
            Args.notNull(request, "HTTP request");

            // the bearer token is stored in the password field, not credentials.getUserPrincipal().getName()
            String bearerToken = credentials.getPassword();

            CharArrayBuffer buffer = new CharArrayBuffer(64);
            if (isProxy()) {
                buffer.append("Proxy-Authorization");
            } else {
                buffer.append("Authorization");
            }
            buffer.append(": Bearer ");
            buffer.append(bearerToken);

            return new BufferedHeader(buffer);
        }
    }

    private class BearerAuthenticator implements J4pAuthenticator {
        @Override
        public void authenticate(HttpClientBuilder pBuilder, String pUser, String pPassword) {
            CredentialsProvider credentialsProvider = new BasicCredentialsProvider();
            credentialsProvider.setCredentials(
                    new AuthScope(AuthScope.ANY),
                    new UsernamePasswordCredentials(pUser, pPassword));
            pBuilder.setDefaultCredentialsProvider(credentialsProvider);
            pBuilder.addInterceptorFirst(new PreemptiveAuthInterceptor(new BearerScheme()));
        }

        class PreemptiveAuthInterceptor implements HttpRequestInterceptor {
            private AuthScheme authScheme;

            PreemptiveAuthInterceptor(AuthScheme pScheme) {
                authScheme = pScheme;
            }

            public void process(final HttpRequest request, final HttpContext context)
                    throws HttpException, IOException {
                AuthState authState = (AuthState) context.getAttribute(HttpClientContext.TARGET_AUTH_STATE);

                if (authState.getAuthScheme() == null) {
                    CredentialsProvider credsProvider = (CredentialsProvider) context
                            .getAttribute(HttpClientContext.CREDS_PROVIDER);
                    HttpHost targetHost = (HttpHost) context.getAttribute(HttpClientContext.HTTP_TARGET_HOST);
                    Credentials creds = credsProvider
                            .getCredentials(new AuthScope(targetHost.getHostName(), targetHost.getPort()));
                    if (creds == null) {
                        throw new HttpException("No credentials given for preemptive authentication");
                    }
                    authState.update(authScheme, creds);
                }
            }
        }
    }

    private final MonitoredEndpoint<EndpointConfiguration> endpoint;

    public JolokiaClientFactory(MonitoredEndpoint<EndpointConfiguration> endpoint) {
        this.endpoint = endpoint;
    }

    public J4pClient createClient() {
        J4pAuthenticator authenticator;

        ConnectionData cnData = endpoint.getConnectionData();
        SSLContext sslContext = endpoint.getSSLContext();

        SSLConnectionSocketFactory sslFactory;

        boolean useBearerAuth = false;
        if (cnData.getUsername() != null) {
            if (cnData.getUsername().equalsIgnoreCase(BEARER_TOKEN_USER_ID)) {
                if (cnData.getPassword() == null) {
                    throw new IllegalStateException("Bearer token is missing. Must be specified as the password");
                }
                useBearerAuth = true;
            }
        }

        if (sslContext != null && cnData.getUri().getScheme().equalsIgnoreCase("https")) {
            sslFactory = new SSLConnectionSocketFactory(sslContext);
            if (useBearerAuth) {
                authenticator = new SecureBearerAuthenticator(sslContext);
            } else {
                authenticator = new SecureBasicAuthenticator(sslContext).preemptive();
            }
        } else {
            sslFactory = null;
            if (useBearerAuth) {
                authenticator = new BearerAuthenticator();
            } else {
                authenticator = new BasicAuthenticator().preemptive();
            }
        }

        J4pClient client = J4pClient
                .url(cnData.getUri().toString())
                .user(cnData.getUsername())
                .password(cnData.getPassword())
                .authenticator(authenticator)
                .connectionTimeout(60000)
                .sslConnectionSocketFactory(sslFactory)
                .build();
        return client;
    }
}
