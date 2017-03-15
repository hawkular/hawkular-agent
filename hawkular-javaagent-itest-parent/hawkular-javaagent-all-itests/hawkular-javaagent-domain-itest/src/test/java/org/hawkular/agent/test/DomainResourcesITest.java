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
package org.hawkular.agent.test;

import java.util.Collection;
import java.util.Map;

import org.hawkular.inventory.api.model.DataEntity;
import org.hawkular.inventory.api.model.OperationType;
import org.hawkular.inventory.api.model.Resource;
import org.hawkular.inventory.api.model.StructuredData;
import org.hawkular.javaagent.itest.util.WildFlyClientConfig;
import org.jboss.as.controller.PathAddress;
import org.junit.Assert;
import org.testng.annotations.Test;

@Test(suiteName = AbstractDomainITestSuite.SUITE)
public class DomainResourcesITest extends AbstractDomainITestSuite {
    public static final String GROUP = "DomainResourcesITest";

    protected static final WildFlyClientConfig wfClientConfig;

    static {
        wfClientConfig = new WildFlyClientConfig();
    }

    @Test(groups = { GROUP })
    public void wfStarted() throws Throwable {
        waitForHawkularServerToBeReady();
    }

    @Test(groups = { GROUP }, dependsOnMethods = { "wfStarted" })
    public void hostsInInventory() throws Throwable {
        String feedId = hawkularFeedId;

        Collection<String> dmrHostNames = getHostNames();
        for (String hostName : dmrHostNames) {
            Resource host = getResource(
                    "/traversal/f;" + feedId + "/type=rt;"
                            + "id=Domain Host/rl;defines/type=r",
                    (r -> r.getName().contains(hostName)));
            System.out.println("domain host in inventory=" + host);
        }

        // make sure we are testing against what we were expecting
        Assert.assertTrue(dmrHostNames.contains("master"));
        Assert.assertEquals("Wrong number of domain hosts", 1, dmrHostNames.size());

        // make sure the Domain Host operations are OK
        // SHUTDOWN
        OperationType op = getOperationType("/traversal/f;" + feedId + "/type=rt;" +
                "id=Domain Host/type=ot;id=Shutdown", 1, 1);
        Assert.assertEquals("Shutdown", op.getId());
        DataEntity data = getDataEntity("/entity/f;" + feedId
                + "/rt;Domain Host/ot;Shutdown/d;parameterTypes", 1, 1);
        Map<String, StructuredData> paramsMap = data.getValue().map();
        Map<String, StructuredData> param = paramsMap.get("restart").map();
        Assert.assertEquals("bool", param.get("type").string());
        Assert.assertNull(param.get("defaultValue")); // this is not defined today
        Assert.assertNotNull(param.get("description").string());

        // RELOAD
        op = getOperationType("/traversal/f;" + feedId + "/type=rt;" +
                "id=Domain Host/type=ot;id=Reload", 1, 1);
        Assert.assertEquals("Reload", op.getId());
    }

    @Test(groups = { GROUP }, dependsOnMethods = { "wfStarted" })
    public void serversInInventory() throws Throwable {
        String feedId = hawkularFeedId;

        Collection<String> dmrServerNames = getServerNames();
        for (String serverName : dmrServerNames) {
            Resource server = getResource(
                    "/traversal/f;" + feedId + "/type=rt;"
                            + "id=Domain WildFly Server/rl;defines/type=r",
                    (r -> r.getName().contains(serverName)));
            System.out.println("domain server in inventory=" + server);
        }

        // make sure we are testing against what we were expecting
        Assert.assertTrue(dmrServerNames.contains("server-one"));
        Assert.assertTrue(dmrServerNames.contains("server-two"));
        Assert.assertTrue(dmrServerNames.contains("server-three"));
        Assert.assertEquals("Wrong number of domain servers", 3, dmrServerNames.size());
    }

    @Test(groups = { GROUP }, dependsOnMethods = { "wfStarted" })
    public void serverGroupsInInventory() throws Throwable {
        String feedId = hawkularFeedId;

        Collection<String> dmrServerGroupNames = getServerGroupNames();
        for (String groupName : dmrServerGroupNames) {
            Resource group = getResource(
                    "/traversal/f;" + feedId + "/type=rt;"
                            + "id=Domain Server Group/rl;defines/type=r",
                    (r -> r.getName().contains(groupName)));
            System.out.println("domain server group in inventory=" + group);
        }

        // make sure we are testing against what we were expecting
        Assert.assertTrue(dmrServerGroupNames.contains("main-server-group"));
        Assert.assertTrue(dmrServerGroupNames.contains("other-server-group"));
        Assert.assertEquals("Wrong number of domain server groups", 2, dmrServerGroupNames.size());

        // make sure the Domain Server Groups operations are OK
        // RELOAD SERVERS
        OperationType op = getOperationType("/traversal/f;" + feedId + "/type=rt;" +
                "id=Domain Server Group/type=ot;id=Reload Servers", 1, 1);
        Assert.assertEquals("Reload Servers", op.getId());
        DataEntity data = getDataEntity("/entity/f;" + feedId
                + "/rt;Domain Server Group/ot;Reload Servers/d;parameterTypes", 1, 1);
        Map<String, StructuredData> paramsMap = data.getValue().map();
        Map<String, StructuredData> param = paramsMap.get("blocking").map();
        Assert.assertEquals("bool", param.get("type").string());
        Assert.assertEquals("false", param.get("defaultValue").string());
        Assert.assertNotNull(param.get("description").string());

        // RESTART SERVERS
        op = getOperationType("/traversal/f;" + feedId + "/type=rt;" +
                "id=Domain Server Group/type=ot;id=Restart Servers", 1, 1);
        Assert.assertEquals("Restart Servers", op.getId());
        data = getDataEntity("/entity/f;" + feedId
                + "/rt;Domain Server Group/ot;Restart Servers/d;parameterTypes", 1, 1);
        paramsMap = data.getValue().map();
        param = paramsMap.get("blocking").map();
        Assert.assertEquals("bool", param.get("type").string());
        Assert.assertEquals("false", param.get("defaultValue").string());
        Assert.assertNotNull(param.get("description").string());

        // START SERVERS
        op = getOperationType("/traversal/f;" + feedId + "/type=rt;" +
                "id=Domain Server Group/type=ot;id=Start Servers", 1, 1);
        Assert.assertEquals("Start Servers", op.getId());
        data = getDataEntity("/entity/f;" + feedId
                + "/rt;Domain Server Group/ot;Start Servers/d;parameterTypes", 1, 1);
        paramsMap = data.getValue().map();
        param = paramsMap.get("blocking").map();
        Assert.assertEquals("bool", param.get("type").string());
        Assert.assertEquals("false", param.get("defaultValue").string());
        Assert.assertNotNull(param.get("description").string());

        // SUSPEND SERVERS
        op = getOperationType("/traversal/f;" + feedId + "/type=rt;" +
                "id=Domain Server Group/type=ot;id=Suspend Servers", 1, 1);
        Assert.assertEquals("Suspend Servers", op.getId());
        data = getDataEntity("/entity/f;" + feedId
                + "/rt;Domain Server Group/ot;Suspend Servers/d;parameterTypes", 1, 1);
        paramsMap = data.getValue().map();
        param = paramsMap.get("timeout").map();
        Assert.assertEquals("int", param.get("type").string());
        Assert.assertNull(param.get("defaultValue")); // today, no default value is defined for this
        Assert.assertNotNull(param.get("description").string());

        // STOP SERVERS
        op = getOperationType("/traversal/f;" + feedId + "/type=rt;" +
                "id=Domain Server Group/type=ot;id=Stop Servers", 1, 1);
        Assert.assertEquals("Stop Servers", op.getId());
        data = getDataEntity("/entity/f;" + feedId
                + "/rt;Domain Server Group/ot;Stop Servers/d;parameterTypes", 1, 1);
        paramsMap = data.getValue().map();
        param = paramsMap.get("timeout").map();
        Assert.assertEquals("int", param.get("type").string());
        Assert.assertNull(param.get("defaultValue")); // today, no default value is defined for this
        Assert.assertNotNull(param.get("description").string());
        param = paramsMap.get("blocking").map();
        Assert.assertEquals("bool", param.get("type").string());
        Assert.assertEquals("false", param.get("defaultValue").string());
        Assert.assertNotNull(param.get("description").string());

        // RESUME SERVERS
        op = getOperationType("/traversal/f;" + feedId + "/type=rt;" +
                "id=Domain Server Group/type=ot;id=Resume Servers", 1, 1);
        Assert.assertEquals("Resume Servers", op.getId());
    }

    @Test(groups = { GROUP }, dependsOnMethods = { "wfStarted" })
    public void profilesInInventory() throws Throwable {
        String feedId = hawkularFeedId;

        Collection<String> dmrProfileNames = getProfileNames();
        for (String profileName : dmrProfileNames) {
            Resource profile = getResource(
                    "/traversal/f;" + feedId + "/type=rt;"
                            + "id=Domain Profile/rl;defines/type=r",
                    (r -> r.getName().contains(profileName)));
            System.out.println("domain profile in inventory=" + profile);
        }

        // make sure we are testing against what we were expecting
        Assert.assertTrue(dmrProfileNames.contains("default"));
        Assert.assertTrue(dmrProfileNames.contains("ha"));
        Assert.assertTrue(dmrProfileNames.contains("full"));
        Assert.assertTrue(dmrProfileNames.contains("full-ha"));
        Assert.assertEquals("Wrong number of domain profiles", 4, dmrProfileNames.size());
    }

    @Test(groups = { GROUP }, dependsOnMethods = { "wfStarted" })
    public void socketBindingGroupsInInventory() throws Throwable {
        String feedId = hawkularFeedId;

        Collection<String> dmrSBGNames = getSocketBindingGroupNames();
        for (String sbgName : dmrSBGNames) {
            Resource sbg = getResource(
                    "/traversal/f;" + feedId + "/type=rt;"
                            + "id=Socket Binding Group/rl;defines/type=r",
                    (r -> r.getName().contains(sbgName)));
            System.out.println("socket binding group in inventory=" + sbg);
        }

        // make sure we are testing against what we were expecting
        Assert.assertTrue(dmrSBGNames.contains("standard-sockets"));
        Assert.assertTrue(dmrSBGNames.contains("ha-sockets"));
        Assert.assertTrue(dmrSBGNames.contains("full-sockets"));
        Assert.assertTrue(dmrSBGNames.contains("full-ha-sockets"));
        Assert.assertEquals("Wrong number of socket binding groups", 4, dmrSBGNames.size());
    }

    private Collection<String> getHostNames() {
        return getDMRChildrenNames(wfClientConfig, "host", PathAddress.EMPTY_ADDRESS);
    }

    private Collection<String> getServerNames() {
        return getDMRChildrenNames(wfClientConfig, "server", PathAddress.parseCLIStyleAddress("/host=master"));
    }

    private Collection<String> getServerGroupNames() {
        return getDMRChildrenNames(wfClientConfig, "server-group", PathAddress.EMPTY_ADDRESS);
    }

    private Collection<String> getProfileNames() {
        return getDMRChildrenNames(wfClientConfig, "profile", PathAddress.EMPTY_ADDRESS);
    }

    private Collection<String> getSocketBindingGroupNames() {
        return getDMRChildrenNames(wfClientConfig, "socket-binding-group", PathAddress.EMPTY_ADDRESS);
    }
}
