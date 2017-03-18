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

import org.junit.Assert;
import org.junit.Test;

/**
 * This tests the copy constructors in all the YAML POJOs.
 */
public class ConfigCopyTest {

    @Test
    public void testEmpty() throws Exception {
        Configuration config = loadTestConfigFile("/empty.yaml");
        Configuration clone = new Configuration(config);
        Assert.assertEquals(clone.getSubsystem().getEnabled(), config.getSubsystem().getEnabled());
    }

    @Test
    public void testRealConfig() throws Exception {
        Configuration config = loadTestConfigFile("/real-config.yaml");
        Configuration clone = new Configuration(config);
        Assert.assertEquals(clone.getSubsystem().getEnabled(), config.getSubsystem().getEnabled());
        Assert.assertEquals(clone.getStorageAdapter().getUrl(), config.getStorageAdapter().getUrl());
        Assert.assertEquals(clone.getDiagnostics().getEnabled(), config.getDiagnostics().getEnabled());
        Assert.assertEquals(clone.getDmrMetricSets().length, config.getDmrMetricSets().length);
        Assert.assertEquals(clone.getDmrAvailSets().length, config.getDmrAvailSets().length);
        Assert.assertEquals(clone.getDmrResourceTypeSets().length, config.getDmrResourceTypeSets().length);
        Assert.assertEquals(clone.getJmxMetricSets().length, config.getJmxMetricSets().length);
        Assert.assertEquals(clone.getJmxAvailSets().length, config.getJmxAvailSets().length);
        Assert.assertEquals(clone.getJmxResourceTypeSets().length, config.getJmxResourceTypeSets().length);
        Assert.assertEquals(clone.getManagedServers().getLocalDmr().getName(),
                config.getManagedServers().getLocalDmr().getName());
        Assert.assertEquals(clone.getManagedServers().getLocalJmx().getName(),
                config.getManagedServers().getLocalJmx().getName());
        Assert.assertEquals(clone.getManagedServers().getRemoteDmrs()[0].getName(),
                config.getManagedServers().getRemoteDmrs()[0].getName());
        Assert.assertEquals(clone.getManagedServers().getRemoteJmxs()[0].getName(),
                config.getManagedServers().getRemoteJmxs()[0].getName());
        Assert.assertEquals(clone.getPlatform().getEnabled(), config.getPlatform().getEnabled());
        Assert.assertEquals(clone.getPlatform().getFileStores().getEnabled(),
                config.getPlatform().getFileStores().getEnabled());
        Assert.assertEquals(clone.getPlatform().getMemory().getEnabled(),
                config.getPlatform().getMemory().getEnabled());
        Assert.assertEquals(clone.getPlatform().getProcessors().getEnabled(),
                config.getPlatform().getProcessors().getEnabled());
        Assert.assertEquals(clone.getPlatform().getPowerSources().getEnabled(),
                config.getPlatform().getPowerSources().getEnabled());
    }

    @Test
    public void testConvertConfig() throws Exception {
        Configuration config = loadTestConfigFile("/test-convert.yaml");
        Configuration clone = new Configuration(config);
        Assert.assertEquals(clone.getSubsystem().getEnabled(), config.getSubsystem().getEnabled());
        Assert.assertEquals(clone.getStorageAdapter().getUrl(), config.getStorageAdapter().getUrl());
        Assert.assertEquals(clone.getDiagnostics().getEnabled(), config.getDiagnostics().getEnabled());
        Assert.assertEquals(clone.getDmrMetricSets().length, config.getDmrMetricSets().length);
        Assert.assertEquals(clone.getDmrAvailSets().length, config.getDmrAvailSets().length);
        Assert.assertEquals(clone.getDmrResourceTypeSets().length, config.getDmrResourceTypeSets().length);
        Assert.assertEquals(clone.getJmxMetricSets().length, config.getJmxMetricSets().length);
        Assert.assertEquals(clone.getJmxAvailSets().length, config.getJmxAvailSets().length);
        Assert.assertEquals(clone.getJmxResourceTypeSets().length, config.getJmxResourceTypeSets().length);
        Assert.assertEquals(clone.getManagedServers().getLocalDmr().getName(),
                config.getManagedServers().getLocalDmr().getName());
        Assert.assertEquals(clone.getManagedServers().getLocalJmx().getName(),
                config.getManagedServers().getLocalJmx().getName());
        Assert.assertEquals(clone.getManagedServers().getRemoteDmrs()[0].getName(),
                config.getManagedServers().getRemoteDmrs()[0].getName());
        Assert.assertEquals(clone.getManagedServers().getRemoteJmxs()[0].getName(),
                config.getManagedServers().getRemoteJmxs()[0].getName());
        Assert.assertEquals(clone.getPlatform().getEnabled(), config.getPlatform().getEnabled());
        Assert.assertEquals(clone.getPlatform().getFileStores().getEnabled(),
                config.getPlatform().getFileStores().getEnabled());
        Assert.assertEquals(clone.getPlatform().getMemory().getEnabled(),
                config.getPlatform().getMemory().getEnabled());
        Assert.assertEquals(clone.getPlatform().getProcessors().getEnabled(),
                config.getPlatform().getProcessors().getEnabled());
        Assert.assertEquals(clone.getPlatform().getPowerSources().getEnabled(),
                config.getPlatform().getPowerSources().getEnabled());
    }

    private Configuration loadTestConfigFile(String path) throws Exception {
        URL url = ConfigCopyTest.class.getResource(path);
        Assert.assertNotNull("yaml config file not found", url);
        File file = new File(url.toURI());
        ConfigManager configManager = new ConfigManager(file);
        return configManager.getConfiguration(false);
    }
}
