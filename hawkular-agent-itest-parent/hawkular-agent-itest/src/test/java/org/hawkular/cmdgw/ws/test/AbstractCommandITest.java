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
import java.net.InetAddress;
import java.net.URL;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Predicate;

import javax.security.auth.callback.Callback;
import javax.security.auth.callback.CallbackHandler;
import javax.security.auth.callback.NameCallback;
import javax.security.auth.callback.PasswordCallback;
import javax.security.auth.callback.UnsupportedCallbackException;
import javax.security.sasl.RealmCallback;

import org.hawkular.bus.common.BasicMessageWithExtraData;
import org.hawkular.cmdgw.api.ApiDeserializer;
import org.hawkular.cmdgw.api.WelcomeResponse;
import org.hawkular.dmr.api.OperationBuilder;
import org.hawkular.dmr.api.SubsystemLoggingConstants;
import org.hawkular.inventory.api.model.CanonicalPath;
import org.hawkular.inventory.api.model.Resource;
import org.hawkular.inventory.json.InventoryJacksonConfig;
import org.jboss.as.controller.client.ModelControllerClient;
import org.jboss.as.controller.client.helpers.Operations;
import org.jboss.as.controller.descriptions.ModelDescriptionConstants;
import org.jboss.dmr.ModelNode;
import org.jboss.dmr.Property;
import org.jboss.logging.Logger.Level;
import org.testng.AssertJUnit;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;

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
                                    // System.out.println("Writing binary data");
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

    protected static final int ATTEMPT_COUNT = 50;
    protected static final long ATTEMPT_DELAY = 5000;
    protected static final String authentication;
    protected static final String baseAccountsUri;

    protected static final String baseGwUri;
    protected static final String baseInvUri;
    protected static final String host;
    protected static final String managementPasword = System.getProperty("hawkular.agent.itest.mgmt.password");
    protected static final int managementPort;
    protected static final String managementUser = System.getProperty("hawkular.agent.itest.mgmt.user");
    protected static final String testPasword = "password";
    protected static final String testUser = "jdoe";

    private static final Object waitForAccountsLock = new Object();

    private static volatile boolean accountsAndInventoryReady = false;

    static {
        String h = System.getProperty("hawkular.bind.address", "localhost");
        if ("0.0.0.0".equals(h)) {
            h = "localhost";
        }
        host = h;
        int portOffset = Integer.parseInt(System.getProperty("hawkular.port.offset", "0"));
        int httpPort = portOffset + 8080;
        managementPort = portOffset + 9990;
        baseAccountsUri = "http://" + host + ":" + httpPort + "/hawkular/accounts";
        baseInvUri = "http://" + host + ":" + httpPort + "/hawkular/inventory";
        baseGwUri = "ws://" + host + ":" + httpPort + "/hawkular/command-gateway";
        authentication = "{\"username\":\"" + testUser + "\",\"password\":\"" + testPasword + "\"}";
    }

    protected OkHttpClient client;
    protected ObjectMapper mapper;
    protected ExecutorService writeExecutor;

    @AfterMethod
    public void after() {
        // Trigger shutdown of the dispatcher's executor so this process can exit cleanly.
        client.getDispatcher().getExecutorService().shutdown();
    }

    protected void assertResourceCount(ModelControllerClient mcc, ModelNode address, String childType,
            int expectedCount) throws IOException {
        ModelNode request = new ModelNode();
        request.get(ModelDescriptionConstants.ADDRESS).set(address);
        request.get(ModelDescriptionConstants.OP).set(ModelDescriptionConstants.READ_CHILDREN_RESOURCES_OPERATION);
        request.get(ModelDescriptionConstants.CHILD_TYPE).set(childType);
        request.get(ModelDescriptionConstants.INCLUDE_RUNTIME).set(true);
        ModelNode response = mcc.execute(request);
        if (response.hasDefined(ModelDescriptionConstants.OUTCOME) && response.get(ModelDescriptionConstants.OUTCOME)
                .asString().equals(ModelDescriptionConstants.SUCCESS)) {
            ModelNode result = response.get(ModelDescriptionConstants.RESULT);
            List<Property> nodes = result.asPropertyList();
            AssertJUnit.assertEquals("Number of child nodes of [" + address + "] " + response, expectedCount,
                    nodes.size());
        } else if (expectedCount != 0) {
            AssertJUnit.fail("Path [" + address + "] has no child nodes, expected " + expectedCount + " : " + response);
        }

    }

    /**
     * @param request
     * @throws IOException
     */
    protected void assertResourceExists(ModelControllerClient mcc, ModelNode address, boolean expectedExists)
            throws IOException {
        ModelNode request = new ModelNode();
        request.get(ModelDescriptionConstants.ADDRESS).set(address);
        request.get(ModelDescriptionConstants.OP).set(ModelDescriptionConstants.READ_RESOURCE_OPERATION);
        request.get(ModelDescriptionConstants.INCLUDE_RUNTIME).set(true);
        ModelNode result = mcc.execute(request);

        String message = String.format("Model node [%s] %s unexpectedly", address.toString(),
                (expectedExists ? "does not exist" : "exists"));
        AssertJUnit.assertTrue(String.format(message, result),
                Operations.isSuccessfulOutcome(result) == expectedExists);

    }

    /**
     * @param msg
     * @return
     */
    protected String assertWelcomeResponse(String msg) {
        String welcomeRe = "\\QWelcomeResponse={\"sessionId\":\"\\E.*";
        AssertJUnit.assertTrue("[" + msg + "] does not match [" + welcomeRe + "]", msg.matches(welcomeRe));
        BasicMessageWithExtraData<WelcomeResponse> bigMessage = new ApiDeserializer().deserialize(msg);
        return bigMessage.getBasicMessage().getSessionId();
    }

    @BeforeMethod
    public void before() {
        JsonFactory f = new JsonFactory();
        mapper = new ObjectMapper(f);
        InventoryJacksonConfig.configure(mapper);
        this.writeExecutor = Executors.newSingleThreadExecutor();
        this.client = new OkHttpClient();
    }

    /**
     * @return the {@link CanonicalPath} or the only AS server present in inventory
     * @throws Throwable
     */
    protected CanonicalPath getCurrentASPath() throws Throwable {
        List<Resource> wfs = getResources("/test/resources", 1);
        AssertJUnit.assertEquals(1, wfs.size());
        CanonicalPath wfPath = wfs.get(0).getPath();
        return wfPath;
    }

    protected Resource getResource(String listPath, Predicate<Resource> predicate) throws Throwable {
        String url = baseInvUri + listPath;
        Throwable e = null;
        for (int i = 0; i < ATTEMPT_COUNT; i++) {
            try {
                String body = getWithRetries(url);
                TypeFactory tf = mapper.getTypeFactory();
                JavaType listType = tf.constructCollectionType(ArrayList.class, Resource.class);
                JsonNode node = mapper.readTree(body);
                List<Resource> result = mapper.readValue(node.traverse(), listType);
                Optional<Resource> found = result.stream().filter(predicate).findFirst();
                if (found.isPresent()) {
                    return found.get();
                }
                System.out.println("Could not find the right resource among " + result.size() + " resources on "
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
                AssertJUnit.assertEquals(200, response.code());
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

    protected ModelControllerClient newModelControllerClient() {
        final CallbackHandler callbackHandler = new CallbackHandler() {
            public void handle(Callback[] callbacks) throws IOException, UnsupportedCallbackException {
                for (Callback current : callbacks) {
                    if (current instanceof NameCallback) {
                        NameCallback ncb = (NameCallback) current;
                        ncb.setName(managementUser);
                    } else if (current instanceof PasswordCallback) {
                        PasswordCallback pcb = (PasswordCallback) current;
                        pcb.setPassword(managementPasword.toCharArray());
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
            InetAddress inetAddr = InetAddress.getByName(host);
            return ModelControllerClient.Factory.create(inetAddr, managementPort, callbackHandler);
        } catch (Exception e) {
            throw new RuntimeException("Failed to create management client", e);
        }
    }

    protected void waitForAccountsAndInventory() throws Throwable {

        synchronized (waitForAccountsLock) {
            if (!accountsAndInventoryReady) {
                Thread.sleep(10000);
                /*
                 * Make sure we can access the tenant first. We will do several attempts because race conditions may
                 * happen between this script and WildFly Agent who may have triggered the same initial tasks in
                 * Accounts
                 */
                getWithRetries(baseAccountsUri + "/personas/current");

                /*
                 * Ensure the "test" env was autocreated. We will do several attempts because race conditions may happen
                 * between this script and WildFly Agent who may have triggered the same initial tasks in Inventory. A
                 * successfull GET to /hawkular/inventory/environments/test should mean that all initial tasks are over
                 */
                getWithRetries(baseInvUri + "/environments/test");
                accountsAndInventoryReady = true;
            }
        }
    }

    public void trace(Class<?> cl) {
        setLogger(cl.getName(), Level.TRACE);
    }

    public void setLogger(String category, org.jboss.logging.Logger.Level level) {
        try (ModelControllerClient cl = newModelControllerClient()) {
            // ModelNode loggingBefore = OperationBuilder.readResource().address().subsystemLogging().parentBuilder()
            // .recursive().includeRuntime().execute(cl).assertSuccess().getResultNode();
            // System.out.println("logging before = " + loggingBefore.toString());

            Set<String> availCategories = OperationBuilder.readChildrenNames().address().subsystemLogging()
                    .parentBuilder().childType(SubsystemLoggingConstants.LOGGER).execute(cl).getHashSet();

            // System.out.println("availCategories = "+ availCategories);

            if (!availCategories.contains(category)) {
                OperationBuilder.add() //
                        .address().subsystemLogging().segment(SubsystemLoggingConstants.LOGGER, category)
                        .parentBuilder() //
                        .attribute(SubsystemLoggingConstants.LoggerNodeConstants.CATEGORY, category)
                        .attribute(SubsystemLoggingConstants.LoggerNodeConstants.LEVEL, level.name())
                        .attribute(SubsystemLoggingConstants.LoggerNodeConstants.USE_PARENT_HANDLERS, true) //
                        .execute(cl).assertSuccess();
            } else {
                OperationBuilder.writeAttribute() //
                        .address().subsystemLogging().segment(SubsystemLoggingConstants.LOGGER, category)
                        .parentBuilder() //
                        .attribute(SubsystemLoggingConstants.LoggerNodeConstants.LEVEL, level.name()) //
                        .execute(cl).assertSuccess();
            }

        } catch (Exception e) {
            throw new RuntimeException(e);
        }

    }

}
