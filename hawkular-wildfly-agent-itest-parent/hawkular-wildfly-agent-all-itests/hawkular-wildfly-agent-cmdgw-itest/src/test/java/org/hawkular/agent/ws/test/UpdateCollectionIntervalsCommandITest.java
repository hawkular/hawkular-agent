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

import java.util.Map;

import org.hawkular.cmdgw.ws.test.TestWebSocketClient;
import org.hawkular.dmrclient.Address;
import org.hawkular.inventory.paths.CanonicalPath;
import org.jboss.as.controller.client.ModelControllerClient;
import org.testng.Assert;
import org.testng.annotations.Test;

/**
 * @author <a href="https://github.com/jshaughn">Jay Shaughnessy</a>
 */
public class UpdateCollectionIntervalsCommandITest extends AbstractCommandITest {
    public static final String GROUP = "UpdateCollectionIntervalsCommandITest";

    @Test(groups = { GROUP })
    public void testUpdateCollectionIntervals() throws Throwable {
        waitForAccountsAndInventory();

        CanonicalPath wfPath = getHawkularWildFlyServerResourcePath();

        try (ModelControllerClient mcc = newHawkularModelControllerClient()) {

            assertNodeAttributeEquals(mcc,
                    Address.parse(
                            "/subsystem=hawkular-wildfly-agent/avail-set-dmr=Server Availability/avail-dmr=Server Availability")
                            .getAddressNode(),
                    "interval",
                    "30");

            assertNodeAttributeEquals(mcc,
                    Address.parse(
                            "/subsystem=hawkular-wildfly-agent/metric-set-dmr=WildFly Memory Metrics/metric-dmr=NonHeap Used")
                            .getAddressNode(),
                    "interval",
                    "30");

            CanonicalPath agentPath = getBlueprintsByType(hawkularFeedId, "Hawkular WildFly Agent")
                    .entrySet().stream()
//                .filter(e -> ((Entity.Blueprint)(e.getValue())).getId() != null)
                    .map(Map.Entry::getKey)
                    .findFirst()
                    .get();

            String req = "UpdateCollectionIntervalsRequest={\"authentication\":" + authentication + ", "
                    + "\"resourcePath\":\"" + agentPath.toString() + "\","
                    + "\"metricTypes\":{\"WildFly Memory Metrics~NonHeap Used\":\"0\",\"Unknown~Metric\":\"666\"},"
                    + "\"availTypes\":{\"Server Availability~Server Availability\":\"0\",\"Unknown~Avail\":\"666\"}"
                    + "}";
            String response = "UpdateCollectionIntervalsResponse={"
                    + "\"resourcePath\":\"" + agentPath + "\","
                    + "\"destinationSessionId\":\"{{sessionId}}\","
                    + "\"status\":\"OK\","
                    + "\"message\":\"Performed [Update Collection Intervals] on a [Agent[DMR]] given by Inventory path ["
                    + agentPath + "]\""
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

            // Make sure the agent reboots before executing other itests
            Assert.assertTrue(waitForAgent(mcc), "Expected agent to be started.");

            assertNodeAttributeEquals(mcc,
                    Address.parse(
                            "/subsystem=hawkular-wildfly-agent/avail-set-dmr=Server Availability/avail-dmr=Server Availability")
                            .getAddressNode(),
                    "interval",
                    "0");
            assertNodeAttributeEquals(mcc,
                    Address.parse(
                            "/subsystem=hawkular-wildfly-agent/metric-set-dmr=WildFly Memory Metrics/metric-dmr=NonHeap Used")
                            .getAddressNode(),
                    "interval",
                    "0");
        }
    }
}
