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

import org.hawkular.cmdgw.ws.test.TestWebSocketClient;
import org.hawkular.cmdgw.ws.test.TestWebSocketClient.MessageAnswer;
import org.hawkular.dmrclient.Address;
import org.hawkular.inventory.api.model.Resource;
import org.hawkular.inventory.paths.CanonicalPath;
import org.hawkular.wildfly.agent.itest.util.AbstractITest;
import org.hawkular.wildfly.agent.itest.util.WildFlyClientConfig;
import org.jboss.as.controller.client.ModelControllerClient;
import org.testng.annotations.Test;

public class DomainDeployApplicationITest extends AbstractITest {
    public static final String GROUP = "DomainDeployApplicationITest";

    @Test(groups = { GROUP }, dependsOnGroups = { AgentInstallerDomainITest.GROUP })
    public void testAddDeployment() throws Throwable {
        waitForAccountsAndInventory();

        WildFlyClientConfig clientConfig = getPlainWildFlyClientConfig();
        CanonicalPath wfPath = getHostController(clientConfig);
        File applicationFile = getTestApplicationFile();
        final String deploymentName = applicationFile.getName();

        String req = "DeployApplicationRequest={\"authentication\":" + authentication + ", "
                + "\"resourcePath\":\"" + wfPath.toString() + "\","
                + "\"destinationFileName\":\"" + deploymentName + "\","
                + "\"serverGroups\":\"main-server-group,other-server-group\""
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
            testClient.validate(10000);
        }

        // check that the app was really deployed on the two server groups
        try (ModelControllerClient mcc = newPlainWildFlyModelControllerClient(clientConfig)) {
            assertNodeAttributeEquals(mcc,
                    Address.parse(
                            "/server-group=main-server-group/deployment=hawkular-wildfly-agent-helloworld-war.war")
                            .getAddressNode(),
                    "enabled",
                    "true");
            assertNodeAttributeEquals(mcc,
                    Address.parse(
                            "/server-group=other-server-group/deployment=hawkular-wildfly-agent-helloworld-war.war")
                            .getAddressNode(),
                    "enabled",
                    "true");
        }
    }

    // this does a full undeploy via the host controller - removing from all server groups
    @Test(groups = { GROUP }, dependsOnMethods = { "testAddDeployment" })
    public void testUndeploy() throws Throwable {
        waitForAccountsAndInventory();

        WildFlyClientConfig clientConfig = getPlainWildFlyClientConfig();
        CanonicalPath wfPath = getHostController(clientConfig);
        File applicationFile = getTestApplicationFile();
        final String deploymentName = applicationFile.getName();

        String req = "UndeployApplicationRequest={\"authentication\":" + authentication + ", "
                + "\"resourcePath\":\"" + wfPath.toString() + "\","
                + "\"destinationFileName\":\"" + deploymentName + "\","
                + "\"serverGroups\":\"main-server-group,other-server-group\""
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
            testClient.validate(10000);
        }

        // check that the app was really undeployed on the two server groups
        try (ModelControllerClient mcc = newPlainWildFlyModelControllerClient(clientConfig)) {
            assertResourceExists(mcc,
                    Address.parse(
                            "/server-group=main-server-group/deployment=hawkular-wildfly-agent-helloworld-war.war/")
                            .getAddressNode(),
                    false);
            assertResourceExists(mcc,
                    Address.parse(
                            "/server-group=other-server-group/deployment=hawkular-wildfly-agent-helloworld-war.war/")
                            .getAddressNode(),
                    false);
        }
    }

    // this and the following tests will use the individual operations on the deployment itself to do things
    @Test(groups = { GROUP }, dependsOnMethods = { "testUndeploy" })
    public void testReloadDeploymentViaExecOp() throws Throwable {
        waitForAccountsAndInventory();

        // put the deployments back in (our earlier test "testUndeploy" removed them)
        testAddDeployment();

        WildFlyClientConfig clientConfig = getPlainWildFlyClientConfig();
        CanonicalPath wfPath = getHostController(clientConfig);
        final String deploymentName = getTestApplicationFile().getName();
        Resource deployment = getResource("/feeds/" + clientConfig.getFeedId() + "/resourceTypes/Deployment/resources",
                (r -> r.getId().endsWith("=" + deploymentName)));

        String req = "ExecuteOperationRequest={\"authentication\":" + authentication + ", "
                + "\"resourcePath\":\"" + deployment.getPath().toString() + "\","
                + "\"operationName\":\"Redeploy\""
                + "}";
        String response = "ExecuteOperationResponse={"
                + "\"operationName\":\"Redeploy\","
                + "\"resourcePath\":\"" + deployment.getPath() + "\","
                + "\"destinationSessionId\":\"{{sessionId}}\","
                + "\"status\":\"OK\","
                + "\"message\":\"Performed [Redeploy] on a [DMR Node] given by Inventory path ["
                + deployment.getPath() + "]\""
                + "}";
        try (TestWebSocketClient testClient = TestWebSocketClient.builder()
                .url(baseGwUri + "/ui/ws")
                .expectWelcome(req)
                .expectGenericSuccess(wfPath.ids().getFeedId())
                .expectText(response, TestWebSocketClient.Answer.CLOSE)
                .expectClose()
                .build()) {
            testClient.validate(10000);
        }
    }

    @Test(groups = { GROUP }, dependsOnMethods = { "testReloadDeploymentViaExecOp" })
    public void testUndeployDeploymentViaExecOp() throws Throwable {
        waitForAccountsAndInventory();

        WildFlyClientConfig clientConfig = getPlainWildFlyClientConfig();
        CanonicalPath wfPath = getHostController(clientConfig);
        final String deploymentName = getTestApplicationFile().getName();
        Resource deployment = getResource("/feeds/" + clientConfig.getFeedId() + "/resourceTypes/Deployment/resources",
                (r -> r.getId().endsWith("=" + deploymentName)));

        String req = "ExecuteOperationRequest={\"authentication\":" + authentication + ", "
                + "\"resourcePath\":\"" + deployment.getPath().toString() + "\","
                + "\"operationName\":\"Undeploy\""
                + "}";
        String response = "ExecuteOperationResponse={"
                + "\"operationName\":\"Undeploy\","
                + "\"resourcePath\":\"" + deployment.getPath() + "\","
                + "\"destinationSessionId\":\"{{sessionId}}\","
                + "\"status\":\"OK\","
                + "\"message\":\"Performed [Undeploy] on a [DMR Node] given by Inventory path ["
                + deployment.getPath() + "]\""
                + "}";
        try (TestWebSocketClient testClient = TestWebSocketClient.builder()
                .url(baseGwUri + "/ui/ws")
                .expectWelcome(req)
                .expectGenericSuccess(wfPath.ids().getFeedId())
                .expectText(response, TestWebSocketClient.Answer.CLOSE)
                .expectClose()
                .build()) {
            testClient.validate(10000);
        }
    }

    @Test(groups = { GROUP }, dependsOnMethods = { "testUndeployDeploymentViaExecOp" })
    public void testRemoveDeploymentViaExecOp() throws Throwable {
        waitForAccountsAndInventory();

        WildFlyClientConfig clientConfig = getPlainWildFlyClientConfig();
        CanonicalPath wfPath = getHostController(clientConfig);
        final String deploymentName = getTestApplicationFile().getName();
        Resource deployment = getResource("/feeds/" + clientConfig.getFeedId() + "/resourceTypes/Deployment/resources",
                (r -> r.getId().endsWith("=" + deploymentName)));

        String req = "ExecuteOperationRequest={\"authentication\":" + authentication + ", "
                + "\"resourcePath\":\"" + deployment.getPath().toString() + "\","
                + "\"operationName\":\"Remove\""
                + "}";
        String response = "ExecuteOperationResponse={"
                + "\"operationName\":\"Remove\","
                + "\"resourcePath\":\"" + deployment.getPath() + "\","
                + "\"destinationSessionId\":\"{{sessionId}}\","
                + "\"status\":\"OK\","
                + "\"message\":\"Performed [Remove] on a [DMR Node] given by Inventory path ["
                + deployment.getPath() + "]\""
                + "}";
        try (TestWebSocketClient testClient = TestWebSocketClient.builder()
                .url(baseGwUri + "/ui/ws")
                .expectWelcome(req)
                .expectGenericSuccess(wfPath.ids().getFeedId())
                .expectText(response, TestWebSocketClient.Answer.CLOSE)
                .expectClose()
                .build()) {
            testClient.validate(10000);
        }
    }
}
