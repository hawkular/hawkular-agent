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
package org.hawkular.agent.ws.test;

import java.io.File;

import org.hawkular.cmdgw.ws.test.TestWebSocketClient;
import org.hawkular.cmdgw.ws.test.TestWebSocketClient.MessageAnswer;
import org.hawkular.inventory.api.model.Resource;
import org.hawkular.inventory.paths.CanonicalPath;
import org.jboss.arquillian.container.test.api.RunAsClient;
import org.junit.Assert;
import org.testng.annotations.Test;

public class DeployApplicationITest extends AbstractCommandITest {
    public static final String GROUP = "DeployApplicationITest";

    @RunAsClient
    @Test(groups = { GROUP }, dependsOnGroups = { ExecuteOperationCommandITest.GROUP })
    public void testAddDeployment() throws Throwable {
        waitForAccountsAndInventory();

        CanonicalPath wfPath = getCurrentASPath();
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
            testClient.validate(10000);
        }
    }

    @RunAsClient
    @Test(groups = { GROUP }, dependsOnMethods = { "testAddDeployment" })
    public void testReloadDeployment() throws Throwable {
        waitForAccountsAndInventory();

        CanonicalPath wfPath = getCurrentASPath();
        final String deploymentName = getTestApplicationFile().getName();
        Resource deployment = getResource("/feeds/" + getFeedId() + "/resourceTypes/Deployment/resources",
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

    @RunAsClient
    @Test(groups = { GROUP }, dependsOnMethods = { "testReloadDeployment" })
    public void testUndeployDeployment() throws Throwable {
        waitForAccountsAndInventory();

        CanonicalPath wfPath = getCurrentASPath();
        final String deploymentName = getTestApplicationFile().getName();
        Resource deployment = getResource("/feeds/" + getFeedId() + "/resourceTypes/Deployment/resources",
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

    @RunAsClient
    @Test(groups = { GROUP }, dependsOnMethods = { "testUndeployDeployment" })
    public void testRemoveDeployment() throws Throwable {
        waitForAccountsAndInventory();

        CanonicalPath wfPath = getCurrentASPath();
        final String deploymentName = getTestApplicationFile().getName();
        Resource deployment = getResource("/feeds/" + getFeedId() + "/resourceTypes/Deployment/resources",
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

    private File getTestApplicationFile() {
        String dir = System.getProperty("hawkular.test.staging.dir"); // the maven build put our test app here
        File app = new File(dir, "hawkular-wildfly-agent-helloworld-war.war");
        Assert.assertTrue("Missing test application - build is bad: " + app.getAbsolutePath(), app.isFile());
        return app;
    }
}
