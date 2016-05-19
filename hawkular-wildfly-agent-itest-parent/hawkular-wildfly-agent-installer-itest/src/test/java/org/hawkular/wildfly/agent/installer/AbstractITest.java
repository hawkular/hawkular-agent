/*
 * Copyright 2015-2016 Red Hat, Inc. and/or its affiliates
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
package org.hawkular.wildfly.agent.installer;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.io.Reader;
import java.io.UnsupportedEncodingException;
import java.net.InetAddress;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.Predicate;
import java.util.logging.Logger;

import javax.security.auth.callback.Callback;
import javax.security.auth.callback.CallbackHandler;
import javax.security.auth.callback.NameCallback;
import javax.security.auth.callback.PasswordCallback;
import javax.security.auth.callback.UnsupportedCallbackException;
import javax.security.sasl.RealmCallback;

import org.hawkular.agent.monitor.protocol.dmr.DMREndpointService;
import org.hawkular.bus.common.BasicMessageWithExtraData;
import org.hawkular.cmdgw.api.ApiDeserializer;
import org.hawkular.cmdgw.api.WelcomeResponse;
import org.hawkular.dmr.api.OperationBuilder;
import org.hawkular.inventory.api.model.Resource;
import org.hawkular.inventory.json.InventoryJacksonConfig;
import org.jboss.as.controller.client.ModelControllerClient;
import org.jboss.as.controller.client.helpers.Operations;
import org.jboss.as.controller.descriptions.ModelDescriptionConstants;
import org.jboss.dmr.ModelNode;
import org.jboss.dmr.Property;
import org.testng.Assert;
import org.testng.AssertJUnit;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;

import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.type.TypeFactory;
import com.squareup.okhttp.Credentials;
import com.squareup.okhttp.OkHttpClient;
import com.squareup.okhttp.Request;
import com.squareup.okhttp.Response;

/**
 * @author <a href="https://github.com/ppalaga">Peter Palaga</a>
 */
public abstract class AbstractITest {

    private static volatile boolean accountsAndInventoryReady = false;

    protected static final int ATTEMPT_COUNT = 50;

    protected static final long ATTEMPT_DELAY = 5000;

    protected static final String authentication;
    protected static final String baseMetricsUri;
    protected static final String baseGwUri;
    protected static final String baseInvUri;

    protected static final String hawkularHost;
    protected static final int hawkularHttpPort;
    private static final Logger log = Logger.getLogger(AbstractITest.class.getName());
    protected static final String managementPasword = System.getProperty("hawkular.agent.itest.mgmt.password");
    protected static final int hawkularManagementPort;
    protected static final String managementUser = System.getProperty("hawkular.agent.itest.mgmt.user");
    protected static final String testPasword = System.getProperty("hawkular.itest.rest.password");
    protected static final String testUser = System.getProperty("hawkular.itest.rest.user");
    protected static final String tenantId = System.getProperty("hawkular.itest.rest.tenantId");
    protected static final String authHeader;
    protected static final String hawkularFeedId;
    protected static final File wfHome;

    protected static final Object waitForAccountsLock = new Object();

    static {
        String h = System.getProperty("hawkular.bind.address", "localhost");
        if ("0.0.0.0".equals(h)) {
            h = "localhost";
        }
        hawkularHost = h;

        int hawkularPortOffset = Integer.parseInt(System.getProperty("hawkular.port.offset", "0"));
        hawkularHttpPort = hawkularPortOffset + 8080;
        hawkularManagementPort = hawkularPortOffset + 9990;
        baseInvUri = "http://" + hawkularHost + ":" + hawkularHttpPort + "/hawkular/inventory";
        baseMetricsUri = "http://" + hawkularHost + ":" + hawkularHttpPort + "/hawkular/metrics";
        baseGwUri = "ws://" + hawkularHost + ":" + hawkularHttpPort + "/hawkular/command-gateway";
        authentication = "{\"username\":\"" + testUser + "\",\"password\":\"" + testPasword + "\"}";
        System.out.println("using REST user ["+ testUser +"] with password ["+ testPasword +"]");
        authHeader = Credentials.basic(testUser, testPasword);

        try (ModelControllerClient mcc = newModelControllerClient(hawkularHost, hawkularManagementPort)) {
            hawkularFeedId = DMREndpointService.lookupServerIdentifier(mcc);
        } catch (IOException e) {
            throw new RuntimeException("Could not get wfFeedId", e);
        }

        wfHome = new File(System.getProperty("plain-wildfly.home.dir"));
        Assert.assertTrue(wfHome.exists(),
                "${plain-wildfly.home.dir} [" + wfHome.getAbsolutePath() + "] does not exist");

    }

    protected static String assertWelcomeResponse(String msg) {
        String welcomeRe = "\\QWelcomeResponse={\"sessionId\":\"\\E.*";
        AssertJUnit.assertTrue("[" + msg + "] does not match [" + welcomeRe + "]", msg.matches(welcomeRe));
        BasicMessageWithExtraData<WelcomeResponse> bigMessage = new ApiDeserializer().deserialize(msg);
        return bigMessage.getBasicMessage().getSessionId();
    }

    public static String readNode(Class<?> caller, String nodeFileName) throws IOException {
        URL url = caller.getResource(caller.getSimpleName() + "." + nodeFileName);
        if (url != null) {
            StringBuilder result = new StringBuilder();
            try (Reader r = new InputStreamReader(url.openStream(), "utf-8")) {
                char[] buff = new char[1024];
                int len = 0;
                while ((len = r.read(buff, 0, buff.length)) != -1) {
                    result.append(buff, 0, len);
                }
            }
            return result.toString();
        } else {
            return null;
        }
    }

    public static void writeNode(Class<?> caller, ModelNode node, String nodeFileName)
            throws UnsupportedEncodingException, FileNotFoundException {
        URL callerUrl = caller.getResource(caller.getSimpleName() + ".class");
        if (!callerUrl.getProtocol().equals("file")) {
            throw new IllegalStateException(AbstractITest.class.getName()
                    + ".store() works only if the caller's class file is loaded using a file:// URL.");
        }
        String callerUrlPath = callerUrl.getPath();

        String nodePath = callerUrlPath.replaceAll("\\.class$", "." + nodeFileName);
        nodePath = nodePath.replace("/target/test-classes/", "/src/test/resources/");
        System.out.println("Storing a node to [" + nodePath + "]");

        File outputFile = new File(nodePath);
        if (!outputFile.getParentFile().exists()) {
            outputFile.getParentFile().mkdirs();
        }

        try (PrintWriter out =
                new PrintWriter(new OutputStreamWriter(new FileOutputStream(outputFile), "utf-8"))) {
            node.writeString(out, false);
        }
    }

    protected OkHttpClient client;
    protected ObjectMapper mapper;

    @BeforeMethod
    public void before() {
        JsonFactory f = new JsonFactory();
        mapper = new ObjectMapper(f);
        InventoryJacksonConfig.configure(mapper);
        this.client = new OkHttpClient();

    }

    @AfterMethod
    public void after() {
        // Trigger shutdown of the dispatcher's executor so this process can exit cleanly.
        client.getDispatcher().getExecutorService().shutdown();
    }

    protected void assertNodeAttributeEquals(ModelControllerClient mcc, ModelNode addressActual, String attributeName,
            String expectedAttributeValue) {
        String actualAttributeValue = OperationBuilder.readAttribute()
                .address(addressActual)
                .name(attributeName)
                .includeDefaults()
                .execute(mcc)
                .assertSuccess()
                .getResultNode()
                .asString();
        Assert.assertEquals(actualAttributeValue, expectedAttributeValue);
    }

    protected void assertNodeEquals(ModelControllerClient mcc, ModelNode addressActual, Class<?> caller,
            String expectedNodeFileName) {
        assertNodeEquals(mcc, addressActual, caller, expectedNodeFileName, false);
    }

    protected void assertNodeEquals(ModelControllerClient mcc, ModelNode addressActual, Class<?> caller,
            String expectedNodeFileName, boolean saveActual) {
        try {
            ModelNode actual = OperationBuilder.readResource().address(addressActual).includeRuntime()
                    .includeDefaults()
                    .recursive().execute(mcc).assertSuccess().getResultNode();
            String expected = readNode(caller, expectedNodeFileName);
            String actualString = actual.toString();
            if (saveActual) {
                writeNode(caller, actual, expectedNodeFileName + ".actual.txt");
            }
            Assert.assertEquals(actualString, expected);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    protected void assertResourceCount(ModelControllerClient mcc, ModelNode address, String childType,
            int expectedCount) throws IOException {
        ModelNode request = new ModelNode();
        request.get(ModelDescriptionConstants.ADDRESS).set(address);
        request.get(ModelDescriptionConstants.OP).set(ModelDescriptionConstants.READ_CHILDREN_RESOURCES_OPERATION);
        request.get(ModelDescriptionConstants.CHILD_TYPE).set(childType);
        request.get(ModelDescriptionConstants.INCLUDE_RUNTIME).set(true);
        ModelNode response = mcc.execute(request);
        if (response.hasDefined(ModelDescriptionConstants.OUTCOME)
                && response.get(ModelDescriptionConstants.OUTCOME)
                        .asString().equals(ModelDescriptionConstants.SUCCESS)) {
            ModelNode result = response.get(ModelDescriptionConstants.RESULT);
            List<Property> nodes = result.asPropertyList();
            AssertJUnit.assertEquals("Number of child nodes of [" + address + "] " + response, expectedCount,
                    nodes.size());
        } else if (expectedCount != 0) {
            AssertJUnit
                    .fail("Path [" + address + "] has no child nodes, expected " + expectedCount + " : "
                            + response);
        }

    }

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

    protected Resource getResource(String listPath, Predicate<Resource> predicate) throws Throwable {
        return getResource(listPath, predicate, ATTEMPT_COUNT, ATTEMPT_DELAY);
    }

    protected Resource getResource(String listPath, Predicate<Resource> predicate, int attemptCount, long attemptDelay)
            throws Throwable {
        String url = baseInvUri + listPath;
        Throwable e = null;
        for (int i = 0; i < attemptCount; i++) {
            try {
                String body = getWithRetries(url, attemptCount, attemptDelay);
                TypeFactory tf = mapper.getTypeFactory();
                JavaType listType = tf.constructCollectionType(ArrayList.class, Resource.class);
                JsonNode node = mapper.readTree(body);
                List<Resource> result = mapper.readValue(node.traverse(), listType);
                Optional<Resource> found = result.stream().filter(predicate).findFirst();
                if (found.isPresent()) {
                    return found.get();
                }
                System.out.println("Could not find the right resource among " + result.size() + " resources on "
                        + (i + 1) + " of " + attemptCount + " attempts for URL [" + url + "]");
                // System.out.println(body);
            } catch (Throwable t) {
                /* some initial attempts may fail */
                e = t;
                System.out.println("URL [" + url + "] not ready yet on " + (i + 1) + " of " + attemptCount
                        + " attempts, about to retry after " + attemptDelay + " ms: " + t.getMessage());
            }
            Thread.sleep(attemptDelay);
        }
        if (e != null) {
            throw e;
        } else {
            throw new AssertionError("Could not get [" + url + "]");
        }
    }

    protected List<Resource> getResources(String path, int minCount) throws Throwable {
        return getResources(path, minCount, ATTEMPT_COUNT, ATTEMPT_DELAY);
    }

    protected List<Resource> getResources(String path, int minCount, int attemptCount, long attemptDelay)
            throws Throwable {
        String url = baseInvUri + path;
        Throwable e = null;
        for (int i = 0; i < attemptCount; i++) {
            try {
                String body = getWithRetries(url, attemptCount, attemptDelay);
                TypeFactory tf = mapper.getTypeFactory();
                JavaType listType = tf.constructCollectionType(ArrayList.class, Resource.class);
                JsonNode node = mapper.readTree(body);
                List<Resource> result = mapper.readValue(node.traverse(), listType);
                if (result.size() >= minCount) {
                    return result;
                }
                System.out.println("Got only " + result.size() + " resources while expected " + minCount + " on "
                        + (i + 1) + " of " + attemptCount + " attempts for URL [" + url + "]");
                // System.out.println(body);
            } catch (Throwable t) {
                /* some initial attempts may fail */
                e = t;
                System.out.println("URL [" + url + "] not ready yet on " + (i + 1) + " of " + attemptCount
                        + " attempts, about to retry after " + attemptDelay + " ms");
            }
            Thread.sleep(attemptDelay);
        }
        if (e != null) {
            throw e;
        } else {
            throw new AssertionError("Could not get [" + url + "]");
        }
    }

    protected String getWithRetries(String url) throws Throwable {
        return getWithRetries(url, ATTEMPT_COUNT, ATTEMPT_DELAY);
    }

    protected String getWithRetries(String url, int attemptCount, long attemptDelay) throws Throwable {
        Throwable e = null;
        for (int i = 0; i < attemptCount; i++) {
            try {
                Request request = newAuthRequest().url(url).build();
                Response response = client.newCall(request).execute();
                System.out.println(
                        "Got code " + response.code() + " and message [" + response.message() + "] retries: " + url);
                AssertJUnit.assertEquals(200, response.code());
                System.out.println("Got after " + (i + 1) + " retries: " + url);
                return response.body().string();
            } catch (Throwable t) {
                /* some initial attempts may fail */
                e = t;
            }
            System.out.println("URL [" + url + "] not ready yet on " + (i + 1) + " of " + attemptCount
                    + " attempts, about to retry after " + attemptDelay + " ms");
            Thread.sleep(attemptDelay);
        }
        if (e != null) {
            throw e;
        } else {
            throw new AssertionError("Could not get [" + url + "]");
        }
    }

    protected void assertResourceNotInInventory(String listPath, Predicate<Resource> predicate, int attemptCount,
            long attemptDelay) throws Throwable {
        try {
            for (int i = 0; i < attemptCount; i++) {
                getResource(listPath, predicate, 1, 1);
                Thread.sleep(attemptDelay);
            }
        } catch (AssertionError expected) {
            return;
        }
        Assert.fail("resource is still in inventory. listPath=" + listPath);
    }

    protected Request.Builder newAuthRequest() {
        return new Request.Builder()//
                .addHeader("Authorization", authHeader)//
                .addHeader("Accept", "application/json")//
                .addHeader("Hawkular-Tenant", tenantId);
    }

    protected static ModelControllerClient newModelControllerClient(final String host, final int managementPort) {
        final CallbackHandler callbackHandler = new CallbackHandler() {
            public void handle(Callback[] callbacks) throws IOException, UnsupportedCallbackException {
                for (Callback current : callbacks) {
                    if (current instanceof NameCallback) {
                        NameCallback ncb = (NameCallback) current;
                        log.fine("ModelControllerClient is sending a username [" + managementUser + "]");
                        ncb.setName(managementUser);
                    } else if (current instanceof PasswordCallback) {
                        PasswordCallback pcb = (PasswordCallback) current;
                        log.fine("ModelControllerClient is sending a password [" + managementPasword + "]");
                        pcb.setPassword(managementPasword.toCharArray());
                    } else if (current instanceof RealmCallback) {
                        RealmCallback rcb = (RealmCallback) current;
                        log.fine("ModelControllerClient is sending a realm [" + rcb.getDefaultText() + "]");
                        rcb.setText(rcb.getDefaultText());
                    } else {
                        throw new UnsupportedCallbackException(current);
                    }
                }
            }
        };

        try {
            InetAddress inetAddr = InetAddress.getByName(host);
            log.fine("Connecting a ModelControllerClient to [" + host + ":" + managementPort + "]");
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
                 * Ensure inventory is running by trying to read our tenant - this is what we authenticate with against
                 * inventory.
                 */
                getWithRetries(baseInvUri + "/tenant");
                accountsAndInventoryReady = true;
            }
        }
    }
}
