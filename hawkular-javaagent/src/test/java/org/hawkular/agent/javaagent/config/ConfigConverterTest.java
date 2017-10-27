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
package org.hawkular.agent.javaagent.config;

import java.io.File;
import java.net.URL;

import org.hawkular.agent.monitor.config.AgentCoreEngineConfiguration;
import org.hawkular.agent.monitor.config.AgentCoreEngineConfiguration.EndpointConfiguration;
import org.hawkular.agent.monitor.inventory.Name;
import org.junit.Assert;
import org.junit.Test;

public class ConfigConverterTest {

    @Test
    public void testManagedServerDefaultAllResourceTypeSets() throws Exception {
        Configuration config = loadTestConfigFile("/all-resource-type-sets.yaml");
        AgentCoreEngineConfiguration agentConfig = new ConfigConverter(config).convert();
        Assert.assertTrue(agentConfig.getGlobalConfiguration().isSubsystemEnabled());
        Assert.assertEquals(2,
                agentConfig.getDmrConfiguration().getEndpoints().get("local-dmr-ms").getResourceTypeSets().size());
        Assert.assertTrue(agentConfig.getDmrConfiguration().getEndpoints().get("local-dmr-ms").getResourceTypeSets()
                .contains(new Name("dmr type set 1")));
        Assert.assertTrue(agentConfig.getDmrConfiguration().getEndpoints().get("local-dmr-ms").getResourceTypeSets()
                .contains(new Name("dmr type set 2")));
        Assert.assertEquals(2,
                agentConfig.getDmrConfiguration().getEndpoints().get("remote-dmr-ms-1").getResourceTypeSets().size());
        Assert.assertTrue(agentConfig.getDmrConfiguration().getEndpoints().get("remote-dmr-ms-1").getResourceTypeSets()
                .contains(new Name("dmr type set 1")));
        Assert.assertTrue(agentConfig.getDmrConfiguration().getEndpoints().get("remote-dmr-ms-1").getResourceTypeSets()
                .contains(new Name("dmr type set 2")));
        Assert.assertEquals(2,
                agentConfig.getDmrConfiguration().getEndpoints().get("remote-dmr-ms-2").getResourceTypeSets().size());
        Assert.assertTrue(agentConfig.getDmrConfiguration().getEndpoints().get("remote-dmr-ms-2").getResourceTypeSets()
                .contains(new Name("dmr type set 1")));
        Assert.assertTrue(agentConfig.getDmrConfiguration().getEndpoints().get("remote-dmr-ms-2").getResourceTypeSets()
                .contains(new Name("dmr type set 2")));
        Assert.assertEquals(2,
                agentConfig.getJmxConfiguration().getEndpoints().get("local-jmx-ms").getResourceTypeSets().size());
        Assert.assertTrue(agentConfig.getJmxConfiguration().getEndpoints().get("local-jmx-ms").getResourceTypeSets()
                .contains(new Name("jmx type set 1")));
        Assert.assertTrue(agentConfig.getJmxConfiguration().getEndpoints().get("local-jmx-ms").getResourceTypeSets()
                .contains(new Name("jmx type set 2")));
        Assert.assertEquals(2,
                agentConfig.getJmxConfiguration().getEndpoints().get("remote-jmx-ms-1").getResourceTypeSets().size());
        Assert.assertTrue(agentConfig.getJmxConfiguration().getEndpoints().get("remote-jmx-ms-1").getResourceTypeSets()
                .contains(new Name("jmx type set 1")));
        Assert.assertTrue(agentConfig.getJmxConfiguration().getEndpoints().get("remote-jmx-ms-1").getResourceTypeSets()
                .contains(new Name("jmx type set 2")));
        Assert.assertEquals(2,
                agentConfig.getJmxConfiguration().getEndpoints().get("remote-jmx-ms-2").getResourceTypeSets().size());
        Assert.assertTrue(agentConfig.getJmxConfiguration().getEndpoints().get("remote-jmx-ms-2").getResourceTypeSets()
                .contains(new Name("jmx type set 1")));
        Assert.assertTrue(agentConfig.getJmxConfiguration().getEndpoints().get("remote-jmx-ms-2").getResourceTypeSets()
                .contains(new Name("jmx type set 2")));
    }

    @Test
    public void testEmpty() throws Exception {
        Configuration config = loadTestConfigFile("/empty.yaml");
        AgentCoreEngineConfiguration agentConfig = new ConfigConverter(config).convert();
        Assert.assertFalse(agentConfig.getGlobalConfiguration().isSubsystemEnabled());
    }

    @Test
    public void testRealConfig() throws Exception {
        Configuration config = loadTestConfigFile("/wildfly10/hawkular-javaagent-config.yaml");
        AgentCoreEngineConfiguration agentConfig = new ConfigConverter(config).convert();
        Assert.assertTrue(agentConfig.getGlobalConfiguration().isSubsystemEnabled());
    }

    @Test
    public void testRealConfigEAP6() throws Exception {
        Configuration config = loadTestConfigFile("/eap6/hawkular-javaagent-config.yaml");
        AgentCoreEngineConfiguration agentConfig = new ConfigConverter(config).convert();
        Assert.assertTrue(agentConfig.getGlobalConfiguration().isSubsystemEnabled());
    }

    @Test
    public void testRealConfigJMX() throws Exception {
        Configuration config = loadTestConfigFile("/real-config-jmx.yaml");
        AgentCoreEngineConfiguration agentConfig = new ConfigConverter(config).convert();
        Assert.assertTrue(agentConfig.getGlobalConfiguration().isSubsystemEnabled());
        Assert.assertTrue(agentConfig.getDmrConfiguration().getEndpoints().isEmpty());
    }

    @Test
    public void testConvertConfig() throws Exception {
        Configuration config = loadTestConfigFile("/test-convert.yaml");
        AgentCoreEngineConfiguration agentConfig = new ConfigConverter(config).convert();
        Assert.assertTrue(agentConfig.getGlobalConfiguration().isSubsystemEnabled());

        Assert.assertEquals(111, agentConfig.getGlobalConfiguration().getAutoDiscoveryScanPeriodSeconds());

        Assert.assertEquals(true, agentConfig.getMetricsExporterConfiguration().isEnabled());
        Assert.assertEquals("thehost", agentConfig.getMetricsExporterConfiguration().getHost());
        Assert.assertEquals(12345, agentConfig.getMetricsExporterConfiguration().getPort());
        Assert.assertEquals("exporter", agentConfig.getMetricsExporterConfiguration().getConfigDir());
        Assert.assertEquals("config.yaml", agentConfig.getMetricsExporterConfiguration().getConfigFile());

        Assert.assertEquals("http://hawkular:8181", agentConfig.getStorageAdapter().getUrl());
        Assert.assertEquals("the user", agentConfig.getStorageAdapter().getUsername());
        Assert.assertEquals("the pass", agentConfig.getStorageAdapter().getPassword());
        Assert.assertEquals("h-server", agentConfig.getStorageAdapter().getSecurityRealm());
        Assert.assertNull(agentConfig.getStorageAdapter().getKeystorePath());
        Assert.assertNull(agentConfig.getStorageAdapter().getKeystorePassword());
        Assert.assertEquals("the feed", agentConfig.getStorageAdapter().getFeedId());

        Assert.assertEquals(5, agentConfig.getDiagnostics().getInterval());

        EndpointConfiguration localDmr = agentConfig.getDmrConfiguration().getEndpoints().get("Test Local DMR");
        Assert.assertEquals(true, localDmr.isEnabled());
        Assert.assertEquals(2, localDmr.getWaitForResources().size());
        Assert.assertEquals("/subsystem=undertow", localDmr.getWaitForResources().get(0).getResource());
        Assert.assertEquals("/", localDmr.getWaitForResources().get(1).getResource());

        EndpointConfiguration localJmx = agentConfig.getJmxConfiguration().getEndpoints().get("Test Local JMX");
        Assert.assertEquals(true, localJmx.isEnabled());
        Assert.assertEquals(2, localJmx.getWaitForResources().size());
        Assert.assertEquals("java.lang:type=Runtime", localJmx.getWaitForResources().get(0).getResource());
        Assert.assertEquals("java.lang:type=Memory", localJmx.getWaitForResources().get(1).getResource());

        EndpointConfiguration remoteDmr = agentConfig.getDmrConfiguration().getEndpoints().get("Test Remote DMR");
        Assert.assertEquals(true, remoteDmr.isEnabled());
        Assert.assertEquals(1, remoteDmr.getWaitForResources().size());
        Assert.assertEquals("/subsystem=undertow", remoteDmr.getWaitForResources().get(0).getResource());

        EndpointConfiguration remoteJmx = agentConfig.getJmxConfiguration().getEndpoints().get("Test Remote JMX");
        Assert.assertEquals(true, remoteJmx.isEnabled());
        Assert.assertEquals(1, remoteJmx.getWaitForResources().size());
        Assert.assertEquals("java.lang:type=Runtime", remoteJmx.getWaitForResources().get(0).getResource());

        EndpointConfiguration remoteDmr2 = agentConfig.getDmrConfiguration().getEndpoints().get("Test Remote DMR 2");
        Assert.assertEquals(true, remoteDmr2.isEnabled());
        Assert.assertEquals(0, remoteDmr2.getWaitForResources().size());

        EndpointConfiguration remoteJmx2 = agentConfig.getJmxConfiguration().getEndpoints().get("Test Remote JMX 2");
        Assert.assertEquals(true, remoteJmx2.isEnabled());
        Assert.assertEquals(0, remoteJmx2.getWaitForResources().size());

        EndpointConfiguration platform = agentConfig.getPlatformConfiguration().getEndpoints().get("platform");
        Assert.assertEquals(true, platform.isEnabled());
    }

    @Test
    public void testBadNotification() throws Exception {
        try {
            loadTestConfigFile("/bad-notif.yaml");
            Assert.fail("Should have failed due to a bad notification name");
        } catch (Exception ok) {
        }
    }

    private Configuration loadTestConfigFile(String path) throws Exception {
        URL url = ConfigConverterTest.class.getResource(path);
        Assert.assertNotNull("yaml config file not found", url);
        File file = new File(url.toURI());
        ConfigManager configManager = new ConfigManager(file);
        return configManager.getConfiguration(false);
    }
}
