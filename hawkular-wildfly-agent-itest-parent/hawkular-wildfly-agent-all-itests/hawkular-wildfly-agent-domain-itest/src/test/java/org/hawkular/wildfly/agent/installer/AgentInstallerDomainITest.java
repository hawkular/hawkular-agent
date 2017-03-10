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
import java.util.Map;

import org.hawkular.inventory.api.model.Blueprint;
import org.hawkular.inventory.api.model.DataEntity;
import org.hawkular.inventory.api.model.Entity;
import org.hawkular.inventory.api.model.OperationType;
import org.hawkular.inventory.api.model.StructuredData;
import org.hawkular.inventory.paths.CanonicalPath;
import org.hawkular.inventory.paths.SegmentType;
import org.hawkular.wildfly.agent.itest.util.AbstractITest;
import org.hawkular.wildfly.agent.itest.util.WildFlyClientConfig;
import org.jboss.as.controller.PathAddress;
import org.junit.Assert;
import org.testng.annotations.Test;

/**
 * @author <a href="https://github.com/ppalaga">Peter Palaga</a>
 *
 */
public class AgentInstallerDomainITest extends AbstractITest {
    public static final String GROUP = "AgentInstallerDomainITest";

    protected static final WildFlyClientConfig wfClientConfig;

    static {
        wfClientConfig = new WildFlyClientConfig();
    }

    @Test(groups = { GROUP })
    public void wfStarted() throws Throwable {
        waitForAccountsAndInventory();
        Assert.assertNotNull("wfFeedId should not be null", wfClientConfig.getFeedId());
        System.out.println("wfFeedId = " + wfClientConfig.getFeedId());
    }

    @Test(groups = { GROUP }, dependsOnMethods = { "wfStarted" })
    public void hostsInInventory() throws Throwable {

        Collection<String> dmrHostNames = getHostNames();
        Collection<Blueprint> hosts = getBlueprintsByType(wfClientConfig.getFeedId(), "Domain Host").values();
        for (String hostName : dmrHostNames) {
            boolean hasMatch = hosts.stream().anyMatch(bp -> bp instanceof Entity.Blueprint
                    && ((Entity.Blueprint)bp).getId().contains(hostName));
            Assert.assertTrue(hasMatch);
            System.out.println("domain host in inventory=" + hostName);
        }

        // make sure we are testing against what we were expecting
        Assert.assertTrue(dmrHostNames.contains("master"));
        Assert.assertEquals("Wrong number of domain hosts", 1, dmrHostNames.size());

        // make sure the Domain Host operations are OK
        // SHUTDOWN
        CanonicalPath shutdownPath = feedPath(wfClientConfig.getFeedId()).modified()
                .extend(SegmentType.rt, "Domain Host")
                .extend(SegmentType.ot, "Shutdown")
                .get();
        OperationType.Blueprint op = (OperationType.Blueprint) getBlueprintFromCP(shutdownPath).get();
        Assert.assertEquals("Shutdown", op.getId());

        CanonicalPath configPath = shutdownPath.extend(SegmentType.d, "parameterTypes").get();
        DataEntity.Blueprint data = (DataEntity.Blueprint) getBlueprintFromCP(configPath).get();
        Map<String, StructuredData> paramsMap = data.getValue().map();
        Map<String, StructuredData> param = paramsMap.get("restart").map();
        Assert.assertEquals("bool", param.get("type").string());
        Assert.assertNull(param.get("defaultValue")); // this is not defined today
        Assert.assertNotNull(param.get("description").string());

        // RELOAD
        CanonicalPath reloadPath = feedPath(wfClientConfig.getFeedId()).modified()
                .extend(SegmentType.rt, "Domain Host")
                .extend(SegmentType.ot, "Reload")
                .get();
        op = (OperationType.Blueprint) getBlueprintFromCP(reloadPath).get();
        Assert.assertEquals("Reload", op.getId());
    }

    @Test(groups = { GROUP }, dependsOnMethods = { "wfStarted" })
    public void serversInInventory() throws Throwable {

        Collection<String> dmrServerNames = getServerNames();
        Collection<Blueprint> servers = getBlueprintsByType(wfClientConfig.getFeedId(), "Domain WildFly Server").values();
        for (String serverName : dmrServerNames) {
            boolean hasMatch = servers.stream().anyMatch(bp -> bp instanceof Entity.Blueprint
                    && ((Entity.Blueprint)bp).getId().contains(serverName));
            Assert.assertTrue(hasMatch);
            System.out.println("domain server in inventory=" + serverName);
        }

        // make sure we are testing against what we were expecting
        Assert.assertTrue(dmrServerNames.contains("server-one"));
        Assert.assertTrue(dmrServerNames.contains("server-two"));
        Assert.assertTrue(dmrServerNames.contains("server-three"));
        Assert.assertEquals("Wrong number of domain servers", 3, dmrServerNames.size());
    }

    @Test(groups = { GROUP }, dependsOnMethods = { "wfStarted" })
    public void serverGroupsInInventory() throws Throwable {

        Collection<String> dmrServerGroupNames = getServerGroupNames();
        Collection<Blueprint> groups = getBlueprintsByType(wfClientConfig.getFeedId(), "Domain Server Group").values();
        for (String groupName : dmrServerGroupNames) {
            boolean hasMatch = groups.stream().anyMatch(bp -> bp instanceof Entity.Blueprint
                    && ((Entity.Blueprint)bp).getId().contains(groupName));
            Assert.assertTrue(hasMatch);
            System.out.println("domain server group in inventory=" + groupName);
        }

        // make sure we are testing against what we were expecting
        Assert.assertTrue(dmrServerGroupNames.contains("main-server-group"));
        Assert.assertTrue(dmrServerGroupNames.contains("other-server-group"));
        Assert.assertEquals("Wrong number of domain server groups", 2, dmrServerGroupNames.size());

        // make sure the Domain Server Groups operations are OK
        // RELOAD SERVERS
        CanonicalPath reloadPath = feedPath(wfClientConfig.getFeedId()).modified()
                .extend(SegmentType.rt, "Domain Server Group")
                .extend(SegmentType.ot, "Reload Servers")
                .get();
        OperationType.Blueprint op = (OperationType.Blueprint) getBlueprintFromCP(reloadPath).get();
        Assert.assertEquals("Reload Servers", op.getId());

        CanonicalPath configPath = reloadPath.extend(SegmentType.d, "parameterTypes").get();
        DataEntity.Blueprint data = (DataEntity.Blueprint) getBlueprintFromCP(configPath).get();
        Map<String, StructuredData> paramsMap = data.getValue().map();
        Map<String, StructuredData> param = paramsMap.get("blocking").map();
        Assert.assertEquals("bool", param.get("type").string());
        Assert.assertEquals("false", param.get("defaultValue").string());
        Assert.assertNotNull(param.get("description").string());

        // RESTART SERVERS
        CanonicalPath restartPath = feedPath(wfClientConfig.getFeedId()).modified()
                .extend(SegmentType.rt, "Domain Server Group")
                .extend(SegmentType.ot, "Restart Servers")
                .get();
        op = (OperationType.Blueprint) getBlueprintFromCP(restartPath).get();
        Assert.assertEquals("Restart Servers", op.getId());
        configPath = restartPath.extend(SegmentType.d, "parameterTypes").get();
        data = (DataEntity.Blueprint) getBlueprintFromCP(configPath).get();
        paramsMap = data.getValue().map();
        param = paramsMap.get("blocking").map();
        Assert.assertEquals("bool", param.get("type").string());
        Assert.assertEquals("false", param.get("defaultValue").string());
        Assert.assertNotNull(param.get("description").string());

        // START SERVERS
        CanonicalPath startPath = feedPath(wfClientConfig.getFeedId()).modified()
                .extend(SegmentType.rt, "Domain Server Group")
                .extend(SegmentType.ot, "Start Servers")
                .get();
        op = (OperationType.Blueprint) getBlueprintFromCP(startPath).get();
        Assert.assertEquals("Start Servers", op.getId());
        configPath = startPath.extend(SegmentType.d, "parameterTypes").get();
        data = (DataEntity.Blueprint) getBlueprintFromCP(configPath).get();
        paramsMap = data.getValue().map();
        param = paramsMap.get("blocking").map();
        Assert.assertEquals("bool", param.get("type").string());
        Assert.assertEquals("false", param.get("defaultValue").string());
        Assert.assertNotNull(param.get("description").string());

        // SUSPEND SERVERS
        CanonicalPath suspendPath = feedPath(wfClientConfig.getFeedId()).modified()
                .extend(SegmentType.rt, "Domain Server Group")
                .extend(SegmentType.ot, "Suspend Servers")
                .get();
        op = (OperationType.Blueprint) getBlueprintFromCP(suspendPath).get();
        Assert.assertEquals("Suspend Servers", op.getId());
        configPath = suspendPath.extend(SegmentType.d, "parameterTypes").get();
        data = (DataEntity.Blueprint) getBlueprintFromCP(configPath).get();
        paramsMap = data.getValue().map();
        param = paramsMap.get("timeout").map();
        Assert.assertEquals("int", param.get("type").string());
        Assert.assertNull(param.get("defaultValue")); // today, no default value is defined for this
        Assert.assertNotNull(param.get("description").string());

        // STOP SERVERS
        CanonicalPath stopPath = feedPath(wfClientConfig.getFeedId()).modified()
                .extend(SegmentType.rt, "Domain Server Group")
                .extend(SegmentType.ot, "Stop Servers")
                .get();
        op = (OperationType.Blueprint) getBlueprintFromCP(stopPath).get();
        Assert.assertEquals("Stop Servers", op.getId());
        configPath = stopPath.extend(SegmentType.d, "parameterTypes").get();
        data = (DataEntity.Blueprint) getBlueprintFromCP(configPath).get();
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
        CanonicalPath resumePath = feedPath(wfClientConfig.getFeedId()).modified()
                .extend(SegmentType.rt, "Domain Server Group")
                .extend(SegmentType.ot, "Resume Servers")
                .get();
        op = (OperationType.Blueprint) getBlueprintFromCP(resumePath).get();
        Assert.assertEquals("Resume Servers", op.getId());
    }

    @Test(groups = { GROUP }, dependsOnMethods = { "wfStarted" })
    public void profilesInInventory() throws Throwable {

        Collection<String> dmrProfileNames = getProfileNames();
        Collection<Blueprint> profiles = getBlueprintsByType(wfClientConfig.getFeedId(), "Domain Profile").values();
        for (String profileName : dmrProfileNames) {
            boolean hasMatch = profiles.stream().anyMatch(bp -> bp instanceof Entity.Blueprint
                    && ((Entity.Blueprint)bp).getId().contains(profileName));
            Assert.assertTrue(hasMatch);
            System.out.println("domain profile in inventory=" + profileName);
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

        Collection<String> dmrSBGNames = getSocketBindingGroupNames();
        Collection<Blueprint> sbgs = getBlueprintsByType(wfClientConfig.getFeedId(), "Socket Binding Group").values();
        for (String sbgName : dmrSBGNames) {
            boolean hasMatch = sbgs.stream().anyMatch(bp -> bp instanceof Entity.Blueprint
                    && ((Entity.Blueprint)bp).getId().contains(sbgName));
            Assert.assertTrue(hasMatch);
            System.out.println("socket binding group in inventory=" + sbgName);
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
