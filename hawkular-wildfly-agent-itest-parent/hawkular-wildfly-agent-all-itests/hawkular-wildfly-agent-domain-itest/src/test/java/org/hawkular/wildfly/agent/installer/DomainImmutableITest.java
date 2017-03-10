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
package org.hawkular.wildfly.agent.installer;

import java.util.Map;

import org.hamcrest.CoreMatchers;
import org.hawkular.cmdgw.ws.test.TestWebSocketClient;
import org.hawkular.cmdgw.ws.test.TestWebSocketClient.ExpectedEvent;
import org.hawkular.cmdgw.ws.test.TestWebSocketClient.ExpectedEvent.ExpectedMessage;
import org.hawkular.cmdgw.ws.test.TestWebSocketClient.PatternMatcher;
import org.hawkular.dmrclient.Address;
import org.hawkular.inventory.api.model.Entity;
import org.hawkular.inventory.paths.CanonicalPath;
import org.hawkular.wildfly.agent.itest.util.AbstractITest;
import org.hawkular.wildfly.agent.itest.util.WildFlyClientConfig;
import org.jboss.as.controller.client.ModelControllerClient;
import org.jboss.dmr.ModelNode;
import org.testng.Assert;
import org.testng.annotations.Test;

import okhttp3.ws.WebSocket;

/**
 * @author <a href="https://github.com/jshaughn">Jay Shaughnessy</a>
 */
public class DomainImmutableITest extends AbstractITest {
    public static final String GROUP = "DomainImmutableITest";

    // because this test will update the agent to be immutable, it must RUN AFTER THE OTHER ITESTS!
    @Test(groups = { GROUP }, dependsOnGroups = {
            AgentInstallerDomainITest.GROUP,
            ControlDomainServersITest.GROUP,
            DomainDeployApplicationITest.GROUP,
    })
    public void testImmutableUpdate() throws Throwable {
        waitForAccountsAndInventory();

        WildFlyClientConfig clientConfig = getPlainWildFlyClientConfig();
        CanonicalPath wfPath = getHostController(clientConfig);

        try (ModelControllerClient mcc = newPlainWildFlyModelControllerClient(clientConfig)) {

            final String serverToTest = "server-one";
            final String hostAgent = "/host=master/subsystem=hawkular-wildfly-agent";
            final Address hostAgentAddress = Address.parse(hostAgent);
            final ModelNode hostAgentNode = hostAgentAddress.getAddressNode();
            final String hostAttr = hostAgent + "/metric-set-dmr=WildFly Memory Metrics/metric-dmr=Heap Max";
            final Address hostAttrAddress = Address.parse(hostAttr);
            final ModelNode hostAttrNode = hostAttrAddress.getAddressNode();

            assertNodeAttributeEquals(mcc, hostAttrNode, "interval", "1");

            assertNodeAttributeEquals(mcc, hostAgentNode, "immutable", "${hawkular.agent.immutable:false}");
            writeNodeAttribute(mcc, hostAgentNode, "immutable", "true");
            assertNodeAttributeEquals(mcc, hostAgentNode, "immutable", "true");

            Assert.assertTrue(waitForAgent(mcc, hostAgentAddress), "Expected host agent to be started.");

            // FIXME
            CanonicalPath agentPath = getBlueprintsByType(clientConfig.getFeedId(), "Domain WildFly Server Controller")
                    .entrySet().stream()
                    .filter(e -> ((Entity.Blueprint)(e.getValue())).getId().contains(serverToTest))
                    .map(Map.Entry::getKey)
                    .findFirst()
                    .get();

            String req = "UpdateCollectionIntervalsRequest={\"authentication\":" + authentication + ", "
                    + "\"resourcePath\":\"" + agentPath.toString() + "\","
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

            assertNodeAttributeEquals(mcc, hostAttrNode, "interval", "1");
        }
    }
}
