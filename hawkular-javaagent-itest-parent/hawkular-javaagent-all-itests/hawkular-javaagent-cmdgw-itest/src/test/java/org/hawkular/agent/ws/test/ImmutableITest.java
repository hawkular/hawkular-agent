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

import java.util.Optional;

import org.hawkular.agent.javaagent.config.ConfigManager;
import org.hawkular.agent.javaagent.config.Configuration;
import org.hawkular.cmdgw.ws.test.TestWebSocketClient;
import org.hawkular.inventory.api.model.Resource;
import org.jboss.as.controller.client.ModelControllerClient;
import org.testng.Assert;
import org.testng.annotations.Test;

/**
 * @author <a href="https://github.com/jshaughn">Jay Shaughnessy</a>
 */
public class ImmutableITest extends AbstractCommandITest {
    public static final String GROUP = "ImmutableITest";

    @Test(groups = { GROUP }, dependsOnGroups = { JdbcDriverCommandITest.GROUP, })
    public void testImmutableUpdate() throws Throwable {
        waitForHawkularServerToBeReady();

        waitForAgentViaJMX();

        Optional<Resource> agent = testHelper.getResourceByType(hawkularFeedId, "Hawkular Java Agent", 1)
                .stream()
                .findFirst();
        if (!agent.isPresent()) {
            throw new IllegalStateException("Agent is not present");
        }

        // check we are starting with our original defaults - this is just a sanity check
        Configuration config = getAgentConfigurationFromFile();
        Assert.assertEquals(config.getSubsystem().getImmutable().booleanValue(), false);

        // make the agent immutable by flipping the flag and restarting it
        config.getSubsystem().setImmutable(true);
        new ConfigManager(agentConfigFile).updateConfiguration(config, true);
        restartJMXAgent();

        changeSomething();

        // make the agent mutable again so future tests can change things if need be
        config.getSubsystem().setImmutable(false);
        new ConfigManager(agentConfigFile).updateConfiguration(config, true);
        restartJMXAgent();
    }

    public void changeSomething() throws Throwable {
        // attempt to flip some statistic enabled flags - if we are immutable, this should not be allowed
        Resource wfResource = getHawkularWildFlyServerResource();

        try (ModelControllerClient mcc = newHawkularModelControllerClient()) {

            String req = "StatisticsControlRequest={\"authentication\":" + authentication + ", "
                    + "\"feedId\":\"" + wfResource.getFeedId() + "\","
                    + "\"resourceId\":\"" + wfResource.getId() + "\","
                    + "\"web\":\"ENABLED\","
                    + "\"transactions\":\"ENABLED\","
                    + "\"datasources\":\"ENABLED\","
                    + "\"infinispan\":\"ENABLED\","
                    + "\"ejb3\":\"ENABLED\","
                    + "\"messaging\":\"ENABLED\""
                    + "}";

            String response = "StatisticsControlResponse={"
                    + "\"feedId\":\"" + wfResource.getFeedId() + "\","
                    + "\"resourceId\":\"" + wfResource.getId() + "\","
                    + "\"destinationSessionId\":\"{{sessionId}}\","
                    + "\"status\":\"ERROR\","
                    + "\"message\":\"\\E.*\\QCommand not allowed because the agent is immutable\""
                    + "}";

            try (TestWebSocketClient testClient = TestWebSocketClient.builder()
                    .url(baseGwUri + "/ui/ws")
                    .expectWelcome(req)
                    .expectGenericSuccess(wfResource.getFeedId())
                    .expectText(response, TestWebSocketClient.Answer.CLOSE)
                    .expectClose()
                    .build()) {
                testClient.validate(10000);
            }
        }
    }

}
