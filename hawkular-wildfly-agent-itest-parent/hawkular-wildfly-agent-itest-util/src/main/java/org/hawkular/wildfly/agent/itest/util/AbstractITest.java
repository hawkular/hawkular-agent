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
package org.hawkular.wildfly.agent.itest.util;

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
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.Collection;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.logging.Logger;
import java.util.stream.Collectors;

import javax.security.auth.callback.Callback;
import javax.security.auth.callback.CallbackHandler;
import javax.security.auth.callback.NameCallback;
import javax.security.auth.callback.PasswordCallback;
import javax.security.auth.callback.UnsupportedCallbackException;
import javax.security.sasl.RealmCallback;

import org.hawkular.agent.monitor.protocol.dmr.DMREndpointService;
import org.hawkular.agent.monitor.service.ServiceStatus;
import org.hawkular.agent.monitor.util.Util;
import org.hawkular.dmr.api.OperationBuilder;
import org.hawkular.dmrclient.Address;
import org.hawkular.dmrclient.JBossASClient;
import org.hawkular.inventory.api.model.Blueprint;
import org.hawkular.inventory.api.model.Entity;
import org.hawkular.inventory.api.model.InventoryStructure;
import org.hawkular.inventory.api.model.ResourceType;
import org.hawkular.inventory.json.InventoryJacksonConfig;
import org.hawkular.inventory.paths.CanonicalPath;
import org.hawkular.inventory.paths.RelativePath;
import org.hawkular.inventory.paths.SegmentType;
import org.jboss.as.controller.PathAddress;
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
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.base.Throwables;

import okhttp3.Credentials;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

/**
 * @author <a href="https://github.com/ppalaga">Peter Palaga</a>
 */
public abstract class AbstractITest {

    private static volatile boolean accountsAndInventoryReady = false;
    private static final int ATTEMPT_COUNT = 500;
    private static final long ATTEMPT_DELAY = 5000;
    private static final String baseInvUri;

    private static final Object waitForAccountsLock = new Object();
    private static final Object waitForAgentLock = new Object();

    protected static final String authentication;
    protected static final String baseMetricsUri;
    protected static final String baseGwUri;

    protected static final String hawkularHost;
    protected static final int hawkularHttpPort;
    private static final Logger log = Logger.getLogger(AbstractITest.class.getName());
    protected static final String managementPasword = System.getProperty("hawkular.agent.itest.mgmt.password");
    protected static final int hawkularManagementPort;
    protected static final String managementUser = System.getProperty("hawkular.agent.itest.mgmt.user");
    protected static final String testPasword = System.getProperty("hawkular.itest.rest.password");
    protected static final String testUser = System.getProperty("hawkular.itest.rest.user");
    private static final String tenantId = System.getProperty("hawkular.itest.rest.tenantId"); // see getTenantId
    protected static final String authHeader;
    protected static final String hawkularFeedId;
    protected static final File wfHome;

    static {
        String h = System.getProperty("hawkular.bind.address", "localhost");
        if ("0.0.0.0".equals(h)) {
            h = "localhost";
        }
        hawkularHost = h;

        int hawkularPortOffset = Integer.parseInt(System.getProperty("hawkular.port.offset", "0"));
        hawkularHttpPort = hawkularPortOffset + 8080;
        hawkularManagementPort = hawkularPortOffset + 9990;
        baseMetricsUri = "http://" + hawkularHost + ":" + hawkularHttpPort + "/hawkular/metrics";
        baseInvUri = baseMetricsUri + "/strings";
        baseGwUri = "ws://" + hawkularHost + ":" + hawkularHttpPort + "/hawkular/command-gateway";
        authentication = "{\"username\":\"" + testUser + "\",\"password\":\"" + testPasword + "\"}";
        System.out.println("using REST user [" + testUser + "] with password [" + testPasword + "]");
        authHeader = Credentials.basic(testUser, testPasword);

        try (ModelControllerClient mcc = newModelControllerClient(hawkularHost, hawkularManagementPort)) {
            hawkularFeedId = DMREndpointService.lookupServerIdentifier(mcc);
        } catch (IOException e) {
            throw new RuntimeException("Could not get wfFeedId", e);
        }

        // some tests might want to start another plain old WF server - if so, this property will be set
        String wfHomeProperty = System.getProperty("plain-wildfly.home.dir");
        if (wfHomeProperty != null) {
            wfHome = new File(wfHomeProperty);
            Assert.assertTrue(wfHome.exists(),
                    "${plain-wildfly.home.dir} [" + wfHome.getAbsolutePath() + "] does not exist");
        } else {
            wfHome = null;
        }

    }

    private static String readNode(Class<?> caller, String nodeFileName) throws IOException {
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

    private static void writeNode(Class<?> caller, ModelNode node, String nodeFileName)
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

        try (PrintWriter out = new PrintWriter(new OutputStreamWriter(new FileOutputStream(outputFile), "utf-8"))) {
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
        client.dispatcher().executorService().shutdown();
    }

    protected String getNodeAttribute(ModelControllerClient mcc, ModelNode addressActual, String attributeName) {
        return OperationBuilder.readAttribute()
                .address(addressActual)
                .name(attributeName)
                .includeDefaults()
                .execute(mcc)
                .assertSuccess()
                .getResultNode()
                .asString();
    }

    protected void writeNodeAttribute(ModelControllerClient mcc, ModelNode addressActual, String attributeName,
            String value) {
        OperationBuilder.writeAttribute()
                .address(addressActual)
                .attribute(attributeName, value)
                .execute(mcc)
                .assertSuccess();
    }

    protected void assertNodeAttributeEquals(ModelControllerClient mcc, ModelNode addressActual, String attributeName,
            String expectedAttributeValue) {
        String actualAttributeValue = getNodeAttribute(mcc, addressActual, attributeName);
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

    private Optional<InventoryStructure> extractStructureFromResponse(String responseBody) {
        try {
            JsonNode node = mapper.readTree(responseBody);
            String embeddedJson = node.get(0).get("value").asText();
            if (embeddedJson.isEmpty()) {
                return Optional.empty();
            }
            return Optional.of(mapper.readValue(embeddedJson, InventoryStructure.class));
        } catch (IOException e) {
            throw Throwables.propagate(e);
        }
    }

    private Map<String, InventoryStructure<?>> extractStructuresFromResponse(String responseBody) {
        try {
            Map<String, InventoryStructure<?>> result = new HashMap<>();
            for (JsonNode child : mapper.readTree(responseBody)) {
                String embeddedJson = child.get("data").get(0).get("value").asText();
                if (!embeddedJson.isEmpty()) {
                    result.put(child.get("id").asText(), mapper.readValue(embeddedJson, InventoryStructure.class));
                }
            }
            return result;
        } catch (IOException e) {
            throw Throwables.propagate(e);
        }
    }

    protected Optional<InventoryStructure> getInventoryStructure(String feedId, String type, String id) throws Throwable {
        String metric = Util.urlEncode("inventory." + feedId + "." + type + "." + id);
        String url = baseInvUri + "/" + metric + "/raw?limit=1&order=DESC";
        String response = getWithRetries(url);
        // System.err.println("Response body: " + response);
        return Optional.of(response)
                .filter(r -> !r.isEmpty())
                .flatMap(this::extractStructureFromResponse);
    }

    protected InventoryStructure.Offline<ResourceType.Blueprint> getResourceType(String feedId, String name)
            throws Throwable {
        Optional<InventoryStructure> optInventoryStructure = getInventoryStructure(feedId, "rt", name);
        AssertJUnit.assertTrue(optInventoryStructure.isPresent());
        return (InventoryStructure.Offline<ResourceType.Blueprint>) optInventoryStructure.get();
    }

    protected Optional<Blueprint> getBlueprintFromCP(CanonicalPath path) throws Throwable {
        Iterator<CanonicalPath> upDown = path.descendingIterator();
        if (!upDown.hasNext()) {
            return Optional.empty();
        }
        // Ignore tenant
        upDown.next();
        if (!upDown.hasNext()) {
            return Optional.empty();
        }
        String feed = upDown.next().getSegment().getElementId();
        // System.err.println("FEED: " + feed);
        if (!upDown.hasNext()) {
            return Optional.empty();
        }
        CanonicalPath itemPath = upDown.next();
        // System.err.println("ITEM: " + itemPath.toString());
        // System.err.println("getInventoryStructure(" + feed + ", " + itemPath.getSegment().getElementType().getSerialized() + ", " + itemPath.getSegment().getElementId());
        Optional<InventoryStructure> inventoryStructure = getInventoryStructure(
                feed,
                itemPath.getSegment().getElementType().getSerialized(),
                itemPath.getSegment().getElementId());
        return inventoryStructure.map(struct -> {
            // System.err.println("RELATIVE: " + path.relativeTo(itemPath));
            Blueprint bp = struct.get(path.relativeTo(itemPath));
            // System.err.println("bp=" + bp);
            return bp;
        });
    }

    protected Map<CanonicalPath, Blueprint> getBlueprintsByType(String feedId, String type) throws Throwable {
        String tagType = "rt." + type;
        // Find related root resources; they are identified in tag "rt.<type>"
        String url = baseMetricsUri + "/metrics?type=string&tags=module:inventory,type:r,feed:" + feedId
                + "," + tagType + ":*";
        String response = getWithRetries(newAuthRequest().url(url).get().build());
        // System.err.println("response=" + response);
        if (response.isEmpty()) {
            return new HashMap<>();
        }
        // The response contains all metrics that contain at least one resource of the required type
        // It also contains the relative path, from metric's root, of concerned resources
        Map<String, String[]> metricsToFetch = new HashMap<>();
        for (JsonNode metricJson : mapper.readTree(response)) {
            String id = URLDecoder.decode(metricJson.get("id").asText(), StandardCharsets.UTF_8.name());
            if (metricJson.has("tags")) {
                JsonNode tagsJson = metricJson.get("tags");
                if (tagsJson.has(tagType)) {
                    String[] resources = tagsJson.get(tagType).asText().split(",");
                    metricsToFetch.put(id, resources);
                }
            }
        }
        if (metricsToFetch.isEmpty()) {
            return new HashMap<>();
        }

        // Now fetch collected metrics
        url = baseInvUri + "/raw/query";
        String ids = metricsToFetch.keySet().stream()
                .map(m -> "\"" + m + "\"")
                .collect(Collectors.joining(","));
        // System.out.println("ids=" + ids);
        String params = "{\"ids\":[" + ids + "],\"limit\":1,\"order\":\"DESC\"}";
        response = getWithRetries(newAuthRequest()
                .url(url)
                .post(RequestBody.create(MediaType.parse("application/json"), params))
                .build());
        // System.err.println("response=" + response);
        if (response.isEmpty()) {
            return new HashMap<>();
        }

        // Now find each collected resource path in their belonging InventoryStructure
        Map<String, InventoryStructure<?>> structures = extractStructuresFromResponse(response);
        Map<CanonicalPath, Blueprint> matchingResources = new HashMap<>();
        CanonicalPath feedPath = feedPath(feedId).get();
        structures.forEach((metric,structure) -> {
            CanonicalPath rootPath = feedPath.modified().extend(SegmentType.r, structure.getRoot().getId()).get();
            // System.out.println("Root path: " + rootPath);
            String[] resourcePaths = metricsToFetch.get(metric);
            for (String resourcePath : resourcePaths) {
                // System.out.println("Resource path: " + resourcePath);
                RelativePath relativePath = RelativePath.fromString(resourcePath);
                // System.out.println("Relative path: " + relativePath);
                Blueprint bp = structure.get(relativePath);
                if (bp != null) {
                    CanonicalPath absolutePath = relativePath.applyTo(rootPath);
                    matchingResources.put(absolutePath, bp);
                    // System.out.println("Added resource: " + absolutePath);
                }
            }
        });
        return matchingResources;
    }

    protected String getWithRetries(String url) throws Throwable {
        return getWithRetries(newAuthRequest().url(url).build());
    }

    private String getWithRetries(Request request) throws Throwable {
        Throwable e = null;
        for (int i = 0; i < ATTEMPT_COUNT; i++) {
            try {
                try (Response response = client.newCall(request).execute()) {
                    System.out.println(
                            "Got code " + response.code() + " and message [" + response.message() + "] retries: " +
                                    request.url());
                    AssertJUnit.assertTrue(response.code() == 200 || response.code() == 204);
//                    System.out.println("Got after " + (i + 1) + " retries: " + request.url());
                    String responseBody = response.body().string();
//                    System.err.println("Response size: " + responseBody.length());
//                    ByteArrayOutputStream obj=new ByteArrayOutputStream();
//                    GZIPOutputStream gzip = new GZIPOutputStream(obj);
//                    gzip.write(responseBody.getBytes("UTF-8"));
//                    gzip.close();
//                    byte[] gzipped = obj.toByteArray();
//                    System.err.println("Compressed size: " + gzipped.length);
                    return responseBody;
                }
            } catch (Throwable t) {
                // some initial attempts may fail so we continue
                e = t;
            }
            System.out.println("URL [" + request.url() + "] not ready yet on " + (i + 1) + " of " + ATTEMPT_COUNT
                    + " attempts, about to retry after " + ATTEMPT_DELAY + " ms");
            Thread.sleep(ATTEMPT_DELAY);
        }
        throw e;
    }

    protected Request.Builder newAuthRequest() {
        return new Request.Builder()
                .addHeader("Authorization", authHeader)
                .addHeader("Accept", "application/json")
                .addHeader("Hawkular-Tenant", getTenantId());
    }

    /**
     * Subclass tests can override this if they are putting things in other tenants.
     * @return the tenant to use when connecting to the hawkular server.
     */
    protected String getTenantId() {
        return tenantId;
    }

    /**
     * If a plain WildFly server was configured for the tests, this returns its data.
     * This will throw a runtime exception if there is no plain wildfly server in the test framework.
     *
     * @return configuration info about the test plain wildfly server
     */
    protected static WildFlyClientConfig getPlainWildFlyClientConfig() {
        return new WildFlyClientConfig();
    }

    /**
     * @return Client to the Hawkular WildFly Server.
     */
    protected static ModelControllerClient newHawkularModelControllerClient() {
        return newModelControllerClient(hawkularHost, hawkularManagementPort);
    }

    /**
     * @return Client to the Hawkular WildFly Server.
     *
     * @see #getPlainWildFlyClientConfig()
     */
    protected static ModelControllerClient newPlainWildFlyModelControllerClient(WildFlyClientConfig config) {
        return newModelControllerClient(config.getHost(), config.getManagementPort());
    }

    protected static ModelControllerClient newModelControllerClient(final String host, final int managementPort) {
        final CallbackHandler callbackHandler = new CallbackHandler() {
            @Override
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

    /**
     * Gets a list of all DMR children names.
     *
     * @param config if null, uses {@link #newHawkularModelControllerClient()}, or creates
     *               connector to {@link #newPlainWildFlyModelControllerClient(WildFlyClientConfig)}
     * @param childTypeName the DMR name of the child type
     * @param parentAddress the parent whose children are to be returned
     * @return collection of child names
     */
    protected Collection<String> getDMRChildrenNames(WildFlyClientConfig config,
            String childTypeName, PathAddress parentAddress) {
        try (ModelControllerClient mcc2 = ((config == null) ? newHawkularModelControllerClient()
                : newPlainWildFlyModelControllerClient(config))) {
            ModelNode result = OperationBuilder.readChildrenNames()
                    .address(parentAddress)
                    .childType(childTypeName)
                    .execute(mcc2)
                    .getResultNode();

            return result.asList().stream().map(ModelNode::asString).collect(Collectors.toList());

        } catch (IOException e) {
            throw new RuntimeException("Could not get: " + parentAddress + "/" + childTypeName, e);
        }
    }

    protected void waitForAccountsAndInventory() throws Throwable {

        synchronized (waitForAccountsLock) {
            if (!accountsAndInventoryReady) {
                Thread.sleep(8000);

                String response = "";
                while (!response.contains("STARTED")) {
                    Thread.sleep(2000);
                    response = getWithRetries(baseMetricsUri + "/status");
                }
                accountsAndInventoryReady = true;
            }
        }
    }

    protected boolean waitForAgent(ModelControllerClient mcc) throws Throwable {
        Address agentAddress = Address.parse("/subsystem=hawkular-wildfly-agent");
        return waitForAgent(mcc, agentAddress);
    }

    protected boolean waitForAgent(ModelControllerClient mcc, Address agentAddress) throws Throwable {
        synchronized (waitForAgentLock) {
            String agentPath = agentAddress.toAddressPathString();
            log.info("Checking [" + agentPath + "] status...");
            int count = 0;
            while (++count <= 12) {
                Thread.sleep(5000);

                ModelNode op = JBossASClient.createRequest("status", agentAddress);
                ModelNode result = new JBossASClient(mcc).execute(op);
                if (JBossASClient.isSuccess(result)) {
                    String status = JBossASClient.getResults(result).asString().toUpperCase();
                    if (ServiceStatus.RUNNING.name().equals(status)) {
                        log.info("Agent [" + agentPath + "] status=" + status + ", continuing...");
                        return true;
                    } else {
                        log.info("Agent [" + agentPath + "] status=" + status + ", waiting...");
                    }
                }
            }
            return false;
        }
    }

    protected ModelNode getAgentInventoryReport(String host, int managementPort) {
        try (ModelControllerClient mcc = newModelControllerClient(host, managementPort)) {
            Address agentAddress = Address.parse("/subsystem=hawkular-wildfly-agent");
            ModelNode op = JBossASClient.createRequest("inventoryReport", agentAddress);
            ModelNode inventoryReport = new JBossASClient(mcc).execute(op);
            if (JBossASClient.isSuccess(inventoryReport)) {
                return JBossASClient.getResults(inventoryReport);
            } else {
                throw new Exception(JBossASClient.getFailureDescription(inventoryReport));
            }
        } catch (Exception e) {
            throw new RuntimeException("Could not get inventory report", e);
        }
    }

    /**
     * Return the {@link CanonicalPath} of the only WildFly server present in inventory under the hawkular feed.
     * This is the Hawkular Server itself.
     *
     * @return path of hawkular wildfly server resource
     */
    protected CanonicalPath getHawkularWildFlyServerResourcePath() throws Throwable {
        Map<CanonicalPath, Blueprint> wildflyServers = getBlueprintsByType(hawkularFeedId, "WildFly Server");
        AssertJUnit.assertEquals(1, wildflyServers.size());
        return wildflyServers.keySet().iterator().next();
    }

    /**
     * Return the {@link CanonicalPath} of the only WildFly Host Controller present in inventory under the feed
     * found in the given client config.
     *
     * @return path of host controller
     */
    protected CanonicalPath getHostController(WildFlyClientConfig hostControllerClientConfig) throws Throwable {
        Map<CanonicalPath, Blueprint> hcs = getBlueprintsByType(hostControllerClientConfig.getFeedId(), "Host Controller");
        AssertJUnit.assertEquals(1, hcs.size());
        return hcs.keySet().iterator().next();
    }

    protected File getTestApplicationFile() {
        String dir = System.getProperty("hawkular.test.staging.dir"); // the maven build put our test app here
        File app = new File(dir, "hawkular-wildfly-agent-helloworld-war.war");
        Assert.assertTrue(app.isFile(), "Missing test application - build is bad: " + app.getAbsolutePath());
        return app;
    }

    protected CanonicalPath.FeedBuilder feedPath(String feedId) {
        return CanonicalPath.of().tenant(getTenantId()).feed(feedId);
    }

    protected Map.Entry<CanonicalPath, Blueprint> waitForResourceContaining(String feed, String rType, String containing, long sleep, int attempts)
            throws Throwable {
        for (int i = 0; i < attempts; i++) {
            Optional<Map.Entry<CanonicalPath, Blueprint>> resource = getBlueprintsByType(feed, rType)
                    .entrySet().stream()
                    .filter(e -> containing == null || ((Entity.Blueprint) (e.getValue())).getId().contains(containing))
                    .findFirst();
            if (resource.isPresent()) {
                return resource.get();
            }
            Thread.sleep(sleep);
        }
        throw new AssertionError("Resource [type=" + rType + ", containing=" + containing + "] not found after " + attempts + " attempts.");
    }

    protected void waitForNoResourceContaining(String feed, String rType, String containing, long sleep, int attempts)
            throws Throwable {
        for (int i = 0; i < attempts; i++) {
            Optional<Map.Entry<CanonicalPath, Blueprint>> resource = getBlueprintsByType(feed, rType)
                    .entrySet().stream()
                    .filter(e -> containing == null || ((Entity.Blueprint) (e.getValue())).getId().contains(containing))
                    .findFirst();
            if (!resource.isPresent()) {
                return;
            }
            Thread.sleep(sleep);
        }
        throw new AssertionError("Resource [type=" + rType + ", containing=" + containing + "] still found after " + attempts + " attempts.");
    }
}
