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
package org.hawkular.agent.javaagent;

import java.io.File;
import java.net.InetAddress;
import java.net.URL;

import org.junit.Assert;
import org.junit.Test;

public class GenerateFeedIdTest {

    @Test
    public void testFullConfigDmrFromFile() throws Exception {
        URL url = GenerateFeedIdTest.class.getResource("/test-config.yaml");
        Assert.assertNotNull("yaml config file not found", url);
        File file = new File(url.toURI());

        // create an agent - we'll use its protected method that generates the feed ID for this test
        JavaAgentEngine agent = new JavaAgentEngine(file);

        // By default (without any sysprops set) the feed ID is determined by the existence (in order) of:
        // HOSTNAME env var
        // COMPUTERNAME env var
        // Canonical hostname via Java API
        String hostNameEnvVar = System.getenv("HOSTNAME");
        String computerNameEnvVar = System.getenv("COMPUTERNAME");
        String canonicalHostName = InetAddress.getLocalHost().getCanonicalHostName();
        String feedId = agent.autoGenerateFeedId();
        if (hostNameEnvVar != null) {
            Assert.assertEquals(hostNameEnvVar, feedId);
        } else if (computerNameEnvVar != null) {
            Assert.assertEquals(computerNameEnvVar, feedId);
        } else {
            Assert.assertEquals(canonicalHostName, feedId);
        }

        // If one of these sysprops is set, it will be used for feed ID. The order of precedence is:
        // jboss.server.management.uuid
        // jboss.host.name
        // jboss.node.name
        String expectedFeedId;

        expectedFeedId = "feed-jboss-node-name";
        System.setProperty("jboss.node.name", expectedFeedId);
        feedId = agent.autoGenerateFeedId();
        Assert.assertEquals(expectedFeedId, feedId);

        expectedFeedId = "feed-jboss-host-name";
        System.setProperty("jboss.host.name", expectedFeedId);
        feedId = agent.autoGenerateFeedId();
        Assert.assertEquals(expectedFeedId, feedId);

        expectedFeedId = "feed-uuid";
        System.setProperty("jboss.server.management.uuid", expectedFeedId);
        feedId = agent.autoGenerateFeedId();
        Assert.assertEquals(expectedFeedId, feedId);
    }
}
