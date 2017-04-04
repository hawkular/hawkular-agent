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
import org.hawkular.agent.monitor.config.AgentCoreEngineConfiguration.DiagnosticsReportTo;
import org.hawkular.agent.monitor.config.AgentCoreEngineConfiguration.EndpointConfiguration;
import org.hawkular.agent.monitor.config.AgentCoreEngineConfiguration.StorageReportTo;
import org.junit.Assert;
import org.junit.Test;

public class ConfigConverterTest {

    @Test
    public void testEmpty() throws Exception {
        Configuration config = loadTestConfigFile("/empty.yaml");
        AgentCoreEngineConfiguration agentConfig = new ConfigConverter(config).convert();
        Assert.assertFalse(agentConfig.getGlobalConfiguration().isSubsystemEnabled());
    }

    @Test
    public void testRealConfig() throws Exception {
        Configuration config = loadTestConfigFile("/real-config.yaml");
        AgentCoreEngineConfiguration agentConfig = new ConfigConverter(config).convert();
        Assert.assertTrue(agentConfig.getGlobalConfiguration().isSubsystemEnabled());
    }

    @Test
    public void testRealConfigEAP6() throws Exception {
        Configuration config = loadTestConfigFile("/real-config-eap6.yaml");
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
        Assert.assertEquals(222, agentConfig.getGlobalConfiguration().getMinCollectionIntervalSeconds());
        Assert.assertEquals(333, agentConfig.getGlobalConfiguration().getPingDispatcherPeriodSeconds());

        Assert.assertEquals(StorageReportTo.METRICS, agentConfig.getStorageAdapter().getType());
        Assert.assertEquals("http://hawkular:8181", agentConfig.getStorageAdapter().getUrl());
        Assert.assertEquals("custom tenant", agentConfig.getStorageAdapter().getTenantId());
        Assert.assertEquals("the user", agentConfig.getStorageAdapter().getUsername());
        Assert.assertEquals("the pass", agentConfig.getStorageAdapter().getPassword());
        Assert.assertEquals("h-server", agentConfig.getStorageAdapter().getSecurityRealm());
        Assert.assertNull(agentConfig.getStorageAdapter().getKeystorePath());
        Assert.assertNull(agentConfig.getStorageAdapter().getKeystorePassword());
        Assert.assertEquals("the feed", agentConfig.getStorageAdapter().getFeedId());

        Assert.assertEquals(DiagnosticsReportTo.LOG, agentConfig.getDiagnostics().getReportTo());
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

    private Configuration loadTestConfigFile(String path) throws Exception {
        URL url = ConfigConverterTest.class.getResource(path);
        Assert.assertNotNull("yaml config file not found", url);
        File file = new File(url.toURI());
        ConfigManager configManager = new ConfigManager(file);
        return configManager.getConfiguration(false);
    }
}
