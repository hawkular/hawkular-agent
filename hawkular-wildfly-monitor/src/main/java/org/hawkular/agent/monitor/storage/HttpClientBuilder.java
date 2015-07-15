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

import org.hawkular.agent.monitor.extension.MonitorServiceConfiguration;
import org.hawkular.agent.monitor.log.MsgLogger;
import org.hawkular.agent.monitor.service.Util;

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
    private final String password;
    private final String username;
    private final boolean useSSL;
    private final String keystorePath;
    private final String keystorePassword;

    // holds the last built client
    private OkHttpClient httpClient;

    public HttpClientBuilder(MonitorServiceConfiguration configuration) {
        username = configuration.storageAdapter.username;
        password = configuration.storageAdapter.password;
        useSSL = configuration.storageAdapter.useSSL;
        keystorePath = configuration.storageAdapter.keystorePath;
        keystorePassword = configuration.storageAdapter.keystorePassword;
    }

    /**
     * Returns the last built HTTP client. This will build one if one has yet to be built.
     *
     * @return an HTTP client
     * @throws Exception
     */
    public OkHttpClient getHttpClient() throws Exception {
        if (httpClient == null) {
            build();
        }
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

    /**
     * Builds and returns an HTTP client. If successfully built, the returned client
     * will be cached and returned by this object via {@link #getHttpClient()}.
     *
     * @return the newly built client
     * @throws Exception
     */
    public OkHttpClient build() throws Exception {

        OkHttpClient httpClient = new OkHttpClient();

        if (this.useSSL) {
            // does not perform any hostname verification when looking at the remote end's cert
            httpClient.setHostnameVerifier(new NullHostNameVerifier());

            // Read the servers cert from the keystore
            setKeystore(httpClient);
        }

        this.httpClient = httpClient;
        return httpClient;
    }

    private void setKeystore(OkHttpClient client) throws Exception {
        KeyStore keyStore = readKeyStore();
        SSLContext sslContext = SSLContext.getInstance("SSL");
        TrustManagerFactory trustManagerFactory = TrustManagerFactory.getInstance(
                TrustManagerFactory.getDefaultAlgorithm());
        trustManagerFactory.init(keyStore);
        KeyManagerFactory keyManagerFactory = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());
        keyManagerFactory.init(keyStore, this.keystorePassword.toCharArray());
        sslContext.init(keyManagerFactory.getKeyManagers(),
                trustManagerFactory.getTrustManagers(),
                new SecureRandom());
        client.setSslSocketFactory(sslContext.getSocketFactory());
    }

    private KeyStore readKeyStore() throws Exception {
        KeyStore ks = KeyStore.getInstance(KeyStore.getDefaultType());

        // get user password and file input stream
        char[] password = this.keystorePassword.toCharArray();
        File file = new File(this.keystorePath);

        MsgLogger.LOG.infoUseKeystore(file.getAbsolutePath());

        try (FileInputStream fis = new FileInputStream(file)) {
            ks.load(fis, password);
        }
        return ks;
    }

    private class NullHostNameVerifier implements HostnameVerifier {
        @Override
        public boolean verify(String hostname, SSLSession session) {
            MsgLogger.LOG.debugf("HTTP client is blindly approving cert for [%s]", hostname);
            return true;
        }
    }
}
