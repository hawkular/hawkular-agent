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
import java.util.Collection;
import java.util.stream.Collectors;

import org.hawkular.dmr.api.OperationBuilder;
import org.hawkular.inventory.api.model.Resource;
import org.hawkular.wildfly.agent.itest.util.AbstractITest;
import org.hawkular.wildfly.agent.itest.util.WildFlyClientConfig;
import org.jboss.as.controller.PathAddress;
import org.jboss.as.controller.client.ModelControllerClient;
import org.jboss.dmr.ModelNode;
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
        for (String hostName : dmrHostNames) {
            Resource host = getResource(
                    "/feeds/" + wfClientConfig.getFeedId() + "/resourceTypes/Domain Host/resources",
                    (r -> r.getName().contains(hostName)));
            System.out.println("domain host in inventory=" + host);
        }

        // make sure we are testing against what we were expecting
        Assert.assertTrue(dmrHostNames.contains("master"));
        Assert.assertEquals("Wrong number of domain hosts", 1, dmrHostNames.size());
    }

    @Test(groups = { GROUP }, dependsOnMethods = { "wfStarted" })
    public void serversInInventory() throws Throwable {

        Collection<String> dmrServerNames = getServerNames();
        for (String serverName : dmrServerNames) {
            Resource server = getResource(
                    "/feeds/" + wfClientConfig.getFeedId() + "/resourceTypes/Domain WildFly Server/resources",
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

        Collection<String> dmrServerGroupNames = getServerGroupNames();
        for (String groupName : dmrServerGroupNames) {
            Resource group = getResource(
                    "/feeds/" + wfClientConfig.getFeedId() + "/resourceTypes/Domain Server Group/resources",
                    (r -> r.getName().contains(groupName)));
            System.out.println("domain server group in inventory=" + group);
        }

        // make sure we are testing against what we were expecting
        Assert.assertTrue(dmrServerGroupNames.contains("main-server-group"));
        Assert.assertTrue(dmrServerGroupNames.contains("other-server-group"));
        Assert.assertEquals("Wrong number of domain server groups", 2, dmrServerGroupNames.size());
    }

    @Test(groups = { GROUP }, dependsOnMethods = { "wfStarted" })
    public void profilesInInventory() throws Throwable {

        Collection<String> dmrProfileNames = getProfileNames();
        for (String profileName : dmrProfileNames) {
            Resource profile = getResource(
                    "/feeds/" + wfClientConfig.getFeedId() + "/resourceTypes/Domain Profile/resources",
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

    private Collection<String> getHostNames() {
        return getChildrenNames("host", PathAddress.EMPTY_ADDRESS);
    }

    private Collection<String> getServerNames() {
        return getChildrenNames("server", PathAddress.parseCLIStyleAddress("/host=master"));
    }

    private Collection<String> getServerGroupNames() {
        return getChildrenNames("server-group", PathAddress.EMPTY_ADDRESS);
    }

    private Collection<String> getProfileNames() {
        return getChildrenNames("profile", PathAddress.EMPTY_ADDRESS);
    }

    private Collection<String> getChildrenNames(String childTypeName, PathAddress parentAddress) {
        try (ModelControllerClient mcc = newPlainWildFlyModelControllerClient(wfClientConfig)) {
            ModelNode result = OperationBuilder.readChildrenNames()
                    .address(parentAddress)
                    .childType(childTypeName)
                    .execute(mcc)
                    .getResultNode();

            return result.asList().stream().map(n -> n.asString()).collect(Collectors.toList());

        } catch (IOException e) {
            throw new RuntimeException("Could not get: " + parentAddress + "/" + childTypeName, e);
        }
    }
}
