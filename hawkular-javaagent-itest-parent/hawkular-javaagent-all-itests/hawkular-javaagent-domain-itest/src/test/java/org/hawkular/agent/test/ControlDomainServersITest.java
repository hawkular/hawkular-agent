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
package org.hawkular.agent.test;

import java.util.Collection;

import org.hawkular.cmdgw.ws.test.TestWebSocketClient;
import org.hawkular.dmrclient.Address;
import org.hawkular.inventory.api.model.Resource;
import org.hawkular.javaagent.itest.util.WildFlyClientConfig;
import org.jboss.as.controller.client.ModelControllerClient;
import org.testng.Assert;
import org.testng.annotations.Test;

@Test(suiteName = AbstractDomainITestSuite.SUITE)
public class ControlDomainServersITest extends AbstractDomainITestSuite {
    public static final String GROUP = "ControlDomainServersITest";

    @Test(groups = { GROUP }, dependsOnGroups = { DomainDeployApplicationITest.GROUP })
    public void testStopIndividualServer() throws Throwable {
        waitForHawkularServerToBeReady();

        final String serverToTest = "server-one";

        WildFlyClientConfig clientConfig = getPlainWildFlyClientConfig();

        // sanity check - the server-one server should be running
        try (ModelControllerClient mcc = newPlainWildFlyModelControllerClient(clientConfig)) {
            assertNodeAttributeEquals(mcc,
                    Address.parse("/host=master/server=" + serverToTest).getAddressNode(),
                    "server-state",
                    "running");
        }

        Collection<Resource> agents = testHelper.getResourceByType(hawkularFeedId, "Domain WildFly Server Controller", 1);
        Resource agent = agents.stream()
                .filter(e -> e.getName().contains(serverToTest))
                .findFirst()
                .get();

        String req = "ExecuteOperationRequest={\"authentication\":" + authentication + ", "
                + "\"feedId\":\"" + agent.getFeedId() + "\","
                + "\"resourceId\":\"" + agent.getId() + "\","
                + "\"operationName\":\"Stop\""
                + "}";
        String response = "ExecuteOperationResponse={"
                + "\"operationName\":\"Stop\","
                + "\"feedId\":\"" + agent.getFeedId() + "\","
                + "\"resourceId\":\"" + agent.getId() + "\","
                + "\"destinationSessionId\":\"{{sessionId}}\","
                + "\"status\":\"OK\","
                + "\"message\":\"Performed [Stop] on a [DMR Node] given by Feed Id ["
                + agent.getFeedId() + "] Resource Id [" + agent.getId() + "]\""
                + "}";
        try (TestWebSocketClient testClient = TestWebSocketClient.builder()
                .url(baseGwUri + "/ui/ws")
                .expectWelcome(req)
                .expectGenericSuccess(agent.getFeedId())
                .expectText(response, TestWebSocketClient.Answer.CLOSE)
                .expectClose()
                .build()) {
            testClient.validate(10000);
        }

        try (ModelControllerClient mcc = newPlainWildFlyModelControllerClient(clientConfig)) {
            String serverState = getNodeAttribute(mcc,
                    Address.parse("/host=master/server=" + serverToTest).getAddressNode(), "server-state");
            if (!serverState.toLowerCase().startsWith("stop")) { // stopped or stopping
                Assert.fail("Expected server state to be stopping or stopped, but was: " + serverState);
            }
        }
    }
}
