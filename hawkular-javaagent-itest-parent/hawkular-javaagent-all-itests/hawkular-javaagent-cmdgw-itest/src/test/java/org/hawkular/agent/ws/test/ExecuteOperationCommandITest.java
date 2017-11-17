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

import org.testng.Assert;
import org.testng.annotations.Test;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;

public class ExecuteOperationCommandITest extends AbstractCommandITest {
    public static final String GROUP = "ExecuteOperationCommandITest";

    @Test(groups = { GROUP }, dependsOnGroups = { StandaloneWildFlyITest.GROUP })
    public void testExecuteAgentDiscoveryScan() throws Throwable {

        // this uses an execute operation request
        forceInventoryDiscoveryScan();

        // this is my backdoor way of testing the inventoryReport operation.
        // This makes sure the agent itself is in inventory.
        JsonNode inventoryReport = getJMXAgentInventoryReport();
        Assert.assertNotNull(inventoryReport);
        JsonNode jsonResources = inventoryReport
                .get("JMX") // the name of protocol service
                .get("Local JMX") // the name of the managed-server - this is the local-dmr
                .get("Resources");
        ArrayNode localJmxResources = ((ArrayNode) jsonResources);
        JsonNode agentNode = null;
        for (JsonNode jmxResource : localJmxResources) {
            String agentId = hawkularFeedId + "~Local JMX~" + AGENT_MBEAN_OBJECT_NAME.toString();
            if (jmxResource.has(agentId)) {
                agentNode = jmxResource.get(agentId);
                break;
            }
        }
        Assert.assertNotNull(agentNode);
        Assert.assertTrue(agentNode.get("Name").asText().startsWith("Hawkular Java Agent"));
        Assert.assertTrue(agentNode.get("Type ID").asText().startsWith("Hawkular Java Agent"));
    }
}
