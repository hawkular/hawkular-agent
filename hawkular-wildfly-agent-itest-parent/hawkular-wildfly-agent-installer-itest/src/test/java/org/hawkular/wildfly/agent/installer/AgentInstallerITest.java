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
package org.hawkular.wildfly.agent.installer;

import java.io.IOException;
import java.net.URLEncoder;
import java.util.List;
import java.util.stream.Collectors;

import org.hawkular.agent.monitor.protocol.dmr.DMREndpointService;
import org.hawkular.dmr.api.OperationBuilder;
import org.hawkular.inventory.paths.CanonicalPath;
import org.hawkular.inventory.api.model.Resource;
import org.jboss.as.controller.PathAddress;
import org.jboss.as.controller.client.ModelControllerClient;
import org.jboss.dmr.ModelNode;
import org.junit.Assert;
import org.testng.AssertJUnit;
import org.testng.annotations.Test;

import com.squareup.okhttp.Request;
import com.squareup.okhttp.Response;

/**
 * @author <a href="https://github.com/ppalaga">Peter Palaga</a>
 *
 */
public class AgentInstallerITest extends AbstractITest {

    protected static final String wfHost;
    protected static final int wfManagementPort;
    protected static final String wfFeedId;

    static {
        String wfH = System.getProperty("plain-wildfly.bind.address", "localhost");
        if ("0.0.0.0".equals(wfH)) {
            wfH = "localhost";
        }
        wfHost = wfH;

        int wfPortOffset = Integer.parseInt(System.getProperty("plain-wildfly.port.offset", "0"));
        wfManagementPort = wfPortOffset + 9990;

        try (ModelControllerClient mcc = newModelControllerClient(wfHost, wfManagementPort)) {
            wfFeedId = DMREndpointService.lookupServerIdentifier(mcc);
        } catch (IOException e) {
            throw new RuntimeException("Could not get wfFeedId", e);
        }

    }

    /**
     * @return the {@link CanonicalPath} of the only AS server present in inventory
     * @throws Throwable
     */
    protected CanonicalPath getCurrentASPath() throws Throwable {
        List<Resource> servers = getResources("/feeds/" + wfFeedId + "/resources", 2);
        List<Resource> wfs = servers.stream().filter(s -> "WildFly Server".equals(s.getType().getId()))
                .collect(Collectors.toList());
        AssertJUnit.assertEquals(1, wfs.size());
        return wfs.get(0).getPath();
    }

    @Test
    public void wfStarted() throws Throwable {
        waitForAccountsAndInventory();
        // System.out.println("wfFeedId = " + wfFeedId);
        Assert.assertNotNull("wfFeedId should not be null", wfFeedId);
    }

    @Test(dependsOnMethods = { "wfStarted" })
    public void datasourcesAddedToInventory() throws Throwable {

        for (String datasourceName : getDatasourceNames()) {
            Resource ds = getResource("/feeds/" + wfFeedId + "/resourceTypes/Datasource/resources",
                    (r -> r.getId().contains(datasourceName)));
            System.out.println("ds = " + ds);
        }

        Assert.assertNotNull("feedId should not be null", wfFeedId);
    }

    private List<String> getDatasourceNames() {
        try (ModelControllerClient mcc = newModelControllerClient(wfHost, wfManagementPort)) {
            ModelNode result = OperationBuilder.readChildrenNames()
                    .address(PathAddress.parseCLIStyleAddress("/subsystem=datasources"))
                    .childType("data-source")
                    .execute(mcc)
                    .getResultNode();

            return result.asList().stream().map(n -> n.asString()).collect(Collectors.toList());

        } catch (IOException e) {
            throw new RuntimeException("Could not get wfFeedId", e);
        }
    }

    @Test(dependsOnMethods = { "datasourcesAddedToInventory" })
    public void datasourceMetricsCollected() throws Throwable {
        String lastUrl = "";
        int second = 1000;
        int timeOutSeconds = 60;
        for (int i = 0; i < timeOutSeconds; i++) {
            Request request = newAuthRequest().url(baseMetricsUri + "/gauges").build();
            Response gaugesResponse = client.newCall(request).execute();

            if (gaugesResponse.code() == 200 && !gaugesResponse.body().string().isEmpty()) {
                for (String datasourceName : getDatasourceNames()) {
                    String id = "MI~R~[" + wfFeedId + "/Local~/subsystem=datasources/data-source=" + datasourceName
                            + "]~MT~Datasource Pool Metrics~Available Count";
                    id = URLEncoder.encode(id, "UTF-8");
                    String url = baseMetricsUri + "/gauges/data?buckets=1&metrics=" + id;
                    lastUrl = url;
                    //System.out.println("url = " + url);
                    Response gaugeResponse = client.newCall(newAuthRequest().url(url).get().build()).execute();
                    if (gaugeResponse.code() == 200 && !gaugeResponse.body().string().isEmpty()) {
                        /* this should be enough to prove that some metric was written successfully */
                        return;
                    }
                }
            }
            Thread.sleep(second);
        }

        Assert.fail("Gauge still not available after ["+ timeOutSeconds +"] seconds: ["+ lastUrl +"]");

    }
}
