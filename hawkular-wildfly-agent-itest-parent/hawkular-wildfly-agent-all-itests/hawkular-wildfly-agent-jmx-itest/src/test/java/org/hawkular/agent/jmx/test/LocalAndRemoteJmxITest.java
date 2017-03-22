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
package org.hawkular.agent.jmx.test;

import java.util.List;

import org.hawkular.agent.monitor.util.Util;
import org.hawkular.dmrclient.Address;
import org.hawkular.dmrclient.CoreJBossASClient;
import org.hawkular.dmrclient.FailureException;
import org.hawkular.dmrclient.JBossASClient;
import org.hawkular.inventory.api.model.Entity;
import org.hawkular.wildfly.agent.itest.util.AbstractITest;
import org.jboss.as.controller.client.ModelControllerClient;
import org.jboss.dmr.ModelNode;
import org.jboss.dmr.Property;
import org.testng.Assert;
import org.testng.annotations.Test;

import okhttp3.Request;
import okhttp3.Response;

public class LocalAndRemoteJmxITest extends AbstractITest {
    public static final String GROUP = "LocalAndRemoteJmxITest";

    @Test(groups = { GROUP })
    public void testDmrResources() throws Throwable {
        waitForAccountsAndInventory();

        // make sure the agent is there - this comes from the DMR managed server - just making sure that still works
        Entity.Blueprint agent = (Entity.Blueprint) testHelper.getBlueprintsByType(hawkularFeedId, "Hawkular WildFly Agent")
                .values().stream().findFirst().get();
        Assert.assertEquals(agent.getName(), "Hawkular WildFly Agent");

        setMetricTagsOnJmxManagedServers();
        enableJmxManagedServers();
    }

    @Test(groups = { GROUP }, dependsOnMethods = { "testDmrResources" })
    public void testLocalJmxResources() throws Throwable {
        // make sure the JMX resource is there
        Entity.Blueprint runtime = (Entity.Blueprint) testHelper.waitForResourceContaining(
                hawkularFeedId, "Runtime MBean", "Local JMX~java.lang:type=Runtime", 5000, 5)
                .getValue();
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
    // FIXME: lost traversal
    public void testRemoteJmxResources() throws Throwable {
        // make sure the JMX resource is there
        Entity.Blueprint runtime = (Entity.Blueprint) testHelper.waitForResourceContaining(
                hawkularFeedId, "Runtime MBean", "Remote JMX~java.lang:type=Runtime", 5000, 5)
                .getValue();
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

    @Test(groups = { GROUP }, dependsOnMethods = { "testRemoteJmxResources" })
    public void testMetrics() throws Throwable {
        String localId1 = "MI~R~[" + hawkularFeedId
                + "/Local JMX~java.lang:type=Runtime]~MT~RuntimeMetricsJMX~Aggregate GC Collection Time";
        String localId2 = "MI~R~[" + hawkularFeedId
                + "/Local JMX~java.lang:type=Runtime]~MT~RuntimeMetricsJMX~Used Heap Memory";
        String remoteId1 = "MI~R~[" + hawkularFeedId
                + "/Remote JMX~java.lang:type=Runtime]~MT~RuntimeMetricsJMX~Aggregate GC Collection Time";
        String remoteId2 = "MI~R~[" + hawkularFeedId
                + "/Remote JMX~java.lang:type=Runtime]~MT~RuntimeMetricsJMX~Used Heap Memory";

        assertGaugeMetricData(localId1);
        assertGaugeMetricData(localId2);
        assertGaugeMetricData(remoteId1);
        assertGaugeMetricData(remoteId2);
    }

    @Test(groups = { GROUP }, dependsOnMethods = { "testMetrics" })
    public void testMetricTags() throws Throwable {
        String localId1 = "MI~R~[" + hawkularFeedId
                + "/Local JMX~java.lang:type=Runtime]~MT~RuntimeMetricsJMX~Aggregate GC Collection Time";
        String localId2 = "MI~R~[" + hawkularFeedId
                + "/Local JMX~java.lang:type=Runtime]~MT~RuntimeMetricsJMX~Used Heap Memory";
        String remoteId1 = "MI~R~[" + hawkularFeedId
                + "/Remote JMX~java.lang:type=Runtime]~MT~RuntimeMetricsJMX~Aggregate GC Collection Time";
        String remoteId2 = "MI~R~[" + hawkularFeedId
                + "/Remote JMX~java.lang:type=Runtime]~MT~RuntimeMetricsJMX~Used Heap Memory";

        assertGaugeMetricTags(localId1, "{\"Label Local\":\"Value Local\",\"feed\":\"" + hawkularFeedId + "\"}");
        assertGaugeMetricTags(localId2, "{\"Label Local\":\"Value Local\",\"feed\":\"" + hawkularFeedId + "\"}");
        assertGaugeMetricTags(remoteId1, "{\"Label Remote\":\"Value Remote\",\"feed\":\"" + hawkularFeedId + "\"}");
        assertGaugeMetricTags(remoteId2, "{\"Label Remote\":\"Value Remote\",\"feed\":\"" + hawkularFeedId + "\"}");
    }

    private void assertGaugeMetricTags(String id, String expectedTagsJson) throws Throwable {
        String lastUrl = "";
        int second = 1000;
        int timeOutSeconds = 60;
        for (int i = 0; i < timeOutSeconds; i++) {
            Request request = testHelper.newAuthRequest().url(baseMetricsUri + "/gauges").build();
            lastUrl = request.url().toString();
            Response gaugesResponse = testHelper.client().newCall(request).execute();

            if (gaugesResponse.code() == 200 && !gaugesResponse.body().string().isEmpty()) {
                String url = baseMetricsUri + "/gauges/" + Util.urlEncode(id) + "/tags";
                lastUrl = url;
                Response tagsResponse = testHelper.client().newCall(testHelper.newAuthRequest().url(url).get().build()).execute();
                if (tagsResponse.code() == 200) {
                    String tags = tagsResponse.body().string();
                    if (tags.equals(expectedTagsJson)) {
                        return;
                    } else {
                        Assert.fail("Unexpected tags from [" + lastUrl + "]. Expected=[" + expectedTagsJson
                                + "], Actual=[" + tags + "]");
                    }
                }
            }
            Thread.sleep(second);
        }

        Assert.fail("Gauge tags still not found after [" + timeOutSeconds + "] seconds: [" + lastUrl + "]");
    }

    private void assertGaugeMetricData(String id) throws Throwable {
        String lastUrl = "";
        int second = 1000;
        int timeOutSeconds = 60;
        for (int i = 0; i < timeOutSeconds; i++) {
            Request request = testHelper.newAuthRequest().url(baseMetricsUri + "/gauges").build();
            lastUrl = request.url().toString();
            Response gaugesResponse = testHelper.client().newCall(request).execute();

            if (gaugesResponse.code() == 200 && !gaugesResponse.body().string().isEmpty()) {
                String url = baseMetricsUri + "/gauges/stats?buckets=1&metrics=" + Util.urlEncodeQuery(id);
                lastUrl = url;
                Response gaugeResponse = testHelper.client().newCall(testHelper.newAuthRequest().url(url).get().build()).execute();
                if (gaugeResponse.code() == 200 && !gaugeResponse.body().string().isEmpty()) {
                    /* this should be enough to prove that some metric was written successfully */
                    return;
                }
            }
            Thread.sleep(second);
        }

        Assert.fail("Gauge still not gathered after [" + timeOutSeconds + "] seconds: [" + lastUrl + "]");
    }

    private void setMetricTagsOnJmxManagedServers() throws Throwable {
        // note that if you call this after the endpoints are already enabled, you have to disable then re-enable them again
        try (ModelControllerClient mcc = newHawkularModelControllerClient()) {
            CoreJBossASClient c = new CoreJBossASClient(mcc);

            String rAddr = "/subsystem=hawkular-wildfly-agent/managed-servers=default/remote-jmx=Remote JMX";
            String lAddr = "/subsystem=hawkular-wildfly-agent/managed-servers=default/local-jmx=Local JMX";
            ModelNode rReq = JBossASClient.createWriteAttributeRequest("metric-tags",
                    "feed=%FeedId,Label Remote=Value Remote", Address.parse(rAddr));
            ModelNode lReq = JBossASClient.createWriteAttributeRequest("metric-tags",
                    "feed=%FeedId,Label Local=Value Local", Address.parse(lAddr));

            ModelNode response;

            response = c.execute(rReq);
            if (!JBossASClient.isSuccess(response)) {
                throw new FailureException("Cannot set metric tags on remote JMX managed server: " + response);
            }

            response = c.execute(lReq);
            if (!JBossASClient.isSuccess(response)) {
                throw new FailureException("Cannot set metric tags on local JMX managed server: " + response);
            }
        }
    }

    private void enableJmxManagedServers() throws Throwable {
        try (ModelControllerClient mcc = newHawkularModelControllerClient()) {
            CoreJBossASClient c = new CoreJBossASClient(mcc);

            // We want to enable by the remote JMX managed server and the local JMX managed server.
            // The default agent configuration already has these managed servers defined with some basic
            // metadata - we just want to enabled them since they are disabled by default.
            String flagStr = String.valueOf(true);
            String rAddr = "/subsystem=hawkular-wildfly-agent/managed-servers=default/remote-jmx=Remote JMX";
            String lAddr = "/subsystem=hawkular-wildfly-agent/managed-servers=default/local-jmx=Local JMX";
            ModelNode rReq = JBossASClient.createWriteAttributeRequest("enabled", flagStr, Address.parse(rAddr));
            ModelNode lReq = JBossASClient.createWriteAttributeRequest("enabled", flagStr, Address.parse(lAddr));

            ModelNode response;

            response = c.execute(rReq);
            if (!JBossASClient.isSuccess(response)) {
                throw new FailureException(
                        "Cannot set enable flag to [" + flagStr + "] on remote JMX managed server: " + response);
            }

            response = c.execute(lReq);
            if (!JBossASClient.isSuccess(response)) {
                throw new FailureException(
                        "Cannot set enable flag to [" + flagStr + "] on local JMX managed server: " + response);
            }
        }
    }
}
