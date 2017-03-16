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

import org.hamcrest.CoreMatchers;
import org.hawkular.agent.javaagent.config.ConfigManager;
import org.hawkular.agent.javaagent.config.Configuration;
import org.hawkular.agent.javaagent.config.DMRMetric;
import org.hawkular.agent.javaagent.config.DMRMetricSet;
import org.hawkular.agent.javaagent.config.TimeUnits;
import org.hawkular.cmdgw.ws.test.TestWebSocketClient;
import org.hawkular.cmdgw.ws.test.TestWebSocketClient.ExpectedEvent;
import org.hawkular.cmdgw.ws.test.TestWebSocketClient.ExpectedEvent.ExpectedMessage;
import org.hawkular.cmdgw.ws.test.TestWebSocketClient.PatternMatcher;
import org.hawkular.inventory.paths.CanonicalPath;
import org.testng.Assert;
import org.testng.annotations.Test;

import okhttp3.ws.WebSocket;

/**
 * @author <a href="https://github.com/jshaughn">Jay Shaughnessy</a>
 */
public class ImmutableITest extends AbstractCommandITest {
    public static final String GROUP = "ImmutableITest";

    @Test(groups = { GROUP }, dependsOnGroups = { JdbcDriverCommandITest.GROUP, })
    public void testImmutableUpdate() throws Throwable {
        waitForHawkularServerToBeReady();

        waitForAgentViaJMX();

        CanonicalPath agentPath = getBlueprintsByType(hawkularFeedId, "Hawkular WildFly Agent")
                .entrySet().stream()
                .map(Map.Entry::getKey)
                .findFirst()
                .get();

        // check we are starting with our original defaults - this is just a sanity check
        Configuration config = getAgentConfigurationFromFile();
        Assert.assertEquals(config.getSubsystem().getImmutable().booleanValue(), false);
        assertMetricInterval(config, "WildFly Threading Metrics", "Thread Count", 2, TimeUnits.minutes);

        // make the agent immutable by flipping the flag and restarting it
        config.getSubsystem().setImmutable(true);
        new ConfigManager(agentConfigFile).updateConfiguration(config, true);
        restartJMXAgent();

        String req = "UpdateCollectionIntervalsRequest={\"authentication\":" + authentication + ", "
                + "\"resourcePath\":\"" + agentPath.toString() + "\","
                + "\"metricTypes\":{\"WildFly Threading Metrics~Thread Count\":\"159\"},"
                + "\"availTypes\":{}"
                + "}";

        String response = ".*\"status\":\"ERROR\""
                + ".*\"message\":\"Could not perform.*Command not allowed because the agent is immutable.*";

        ExpectedEvent expectedEvent = new ExpectedMessage(new PatternMatcher(response),
                CoreMatchers.equalTo(WebSocket.TEXT), TestWebSocketClient.Answer.CLOSE);

        try (TestWebSocketClient testClient = TestWebSocketClient.builder()
                .url(baseGwUri + "/ui/ws")
                .expectWelcome(req)
                .expectGenericSuccess(agentPath.ids().getFeedId())
                .expectMessage(expectedEvent)
                .expectClose()
                .build()) {
            testClient.validate(10000);
        }

        // value should not have changed
        config = getAgentConfigurationFromFile();
        assertMetricInterval(config, "WildFly Threading Metrics", "Thread Count", 2, TimeUnits.minutes);

        // make the agent mutable again so future tests can change things if need be
        config.getSubsystem().setImmutable(false);
        new ConfigManager(agentConfigFile).updateConfiguration(config, true);
        restartJMXAgent();
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

}
