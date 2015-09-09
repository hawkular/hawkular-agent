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

import static org.mockito.Mockito.verify;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import org.hawkular.inventory.api.model.CanonicalPath;
import org.hawkular.inventory.api.model.Resource;
import org.hawkular.inventory.json.InventoryJacksonConfig;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;

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
import com.squareup.okhttp.ws.WebSocketCall;
import com.squareup.okhttp.ws.WebSocketListener;

import okio.Buffer;
import okio.BufferedSource;

/**
 * @author <a href="https://github.com/ppalaga">Peter Palaga</a>
 */
public class CommandGatewayITest {
    private static class TestListener implements WebSocketListener {

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

        public void sendText(final WebSocket webSocket, final String text) {
            writeExecutor.execute(new Runnable() {
                @Override
                public void run() {
                    try (Buffer b1 = new Buffer()) {
                        webSocket.sendMessage(PayloadType.TEXT, b1.writeUtf8(text));
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
    private static final int ATTEMPT_COUNT = 50;
    private static final long ATTEMPT_DELAY = 5000;

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

    private OkHttpClient client;
    private ObjectMapper mapper;
    private ExecutorService writeExecutor;

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

    private List<Resource> getResources(String path, int minCount) throws Throwable {
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

    private String getWithRetries(String url) throws Throwable {
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

    private Request.Builder newAuthRequest() {
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

    private void waitForAccountsAndInventory() throws Throwable {
        Thread.sleep(10000);
        /*
         * Make sure we can access the tenant first. We will do several attempts because race conditions may happen
         * between this script and WildFly Agent who may have triggered the same initial tasks in Accounts
         */
        String body = getWithRetries(baseAccountsUri + "/personas/current");

        /*
         * Ensure the "test" env was autocreated. We will do several attempts because race conditions may happen between
         * this script and WildFly Agent who may have triggered the same initial tasks in Inventory. A successfull GET
         * to /hawkular/inventory/environments/test should mean that all initial tasks are over
         */
        body = getWithRetries(baseInvUri + "/environments/test");
    }

    @Test
    public void testEcho() throws InterruptedException, IOException {

        Request request = new Request.Builder().url(baseGwUri + "/ui/ws").build();
        WebSocketListener mockListener = Mockito.mock(WebSocketListener.class);

        WebSocketListener openingListener = new TestListener(mockListener, writeExecutor) {
            @Override
            public void onOpen(WebSocket webSocket, Response response) {
                sendText(webSocket, "EchoRequest={\"authentication\": " + authentication
                        + ", \"echoMessage\": \"Yodel Ay EEE Oooo\"}");
                super.onOpen(webSocket, response);
            }
        };

        WebSocketCall.create(client, request).enqueue(openingListener);

        verify(mockListener, Mockito.timeout(10000).times(1)).onOpen(Mockito.any(), Mockito.any());
        ArgumentCaptor<BufferedSource> bufferedSourceCaptor = ArgumentCaptor.forClass(BufferedSource.class);
        verify(mockListener, Mockito.timeout(10000).times(1)).onMessage(bufferedSourceCaptor.capture(),
                Mockito.same(PayloadType.TEXT));

        List<BufferedSource> receivedMessages = bufferedSourceCaptor.getAllValues();
        int i = 0;
        Assert.assertEquals("EchoResponse={\"reply\":\"ECHO [Yodel Ay EEE Oooo]\"}",
                receivedMessages.get(i++).readUtf8());

        Assert.assertEquals(1, receivedMessages.size());

    }

    @Test
    public void testExecuteOperation() throws Throwable {
        waitForAccountsAndInventory();

        List<Resource> wfs = getResources("/test/resources", 1);
        Assert.assertEquals(1, wfs.size());
        CanonicalPath wfPath = wfs.get(0).getPath();
        String feedId = wfPath.ids().getFeedId();
        List<Resource> deployments = getResources("/test/" + feedId + "/resourceTypes/Deployment/resources", 6);
        final String deploymentName = "hawkular-helloworld-war.war";
        Resource deployment = deployments.stream().filter(r -> r.getId().endsWith("=" + deploymentName)).findFirst()
                .get();

        Request request = new Request.Builder().url(baseGwUri + "/ui/ws").build();
        WebSocketListener mockListener = Mockito.mock(WebSocketListener.class);
        WebSocketListener openingListener = new TestListener(mockListener, writeExecutor) {
            @Override
            public void onOpen(WebSocket webSocket, Response response) {
                sendText(webSocket,
                        "ExecuteOperationRequest={\"authentication\":" + authentication + ", " //
                                + "\"resourcePath\":\"" + deployment.getPath().toString() + "\"," //
                                + "\"operationName\":\"Redeploy\"" //
                                + "}");
                super.onOpen(webSocket, response);
            }
        };

        WebSocketCall.create(client, request).enqueue(openingListener);

        verify(mockListener, Mockito.timeout(10000).times(1)).onOpen(Mockito.any(), Mockito.any());
        ArgumentCaptor<BufferedSource> bufferedSourceCaptor = ArgumentCaptor.forClass(BufferedSource.class);
        verify(mockListener, Mockito.timeout(10000).times(2)).onMessage(bufferedSourceCaptor.capture(),
                Mockito.same(PayloadType.TEXT));

        List<BufferedSource> receivedMessages = bufferedSourceCaptor.getAllValues();
        int i = 0;

        String expectedRe = "\\QGenericSuccessResponse={\"message\":"
                + "\"The execution request has been forwarded to feed [" + wfPath.ids().getFeedId() + "] (\\E.*";

        String msg = receivedMessages.get(i++).readUtf8();
        Assert.assertTrue("[" + msg + "] does not match [" + expectedRe + "]", msg.matches(expectedRe));

        Assert.assertEquals("ExecuteOperationResponse={" + "\"resourcePath\":\"" + deployment.getPath() + "\"," //
                + "\"operationName\":\"Redeploy\"," + "\"status\":\"OK\"," //
        // FIXME HAWKULAR-604 the message should not be undefined
                + "\"message\":\"undefined\"," //
        // FIXME HAWKULAR-603 the server should not forward the authentication to UI
                + "\"authentication\":" + authentication //
                + "}", receivedMessages.get(i++).readUtf8());

        Assert.assertEquals(2, receivedMessages.size());

    }

    @Test
    @Ignore // created as a proof of concept of a ws test. Can be removed later
    public void testWsOrg() throws InterruptedException, IOException {

        Request request = new Request.Builder().url("ws://echo.websocket.org").build();
        WebSocketListener mockListener = Mockito.mock(WebSocketListener.class);

        WebSocketListener openingListener = new TestListener(mockListener, writeExecutor) {
            @Override
            public void onOpen(WebSocket webSocket, Response response) {
                sendText(webSocket, "whatever");
                super.onOpen(webSocket, response);
            }
        };

        WebSocketCall.create(client, request).enqueue(openingListener);

        verify(mockListener, Mockito.timeout(10000).times(1)).onOpen(Mockito.any(), Mockito.any());
        ArgumentCaptor<BufferedSource> bufferedSourceCaptor = ArgumentCaptor.forClass(BufferedSource.class);
        verify(mockListener, Mockito.timeout(10000).times(1)).onMessage(bufferedSourceCaptor.capture(),
                Mockito.same(PayloadType.TEXT));
        Assert.assertEquals("whatever", bufferedSourceCaptor.getValue().readUtf8());

    }

}
