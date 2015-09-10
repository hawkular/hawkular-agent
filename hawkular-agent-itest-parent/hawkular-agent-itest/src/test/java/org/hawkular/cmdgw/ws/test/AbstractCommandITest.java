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
package org.hawkular.cmdgw.ws.test;

import java.io.IOException;
import java.io.InputStream;
import java.io.UnsupportedEncodingException;
import java.net.URL;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import org.hawkular.inventory.api.model.Resource;
import org.hawkular.inventory.json.InventoryJacksonConfig;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;

import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.type.TypeFactory;
import com.squareup.okhttp.OkHttpClient;
import com.squareup.okhttp.Request;
import com.squareup.okhttp.Response;
import com.squareup.okhttp.ws.WebSocket;
import com.squareup.okhttp.ws.WebSocket.PayloadType;
import com.squareup.okhttp.ws.WebSocketListener;

import okio.Buffer;
import okio.BufferedSource;

/**
 * @author <a href="https://github.com/ppalaga">Peter Palaga</a>
 */
public abstract class AbstractCommandITest {
    protected static class TestListener implements WebSocketListener {

        private final WebSocketListener delegate;

        protected final Executor writeExecutor;

        public TestListener(WebSocketListener delegate, Executor writeExecutor) {
            super();
            this.delegate = delegate;
            this.writeExecutor = writeExecutor;
        }

        protected Buffer copy(BufferedSource payload) throws IOException {
            Buffer payloadCopy = new Buffer();
            payload.readAll(payloadCopy);
            payload.close();
            return payloadCopy;
        }

        public void onClose(int code, String reason) {
            delegate.onClose(code, reason);
        }

        public void onFailure(IOException e, Response response) {
            delegate.onFailure(e, response);
        }

        public void onMessage(BufferedSource payload, PayloadType type) throws IOException {
            // System.out.println("onMessage");
            delegate.onMessage(copy(payload), type);
        }

        public void onOpen(WebSocket webSocket, Response response) {
            delegate.onOpen(webSocket, response);
        }

        public void onPong(Buffer payload) {
            try {
                delegate.onPong(copy(payload));
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }

        public void send(final WebSocket webSocket, final String text) {
            send(webSocket, text, null);
        }

        public void send(final WebSocket webSocket, final String text, final URL dataUrl) {
            writeExecutor.execute(new Runnable() {
                @Override
                public void run() {
                    try (Buffer b1 = new Buffer()) {
                        if (text != null) {
                            b1.writeUtf8(text);
                        }
                        if (dataUrl != null) {
                            try (InputStream in = dataUrl.openStream()) {
                                int b;
                                while ((b = in.read()) != -1) {
                                    b1.writeByte(b);
                                    //System.out.println("Writing binary data");
                                }
                            }
                        }
                        webSocket.sendMessage(dataUrl == null ? PayloadType.TEXT : PayloadType.BINARY, b1);
                    } catch (IOException e) {
                        throw new RuntimeException("Unable to send message", e);
                    }
                }
            });
        }
    }

    protected static final String authentication;
    protected static final String baseAccountsUri;
    protected static final String baseGwUri;
    protected static final String baseInvUri;
    protected static final String testPasword = "password";

    protected static final String testUser = "jdoe";
    protected static final int ATTEMPT_COUNT = 50;
    protected static final long ATTEMPT_DELAY = 5000;

    static {
        String host = System.getProperty("hawkular.bind.address", "localhost");
        if ("0.0.0.0".equals(host)) {
            host = "localhost";
        }
        int portOffset = Integer.parseInt(System.getProperty("hawkular.port.offset", "0"));
        int httpPort = portOffset + 8080;
        baseAccountsUri = "http://" + host + ":" + httpPort + "/hawkular/accounts";
        baseInvUri = "http://" + host + ":" + httpPort + "/hawkular/inventory";
        baseGwUri = "ws://" + host + ":" + httpPort + "/hawkular/command-gateway";
        authentication = "{\"username\":\"" + testUser + "\",\"password\":\"" + testPasword + "\"}";
    }

    protected OkHttpClient client;
    protected ObjectMapper mapper;
    protected ExecutorService writeExecutor;

    @After
    public void after() {
        // Trigger shutdown of the dispatcher's executor so this process can exit cleanly.
        client.getDispatcher().getExecutorService().shutdown();
    }

    @Before
    public void before() {
        JsonFactory f = new JsonFactory();
        mapper = new ObjectMapper(f);
        InventoryJacksonConfig.configure(mapper);
        this.writeExecutor = Executors.newSingleThreadExecutor();
        this.client = new OkHttpClient();
    }

    protected List<Resource> getResources(String path, int minCount) throws Throwable {
        String url = baseInvUri + path;
        Throwable e = null;
        for (int i = 0; i < ATTEMPT_COUNT; i++) {
            try {
                String body = getWithRetries(url);
                TypeFactory tf = mapper.getTypeFactory();
                JavaType listType = tf.constructCollectionType(ArrayList.class, Resource.class);
                JsonNode node = mapper.readTree(body);
                List<Resource> result = mapper.readValue(node.traverse(), listType);
                if (result.size() >= minCount) {
                    return result;
                }
                System.out.println("Got only " + result.size() + " resources while expected " + minCount + " on "
                        + (i + 1) + " of " + ATTEMPT_COUNT + " attempts for URL [" + url + "]");
                // System.out.println(body);
            } catch (Throwable t) {
                /* some initial attempts may fail */
                e = t;
                System.out.println("URL [" + url + "] not ready yet on " + (i + 1) + " of " + ATTEMPT_COUNT
                        + " attempts, about to retry after " + ATTEMPT_DELAY + " ms");
            }
            /* sleep one second */
            Thread.sleep(ATTEMPT_DELAY);
        }
        if (e != null) {
            throw e;
        } else {
            throw new AssertionError("Could not get [" + url + "]");
        }
    }

    protected String getWithRetries(String url) throws Throwable {
        Throwable e = null;
        for (int i = 0; i < ATTEMPT_COUNT; i++) {
            try {
                Request request = newAuthRequest().url(url).build();
                Response response = client.newCall(request).execute();
                Assert.assertEquals(200, response.code());
                System.out.println("Got after " + (i + 1) + " retries: " + url);
                return response.body().string();
            } catch (Throwable t) {
                /* some initial attempts may fail */
                e = t;
            }
            System.out.println("URL [" + url + "] not ready yet on " + (i + 1) + " of " + ATTEMPT_COUNT
                    + " attempts, about to retry after " + ATTEMPT_DELAY + " ms");
            /* sleep one second */
            Thread.sleep(ATTEMPT_DELAY);
        }
        if (e != null) {
            throw e;
        } else {
            throw new AssertionError("Could not get [" + url + "]");
        }
    }

    protected Request.Builder newAuthRequest() {
        /*
         * http://en.wikipedia.org/wiki/Basic_access_authentication#Client_side : The Authorization header is
         * constructed as follows: * Username and password are combined into a string "username:password" * The
         * resulting string is then encoded using the RFC2045-MIME variant of Base64, except not limited to 76
         * char/line[9] * The authorization method and a space i.e. "Basic " is then put before the encoded string.
         */
        try {
            String encodedCredentials = Base64.getMimeEncoder()
                    .encodeToString((testUser + ":" + testPasword).getBytes("utf-8"));
            return new Request.Builder() //
                    .addHeader("Authorization", "Basic " + encodedCredentials) //
                    .addHeader("Accept", "application/json");
        } catch (UnsupportedEncodingException e) {
            throw new RuntimeException(e);
        }
    }

    protected void waitForAccountsAndInventory() throws Throwable {
        Thread.sleep(10000);
        /*
         * Make sure we can access the tenant first. We will do several attempts because race conditions may happen
         * between this script and WildFly Agent who may have triggered the same initial tasks in Accounts
         */
        getWithRetries(baseAccountsUri + "/personas/current");

        /*
         * Ensure the "test" env was autocreated. We will do several attempts because race conditions may happen between
         * this script and WildFly Agent who may have triggered the same initial tasks in Inventory. A successfull GET
         * to /hawkular/inventory/environments/test should mean that all initial tasks are over
         */
        getWithRetries(baseInvUri + "/environments/test");
    }

}
