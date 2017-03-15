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
import java.io.FileNotFoundException;
import java.net.URISyntaxException;
import java.net.URL;

import org.hawkular.agent.monitor.api.Avail;
import org.hawkular.metrics.client.common.MetricType;
import org.jboss.as.controller.client.helpers.MeasurementUnit;
import org.junit.Assert;
import org.junit.Test;

public class ConfigManagerTest {

    // this is useful to see what the yaml looks like. Helpful for developing more than testing.
    @Test
    public void testWrite() throws Exception {
        Configuration config = new Configuration();
        config.subsystem = new Subsystem();
        config.diagnostics = new Diagnostics();
        config.storageAdapter = new StorageAdapter();
        config.platform = new Platform();

        // SECURITY REALMS
        config.securityRealms = new SecurityRealm[1];
        config.securityRealms[0] = new SecurityRealm();
        config.securityRealms[0].name = "first security realm";
        config.securityRealms[0].keystorePath = "/first/security/realm.truststore";
        config.securityRealms[0].keystorePassword = "first password";

        // DMR

        config.dmrMetricSets = new DMRMetricSet[2];
        config.dmrMetricSets[0] = new DMRMetricSet();
        config.dmrMetricSets[0].name = "dmr metric set 0";
        config.dmrMetricSets[0].dmrMetrics = new DMRMetric[2];
        config.dmrMetricSets[0].dmrMetrics[0] = new DMRMetric();
        config.dmrMetricSets[0].dmrMetrics[0].name = "dmr metric 0";
        config.dmrMetricSets[0].dmrMetrics[1] = new DMRMetric();
        config.dmrMetricSets[0].dmrMetrics[1].name = "dmr metric 1";
        config.dmrMetricSets[1] = new DMRMetricSet();
        config.dmrMetricSets[1].name = "dmr metric set 1";
        config.dmrMetricSets[1].dmrMetrics = new DMRMetric[2];
        config.dmrMetricSets[1].dmrMetrics[0] = new DMRMetric();
        config.dmrMetricSets[1].dmrMetrics[0].name = "dmr metric 2";
        config.dmrMetricSets[1].dmrMetrics[1] = new DMRMetric();
        config.dmrMetricSets[1].dmrMetrics[1].name = "dmr metric 3";

        config.dmrAvailSets = new DMRAvailSet[2];
        config.dmrAvailSets[0] = new DMRAvailSet();
        config.dmrAvailSets[0].name = "dmr avail set 0";
        config.dmrAvailSets[0].dmrAvails = new DMRAvail[2];
        config.dmrAvailSets[0].dmrAvails[0] = new DMRAvail();
        config.dmrAvailSets[0].dmrAvails[0].name = "dmr avail 0";
        config.dmrAvailSets[0].dmrAvails[1] = new DMRAvail();
        config.dmrAvailSets[0].dmrAvails[1].name = "dmr avail 1";
        config.dmrAvailSets[1] = new DMRAvailSet();
        config.dmrAvailSets[1].name = "dmr avail set 1";
        config.dmrAvailSets[1].dmrAvails = new DMRAvail[2];
        config.dmrAvailSets[1].dmrAvails[0] = new DMRAvail();
        config.dmrAvailSets[1].dmrAvails[0].name = "dmr avail 2";
        config.dmrAvailSets[1].dmrAvails[1] = new DMRAvail();
        config.dmrAvailSets[1].dmrAvails[1].name = "dmr avail 3";

        config.dmrResourceTypeSets = new DMRResourceTypeSet[2];
        config.dmrResourceTypeSets[0] = new DMRResourceTypeSet();
        config.dmrResourceTypeSets[0].name = "dmr resource type set 0";
        config.dmrResourceTypeSets[0].dmrResourceTypes = new DMRResourceType[2];
        config.dmrResourceTypeSets[0].dmrResourceTypes[0] = new DMRResourceType();
        config.dmrResourceTypeSets[0].dmrResourceTypes[0].name = "dmr resource type 0";
        config.dmrResourceTypeSets[0].dmrResourceTypes[0].dmrResourceConfigs = new DMRResourceConfig[1];
        config.dmrResourceTypeSets[0].dmrResourceTypes[0].dmrResourceConfigs[0] = new DMRResourceConfig();
        config.dmrResourceTypeSets[0].dmrResourceTypes[0].dmrResourceConfigs[0].name = "dmr res config 0";
        config.dmrResourceTypeSets[0].dmrResourceTypes[1] = new DMRResourceType();
        config.dmrResourceTypeSets[0].dmrResourceTypes[1].name = "dmr resource type 1";
        config.dmrResourceTypeSets[0].dmrResourceTypes[1].dmrResourceConfigs = new DMRResourceConfig[1];
        config.dmrResourceTypeSets[0].dmrResourceTypes[1].dmrResourceConfigs[0] = new DMRResourceConfig();
        config.dmrResourceTypeSets[0].dmrResourceTypes[1].dmrResourceConfigs[0].name = "dmr res config 1";
        config.dmrResourceTypeSets[1] = new DMRResourceTypeSet();
        config.dmrResourceTypeSets[1].name = "dmr resource type set 1";
        config.dmrResourceTypeSets[1].dmrResourceTypes = new DMRResourceType[2];
        config.dmrResourceTypeSets[1].dmrResourceTypes[0] = new DMRResourceType();
        config.dmrResourceTypeSets[1].dmrResourceTypes[0].name = "dmr resource type 2";
        config.dmrResourceTypeSets[1].dmrResourceTypes[1] = new DMRResourceType();
        config.dmrResourceTypeSets[1].dmrResourceTypes[1].name = "dmr resource type 3";

        // JMX

        config.jmxMetricSets = new JMXMetricSet[2];
        config.jmxMetricSets[0] = new JMXMetricSet();
        config.jmxMetricSets[0].name = "jmx metric set 0";
        config.jmxMetricSets[0].jmxMetrics = new JMXMetric[2];
        config.jmxMetricSets[0].jmxMetrics[0] = new JMXMetric();
        config.jmxMetricSets[0].jmxMetrics[0].name = "jmx metric 0";
        config.jmxMetricSets[0].jmxMetrics[1] = new JMXMetric();
        config.jmxMetricSets[0].jmxMetrics[1].name = "jmx metric 1";
        config.jmxMetricSets[1] = new JMXMetricSet();
        config.jmxMetricSets[1].name = "jmx metric set 1";
        config.jmxMetricSets[1].jmxMetrics = new JMXMetric[2];
        config.jmxMetricSets[1].jmxMetrics[0] = new JMXMetric();
        config.jmxMetricSets[1].jmxMetrics[0].name = "jmx metric 2";
        config.jmxMetricSets[1].jmxMetrics[1] = new JMXMetric();
        config.jmxMetricSets[1].jmxMetrics[1].name = "jmx metric 3";

        config.jmxAvailSets = new JMXAvailSet[2];
        config.jmxAvailSets[0] = new JMXAvailSet();
        config.jmxAvailSets[0].name = "jmx avail set 0";
        config.jmxAvailSets[0].jmxAvails = new JMXAvail[2];
        config.jmxAvailSets[0].jmxAvails[0] = new JMXAvail();
        config.jmxAvailSets[0].jmxAvails[0].name = "jmx avail 0";
        config.jmxAvailSets[0].jmxAvails[1] = new JMXAvail();
        config.jmxAvailSets[0].jmxAvails[1].name = "jmx avail 1";
        config.jmxAvailSets[1] = new JMXAvailSet();
        config.jmxAvailSets[1].name = "jmx avail set 1";
        config.jmxAvailSets[1].jmxAvails = new JMXAvail[2];
        config.jmxAvailSets[1].jmxAvails[0] = new JMXAvail();
        config.jmxAvailSets[1].jmxAvails[0].name = "jmx avail 2";
        config.jmxAvailSets[1].jmxAvails[1] = new JMXAvail();
        config.jmxAvailSets[1].jmxAvails[1].name = "jmx avail 3";

        config.jmxResourceTypeSets = new JMXResourceTypeSet[2];
        config.jmxResourceTypeSets[0] = new JMXResourceTypeSet();
        config.jmxResourceTypeSets[0].name = "jmx resource type set 0";
        config.jmxResourceTypeSets[0].jmxResourceTypes = new JMXResourceType[2];
        config.jmxResourceTypeSets[0].jmxResourceTypes[0] = new JMXResourceType();
        config.jmxResourceTypeSets[0].jmxResourceTypes[0].name = "jmx resource type 0";
        config.jmxResourceTypeSets[0].jmxResourceTypes[0].jmxResourceConfigs = new JMXResourceConfig[1];
        config.jmxResourceTypeSets[0].jmxResourceTypes[0].jmxResourceConfigs[0] = new JMXResourceConfig();
        config.jmxResourceTypeSets[0].jmxResourceTypes[0].jmxResourceConfigs[0].name = "jmx res config 0";
        config.jmxResourceTypeSets[0].jmxResourceTypes[1] = new JMXResourceType();
        config.jmxResourceTypeSets[0].jmxResourceTypes[1].name = "jmx resource type 1";
        config.jmxResourceTypeSets[0].jmxResourceTypes[1].jmxResourceConfigs = new JMXResourceConfig[1];
        config.jmxResourceTypeSets[0].jmxResourceTypes[1].jmxResourceConfigs[0] = new JMXResourceConfig();
        config.jmxResourceTypeSets[0].jmxResourceTypes[1].jmxResourceConfigs[0].name = "jmx res config 1";
        config.jmxResourceTypeSets[1] = new JMXResourceTypeSet();
        config.jmxResourceTypeSets[1].name = "jmx resource type set 1";
        config.jmxResourceTypeSets[1].jmxResourceTypes = new JMXResourceType[2];
        config.jmxResourceTypeSets[1].jmxResourceTypes[0] = new JMXResourceType();
        config.jmxResourceTypeSets[1].jmxResourceTypes[0].name = "jmx resource type 2";
        config.jmxResourceTypeSets[1].jmxResourceTypes[1] = new JMXResourceType();
        config.jmxResourceTypeSets[1].jmxResourceTypes[1].name = "jmx resource type 3";

        config.managedServers = new ManagedServers();
        config.managedServers.localDmr = new LocalDMR();
        config.managedServers.localDmr.name = "Local DMR";
        config.managedServers.localDmr.resourceTypeSets = new String[] { "dmr resource type set 0",
                "dmr resource type set 1" };
        config.managedServers.localJmx = new LocalJMX();
        config.managedServers.localJmx.name = "Local JMX";
        config.managedServers.localJmx.resourceTypeSets = new String[] { "jmx resource type set 0",
                "jmx resource type set 1" };
        config.managedServers.remoteDmrs = new RemoteDMR[1];
        config.managedServers.remoteDmrs[0] = new RemoteDMR();
        config.managedServers.remoteDmrs[0].name = "Remote DMR";
        config.managedServers.remoteDmrs[0].resourceTypeSets = new String[] { "dmr resource type set 0",
                "dmr resource type set 1" };
        config.managedServers.remoteJmxs = new RemoteJMX[1];
        config.managedServers.remoteJmxs[0] = new RemoteJMX();
        config.managedServers.remoteJmxs[0].name = "Remote JMX";
        config.managedServers.remoteJmxs[0].resourceTypeSets = new String[] { "jmx resource type set 0",
                "jmx resource type set 1" };

        // write the config out
        File file = new File("/tmp/org.hawkular.agent.javaagent.config.ConfigManagerTest.yaml");
        ConfigManager configManager = new ConfigManager(file);
        configManager.updateConfiguration(config, false);
    }

    @Test
    public void testFullConfigDmr() throws Exception {
        File file = loadTestConfigFile("/test-config.yaml");
        ConfigManager configManager = new ConfigManager(file);
        Configuration config = configManager.getConfiguration(false);
        Assert.assertTrue(configManager.hasConfiguration());

        Assert.assertEquals(2, config.dmrMetricSets.length);
        Assert.assertEquals(2, config.dmrAvailSets.length);
        Assert.assertEquals(2, config.dmrResourceTypeSets.length);

        Assert.assertEquals(2, config.dmrMetricSets[0].dmrMetrics.length);
        Assert.assertEquals(2, config.dmrAvailSets[0].dmrAvails.length);
        Assert.assertEquals(2, config.dmrResourceTypeSets[0].dmrResourceTypes.length);

        Assert.assertEquals(2, config.dmrMetricSets[1].dmrMetrics.length);
        Assert.assertEquals(2, config.dmrAvailSets[1].dmrAvails.length);
        Assert.assertEquals(2, config.dmrResourceTypeSets[1].dmrResourceTypes.length);

        Assert.assertEquals("first metric set d", config.dmrMetricSets[0].name);
        Assert.assertEquals("second metric set d", config.dmrMetricSets[1].name);

        Assert.assertEquals("first metric d", config.dmrMetricSets[0].dmrMetrics[0].name);
        Assert.assertEquals("second metric d", config.dmrMetricSets[0].dmrMetrics[1].name);
        Assert.assertEquals("third metric d", config.dmrMetricSets[1].dmrMetrics[0].name);
        Assert.assertEquals("fourth metric d", config.dmrMetricSets[1].dmrMetrics[1].name);

        Assert.assertEquals("first avail set d", config.dmrAvailSets[0].name);
        Assert.assertEquals("second avail set d", config.dmrAvailSets[1].name);

        Assert.assertEquals("first avail d", config.dmrAvailSets[0].dmrAvails[0].name);
        Assert.assertEquals("second avail d", config.dmrAvailSets[0].dmrAvails[1].name);
        Assert.assertEquals("third avail d", config.dmrAvailSets[1].dmrAvails[0].name);
        Assert.assertEquals("fourth avail d", config.dmrAvailSets[1].dmrAvails[1].name);

        Assert.assertEquals("first resource type set d", config.dmrResourceTypeSets[0].name);
        Assert.assertEquals("second resource type set d", config.dmrResourceTypeSets[1].name);

        Assert.assertEquals("first resource type d", config.dmrResourceTypeSets[0].dmrResourceTypes[0].name);
        Assert.assertEquals("second resource type d", config.dmrResourceTypeSets[0].dmrResourceTypes[1].name);
        Assert.assertEquals("third resource type d", config.dmrResourceTypeSets[1].dmrResourceTypes[0].name);
        Assert.assertEquals("fourth resource type d", config.dmrResourceTypeSets[1].dmrResourceTypes[1].name);

        Assert.assertEquals("/metric=one", config.dmrMetricSets[0].dmrMetrics[0].path);
        Assert.assertEquals("attrib1", config.dmrMetricSets[0].dmrMetrics[0].attribute);
        Assert.assertEquals(12345, config.dmrMetricSets[0].dmrMetrics[0].interval.intValue());
        Assert.assertEquals(TimeUnits.milliseconds, config.dmrMetricSets[0].dmrMetrics[0].timeUnits);
        Assert.assertEquals(MeasurementUnit.MEGABYTES, config.dmrMetricSets[0].dmrMetrics[0].metricUnits);
        Assert.assertEquals(MetricType.COUNTER, config.dmrMetricSets[0].dmrMetrics[0].metricType);
        Assert.assertEquals("the template", config.dmrMetricSets[0].dmrMetrics[0].metricIdTemplate);
        Assert.assertEquals("{tag1=value1, tag2=value2}", config.dmrMetricSets[0].dmrMetrics[0].metricTags.toString());

        Assert.assertEquals(2, config.dmrResourceTypeSets[0].dmrResourceTypes[0].dmrResourceConfigs.length);
        Assert.assertEquals("first resconfig d",
                config.dmrResourceTypeSets[0].dmrResourceTypes[0].dmrResourceConfigs[0].name);
        Assert.assertEquals("/",
                config.dmrResourceTypeSets[0].dmrResourceTypes[0].dmrResourceConfigs[0].path);
        Assert.assertEquals("attrib1#subattrib1",
                config.dmrResourceTypeSets[0].dmrResourceTypes[0].dmrResourceConfigs[0].attribute);
        Assert.assertEquals("second resconfig d",
                config.dmrResourceTypeSets[0].dmrResourceTypes[0].dmrResourceConfigs[1].name);
        Assert.assertEquals("/config=two",
                config.dmrResourceTypeSets[0].dmrResourceTypes[0].dmrResourceConfigs[1].path);
        Assert.assertEquals("attrib2#subattrib2",
                config.dmrResourceTypeSets[0].dmrResourceTypes[0].dmrResourceConfigs[1].attribute);

        Assert.assertEquals("Test Local DMR", config.managedServers.localDmr.name);
        Assert.assertEquals("first resource type set d", config.managedServers.localDmr.resourceTypeSets[0]);
        Assert.assertEquals("second resource type set d", config.managedServers.localDmr.resourceTypeSets[1]);
    }

    @Test
    public void testFullConfigJmx() throws Exception {
        File file = loadTestConfigFile("/test-config.yaml");
        ConfigManager configManager = new ConfigManager(file);
        Configuration config = configManager.getConfiguration(false);
        Assert.assertTrue(configManager.hasConfiguration());

        Assert.assertEquals(2, config.jmxMetricSets.length);
        Assert.assertEquals(2, config.jmxAvailSets.length);
        Assert.assertEquals(2, config.jmxResourceTypeSets.length);

        Assert.assertEquals(2, config.jmxMetricSets[0].jmxMetrics.length);
        Assert.assertEquals(2, config.jmxAvailSets[0].jmxAvails.length);
        Assert.assertEquals(2, config.jmxResourceTypeSets[0].jmxResourceTypes.length);

        Assert.assertEquals(2, config.jmxMetricSets[1].jmxMetrics.length);
        Assert.assertEquals(2, config.jmxAvailSets[1].jmxAvails.length);
        Assert.assertEquals(2, config.jmxResourceTypeSets[1].jmxResourceTypes.length);

        Assert.assertEquals("first metric set", config.jmxMetricSets[0].name);
        Assert.assertEquals("second metric set", config.jmxMetricSets[1].name);

        Assert.assertEquals("first metric", config.jmxMetricSets[0].jmxMetrics[0].name);
        Assert.assertEquals("second metric", config.jmxMetricSets[0].jmxMetrics[1].name);
        Assert.assertEquals("third metric", config.jmxMetricSets[1].jmxMetrics[0].name);
        Assert.assertEquals("fourth metric", config.jmxMetricSets[1].jmxMetrics[1].name);

        Assert.assertEquals("first avail set", config.jmxAvailSets[0].name);
        Assert.assertEquals("second avail set", config.jmxAvailSets[1].name);

        Assert.assertEquals("first avail", config.jmxAvailSets[0].jmxAvails[0].name);
        Assert.assertEquals("second avail", config.jmxAvailSets[0].jmxAvails[1].name);
        Assert.assertEquals("third avail", config.jmxAvailSets[1].jmxAvails[0].name);
        Assert.assertEquals("fourth avail", config.jmxAvailSets[1].jmxAvails[1].name);

        Assert.assertEquals("first resource type set", config.jmxResourceTypeSets[0].name);
        Assert.assertEquals("second resource type set", config.jmxResourceTypeSets[1].name);

        Assert.assertEquals("first resource type", config.jmxResourceTypeSets[0].jmxResourceTypes[0].name);
        Assert.assertEquals("second resource type", config.jmxResourceTypeSets[0].jmxResourceTypes[1].name);
        Assert.assertEquals("third resource type", config.jmxResourceTypeSets[1].jmxResourceTypes[0].name);
        Assert.assertEquals("fourth resource type", config.jmxResourceTypeSets[1].jmxResourceTypes[1].name);

        Assert.assertEquals("domain:metric=one", config.jmxMetricSets[0].jmxMetrics[0].objectName);
        Assert.assertEquals("attrib1", config.jmxMetricSets[0].jmxMetrics[0].attribute);
        Assert.assertEquals(12345, config.jmxMetricSets[0].jmxMetrics[0].interval.intValue());
        Assert.assertEquals(TimeUnits.seconds, config.jmxMetricSets[0].jmxMetrics[0].timeUnits);
        Assert.assertEquals(MeasurementUnit.BYTES, config.jmxMetricSets[0].jmxMetrics[0].metricUnits);
        Assert.assertEquals(MetricType.GAUGE, config.jmxMetricSets[0].jmxMetrics[0].metricType);
        Assert.assertEquals("the template", config.jmxMetricSets[0].jmxMetrics[0].metricIdTemplate);
        Assert.assertEquals("{tag1=value1, tag2=value2}", config.jmxMetricSets[0].jmxMetrics[0].metricTags.toString());

        Assert.assertEquals(2, config.jmxResourceTypeSets[0].jmxResourceTypes[0].jmxResourceConfigs.length);
        Assert.assertEquals("first resconfig",
                config.jmxResourceTypeSets[0].jmxResourceTypes[0].jmxResourceConfigs[0].name);
        Assert.assertNull(config.jmxResourceTypeSets[0].jmxResourceTypes[0].jmxResourceConfigs[0].objectName);
        Assert.assertEquals("attrib1#subattrib1",
                config.jmxResourceTypeSets[0].jmxResourceTypes[0].jmxResourceConfigs[0].attribute);
        Assert.assertEquals("second resconfig",
                config.jmxResourceTypeSets[0].jmxResourceTypes[0].jmxResourceConfigs[1].name);
        Assert.assertEquals("domain:type=two",
                config.jmxResourceTypeSets[0].jmxResourceTypes[0].jmxResourceConfigs[1].objectName);
        Assert.assertEquals("attrib2#subattrib2",
                config.jmxResourceTypeSets[0].jmxResourceTypes[0].jmxResourceConfigs[1].attribute);

        Assert.assertEquals("Test Local JMX", config.managedServers.localJmx.name);
        Assert.assertEquals("first resource type set", config.managedServers.localJmx.resourceTypeSets[0]);
        Assert.assertEquals("second resource type set", config.managedServers.localJmx.resourceTypeSets[1]);
    }

    @Test
    public void testDefaults() {
        // subsystem
        Subsystem s = new Subsystem();
        Assert.assertEquals(true, s.enabled);
        Assert.assertEquals(Integer.valueOf(600), s.autoDiscoveryScanPeriodSecs);

        // storage adapter
        StorageAdapter sa = new StorageAdapter();
        Assert.assertEquals(StorageAdapter.Type.HAWKULAR, sa.type);
        Assert.assertEquals("hawkular", sa.tenantId);
        Assert.assertEquals("/hawkular/inventory/", sa.inventoryContext);
        Assert.assertEquals("/hawkular/metrics/", sa.metricsContext);
        Assert.assertEquals("/hawkular/command-gateway/", sa.feedcommContext);
        Assert.assertEquals(10, sa.connectTimeoutSecs.intValue());
        Assert.assertEquals(120, sa.readTimeoutSecs.intValue());

        // platform
        Platform p = new Platform();
        Assert.assertEquals(false, p.enabled);
        Assert.assertEquals(5, p.interval.intValue());
        Assert.assertEquals(TimeUnits.minutes, p.timeUnits);

        Assert.assertEquals(true, p.memory.enabled);
        Assert.assertEquals(5, p.memory.interval.intValue());
        Assert.assertEquals(TimeUnits.minutes, p.memory.timeUnits);

        Assert.assertEquals(true, p.fileStores.enabled);
        Assert.assertEquals(5, p.fileStores.interval.intValue());
        Assert.assertEquals(TimeUnits.minutes, p.fileStores.timeUnits);

        Assert.assertEquals(true, p.processors.enabled);
        Assert.assertEquals(5, p.processors.interval.intValue());
        Assert.assertEquals(TimeUnits.minutes, p.processors.timeUnits);

        Assert.assertEquals(false, p.powerSources.enabled);
        Assert.assertEquals(5, p.powerSources.interval.intValue());
        Assert.assertEquals(TimeUnits.minutes, p.powerSources.timeUnits);

        // managed servers
        LocalDMR ldmr = new LocalDMR();
        Assert.assertEquals(true, ldmr.enabled);

        LocalJMX ljmx = new LocalJMX();
        Assert.assertEquals(true, ljmx.enabled);
    }

    @Test
    public void testLoadFailure() throws Exception {
        ConfigManager cm = new ConfigManager(new File("/bogus/file/name.boo"));
        try {
            cm.getConfiguration(false);
            Assert.fail("File was invalid - exception should have been thrown");
        } catch (FileNotFoundException expected) {
        }
        Assert.assertFalse(cm.hasConfiguration());
    }

    @Test
    public void testLoadSimple() throws Exception {
        File file = loadTestConfigFile("/simple.yaml");
        ConfigManager configManager = new ConfigManager(file);
        Assert.assertFalse(configManager.hasConfiguration());
        Configuration config = configManager.getConfiguration(false);
        Assert.assertTrue(configManager.hasConfiguration());
        assertSimpleConfig(config, "jdoe");
        config.validate();
    }

    @Test
    public void testUpdate() throws Exception {
        File file = loadTestConfigFile("/simple.yaml");
        ConfigManager configManager = new ConfigManager(file);
        Configuration config = configManager.getConfiguration(false);
        assertSimpleConfig(config, "jdoe");

        config.storageAdapter.username = "CHANGE";
        configManager.updateConfiguration(config, true);
        assertSimpleConfig(configManager.getConfiguration(false), "CHANGE");

        // see that it really made it to the file
        config = configManager.getConfiguration(true);
        assertSimpleConfig(config, "CHANGE");

        // see that a backup was created
        ConfigManager backupConfigManager = new ConfigManager(new File(file.getAbsolutePath() + ".bak"));
        config = backupConfigManager.getConfiguration(false);
        assertSimpleConfig(config, "jdoe");

        // put the original back the way it was
        backupConfigManager.getConfigFile().delete(); // remove the backup
        configManager.updateConfiguration(config, false); // don't backup this time
        Assert.assertFalse(backupConfigManager.getConfigFile().exists());
    }

    private File loadTestConfigFile(String path) throws URISyntaxException {
        URL url = ConfigManagerTest.class.getResource(path);
        Assert.assertNotNull("yaml config file not found", url);
        File file = new File(url.toURI());
        return file;
    }

    private void assertSimpleConfig(Configuration config, String expectedStorageAdapterUsername) {
        Assert.assertNotNull("yaml config was null", config);

        // subsystem
        Assert.assertEquals(true, config.subsystem.enabled);
        Assert.assertEquals(Integer.valueOf(600), config.subsystem.autoDiscoveryScanPeriodSecs);

        // security-realm
        SecurityRealm securityRealm = config.securityRealms[0];
        Assert.assertEquals("h-server-security-realm", securityRealm.name);
        Assert.assertEquals("/my/keystore/path", securityRealm.keystorePath);
        Assert.assertEquals("my keystore password", securityRealm.keystorePassword);
        securityRealm = config.securityRealms[1];
        Assert.assertEquals("some-truststore", securityRealm.name);
        Assert.assertEquals("/my/truststore/path", securityRealm.keystorePath);
        Assert.assertEquals("my truststore password", securityRealm.keystorePassword);

        // storage-adapter
        Assert.assertEquals(StorageAdapter.Type.HAWKULAR, config.storageAdapter.type);
        Assert.assertEquals("http://127.0.0.1:8080", config.storageAdapter.url);
        Assert.assertEquals("hawkular", config.storageAdapter.tenantId);
        Assert.assertEquals(expectedStorageAdapterUsername, config.storageAdapter.username);
        Assert.assertEquals("password", config.storageAdapter.password);
        Assert.assertEquals("autogenerate", config.storageAdapter.feedId);
        Assert.assertEquals("h-server-security-realm", config.storageAdapter.securityRealmName);
        Assert.assertEquals("/my-inventory", config.storageAdapter.inventoryContext);
        Assert.assertEquals("/my-metrics", config.storageAdapter.metricsContext);
        Assert.assertEquals("/my-feedcomm", config.storageAdapter.feedcommContext);
        Assert.assertEquals(Integer.valueOf(123), config.storageAdapter.connectTimeoutSecs);
        Assert.assertEquals(Integer.valueOf(456), config.storageAdapter.readTimeoutSecs);

        // managed-servers - local dmr
        LocalDMR ldmr = config.managedServers.localDmr;
        Assert.assertEquals("Local WildFly", ldmr.name);
        Assert.assertEquals(Boolean.FALSE, ldmr.enabled);
        Assert.assertEquals("local wildfly tenant", ldmr.tenantId);
        Assert.assertEquals("LocalTypeSet1", ldmr.resourceTypeSets[0]);
        Assert.assertEquals("TypeSet2", ldmr.resourceTypeSets[1]);
        Assert.assertEquals("local feed id is %FeedId and metric name is %MetricName", ldmr.metricIdTemplate);
        Assert.assertEquals(2, ldmr.metricTags.size());
        Assert.assertEquals("val1", ldmr.metricTags.get("localdmrtag1"));
        Assert.assertEquals("val2", ldmr.metricTags.get("dmrtag2"));
        Assert.assertEquals(Avail.DOWN, ldmr.setAvailOnShutdown);

        // managed-servers - local jmx
        LocalJMX ljmx = config.managedServers.localJmx;
        Assert.assertEquals("Local JMX", ljmx.name);
        Assert.assertEquals(Boolean.FALSE, ljmx.enabled);
        Assert.assertEquals("jmx local wildfly tenant", ljmx.tenantId);
        Assert.assertEquals("jmx LocalTypeSet1", ljmx.resourceTypeSets[0]);
        Assert.assertEquals("jmx TypeSet2", ljmx.resourceTypeSets[1]);
        Assert.assertEquals("jmx local feed id is %FeedId and metric name is %MetricName", ljmx.metricIdTemplate);
        Assert.assertEquals(2, ljmx.metricTags.size());
        Assert.assertEquals("val1", ljmx.metricTags.get("localjmxtag1"));
        Assert.assertEquals("val2", ljmx.metricTags.get("jmxtag2"));
        Assert.assertEquals(Avail.UNKNOWN, ljmx.setAvailOnShutdown);
        Assert.assertEquals("some-mbs-name", ljmx.mbeanServerName);

        // managed-servers - remote dmr
        RemoteDMR rdmr = config.managedServers.remoteDmrs[0];
        Assert.assertEquals("Remote WildFly", rdmr.name);
        Assert.assertEquals(Boolean.FALSE, rdmr.enabled);
        Assert.assertEquals("remote wildfly tenant", rdmr.tenantId);
        Assert.assertEquals("RemoteTypeSet1", rdmr.resourceTypeSets[0]);
        Assert.assertEquals("TypeSet2", rdmr.resourceTypeSets[1]);
        Assert.assertEquals("remote feed id is %FeedId and metric name is %MetricName", rdmr.metricIdTemplate);
        Assert.assertEquals(2, rdmr.metricTags.size());
        Assert.assertEquals("val1", rdmr.metricTags.get("remotedmrtag1"));
        Assert.assertEquals("val2", rdmr.metricTags.get("dmrtag2"));
        Assert.assertEquals(Avail.DOWN, rdmr.setAvailOnShutdown);

        // managed-servers - remote jmx
        RemoteJMX rjmx = config.managedServers.remoteJmxs[0];
        Assert.assertEquals("Remote JMX", rjmx.name);
        Assert.assertEquals(Boolean.FALSE, rjmx.enabled);
        Assert.assertEquals("jmx remote wildfly tenant", rjmx.tenantId);
        Assert.assertEquals("jmx RemoteTypeSet1", rjmx.resourceTypeSets[0]);
        Assert.assertEquals("jmx TypeSet2", rjmx.resourceTypeSets[1]);
        Assert.assertEquals("jmx remote feed id is %FeedId and metric name is %MetricName", rjmx.metricIdTemplate);
        Assert.assertEquals(2, rjmx.metricTags.size());
        Assert.assertEquals("val1", rjmx.metricTags.get("remotejmxtag1"));
        Assert.assertEquals("val2", rjmx.metricTags.get("jmxtag2"));
        Assert.assertEquals(Avail.UNKNOWN, rjmx.setAvailOnShutdown);

        // platform
        Assert.assertEquals(true, config.platform.enabled);
        Assert.assertEquals(Integer.valueOf(1234), config.platform.interval);
        Assert.assertEquals(TimeUnits.seconds, config.platform.timeUnits);
        Assert.assertEquals("my-machine-id-here", config.platform.machineId);
        Assert.assertEquals(Boolean.TRUE, config.platform.fileStores.enabled);
        Assert.assertEquals(Integer.valueOf(5000), config.platform.fileStores.interval);
        Assert.assertEquals(TimeUnits.milliseconds, config.platform.fileStores.timeUnits);
        Assert.assertEquals(Boolean.TRUE, config.platform.memory.enabled);
        Assert.assertEquals(Integer.valueOf(30), config.platform.memory.interval);
        Assert.assertEquals(TimeUnits.seconds, config.platform.memory.timeUnits);
        Assert.assertEquals(Boolean.TRUE, config.platform.processors.enabled);
        Assert.assertEquals(Integer.valueOf(1), config.platform.processors.interval);
        Assert.assertEquals(TimeUnits.minutes, config.platform.processors.timeUnits);
        Assert.assertEquals(Boolean.FALSE, config.platform.powerSources.enabled);
        Assert.assertEquals(Integer.valueOf(5), config.platform.powerSources.interval);
        Assert.assertEquals(TimeUnits.minutes, config.platform.powerSources.timeUnits);
    }
}
