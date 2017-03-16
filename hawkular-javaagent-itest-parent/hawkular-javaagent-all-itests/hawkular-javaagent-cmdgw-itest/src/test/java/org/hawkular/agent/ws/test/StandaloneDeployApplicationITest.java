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
package org.hawkular.agent.ws.test;

import java.io.File;
import java.util.Optional;

import org.hawkular.cmdgw.ws.test.TestWebSocketClient;
import org.hawkular.cmdgw.ws.test.TestWebSocketClient.MessageAnswer;
import org.hawkular.dmrclient.Address;
import org.hawkular.inventory.api.model.Blueprint;
import org.hawkular.inventory.paths.CanonicalPath;
import org.jboss.as.controller.client.ModelControllerClient;
import org.junit.Assert;
import org.testng.annotations.Test;

public class StandaloneDeployApplicationITest extends AbstractCommandITest {
    public static final String GROUP = "StandaloneDeployApplicationITest";

    @Test(groups = { GROUP }, dependsOnGroups = { UpdateCollectionIntervalsCommandITest.GROUP })
    public void testAddDeployment() throws Throwable {
        waitForHawkularServerToBeReady();

        CanonicalPath wfPath = getHawkularWildFlyServerResourcePath();
        File applicationFile = getTestApplicationFile();
        final String deploymentName = applicationFile.getName();

        String req = "DeployApplicationRequest={\"authentication\":" + authentication + ", "
                + "\"resourcePath\":\"" + wfPath.toString() + "\","
                + "\"destinationFileName\":\"" + deploymentName + "\""
                + "}";
        String response = "DeployApplicationResponse={"
                + "\"destinationFileName\":\"" + deploymentName + "\","
                + "\"resourcePath\":\"" + wfPath.toString() + "\","
                + "\"destinationSessionId\":\"{{sessionId}}\","
                + "\"status\":\"OK\","
                + "\"message\":\"Performed [Deploy] on a [Application] given by Inventory path ["
                + wfPath.toString() + "]\""
                + "}";
        try (TestWebSocketClient testClient = TestWebSocketClient.builder()
                .url(baseGwUri + "/ui/ws")
                .expectWelcome(new MessageAnswer(req, applicationFile.toURI().toURL(), 0))
                .expectGenericSuccess(wfPath.ids().getFeedId())
                .expectText(response, TestWebSocketClient.Answer.CLOSE)
                .expectClose()
                .build()) {
            testClient.validate(30000);
        }

        // check that the app was really deployed
        try (ModelControllerClient mcc = newHawkularModelControllerClient()) {
            assertNodeAttributeEquals(mcc,
                    Address.parse(
                            "/deployment=hawkular-javaagent-helloworld-war.war")
                            .getAddressNode(),
                    "enabled",
                    "true");
        }
    }

    @Test(groups = { GROUP }, dependsOnMethods = { "testAddDeployment" })
    public void testDisableDeployment() throws Throwable {
        waitForHawkularServerToBeReady();

        // check that the app is currently enabled
        try (ModelControllerClient mcc = newHawkularModelControllerClient()) {
            assertNodeAttributeEquals(mcc,
                    Address.parse(
                            "/deployment=hawkular-javaagent-helloworld-war.war")
                            .getAddressNode(),
                    "enabled",
                    "true");
        }

        CanonicalPath wfPath = getHawkularWildFlyServerResourcePath();
        File applicationFile = getTestApplicationFile();
        final String deploymentName = applicationFile.getName();

        String req = "DisableApplicationRequest={\"authentication\":" + authentication + ", "
                + "\"resourcePath\":\"" + wfPath.toString() + "\","
                + "\"destinationFileName\":\"" + deploymentName + "\""
                + "}";
        String response = "DisableApplicationResponse={"
                + "\"destinationFileName\":\"" + deploymentName + "\","
                + "\"resourcePath\":\"" + wfPath.toString() + "\","
                + "\"destinationSessionId\":\"{{sessionId}}\","
                + "\"status\":\"OK\","
                + "\"message\":\"Performed [Disable Deployment] on a [Application] given by Inventory path ["
                + wfPath.toString() + "]\""
                + "}";
        try (TestWebSocketClient testClient = TestWebSocketClient.builder()
                .url(baseGwUri + "/ui/ws")
                .expectWelcome(new MessageAnswer(req, applicationFile.toURI().toURL(), 0))
                .expectGenericSuccess(wfPath.ids().getFeedId())
                .expectText(response, TestWebSocketClient.Answer.CLOSE)
                .expectClose()
                .build()) {
            testClient.validate(30000);
        }

        // check that the app is currently disabled
        try (ModelControllerClient mcc = newHawkularModelControllerClient()) {
            assertNodeAttributeEquals(mcc,
                    Address.parse(
                            "/deployment=hawkular-javaagent-helloworld-war.war")
                            .getAddressNode(),
                    "enabled",
                    "false");
        }
    }

    @Test(groups = { GROUP }, dependsOnMethods = { "testDisableDeployment" })
    public void testEnableDeployment() throws Throwable {
        waitForHawkularServerToBeReady();

        // check that the app is currently disabled
        try (ModelControllerClient mcc = newHawkularModelControllerClient()) {
            assertNodeAttributeEquals(mcc,
                    Address.parse(
                            "/deployment=hawkular-javaagent-helloworld-war.war")
                            .getAddressNode(),
                    "enabled",
                    "false");
        }

        CanonicalPath wfPath = getHawkularWildFlyServerResourcePath();
        File applicationFile = getTestApplicationFile();
        final String deploymentName = applicationFile.getName();

        String req = "EnableApplicationRequest={\"authentication\":" + authentication + ", "
                + "\"resourcePath\":\"" + wfPath.toString() + "\","
                + "\"destinationFileName\":\"" + deploymentName + "\""
                + "}";
        String response = "EnableApplicationResponse={"
                + "\"destinationFileName\":\"" + deploymentName + "\","
                + "\"resourcePath\":\"" + wfPath.toString() + "\","
                + "\"destinationSessionId\":\"{{sessionId}}\","
                + "\"status\":\"OK\","
                + "\"message\":\"Performed [Enable Deployment] on a [Application] given by Inventory path ["
                + wfPath.toString() + "]\""
                + "}";
        try (TestWebSocketClient testClient = TestWebSocketClient.builder()
                .url(baseGwUri + "/ui/ws")
                .expectWelcome(new MessageAnswer(req, applicationFile.toURI().toURL(), 0))
                .expectGenericSuccess(wfPath.ids().getFeedId())
                .expectText(response, TestWebSocketClient.Answer.CLOSE)
                .expectClose()
                .build()) {
            testClient.validate(30000);
        }

        // check that the app is currently enabled
        try (ModelControllerClient mcc = newHawkularModelControllerClient()) {
            assertNodeAttributeEquals(mcc,
                    Address.parse(
                            "/deployment=hawkular-javaagent-helloworld-war.war")
                            .getAddressNode(),
                    "enabled",
                    "true");
        }
    }

    @Test(groups = { GROUP }, dependsOnMethods = { "testEnableDeployment" })
    public void testRestartDeployment() throws Throwable {
        waitForHawkularServerToBeReady();

        // check that the app is currently enabled
        try (ModelControllerClient mcc = newHawkularModelControllerClient()) {
            assertNodeAttributeEquals(mcc,
                    Address.parse(
                            "/deployment=hawkular-javaagent-helloworld-war.war")
                            .getAddressNode(),
                    "enabled",
                    "true");
        }

        CanonicalPath wfPath = getHawkularWildFlyServerResourcePath();
        File applicationFile = getTestApplicationFile();
        final String deploymentName = applicationFile.getName();

        String req = "RestartApplicationRequest={\"authentication\":" + authentication + ", "
                + "\"resourcePath\":\"" + wfPath.toString() + "\","
                + "\"destinationFileName\":\"" + deploymentName + "\""
                + "}";
        String response = "RestartApplicationResponse={"
                + "\"destinationFileName\":\"" + deploymentName + "\","
                + "\"resourcePath\":\"" + wfPath.toString() + "\","
                + "\"destinationSessionId\":\"{{sessionId}}\","
                + "\"status\":\"OK\","
                + "\"message\":\"Performed [Restart Deployment] on a [Application] given by Inventory path ["
                + wfPath.toString() + "]\""
                + "}";
        try (TestWebSocketClient testClient = TestWebSocketClient.builder()
                .url(baseGwUri + "/ui/ws")
                .expectWelcome(new MessageAnswer(req, applicationFile.toURI().toURL(), 0))
                .expectGenericSuccess(wfPath.ids().getFeedId())
                .expectText(response, TestWebSocketClient.Answer.CLOSE)
                .expectClose()
                .build()) {
            testClient.validate(30000);
        }

        // check that the app is again enabled
        try (ModelControllerClient mcc = newHawkularModelControllerClient()) {
            assertNodeAttributeEquals(mcc,
                    Address.parse(
                            "/deployment=hawkular-javaagent-helloworld-war.war")
                            .getAddressNode(),
                    "enabled",
                    "true");
        }
    }

    // While we have our war deployed, let's test some JMX operations via the ExecuteOperations request.
    // Our test war deploys an MXBean that let's us exercise passing in different types of params.
    @Test(groups = { GROUP }, dependsOnMethods = { "testRestartDeployment" })
    public void testLocalAndRemoteJMXOperations() throws Throwable {
        waitForHawkularServerToBeReady();

        // this should exist
        waitForResourceContaining(hawkularFeedId, "Deployment", "hawkular-javaagent-helloworld-war.war", 5000, 10);

        // make sure to discover the mbean resources (same MBean discovered by local and remote managed server)
        forceInventoryDiscoveryScan();

        CanonicalPath localPath = feedPath(hawkularFeedId).resource("Local JMX~org.hawkular.agent.itest:type=simple").get();
        Optional<Blueprint> resource = getBlueprintFromCP(localPath);
        Assert.assertTrue(resource.isPresent());

        invokeJMXOperations(localPath);

        CanonicalPath remotePath = feedPath(hawkularFeedId).resource("Remote JMX~org.hawkular.agent.itest:type=simple").get();
        resource = getBlueprintFromCP(remotePath);
        Assert.assertTrue(resource.isPresent());

        invokeJMXOperations(remotePath);

    }

    private void invokeJMXOperations(CanonicalPath mbeanPath) throws Throwable {

        // jmx operation with no parameters

        String req = "ExecuteOperationRequest={\"authentication\":" + authentication + ", "
                + "\"resourcePath\":\"" + mbeanPath.toString() + "\","
                + "\"operationName\":\"testOperationNoParams\""
                + "}";
        String response = "ExecuteOperationResponse={"
                + "\"operationName\":\"testOperationNoParams\","
                + "\"resourcePath\":\"" + mbeanPath + "\","
                + "\"destinationSessionId\":\"{{sessionId}}\","
                + "\"status\":\"OK\","
                + "\"message\":\"Performed [testOperationNoParams] on a [JMX MBean] given by Inventory path ["
                + mbeanPath + "]\""
                + "}";
        try (TestWebSocketClient testClient = TestWebSocketClient.builder()
                .url(baseGwUri + "/ui/ws")
                .expectWelcome(req)
                .expectGenericSuccess(mbeanPath.ids().getFeedId())
                .expectText(response, TestWebSocketClient.Answer.CLOSE)
                .expectClose()
                .build()) {
            testClient.validate(10000);
        }

        // jmx operation with the different primitive types as parameters
        req = "ExecuteOperationRequest={\"authentication\":" + authentication + ", "
                + "\"resourcePath\":\"" + mbeanPath.toString() + "\","
                + "\"operationName\":\"testOperationPrimitive\","
                + "\"parameters\": {"
                + "    \"s\":\"STR\","
                + "    \"i\":\"1\","
                + "    \"b\":\"true\","
                + "    \"l\":\"2\","
                + "    \"d\":\"3.0\","
                + "    \"f\":\"4.0\","
                + "    \"h\":\"5\","
                + "    \"c\":\"a\","
                + "    \"y\":\"6\""
                + "  }"
                + "}";
        response = "ExecuteOperationResponse={"
                + "\"operationName\":\"testOperationPrimitive\","
                + "\"resourcePath\":\"" + mbeanPath + "\","
                + "\"destinationSessionId\":\"{{sessionId}}\","
                + "\"status\":\"OK\","
                + "\"message\":\"Performed [testOperationPrimitive] on a [JMX MBean] given by Inventory path ["
                + mbeanPath + "]: "
                + "string=STR, int=1, boolean=true, long=2, double=3.0, float=4.0, short=5, char=a, byte=6"
                + "\"}";
        try (TestWebSocketClient testClient = TestWebSocketClient.builder()
                .url(baseGwUri + "/ui/ws")
                .expectWelcome(req)
                .expectGenericSuccess(mbeanPath.ids().getFeedId())
                .expectText(response, TestWebSocketClient.Answer.CLOSE)
                .expectClose()
                .build()) {
            testClient.validate(10000);
        }

        // jmx operation with the different "Object primitive" types as parameters
        req = "ExecuteOperationRequest={\"authentication\":" + authentication + ", "
                + "\"resourcePath\":\"" + mbeanPath.toString() + "\","
                + "\"operationName\":\"testOperation\","
                + "\"parameters\": {"
                + "    \"s\":\"STR\","
                + "    \"i\":\"1\","
                + "    \"b\":\"true\","
                + "    \"l\":\"2\","
                + "    \"d\":\"3.0\","
                + "    \"f\":\"4.0\","
                + "    \"h\":\"5\","
                + "    \"c\":\"a\","
                + "    \"y\":\"6\""
                + "  }"
                + "}";
        response = "ExecuteOperationResponse={"
                + "\"operationName\":\"testOperation\","
                + "\"resourcePath\":\"" + mbeanPath + "\","
                + "\"destinationSessionId\":\"{{sessionId}}\","
                + "\"status\":\"OK\","
                + "\"message\":\"Performed [testOperation] on a [JMX MBean] given by Inventory path ["
                + mbeanPath + "]: "
                + "String=STR, Int=1, Boolean=true, Long=2, Double=3.0, Float=4.0, Short=5, Char=a, Byte=6"
                + "\"}";
        try (TestWebSocketClient testClient = TestWebSocketClient.builder()
                .url(baseGwUri + "/ui/ws")
                .expectWelcome(req)
                .expectGenericSuccess(mbeanPath.ids().getFeedId())
                .expectText(response, TestWebSocketClient.Answer.CLOSE)
                .expectClose()
                .build()) {
            testClient.validate(10000);
        }

        // jmx operation - signature with primitives but relying on default values (not passing in any args)
        req = "ExecuteOperationRequest={\"authentication\":" + authentication + ", "
                + "\"resourcePath\":\"" + mbeanPath.toString() + "\","
                + "\"operationName\":\"testOperationPrimitive\""
                + "}";
        response = "ExecuteOperationResponse={"
                + "\"operationName\":\"testOperationPrimitive\","
                + "\"resourcePath\":\"" + mbeanPath + "\","
                + "\"destinationSessionId\":\"{{sessionId}}\","
                + "\"status\":\"OK\","
                + "\"message\":\"Performed [testOperationPrimitive] on a [JMX MBean] given by Inventory path ["
                + mbeanPath + "]: "
                + "string=yaml default"
                + ", int=111, boolean=false, long=222, double=3.33, float=4.44, short=5, char=x, byte=0"
                + "\"}";
        try (TestWebSocketClient testClient = TestWebSocketClient.builder()
                .url(baseGwUri + "/ui/ws")
                .expectWelcome(req)
                .expectGenericSuccess(mbeanPath.ids().getFeedId())
                .expectText(response, TestWebSocketClient.Answer.CLOSE)
                .expectClose()
                .build()) {
            testClient.validate(10000);
        }

        // jmx operation - signature with Objects but relying on default values (not passing in any args)
        req = "ExecuteOperationRequest={\"authentication\":" + authentication + ", "
                + "\"resourcePath\":\"" + mbeanPath.toString() + "\","
                + "\"operationName\":\"testOperation\""
                + "}";
        response = "ExecuteOperationResponse={"
                + "\"operationName\":\"testOperation\","
                + "\"resourcePath\":\"" + mbeanPath + "\","
                + "\"destinationSessionId\":\"{{sessionId}}\","
                + "\"status\":\"OK\","
                + "\"message\":\"Performed [testOperation] on a [JMX MBean] given by Inventory path ["
                + mbeanPath + "]: "
                + "String=null"
                + ", Int=null, Boolean=null, Long=null, Double=null, Float=null, Short=null, Char=null, Byte=null"
                + "\"}";
        try (TestWebSocketClient testClient = TestWebSocketClient.builder()
                .url(baseGwUri + "/ui/ws")
                .expectWelcome(req)
                .expectGenericSuccess(mbeanPath.ids().getFeedId())
                .expectText(response, TestWebSocketClient.Answer.CLOSE)
                .expectClose()
                .build()) {
            testClient.validate(10000);
        }
    }

    @Test(groups = { GROUP }, dependsOnMethods = { "testLocalAndRemoteJMXOperations" })
    public void testUndeploy() throws Throwable {
        waitForHawkularServerToBeReady();

        // this should exist
        waitForResourceContaining(hawkularFeedId, "Deployment", "hawkular-javaagent-helloworld-war.war",
                5000, 10);

        CanonicalPath wfPath = getHawkularWildFlyServerResourcePath();
        File applicationFile = getTestApplicationFile();
        final String deploymentName = applicationFile.getName();

        String req = "UndeployApplicationRequest={\"authentication\":" + authentication + ", "
                + "\"resourcePath\":\"" + wfPath.toString() + "\","
                + "\"destinationFileName\":\"" + deploymentName + "\""
                + "}";
        String response = "UndeployApplicationResponse={"
                + "\"destinationFileName\":\"" + deploymentName + "\","
                + "\"resourcePath\":\"" + wfPath.toString() + "\","
                + "\"destinationSessionId\":\"{{sessionId}}\","
                + "\"status\":\"OK\","
                + "\"message\":\"Performed [Undeploy] on a [Application] given by Inventory path ["
                + wfPath.toString() + "]\""
                + "}";
        try (TestWebSocketClient testClient = TestWebSocketClient.builder()
                .url(baseGwUri + "/ui/ws")
                .expectWelcome(new MessageAnswer(req, applicationFile.toURI().toURL(), 0))
                .expectGenericSuccess(wfPath.ids().getFeedId())
                .expectText(response, TestWebSocketClient.Answer.CLOSE)
                .expectClose()
                .build()) {
            testClient.validate(30000);
        }

        // check that the app was really undeployed
        try (ModelControllerClient mcc = newHawkularModelControllerClient()) {
            assertResourceExists(mcc,
                    Address.parse(
                            "/deployment=hawkular-javaagent-helloworld-war.war")
                            .getAddressNode(),
                    false);
        }

        // this should be gone now, let's make sure it does get deleted from h-inventory
        waitForNoResourceContaining(hawkularFeedId, "Deployment", "hawkular-javaagent-helloworld-war.war",
                5000, 10);
    }
}
