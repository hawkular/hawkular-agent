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
package org.hawkular.agent.monitor.storage;

import java.io.File;
import java.io.FileInputStream;
import java.security.KeyStore;
import java.security.SecureRandom;
import java.util.Map;

import javax.net.ssl.HostnameVerifier;
import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSession;
import javax.net.ssl.TrustManagerFactory;

import org.hawkular.agent.monitor.extension.MonitorServiceConfiguration.StorageAdapterConfiguration;
import org.hawkular.agent.monitor.log.AgentLoggers;
import org.hawkular.agent.monitor.log.MsgLogger;
import org.hawkular.agent.monitor.util.Util;

import com.squareup.okhttp.MediaType;
import com.squareup.okhttp.OkHttpClient;
import com.squareup.okhttp.Request;
import com.squareup.okhttp.Request.Builder;
import com.squareup.okhttp.RequestBody;
import com.squareup.okhttp.ws.WebSocketCall;

/**
 * Builds an HTTP client that can be used to talk to the Hawkular server-side.
 * This builder can also be used to cache a HTTP client - it will cache the last built client.
 * This builder also has methods that you can use to build requests.
 */
public class HttpClientBuilder {
    private static final MsgLogger log = AgentLoggers.getLogger(HttpClientBuilder.class);
    private final String password;
    private final String username;
    private final boolean useSSL;
    private final String keystorePath;
    private final String keystorePassword;
    private final SSLContext sslContext;

    /** The configured client singleton */
    private final OkHttpClient httpClient;

    /**
     * Creates the object that can be used to build HTTP clients.
     * Note that if sslContext is null, this object will use the configured keystorePath
     * and keystorePassword to build one itself. If sslContext is provided (not-null)
     * then the configuration's keystorePath and keystorePassword are ignored.
     *
     * Note that if the configuration tells use to NOT use SSL in the first place,
     * the given SSL context (if any) as well as the configured keystorePath and keystorePassword
     * will all be ignored since we are being told to not use SSL.
     *
     * @param configuration configuration settings to determine things about the HTTP connections
     *                      any built clients will be asked to make
     * @param sslContext if not null, and if the configuration tells use to use SSL, this will
     *                   be the SSL context to use.
     */
    public HttpClientBuilder(StorageAdapterConfiguration storageAdapter, SSLContext sslContext) {
        this.username = storageAdapter.getUsername();
        this.password = storageAdapter.getPassword();
        this.useSSL = storageAdapter.isUseSSL();
        this.keystorePath = storageAdapter.getKeystorePath();
        this.keystorePassword = storageAdapter.getKeystorePassword();
        OkHttpClient httpClient = new OkHttpClient();
        if (this.useSSL) {
            this.sslContext = (sslContext == null) ? buildSSLContext() : sslContext;
            httpClient.setSslSocketFactory(this.sslContext.getSocketFactory());

            // does not perform any hostname verification when looking at the remote end's cert
            httpClient.setHostnameVerifier(new NullHostNameVerifier());
        } else {
            this.sslContext = null;
        }

        this.httpClient = httpClient;

    }

    /**
     * Returns the last built HTTP client. This will build one if one has yet to be built.
     *
     * @return an HTTP client
     * @throws Exception
     */
    public OkHttpClient getHttpClient() throws Exception {
        return httpClient;
    }

    public Request buildJsonGetRequest(String url, Map<String, String> headers) {
        String base64Credentials = Util.base64Encode(username + ":" + password);

        Builder requestBuilder = new Request.Builder()
                .url(url)
                .addHeader("Authorization", "Basic " + base64Credentials)
                .addHeader("Accept", "application/json");

        if (headers != null) {
            for (Map.Entry<String, String> header : headers.entrySet()) {
                requestBuilder.addHeader(header.getKey(), header.getValue());
            }
        }

        return requestBuilder.get().build();
    }

    public Request buildJsonPostRequest(String url, Map<String, String> headers, String jsonPayload) {
        // make sure we are authenticated. see http://en.wikipedia.org/wiki/Basic_access_authentication#Client_side
        String base64Credentials = Util.base64Encode(username + ":" + password);

        Builder requestBuilder = new Request.Builder()
                .url(url)
                .addHeader("Authorization", "Basic " + base64Credentials)
                .addHeader("Accept", "application/json");

        if (headers != null) {
            for (Map.Entry<String, String> header : headers.entrySet()) {
                requestBuilder.addHeader(header.getKey(), header.getValue());
            }
        }

        RequestBody body = RequestBody.create(MediaType.parse("application/json"), jsonPayload);

        return requestBuilder.post(body).build();
    }

    public Request buildJsonPutRequest(String url, Map<String, String> headers, String jsonPayload) {
        // make sure we are authenticated. see http://en.wikipedia.org/wiki/Basic_access_authentication#Client_side
        String base64Credentials = Util.base64Encode(username + ":" + password);

        Builder requestBuilder = new Request.Builder()
                .url(url)
                .addHeader("Authorization", "Basic " + base64Credentials)
                .addHeader("Accept", "application/json");

        if (headers != null) {
            for (Map.Entry<String, String> header : headers.entrySet()) {
                requestBuilder.addHeader(header.getKey(), header.getValue());
            }
        }

        RequestBody body = RequestBody.create(MediaType.parse("application/json"), jsonPayload);

        return requestBuilder.put(body).build();
    }

    public WebSocketCall createWebSocketCall(String url, Map<String, String> headers) {
        String base64Credentials = Util.base64Encode(username + ":" + password);

        Builder requestBuilder = new Request.Builder()
                .url(url)
                .addHeader("Authorization", "Basic " + base64Credentials)
                .addHeader("Accept", "application/json");

        if (headers != null) {
            for (Map.Entry<String, String> header : headers.entrySet()) {
                requestBuilder.addHeader(header.getKey(), header.getValue());
            }
        }

        Request request = requestBuilder.build();
        WebSocketCall wsc = WebSocketCall.create(httpClient, request);
        return wsc;
    }

    private SSLContext buildSSLContext() {
        try {
            KeyStore keyStore = readKeyStore();
            SSLContext sslContext = SSLContext.getInstance("SSL");
            TrustManagerFactory trustManagerFactory = TrustManagerFactory.getInstance(
                    TrustManagerFactory.getDefaultAlgorithm());
            trustManagerFactory.init(keyStore);
            KeyManagerFactory keyManagerFactory = KeyManagerFactory.getInstance(KeyManagerFactory
                    .getDefaultAlgorithm());
            keyManagerFactory.init(keyStore, this.keystorePassword.toCharArray());
            sslContext.init(keyManagerFactory.getKeyManagers(), trustManagerFactory.getTrustManagers(),
                    new SecureRandom());
            return sslContext;
        } catch (Exception e) {
            throw new RuntimeException(
                    String.format("Cannot create SSL context from keystore [%s]", this.keystorePath), e);
        }
    }

    private KeyStore readKeyStore() throws Exception {
        KeyStore ks = KeyStore.getInstance(KeyStore.getDefaultType());

        // get user password and file input stream
        char[] password = this.keystorePassword.toCharArray();
        File file = new File(this.keystorePath);

        log.infoUseKeystore(file.getAbsolutePath());

        try (FileInputStream fis = new FileInputStream(file)) {
            ks.load(fis, password);
        }
        return ks;
    }

    private class NullHostNameVerifier implements HostnameVerifier {
        @Override
        public boolean verify(String hostname, SSLSession session) {
            log.debugf("HTTP client is blindly approving cert for [%s]", hostname);
            return true;
        }
    }
}
