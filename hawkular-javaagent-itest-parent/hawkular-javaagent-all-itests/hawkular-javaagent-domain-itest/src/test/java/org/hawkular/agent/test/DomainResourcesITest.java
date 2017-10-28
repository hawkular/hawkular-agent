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
import java.util.Optional;

import org.hawkular.inventory.api.model.Operation;
import org.hawkular.inventory.api.model.Resource;
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

        Collection<Resource> domainHosts = testHelper.getResourceByType(hawkularFeedId, "Domain Host", 1);

        Collection<String> dmrHostNames = getHostNames();
        for (String hostName : dmrHostNames) {
            boolean hasMatch = domainHosts.stream()
                    .anyMatch(dh -> dh.getName().contains(hostName));
            Assert.assertTrue("No match for " + hostName, hasMatch);
            System.out.println("domain host in inventory=" + hostName);
        }

        // make sure we are testing against what we were expecting
        Assert.assertTrue(dmrHostNames.contains("master"));
        Assert.assertEquals("Wrong number of domain hosts", 1, dmrHostNames.size());

        // make sure the Domain Host operations are OK
        // SHUTDOWN
        Resource domainHost = domainHosts.iterator().next();
        Optional<Operation> shutdown = domainHost.getType()
                .getOperations()
                .stream()
                .filter(o -> o.getName().contains("Shutdown"))
                .findFirst();
        Assert.assertTrue(shutdown.isPresent());

        Assert.assertTrue(shutdown.get().getParameters().containsKey("restart"));
        Assert.assertEquals("bool", shutdown.get().getParameters().get("restart").get("type"));
        Assert.assertNull(shutdown.get().getParameters().get("restart").get("defaultValue"));
        Assert.assertNotNull(shutdown.get().getParameters().get("restart").get("description"));

        // RELOAD
        Optional<Operation> reload = domainHost.getType()
                .getOperations()
                .stream()
                .filter(o -> o.getName().contains("Reload"))
                .findFirst();
        Assert.assertTrue(reload.isPresent());
    }

    @Test(groups = { GROUP }, dependsOnMethods = { "wfStarted" })
    public void serversInInventory() throws Throwable {
        testHelper.printAllResources(hawkularFeedId);
        Collection<Resource> servers = testHelper.getResourceByType(hawkularFeedId, "Domain WildFly Server", 2);
        Collection<String> dmrServerNames = getServerNames();
        for (String serverName : dmrServerNames) {
            boolean hasMatch = servers.stream()
                    .anyMatch(s -> s.getName().contains(serverName));
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
        Collection<Resource> servers = testHelper.getResourceByType(hawkularFeedId, "Domain Server Group", 2);
        Collection<String> dmrServerGroupNames = getServerGroupNames();
        for (String groupName : dmrServerGroupNames) {
            boolean hasMatch = servers.stream()
                    .anyMatch(s -> s.getName().contains(groupName));
            Assert.assertTrue(hasMatch);
            System.out.println("domain server group in inventory=" + groupName);
        }

        // make sure we are testing against what we were expecting
        Assert.assertTrue(dmrServerGroupNames.contains("main-server-group"));
        Assert.assertTrue(dmrServerGroupNames.contains("other-server-group"));
        Assert.assertEquals("Wrong number of domain server groups", 2, dmrServerGroupNames.size());

        // make sure the Domain Server Groups operations are OK
        // RELOAD SERVERS
        Resource server = servers.iterator().next();
        Optional<Operation> reloadServers = server.getType()
                .getOperations()
                .stream()
                .filter(o -> o.getName().contains("Reload Servers"))
                .findFirst();
        Assert.assertTrue(reloadServers.isPresent());

        Assert.assertEquals("bool", reloadServers.get().getParameters().get("blocking").get("type"));
        Assert.assertEquals("false", reloadServers.get().getParameters().get("blocking").get("defaultValue"));
        Assert.assertNotNull(reloadServers.get().getParameters().get("blocking").get("description"));

        // RESTART SERVERS
        Optional<Operation> restartServers = server.getType()
                .getOperations()
                .stream()
                .filter(o -> o.getName().contains("Restart Servers"))
                .findFirst();
        Assert.assertTrue(restartServers.isPresent());

        Assert.assertEquals("bool", reloadServers.get().getParameters().get("blocking").get("type"));
        Assert.assertEquals("false", reloadServers.get().getParameters().get("blocking").get("defaultValue"));
        Assert.assertNotNull(reloadServers.get().getParameters().get("blocking").get("description"));

        // START SERVERS
        Optional<Operation> startServers = server.getType()
                .getOperations()
                .stream()
                .filter(o -> o.getName().contains("Start Servers"))
                .findFirst();
        Assert.assertTrue(startServers.isPresent());

        Assert.assertEquals("bool", startServers.get().getParameters().get("blocking").get("type"));
        Assert.assertEquals("false", startServers.get().getParameters().get("blocking").get("defaultValue"));
        Assert.assertNotNull(startServers.get().getParameters().get("blocking").get("description"));

        // SUSPEND SERVERS
        Optional<Operation> suspendServers = server.getType()
                .getOperations()
                .stream()
                .filter(o -> o.getName().contains("Suspend Servers"))
                .findFirst();
        Assert.assertTrue(suspendServers.isPresent());

        Assert.assertEquals("int", suspendServers.get().getParameters().get("timeout").get("type"));
        Assert.assertNull(suspendServers.get().getParameters().get("timeout").get("defaultValue"));
        Assert.assertNotNull(suspendServers.get().getParameters().get("timeout").get("description"));

        // STOP SERVERS
        Optional<Operation> stopServers = server.getType()
                .getOperations()
                .stream()
                .filter(o -> o.getName().contains("Stop Servers"))
                .findFirst();
        Assert.assertTrue(stopServers.isPresent());

        Assert.assertEquals("bool", stopServers.get().getParameters().get("blocking").get("type"));
        Assert.assertEquals("false", stopServers.get().getParameters().get("blocking").get("defaultValue"));
        Assert.assertNotNull(stopServers.get().getParameters().get("blocking").get("description"));

        Assert.assertEquals("int", stopServers.get().getParameters().get("timeout").get("type"));
        Assert.assertNull(stopServers.get().getParameters().get("timeout").get("defaultValue"));
        Assert.assertNotNull(stopServers.get().getParameters().get("timeout").get("description"));

        // RESUME SERVERS
        Optional<Operation> resumeServers = server.getType()
                .getOperations()
                .stream()
                .filter(o -> o.getName().contains("Resume Servers"))
                .findFirst();
        Assert.assertTrue(resumeServers.isPresent());
    }

    @Test(groups = { GROUP }, dependsOnMethods = { "wfStarted" })
    public void profilesInInventory() throws Throwable {
        Collection<Resource> domains = testHelper.getResourceByType(hawkularFeedId, "Domain Profile", 4);
        Collection<String> dmrProfileNames = getProfileNames();
        for (String profileName : dmrProfileNames) {
            boolean hasMatch = domains.stream()
                    .anyMatch(d -> d.getName().contains(profileName));
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
        Collection<Resource> sockets = testHelper.getResourceByType(hawkularFeedId, "Socket Binding Group", 4);
        Collection<String> dmrSBGNames = getSocketBindingGroupNames();
        for (String sbgName : dmrSBGNames) {
            boolean hasMatch = sockets.stream()
                    .anyMatch(s -> s.getName().contains(sbgName));
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
