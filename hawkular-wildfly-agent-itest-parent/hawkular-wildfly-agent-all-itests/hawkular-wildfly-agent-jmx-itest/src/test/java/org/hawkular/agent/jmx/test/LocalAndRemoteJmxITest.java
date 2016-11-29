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
package org.hawkular.agent.jmx.test;

import java.util.List;

import org.hawkular.dmrclient.Address;
import org.hawkular.dmrclient.CoreJBossASClient;
import org.hawkular.dmrclient.FailureException;
import org.hawkular.dmrclient.JBossASClient;
import org.hawkular.inventory.api.model.Resource;
import org.hawkular.wildfly.agent.itest.util.AbstractITest;
import org.jboss.as.controller.client.ModelControllerClient;
import org.jboss.dmr.ModelNode;
import org.jboss.dmr.Property;
import org.testng.Assert;
import org.testng.annotations.Test;

public class LocalAndRemoteJmxITest extends AbstractITest {
    public static final String GROUP = "ExecuteOperationCommandITest";

    @Test(groups = { GROUP })
    public void testDmrResources() throws Throwable {
        waitForAccountsAndInventory();

        // make sure the agent is there - this comes from the DMR managed server - just making sure that still works
        Resource agent = getResource(
                "/traversal/f;" + hawkularFeedId + "/type=rt;id=Hawkular%20WildFly%20Agent/rl;defines/type=r",
                (r -> r.getId() != null));
        Assert.assertNotNull(agent);
        Assert.assertEquals(agent.getName(), "Hawkular WildFly Agent");

        enableJmxManagedServers();
    }

    private void enableJmxManagedServers() throws Throwable {
        try (ModelControllerClient mcc = newHawkularModelControllerClient()) {
            CoreJBossASClient c = new CoreJBossASClient(mcc);

            // We want to enable by the remote JMX managed server and the local JMX managed server.
            // The default agent configuration already has these managed servers defined with some basic
            // metadata - we just want to enabled them since they are disabled by default.
            String rAddr = "/subsystem=hawkular-wildfly-agent/managed-servers=default/remote-jmx=Remote JMX";
            String lAddr = "/subsystem=hawkular-wildfly-agent/managed-servers=default/local-jmx=Local JMX";
            ModelNode rReq = JBossASClient.createWriteAttributeRequest("enabled", "true", Address.parse(rAddr));
            ModelNode lReq = JBossASClient.createWriteAttributeRequest("enabled", "true", Address.parse(lAddr));

            ModelNode response;

            response = c.execute(rReq);
            if (!JBossASClient.isSuccess(response)) {
                throw new FailureException("Cannot enable remote JMX managed server: " + response);
            }

            response = c.execute(lReq);
            if (!JBossASClient.isSuccess(response)) {
                throw new FailureException("Cannot enable local JMX managed server: " + response);
            }
        }
    }

    @Test(groups = { GROUP }, dependsOnMethods = { "testDmrResources" })
    public void testLocalJmxResources() throws Throwable {
        // make sure the JMX resource is there
        Resource runtime = getResource(
                "/traversal/f;" + hawkularFeedId + "/type=rt;id=Runtime%20MBean/rl;defines/type=r",
                (r -> r.getId().equals("Local JMX~java.lang:type=Runtime")));
        Assert.assertNotNull(runtime);
        Assert.assertEquals(runtime.getName(), "JMX [Local JMX][Runtime]");

        // makes sure the resources are in the agent's internal inventory
        ModelNode inventoryReport = getAgentInventoryReport(hawkularHost, hawkularManagementPort);
        Assert.assertNotNull(inventoryReport);
        ModelNode jmxResourceNode = inventoryReport
                .get("JMX") // the name of protocol service
                .get("Local JMX") // the name of the managed-server - this is the local-jmx
                .get("Resources")
                .asList()
                .get(0)
                .get("Local JMX~java.lang:type=Runtime"); // the resource ID
        Assert.assertNotNull(jmxResourceNode);
        Assert.assertEquals(jmxResourceNode.get("Name").asString(), "JMX [Local JMX][Runtime]");
        Assert.assertEquals(jmxResourceNode.get("Type ID").asString(), "Runtime MBean");
        List<Property> resConfig = jmxResourceNode.get("Resource Configuration").asPropertyList();
        Assert.assertEquals(resConfig.size(), 2);
        for (Property prop : resConfig) {
            if (prop.getName().equals("OS Name")) {
                Assert.assertFalse(prop.getValue().asString().isEmpty());
            } else if (prop.getName().equals("Java VM Name")) {
                Assert.assertFalse(prop.getValue().asString().isEmpty());
            } else {
                Assert.fail("Bad resource config: " + prop.getName() + "=" + prop.getValue().asString());
            }
        }
    }

    @Test(groups = { GROUP }, dependsOnMethods = { "testLocalJmxResources" })
    public void testRemoteJmxResources() throws Throwable {
        // make sure the JMX resource is there
        Resource runtime = getResource(
                "/traversal/f;" + hawkularFeedId + "/type=rt;id=Runtime%20MBean/rl;defines/type=r",
                (r -> r.getId().equals("Remote JMX~java.lang:type=Runtime")));
        Assert.assertNotNull(runtime);
        Assert.assertEquals(runtime.getName(), "JMX [Remote JMX][Runtime]");

        // makes sure the resources are in the agent's internal inventory
        ModelNode inventoryReport = getAgentInventoryReport(hawkularHost, hawkularManagementPort);
        Assert.assertNotNull(inventoryReport);
        ModelNode jmxResourceNode = inventoryReport
                .get("JMX") // the name of protocol service
                .get("Remote JMX") // the name of the managed-server - this is the remote-jmx
                .get("Resources")
                .asList()
                .get(0)
                .get("Remote JMX~java.lang:type=Runtime"); // the resource ID
        Assert.assertNotNull(jmxResourceNode);
        Assert.assertEquals(jmxResourceNode.get("Name").asString(), "JMX [Remote JMX][Runtime]");
        Assert.assertEquals(jmxResourceNode.get("Type ID").asString(), "Runtime MBean");
        List<Property> resConfig = jmxResourceNode.get("Resource Configuration").asPropertyList();
        Assert.assertEquals(resConfig.size(), 2);
        for (Property prop : resConfig) {
            if (prop.getName().equals("OS Name")) {
                Assert.assertFalse(prop.getValue().asString().isEmpty());
            } else if (prop.getName().equals("Java VM Name")) {
                Assert.assertFalse(prop.getValue().asString().isEmpty());
            } else {
                Assert.fail("Bad resource config: " + prop.getName() + "=" + prop.getValue().asString());
            }
        }
    }
}
