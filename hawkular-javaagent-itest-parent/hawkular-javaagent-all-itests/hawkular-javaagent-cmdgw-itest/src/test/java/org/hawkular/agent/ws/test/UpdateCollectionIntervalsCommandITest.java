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

import org.hawkular.agent.javaagent.config.Configuration;
import org.hawkular.agent.javaagent.config.DMRAvail;
import org.hawkular.agent.javaagent.config.DMRAvailSet;
import org.hawkular.agent.javaagent.config.DMRMetric;
import org.hawkular.agent.javaagent.config.DMRMetricSet;
import org.hawkular.agent.javaagent.config.TimeUnits;
import org.hawkular.cmdgw.ws.test.TestWebSocketClient;
import org.hawkular.inventory.paths.CanonicalPath;
import org.testng.Assert;
import org.testng.annotations.Test;

/**
 * @author <a href="https://github.com/jshaughn">Jay Shaughnessy</a>
 */
public class UpdateCollectionIntervalsCommandITest extends AbstractCommandITest {
    public static final String GROUP = "UpdateCollectionIntervalsCommandITest";

    @Test(groups = { GROUP }, dependsOnGroups = { ExecuteOperationCommandITest.GROUP })
    public void testUpdateCollectionIntervals() throws Throwable {
        waitForHawkularServerToBeReady();

        waitForAgentViaJMX();

        CanonicalPath agentPath = getBlueprintsByType(hawkularFeedId, "Hawkular WildFly Agent")
                .keySet().iterator().next();

        // check we are starting with our original defaults - this is just a sanity check
        Configuration agentConfig = getAgentConfigurationFromFile();
        assertMetricInterval(agentConfig, "WildFly Memory Metrics", "NonHeap Committed", 1, TimeUnits.minutes);
        assertAvailInterval(agentConfig, "Server Availability", "Server Availability", 30, TimeUnits.seconds);

        String req = "UpdateCollectionIntervalsRequest={\"authentication\":" + authentication + ", "
                + "\"resourcePath\":\"" + agentPath.toString() + "\","
                + "\"metricTypes\":{\"WildFly Memory Metrics~NonHeap Committed\":\"0\",\"Unknown~Metric\":\"666\"},"
                + "\"availTypes\":{\"Server Availability~Server Availability\":\"0\",\"Unknown~Avail\":\"666\"}"
                + "}";
        String response = "UpdateCollectionIntervalsResponse={"
                + "\"resourcePath\":\"" + agentPath + "\","
                + "\"destinationSessionId\":\"{{sessionId}}\","
                + "\"status\":\"OK\","
                + "\"message\":\"Performed [Update Collection Intervals] on a [Agent[JMX]] given by Inventory path ["
                + agentPath + "]\""
                + "}";

        try (TestWebSocketClient testClient = TestWebSocketClient.builder()
                .url(baseGwUri + "/ui/ws")
                .expectWelcome(req)
                .expectGenericSuccess(agentPath.ids().getFeedId())
                .expectText(response, TestWebSocketClient.Answer.CLOSE)
                .expectClose()
                .build()) {
            testClient.validate(10000);
        }

        // Make sure the agent reboots before executing other itests
        Assert.assertTrue(waitForAgentViaJMX(), "Expected agent to be started.");

        // re-read the agent config - it should have changed with the new values
        agentConfig = getAgentConfigurationFromFile();
        assertMetricInterval(agentConfig, "WildFly Memory Metrics", "NonHeap Committed", 0, TimeUnits.seconds);
        assertAvailInterval(agentConfig, "Server Availability", "Server Availability", 0, TimeUnits.seconds);
    }

    private void assertMetricInterval(Configuration agentConfig, String setName, String metricName, int expectedVal,
            TimeUnits expectedUnits) {
        for (DMRMetricSet s : agentConfig.getDmrMetricSets()) {
            if (s.getName().equals(setName)) {
                for (DMRMetric m : s.getDmrMetrics()) {
                    if (m.getName().equals(metricName)) {
                        if (m.getInterval().intValue() == expectedVal) {
                            return;
                        } else {
                            Assert.fail(String.format("Metric type [%s~%s] expected to be [%d %s] but was [%d %s]",
                                    setName, metricName,
                                    expectedVal, expectedUnits.name(),
                                    m.getInterval().intValue(), m.getTimeUnits().name()));
                        }
                    }
                }
            }
        }
        Assert.fail(String.format("Agent missing metric type [%s~%s]", setName, metricName));
    }

    private void assertAvailInterval(Configuration agentConfig, String setName, String availName, int expectedVal,
            TimeUnits expectedUnits) {
        for (DMRAvailSet s : agentConfig.getDmrAvailSets()) {
            if (s.getName().equals(setName)) {
                for (DMRAvail a : s.getDmrAvails()) {
                    if (a.getName().equals(availName)) {
                        if (a.getInterval().intValue() == expectedVal) {
                            return;
                        } else {
                            Assert.fail(String.format("Avail type [%s~%s] expected to be [%d %s] but was [%d %s]",
                                    setName, availName,
                                    expectedVal, expectedUnits.name(),
                                    a.getInterval().intValue(), a.getTimeUnits().name()));
                        }
                    }
                }
            }
        }
        Assert.fail(String.format("Agent missing avail type [%s~%s]", setName, availName));
    }
}
