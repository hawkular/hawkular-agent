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

import org.hawkular.cmdgw.ws.test.EchoCommandITest;
import org.hawkular.cmdgw.ws.test.TestWebSocketClient;
import org.hawkular.inventory.api.model.Resource;
import org.hawkular.inventory.paths.CanonicalPath;
import org.jboss.arquillian.container.test.api.RunAsClient;
import org.testng.annotations.Test;

/**
 * @author <a href="https://github.com/ppalaga">Peter Palaga</a>
 */
public class ExecuteOperationCommandITest extends AbstractCommandITest {
    public static final String GROUP = "ExecuteOperationCommandITest";

    @RunAsClient
    @Test(groups = { GROUP }, dependsOnGroups = { EchoCommandITest.GROUP })
    public void testExecuteAgentDiscoveryScan() throws Throwable {
        waitForAccountsAndInventory();

        CanonicalPath wfPath = getCurrentASPath();
        Resource agent = getResource("/feeds/" + getFeedId() + "/resourceTypes/Hawkular%20WildFly%20Agent/resources",
                (r -> r.getId() != null));

        String req = "ExecuteOperationRequest={\"authentication\":" + authentication + ", "
                + "\"resourcePath\":\"" + agent.getPath().toString() + "\","
                + "\"operationName\":\"Inventory Discovery Scan\""
                + "}";
        String response = "ExecuteOperationResponse={"
                + "\"operationName\":\"Inventory Discovery Scan\","
                + "\"resourcePath\":\"" + agent.getPath() + "\","
                + "\"destinationSessionId\":\"{{sessionId}}\","
                + "\"status\":\"OK\","
                + "\"message\":\"Performed [Inventory Discovery Scan] on a [DMR Node] given by Inventory path ["
                + agent.getPath() + "]\""
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
