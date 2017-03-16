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
import java.util.Map;

import org.hawkular.agent.javaagent.config.Configuration;
import org.hawkular.agent.javaagent.config.DMRMetric;
import org.hawkular.agent.javaagent.config.DMRMetricSet;
import org.hawkular.agent.javaagent.config.TimeUnits;
import org.hawkular.agent.monitor.util.Util;
import org.hawkular.cmdgw.ws.test.TestWebSocketClient;
import org.hawkular.inventory.api.model.Blueprint;
import org.hawkular.inventory.api.model.DataEntity;
import org.hawkular.inventory.api.model.Entity;
import org.hawkular.inventory.api.model.InventoryStructure;
import org.hawkular.inventory.api.model.OperationType;
import org.hawkular.inventory.api.model.ResourceType;
import org.hawkular.inventory.api.model.StructuredData;
import org.hawkular.inventory.paths.CanonicalPath;
import org.hawkular.inventory.paths.SegmentType;
import org.jboss.as.controller.PathAddress;
import org.testng.Assert;
import org.testng.annotations.Test;

import okhttp3.Request;
import okhttp3.Response;

public class StandaloneWildFlyITest extends AbstractCommandITest {
    public static final String GROUP = "StandloneWildFlyITest";

    @Test(groups = { GROUP })
    public void configureAgent() throws Throwable {
        // Test metric collection disable by immediately disabling a datasource metric, we'll check later
        // to ensure no data points were reported for the metric.
        waitForHawkularServerToBeReady();

        waitForAgentViaJMX();

        CanonicalPath agentPath = getBlueprintsByType(hawkularFeedId, "Hawkular WildFly Agent")
                .keySet().iterator().next();

        // disable Datasource Pool Metrics~Active Count
        String req = "UpdateCollectionIntervalsRequest={\"authentication\":" + authentication + ", "
                + "\"resourcePath\":\"" + agentPath.toString() + "\","
                + "\"metricTypes\":{\"Datasource Pool Metrics~Active Count\":\"0\",\"Unknown~Metric\":\"666\"},"
                + "\"availTypes\":{}"
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
        Configuration agentConfig = getAgentConfigurationFromFile();
        assertMetricInterval(agentConfig, "Datasource Pool Metrics", "Active Count", 0, TimeUnits.seconds);
    }

    @Test(groups = { GROUP }, dependsOnMethods = { "configureAgent" })
    public void operationParameters() throws Throwable {

        // get the operation
        CanonicalPath shutdownPath = feedPath(hawkularFeedId)
                .resourceType("WildFly Server").operationType("Shutdown").get();
        OperationType.Blueprint op = (OperationType.Blueprint) getBlueprintFromCP(shutdownPath).get();
        Assert.assertEquals("Shutdown", op.getId());
        System.out.println("StandaloneWildFlyITest.operationParameters() ===> " + op);

        // get parameters
        CanonicalPath configPath = shutdownPath.extend(SegmentType.d, "parameterTypes").get();
        DataEntity.Blueprint data = (DataEntity.Blueprint) getBlueprintFromCP(configPath).get();
        Assert.assertNotNull(data);
        Map<String, StructuredData> paramsMap = data.getValue().map();
        Map<String, StructuredData> timeoutParam = paramsMap.get("timeout").map();
        Assert.assertEquals("int", timeoutParam.get("type").string());
        Assert.assertEquals("0", timeoutParam.get("defaultValue").string());
        Assert.assertNotNull(timeoutParam.get("description").string());

        Map<String, StructuredData> restartParam = paramsMap.get("restart").map();
        Assert.assertEquals("bool", restartParam.get("type").string());
        Assert.assertEquals("false", restartParam.get("defaultValue").string());
        Assert.assertNotNull(restartParam.get("description").string());
    }

    @Test(groups = { GROUP }, dependsOnMethods = { "configureAgent" })
    public void socketBindingGroupsInInventory() throws Throwable {

        Collection<Blueprint> blueprints = getBlueprintsByType(hawkularFeedId, "Socket Binding Group").values();
        Collection<String> dmrSBGNames = getSocketBindingGroupNames();
        for (String sbgName : dmrSBGNames) {
            boolean hasMatch = blueprints.stream().anyMatch(bp -> bp instanceof Entity.Blueprint
                    && ((Entity.Blueprint)bp).getId().contains(sbgName));
            Assert.assertTrue(hasMatch);
            System.out.println("StandaloneWildFlyITest.socketBindingGroupsInInventory() ===> group: " + sbgName);
        }

        // make sure we are testing against what we were expecting
        Assert.assertTrue(dmrSBGNames.contains("standard-sockets"));
        Assert.assertEquals(dmrSBGNames.size(), 1, "Wrong number of socket binding groups");

        // there is only one group - get the names of all the bindings (incoming and outbound) in that group
        blueprints = getBlueprintsByType(hawkularFeedId, "Socket Binding").values();
        Collection<String> dmrBindingNames = getSocketBindingNames();
        for (String bindingName : dmrBindingNames) {
            boolean hasMatch = blueprints.stream().anyMatch(bp -> bp instanceof Entity.Blueprint
                    && ((Entity.Blueprint)bp).getId().contains(bindingName));
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

        blueprints = getBlueprintsByType(hawkularFeedId, "Remote Destination Outbound Socket Binding").values();
        dmrBindingNames = getOutboundSocketBindingNames();
        for (String bindingName : dmrBindingNames) {
            boolean hasMatch = blueprints.stream().anyMatch(bp -> bp instanceof Entity.Blueprint
                    && ((Entity.Blueprint)bp).getId().contains(bindingName));
            Assert.assertTrue(hasMatch);
            System.out.println("StandaloneWildFlyITest.socketBindingGroupsInInventory() ===> out-binding: " + bindingName);
        }

        // make sure we are testing against what we were expecting
        Assert.assertTrue(dmrBindingNames.contains("mail-smtp"));
        Assert.assertEquals(dmrBindingNames.size(), 1, "Wrong number of outbound socket binding groups");
    }

    @Test(groups = { GROUP }, dependsOnMethods = { "configureAgent" })
    public void datasourcesAddedToInventory() throws Throwable {
        Collection<Blueprint> blueprints = getBlueprintsByType(hawkularFeedId, "Datasource").values();
        for (String datasourceName : getDatasourceNames()) {
            boolean hasMatch = blueprints.stream().anyMatch(bp -> bp instanceof Entity.Blueprint
                    && ((Entity.Blueprint)bp).getId().contains(datasourceName));
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
    public void datasourceMetricsCollected() throws Throwable {
        long startTime = System.currentTimeMillis();
        String lastUrl = "";
        int second = 1000;
        int timeOutSeconds = 90; // enabled metrics should have been collected at least once in this time
        for (int i = 0; i < timeOutSeconds; i++) {
            Request request = newAuthRequest().url(baseMetricsUri + "/gauges").build();
            Response gaugesResponse = client.newCall(request).execute();

            if (gaugesResponse.code() == 200 && !gaugesResponse.body().string().isEmpty()) {
                boolean found = false;

                for (String datasourceName : getDatasourceNames()) {
                    // enabled
                    String id = "MI~R~[" + hawkularFeedId + "/Local DMR~/subsystem=datasources/data-source="
                            + datasourceName
                            + "]~MT~Datasource Pool Metrics~Available Count";
                    id = Util.urlEncodeQuery(id);
                    String url = baseMetricsUri + "/gauges/stats?start=" + startTime + "&buckets=1&metrics=" + id;
                    lastUrl = url;
                    Response gaugeResponse = client.newCall(newAuthRequest().url(url).get().build()).execute();
                    if (gaugeResponse.code() == 200) {
                        String body = gaugeResponse.body().string();
                        // this should be enough to prove that some metric was written successfully
                        if (body.contains("\"empty\":false")) {
                            found = true;
                        }
                    }

                    // disabled
                    id = "MI~R~[" + hawkularFeedId + "/Local DMR~/subsystem=datasources/data-source="
                            + datasourceName
                            + "]~MT~Datasource Pool Metrics~Active Count";
                    id = Util.urlEncodeQuery(id);
                    url = baseMetricsUri + "/gauges/stats?start=" + startTime + "&buckets=1&metrics=" + id;
                    //System.out.println("url = " + url);
                    gaugeResponse = client.newCall(newAuthRequest().url(url).get().build()).execute();
                    if (gaugeResponse.code() == 200) {
                        String body = gaugeResponse.body().string();
                        // this should be enough to prove that the metric was not disabled
                        if (body.contains("\"empty\":false")) {
                            String msg = String.format("Disabled Gauge gathered after [%d]ms. url=[%s], data=%s",
                                    (System.currentTimeMillis() - startTime), url, body);
                            Assert.fail(msg);
                        }
                    }

                    // if enabled metric collected and disabled metric not collected then we should be good.
                    if (found) {
                        return;
                    }
                }
            }
            Thread.sleep(second);
        }

        Assert.fail("Gauge still not gathered after [" + timeOutSeconds + "] seconds: [" + lastUrl + "]");
    }

    @Test(dependsOnMethods = { "datasourcesAddedToInventory" }, enabled = true)
    public void serverAvailCollected() throws Throwable {
        String lastUrl = "";
        int second = 1000;
        int timeOutSeconds = 90; // avail should be been collected at least once within this time

        for (int i = 0; i < timeOutSeconds; i++) {
            Request request = newAuthRequest().url(baseMetricsUri + "/availability").build();
            Response availabilityResponse = client.newCall(request).execute();

            if (availabilityResponse.code() == 200 && !availabilityResponse.body().string().isEmpty()) {
                String id = "AI~R~[" + hawkularFeedId
                        + "/Local DMR~~]~AT~Server Availability~Server Availability";
                id = Util.urlEncode(id);
                String url = baseMetricsUri + "/availability/" + id + "/raw";
                availabilityResponse = client.newCall(newAuthRequest().url(url).get().build()).execute();
                if (availabilityResponse.code() == 200) {
                    String body = availabilityResponse.body().string();
                    // this should be enough to prove that some metric was written successfully
                    if (body.contains("\"value\":\"up\"")) {
                        return;
                    }
                } else {
                    System.out.println("code = " + availabilityResponse.code());
                }
            }
            Thread.sleep(second);
        }

        Assert.fail("Availability still not gathered after [" + timeOutSeconds + "] seconds: [" + lastUrl + "]");

    }

    @Test(groups = { GROUP }, dependsOnMethods = { "datasourcesAddedToInventory" })
    public void resourceConfig() throws Throwable {
        CanonicalPath wfPath = getWildFlyServerResourcePath().extend(SegmentType.d, "configuration").get();
        DataEntity.Blueprint data = (DataEntity.Blueprint) getBlueprintFromCP(wfPath).get();
        Map<String, StructuredData> resConfig = data.getValue().map();
        Assert.assertEquals("NORMAL", resConfig.get("Running Mode").string());
        Assert.assertEquals("RUNNING", resConfig.get("Suspend State").string());
        Assert.assertTrue(resConfig.containsKey("Name"));
        Assert.assertTrue(resConfig.containsKey("UUID"));
        Assert.assertTrue(resConfig.containsKey("Hostname"));
        Assert.assertTrue(resConfig.containsKey("Product Name"));
        Assert.assertTrue(resConfig.containsKey("Server State"));
        Assert.assertTrue(resConfig.containsKey("Node Name"));
        Assert.assertTrue(resConfig.containsKey("Version"));
        Assert.assertTrue(resConfig.containsKey("Home Directory"));
        Assert.assertTrue(resConfig.containsKey("Bound Address"));
    }

    private CanonicalPath getWildFlyServerResourcePath() throws Throwable {
        Map<CanonicalPath, Blueprint> servers = getBlueprintsByType(hawkularFeedId, "WildFly Server");
        Assert.assertEquals(1, servers.size());
        return servers.keySet().iterator().next();
    }

    @Test(groups = { GROUP }, dependsOnMethods = { "datasourcesAddedToInventory" })
    public void machineId() throws Throwable {
        CanonicalPath osTypePath = getOperatingSystemResourceTypePath().extend(SegmentType.d, "configurationSchema").get();
        DataEntity.Blueprint data = (DataEntity.Blueprint) getBlueprintFromCP(osTypePath).get();
        Map<String, StructuredData> schema = data.getValue().map();
        Assert.assertTrue(schema.containsKey("Machine Id"));

        CanonicalPath osPath = getOperatingSystemResourcePath().extend(SegmentType.d, "configuration").get();
        data = (DataEntity.Blueprint) getBlueprintFromCP(osPath).get();
        Map<String, StructuredData> resConfig = data.getValue().map();
        Assert.assertTrue(resConfig.containsKey("Machine Id"));
    }

    private CanonicalPath getOperatingSystemResourceTypePath() throws Throwable {
        InventoryStructure.Offline<ResourceType.Blueprint>
                osType = getResourceType(hawkularFeedId, "Platform_Operating System");
        Assert.assertNotNull(osType);
        return feedPath(hawkularFeedId).resourceType(osType.getRoot().getId()).get();
    }

    private CanonicalPath getOperatingSystemResourcePath() throws Throwable {
        Map<CanonicalPath, Blueprint> os = getBlueprintsByType(hawkularFeedId, "Platform_Operating System");
        Assert.assertEquals(1, os.size());
        return os.keySet().iterator().next();
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
