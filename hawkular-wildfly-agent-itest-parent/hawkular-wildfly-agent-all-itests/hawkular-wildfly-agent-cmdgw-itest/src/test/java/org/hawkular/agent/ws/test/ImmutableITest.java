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

import org.hamcrest.CoreMatchers;
import org.hawkular.cmdgw.ws.test.TestWebSocketClient;
import org.hawkular.cmdgw.ws.test.TestWebSocketClient.ExpectedEvent;
import org.hawkular.cmdgw.ws.test.TestWebSocketClient.ExpectedEvent.ExpectedMessage;
import org.hawkular.cmdgw.ws.test.TestWebSocketClient.PatternMatcher;
import org.hawkular.dmrclient.Address;
import org.hawkular.inventory.api.model.Resource;
import org.hawkular.inventory.paths.CanonicalPath;
import org.jboss.as.controller.client.ModelControllerClient;
import org.jboss.dmr.ModelNode;
import org.testng.Assert;
import org.testng.annotations.Test;

import okhttp3.ws.WebSocket;

/**
 * @author <a href="https://github.com/jshaughn">Jay Shaughnessy</a>
 */
public class ImmutableITest extends AbstractCommandITest {
    public static final String GROUP = "ImmutableITest";

    // because this test will update the agent to be immutable, it must RUN AFTER THE OTHER ITESTS!
    @Test(groups = { GROUP }, dependsOnGroups = {
            DatasourceCommandITest.GROUP,
            ExecuteOperationCommandITest.GROUP,
            ExportJdrCommandITest.GROUP,
            JdbcDriverCommandITest.GROUP,
            StandaloneDeployApplicationITest.GROUP,
            StatisticsControlCommandITest.GROUP,
            UpdateCollectionIntervalsCommandITest.GROUP
    })
    public void testImmutableUpdate() throws Throwable {
        waitForAccountsAndInventory();

        CanonicalPath wfPath = getHawkularWildFlyServerResourcePath();

        try (ModelControllerClient mcc = newHawkularModelControllerClient()) {

            assertNodeAttributeEquals(mcc,
                    Address.parse(
                            "/subsystem=hawkular-wildfly-agent/metric-set-dmr=WildFly Memory Metrics/metric-dmr=Heap Max")
                            .getAddressNode(),
                    "interval",
                    "1");

            ModelNode agentAddress = Address.parse("/subsystem=hawkular-wildfly-agent").getAddressNode();
            assertNodeAttributeEquals(mcc, agentAddress, "immutable", "${hawkular.agent.immutable:false}");
            writeNodeAttribute(mcc, agentAddress, "immutable", "true");
            assertNodeAttributeEquals(mcc, agentAddress, "immutable", "true");

            Assert.assertTrue(waitForAgent(mcc), "Expected agent to be started.");

            // FIXME
            Resource agent = getResource(hawkularFeedId, "rt", "Hawkular%20WildFly%20Agent",
                    (r -> r.getId() != null));

            String req = "UpdateCollectionIntervalsRequest={\"authentication\":" + authentication + ", "
                    + "\"resourcePath\":\"" + agent.getPath().toString() + "\","
                    + "\"metricTypes\":{\"WildFly Memory Metrics~Heap Max\":\"77\"}"
                    + "}";
            String response = ".*\"status\":\"ERROR\""
                    + ".*\"message\":\"Could not perform.*Command not allowed because the agent is immutable.*";

            ExpectedEvent expectedEvent = new ExpectedMessage(new PatternMatcher(response),
                    CoreMatchers.equalTo(WebSocket.TEXT), TestWebSocketClient.Answer.CLOSE);

            try (TestWebSocketClient testClient = TestWebSocketClient.builder()
                    .url(baseGwUri + "/ui/ws")
                    .expectWelcome(req)
                    .expectGenericSuccess(wfPath.ids().getFeedId())
                    .expectMessage(expectedEvent)
                    .expectClose()
                    .build()) {
                testClient.validate(10000);
            }

            assertNodeAttributeEquals(mcc,
                    Address.parse(
                            "/subsystem=hawkular-wildfly-agent/metric-set-dmr=WildFly Memory Metrics/metric-dmr=Heap Max")
                            .getAddressNode(),
                    "interval",
                    "1");
        }
    }
}
