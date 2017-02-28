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

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.hawkular.agent.monitor.util.Util;
import org.hawkular.inventory.api.model.DataEntity;
import org.hawkular.inventory.api.model.OperationType;
import org.hawkular.inventory.api.model.Resource;
import org.hawkular.inventory.api.model.ResourceType;
import org.hawkular.inventory.api.model.StructuredData;
import org.hawkular.inventory.paths.CanonicalPath;
import org.hawkular.inventory.paths.SegmentType;
import org.hawkular.wildfly.agent.itest.util.AbstractITest;
import org.hawkular.wildfly.agent.itest.util.WildFlyClientConfig;
import org.jboss.as.controller.PathAddress;
import org.jboss.as.controller.client.ModelControllerClient;
import org.jboss.dmr.ModelNode;
import org.junit.Assert;
import org.testng.annotations.Test;

import okhttp3.Request;
import okhttp3.Response;

/**
 * @author <a href="https://github.com/ppalaga">Peter Palaga</a>
 *
 */
public class AgentInstallerStandaloneITest extends AbstractITest {
    public static final String GROUP = "AgentInstallerStandloneITest";

    protected static final WildFlyClientConfig wfClientConfig;

    static {
        wfClientConfig = new WildFlyClientConfig();
    }

    @Test(groups = { GROUP })
    public void configureAgent() throws Throwable {
        // Test metric collection disable by immediately disabling a datasource metric, we'll check later
        // to ensure no data points were reported for the metric.
        ModelNode addressActual = PathAddress
                .parseCLIStyleAddress(
                        "/subsystem=hawkular-wildfly-agent/metric-set-dmr=Datasource Pool Metrics/metric-dmr=Active Count")
                .toModelNode();
        // Note that we want to update the plain wildfly, this is where we installed the agent and collect metrics
        ModelControllerClient mcc = newPlainWildFlyModelControllerClient(getPlainWildFlyClientConfig());
        assertNodeAttributeEquals(mcc, addressActual, "interval", "30");
        // this update should automatically trigger an agent restart, the operation is flagged to re-read the config
        writeNodeAttribute(mcc, addressActual, "interval", "0");
        assertNodeAttributeEquals(mcc, addressActual, "interval", "0");

        // Don't proceed with other tests until we're sure the shutdown has initiated
        Thread.sleep(2000);
    }

    @Test(groups = { GROUP }, dependsOnMethods = { "configureAgent" })
    public void wfStarted() throws Throwable {
        waitForAccountsAndInventory();

        // System.out.println("wfFeedId = " + wfFeedId);
        Assert.assertNotNull("wfFeedId should not be null", wfClientConfig.getFeedId());
    }

    @Test(groups = { GROUP }, dependsOnMethods = { "wfStarted" })
    public void operationParameters() throws Throwable {

        // get the operation
        OperationType op = getOperationType("/traversal/f;" + wfClientConfig.getFeedId() + "/type=rt;" +
                "id=WildFly Server/type=ot;id=Shutdown", ATTEMPT_COUNT, ATTEMPT_DELAY);
        Assert.assertEquals("Shutdown", op.getId());
        System.out.println("operation=" + op);

        // get parameters
        DataEntity data = getDataEntity(
                "/entity/f;" + wfClientConfig.getFeedId() + "/rt;WildFly Server/ot;Shutdown/d;parameterTypes",
                ATTEMPT_COUNT, ATTEMPT_DELAY);
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

    @Test(groups = { GROUP }, dependsOnMethods = { "wfStarted" })
    // FIXME: lost traversal
    public void socketBindingGroupsInInventory() throws Throwable {

        Collection<String> dmrSBGNames = getSocketBindingGroupNames();
        for (String sbgName : dmrSBGNames) {
            Resource sbg = getResource(wfClientConfig.getFeedId(), "rt", "Socket Binding Group",
                    (r -> r.getName().contains(sbgName)));
            System.out.println("socket binding group in inventory=" + sbg);
        }

        // make sure we are testing against what we were expecting
        Assert.assertTrue(dmrSBGNames.contains("standard-sockets"));
        Assert.assertEquals("Wrong number of socket binding groups", 1, dmrSBGNames.size());

        // there is only one group - get the names of all the bindings (incoming and outbound) in that group
        Collection<String> dmrBindingNames = getSocketBindingNames();
        for (String bindingName : dmrBindingNames) {
            Resource binding = getResource(wfClientConfig.getFeedId(), "rt", "Socket Binding",
                    (r -> r.getName().contains(bindingName)));
            System.out.println("socket binding in inventory=" + binding);
        }

        // make sure we are testing against what we were expecting
        Assert.assertTrue(dmrBindingNames.contains("management-http"));
        Assert.assertTrue(dmrBindingNames.contains("management-https"));
        Assert.assertTrue(dmrBindingNames.contains("ajp"));
        Assert.assertTrue(dmrBindingNames.contains("http"));
        Assert.assertTrue(dmrBindingNames.contains("https"));
        Assert.assertTrue(dmrBindingNames.contains("txn-recovery-environment"));
        Assert.assertTrue(dmrBindingNames.contains("txn-status-manager"));
        Assert.assertEquals("Wrong number of socket binding groups", 7, dmrBindingNames.size());

        dmrBindingNames = getOutboundSocketBindingNames();
        for (String bindingName : dmrBindingNames) {
            Resource binding = getResource(wfClientConfig.getFeedId(), "rt", "Remote Destination Outbound Socket Binding",
                    (r -> r.getName().contains(bindingName)));
            System.out.println("outbound socket binding in inventory=" + binding);
        }

        // make sure we are testing against what we were expecting
        Assert.assertTrue(dmrBindingNames.contains("mail-smtp"));
        Assert.assertTrue(dmrBindingNames.contains("hawkular"));
        Assert.assertEquals("Wrong number of outbound socket binding groups", 2, dmrBindingNames.size());
    }

    @Test(groups = { GROUP }, dependsOnMethods = { "wfStarted" })
    // FIXME: lost traversal
    public void datasourcesAddedToInventory() throws Throwable {

        for (String datasourceName : getDatasourceNames()) {
            Resource ds = getResource(wfClientConfig.getFeedId(), "rt", "Datasource",
                    (r -> r.getId().contains(datasourceName)));
            System.out.println("ds = " + ds);
        }

        Assert.assertNotNull("feedId should not be null", wfClientConfig.getFeedId());
    }

    private Collection<String> getSocketBindingGroupNames() {
        return getDMRChildrenNames(wfClientConfig, "socket-binding-group", PathAddress.EMPTY_ADDRESS);
    }

    private Collection<String> getSocketBindingNames() {
        return getDMRChildrenNames(wfClientConfig,
                "socket-binding", PathAddress.parseCLIStyleAddress("/socket-binding-group=standard-sockets"));
    }

    private Collection<String> getOutboundSocketBindingNames() {
        return getDMRChildrenNames(wfClientConfig,
                "remote-destination-outbound-socket-binding",
                PathAddress.parseCLIStyleAddress("/socket-binding-group=standard-sockets"));
    }

    private Collection<String> getDatasourceNames() {
        return getDMRChildrenNames(wfClientConfig,
                "data-source", PathAddress.parseCLIStyleAddress("/subsystem=datasources"));
    }

    @Test(dependsOnMethods = { "datasourcesAddedToInventory" })
    public void datasourceMetricsCollected() throws Throwable {
        long startTime = System.currentTimeMillis();
        String lastUrl = "";
        int second = 1000;
        int timeOutSeconds = 60;
        for (int i = 0; i < timeOutSeconds; i++) {
            Request request = newAuthRequest().url(baseMetricsUri + "/gauges").build();
            Response gaugesResponse = client.newCall(request).execute();

            if (gaugesResponse.code() == 200 && !gaugesResponse.body().string().isEmpty()) {
                boolean found = false;

                for (String datasourceName : getDatasourceNames()) {
                    // enabled
                    String id = "MI~R~[" + wfClientConfig.getFeedId() + "/Local~/subsystem=datasources/data-source="
                            + datasourceName
                            + "]~MT~Datasource Pool Metrics~Available Count";
                    id = Util.urlEncodeQuery(id);
                    String url = baseMetricsUri + "/gauges/stats?start=" + startTime + "&buckets=1&metrics=" + id;
                    lastUrl = url;
                    Response gaugeResponse = client.newCall(newAuthRequest().url(url).get().build()).execute();
                    if (gaugeResponse.code() == 200) {
                        String body = gaugeResponse.body().string();
                        //System.out.println("ActiveBody=" + body);
                        /* this should be enough to prove that some metric was written successfully */
                        if (body.contains("\"empty\":false")) {
                            found = true;
                        }
                    }

                    // disabled
                    id = "MI~R~[" + wfClientConfig.getFeedId() + "/Local~/subsystem=datasources/data-source="
                            + datasourceName
                            + "]~MT~Datasource Pool Metrics~Active Count";
                    id = Util.urlEncodeQuery(id);
                    url = baseMetricsUri + "/gauges/stats?start=" + startTime + "&buckets=1&metrics=" + id;
                    //System.out.println("url = " + url);
                    gaugeResponse = client.newCall(newAuthRequest().url(url).get().build()).execute();
                    if (gaugeResponse.code() == 200) {
                        String body = gaugeResponse.body().string();
                        // System.out.println("DisabledBody=" + body);
                        /* this should be enough to prove that the metric was not disabled */
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
        int timeOutSeconds = 60;

        for (int i = 0; i < timeOutSeconds; i++) {
            Request request = newAuthRequest().url(baseMetricsUri + "/availability").build();
            Response availabilityResponse = client.newCall(request).execute();

            if (availabilityResponse.code() == 200 && !availabilityResponse.body().string().isEmpty()) {
                String id = "AI~R~[" + wfClientConfig.getFeedId()
                        + "/Local~~]~AT~Server Availability~Server Availability";
                id = Util.urlEncode(id);
                String url = baseMetricsUri + "/availability/" + id + "/raw";
                //System.out.println("url = " + url);
                availabilityResponse = client.newCall(newAuthRequest().url(url).get().build()).execute();
                if (availabilityResponse.code() == 200) {
                    String body = availabilityResponse.body().string();
                    System.out.println("AvailResponse ===>" + body);
                    /* this should be enough to prove that some metric was written successfully */
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
        CanonicalPath wfPath = getWildFlyServerResourcePath();
        wfPath = wfPath.extend(SegmentType.d, "configuration").get();
        Map<String, StructuredData> resConfig = getStructuredData("/entity" + wfPath.toString(), 1, 1);
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
        List<Resource> servers = getResources(wfClientConfig.getFeedId(), "r", 2);
        List<Resource> wfs = servers.stream().filter(s -> "WildFly Server".equals(s.getType().getId()))
                .collect(Collectors.toList());
        Assert.assertEquals(1, wfs.size());
        return wfs.get(0).getPath();
    }

    @Test(groups = { GROUP }, dependsOnMethods = { "datasourcesAddedToInventory" })
    public void machineId() throws Throwable {
        CanonicalPath osTypePath = getOperatingSystemResourceTypePath();
        osTypePath = osTypePath.extend(SegmentType.d, "configurationSchema").get();
        Map<String, StructuredData> schema = getStructuredData("/entity" + osTypePath.toString(), 1, 1);
        Assert.assertTrue(schema.containsKey("Machine Id"));

        CanonicalPath osPath = getOperatingSystemResourcePath();
        osPath = osPath.extend(SegmentType.d, "configuration").get();
        Map<String, StructuredData> resConfig = getStructuredData("/entity" + osPath.toString(), 1, 1);
        Assert.assertTrue(resConfig.containsKey("Machine Id"));
    }

    private CanonicalPath getOperatingSystemResourceTypePath() throws Throwable {
        ResourceType osType = getResourceType(wfClientConfig.getFeedId(), "Platform_Operating%20System", 1, 1);
        Assert.assertNotNull(osType);
        return osType.getPath();
    }

    private CanonicalPath getOperatingSystemResourcePath() throws Throwable {
        List<Resource> servers = getResources(wfClientConfig.getFeedId(), "r", 2);
        List<Resource> os = servers.stream().filter(s -> "Platform_Operating System".equals(s.getType().getId()))
                .collect(Collectors.toList());
        Assert.assertEquals(1, os.size());
        return os.get(0).getPath();
    }
}
