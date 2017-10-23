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

import java.util.Collection;
import java.util.Optional;

import org.hawkular.agent.javaagent.config.Configuration;
import org.hawkular.agent.javaagent.config.DMRMetric;
import org.hawkular.agent.javaagent.config.DMRMetricSet;
import org.hawkular.agent.javaagent.config.TimeUnits;
import org.hawkular.inventory.api.model.Operation;
import org.hawkular.inventory.api.model.Resource;
import org.jboss.as.controller.PathAddress;
import org.testng.Assert;
import org.testng.annotations.Test;

public class StandaloneWildFlyITest extends AbstractCommandITest {
    public static final String GROUP = "StandloneWildFlyITest";

    @Test(groups = { GROUP })
    public void testAgent() throws Throwable {
        waitForHawkularServerToBeReady();
        Assert.assertTrue(waitForAgentViaJMX(), "Expected agent to be started.");
        Resource agentResource = testHelper.waitForResourceContaining(
                hawkularFeedId, "Hawkular Java Agent", null, 5000, 10);
        Assert.assertNotNull(agentResource);
    }

    @Test(groups = { GROUP }, dependsOnMethods = { "testAgent" })
    public void operationParameters() throws Throwable {

        // get the operation
        Resource wfResource = getHawkularWildFlyServerResource();
        Optional<Operation> operation = wfResource.getType()
                .getOperations()
                .stream()
                .filter(o -> o.getName().equals("Shutdown"))
                .findFirst();
        Assert.assertTrue(operation.isPresent());
        Operation op = operation.get();

        System.out.println("StandaloneWildFlyITest.operationParameters() ===> " + op);

        // get parameters
        Assert.assertNotNull(op.getParameters().get("timeout"));
        Assert.assertEquals("int", op.getParameters().get("timeout").get("type"));
        Assert.assertEquals("0", op.getParameters().get("timeout").get("defaultValue"));
        Assert.assertNotNull(op.getParameters().get("timeout").get("description"));

        Assert.assertNotNull(op.getParameters().get("restart"));
        Assert.assertEquals("bool", op.getParameters().get("restart").get("type"));
        Assert.assertEquals("false", op.getParameters().get("restart").get("defaultValue"));
        Assert.assertNotNull(op.getParameters().get("restart").get("description"));
    }

    @Test(groups = { GROUP }, dependsOnMethods = { "testAgent" })
    public void socketBindingGroupsInInventory() throws Throwable {

        Collection<Resource> bindingGroups = testHelper.getResourceByType(hawkularFeedId, "Socket Binding Group", 0);

        Collection<String> dmrSBGNames = getSocketBindingGroupNames();
        for (String sbgName : dmrSBGNames) {
            boolean hasMatch = bindingGroups.stream()
                    .anyMatch(bg -> bg.getName().contains(sbgName));
            Assert.assertTrue(hasMatch);
            System.out.println("StandaloneWildFlyITest.socketBindingGroupsInInventory() ===> group: " + sbgName);

        }

        // make sure we are testing against what we were expecting
        Assert.assertTrue(dmrSBGNames.contains("standard-sockets"));
        Assert.assertEquals(dmrSBGNames.size(), 1, "Wrong number of socket binding groups");

        // there is only one group - get the names of all the bindings (incoming and outbound) in that group
        Collection<Resource> socketBindings = testHelper.getResourceByType(hawkularFeedId, "Socket Binding", 0);
        Collection<String> dmrBindingNames = getSocketBindingNames();
        for (String bindingName : dmrBindingNames) {
            boolean hasMatch = socketBindings.stream()
                    .anyMatch(sb -> sb.getName().contains(bindingName));
            Assert.assertTrue(hasMatch);
            System.out.println("StandaloneWildFlyITest.socketBindingGroupsInInventory() ===> binding: " + bindingName);
        }

        // make sure we are testing against what we were expecting
        Assert.assertTrue(dmrBindingNames.contains("management-http"));
        Assert.assertTrue(dmrBindingNames.contains("management-https"));
        Assert.assertTrue(dmrBindingNames.contains("ajp"));
        Assert.assertTrue(dmrBindingNames.contains("http"));
        Assert.assertTrue(dmrBindingNames.contains("https"));
        Assert.assertTrue(dmrBindingNames.contains("txn-recovery-environment"));
        Assert.assertTrue(dmrBindingNames.contains("txn-status-manager"));
        Assert.assertEquals(dmrBindingNames.size(), 7, "Wrong number of socket binding groups");

        Collection<Resource> oSocketBindings = testHelper.getResourceByType(hawkularFeedId, "Remote Destination Outbound Socket Binding", 1);
        dmrBindingNames = getOutboundSocketBindingNames();
        for (String bindingName : dmrBindingNames) {
            boolean hasMatch = oSocketBindings.stream()
                    .anyMatch(osb -> osb.getName().contains(bindingName));
            Assert.assertTrue(hasMatch);
            System.out.println(
                    "StandaloneWildFlyITest.socketBindingGroupsInInventory() ===> out-binding: " + bindingName);
        }

        // make sure we are testing against what we were expecting
        Assert.assertTrue(dmrBindingNames.contains("mail-smtp"));
        Assert.assertEquals(dmrBindingNames.size(), 1, "Wrong number of outbound socket binding groups");
    }

    @Test(groups = { GROUP }, dependsOnMethods = { "testAgent" })
    public void datasourcesAddedToInventory() throws Throwable {
        Collection<String> datasourceNames = getDatasourceNames();
        Collection<Resource> datasources = testHelper.getResourceByType(hawkularFeedId, "Datasource", datasourceNames.size());
        for (String datasourceName : datasourceNames) {
            boolean hasMatch = datasources.stream().anyMatch(ds -> ds.getId().contains(datasourceName));
            Assert.assertTrue(hasMatch);
            System.out.println("StandaloneWildFlyITest.datasourcesAddedToInventory() ===> " + datasourceName);
        }
    }

    private Collection<String> getSocketBindingGroupNames() {
        return getDMRChildrenNames("socket-binding-group", PathAddress.EMPTY_ADDRESS);
    }

    private Collection<String> getSocketBindingNames() {
        return getDMRChildrenNames("socket-binding",
                PathAddress.parseCLIStyleAddress("/socket-binding-group=standard-sockets"));
    }

    private Collection<String> getOutboundSocketBindingNames() {
        return getDMRChildrenNames("remote-destination-outbound-socket-binding",
                PathAddress.parseCLIStyleAddress("/socket-binding-group=standard-sockets"));
    }

    private Collection<String> getDatasourceNames() {
        return getDMRChildrenNames("data-source", PathAddress.parseCLIStyleAddress("/subsystem=datasources"));
    }

    @Test(groups = { GROUP }, dependsOnMethods = { "datasourcesAddedToInventory" })
    public void resourceConfig() throws Throwable {
        Collection<Resource> servers = testHelper.getResourceByType(hawkularFeedId, "WildFly Server", 1);
        Assert.assertEquals(1, servers.size());
        Resource server = servers.iterator().next();

        Assert.assertEquals("NORMAL", server.getConfig().get("Running Mode"));
        Assert.assertEquals("RUNNING", server.getConfig().get("Suspend State"));
        Assert.assertTrue(server.getConfig().containsKey("Name"));
        Assert.assertTrue(server.getConfig().containsKey("UUID"));
        Assert.assertTrue(server.getConfig().containsKey("Hostname"));
        Assert.assertTrue(server.getConfig().containsKey("Product Name"));
        Assert.assertTrue(server.getConfig().containsKey("Server State"));
        Assert.assertTrue(server.getConfig().containsKey("Node Name"));
        Assert.assertTrue(server.getConfig().containsKey("Version"));
        Assert.assertTrue(server.getConfig().containsKey("Home Directory"));
        Assert.assertTrue(server.getConfig().containsKey("Bound Address"));
    }

    @Test(groups = { GROUP }, dependsOnMethods = { "datasourcesAddedToInventory" })
    public void machineId() throws Throwable {
        Collection<Resource> platforms = testHelper.getResourceByType(hawkularFeedId, "Platform_Operating System", 1);
        Assert.assertEquals(1, platforms.size());
        Resource platform = platforms.iterator().next();
        Assert.assertTrue(platform.getConfig().containsKey("Machine Id"));
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
                            Assert.fail(String.format("Metric type [%s~%s] expected to be [%d] but was [%d]",
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
