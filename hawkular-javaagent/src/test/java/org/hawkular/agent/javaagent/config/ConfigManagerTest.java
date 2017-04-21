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
        config.setSubsystem(new Subsystem());
        config.setDiagnostics(new Diagnostics());
        config.setStorageAdapter(new StorageAdapter());
        config.setPlatform(new Platform());

        // SECURITY REALMS
        SecurityRealm[] securityRealms = new SecurityRealm[1];
        config.setSecurityRealms(securityRealms);
        securityRealms[0] = new SecurityRealm();
        securityRealms[0].setName("first security realm");
        securityRealms[0].setKeystorePath("/first/security/realm.truststore");
        securityRealms[0].setKeystorePassword("first password");

        // DMR

        DMRMetricSet[] dmrMetricSets = new DMRMetricSet[2];
        config.setDmrMetricSets(dmrMetricSets);
        dmrMetricSets[0] = new DMRMetricSet();
        dmrMetricSets[0].setName("dmr metric set 0");
        dmrMetricSets[0].setDmrMetrics(new DMRMetric[2]);
        dmrMetricSets[0].getDmrMetrics()[0] = new DMRMetric();
        dmrMetricSets[0].getDmrMetrics()[0].setName("dmr metric 0");
        dmrMetricSets[0].getDmrMetrics()[1] = new DMRMetric();
        dmrMetricSets[0].getDmrMetrics()[1].setName("dmr metric 1");
        dmrMetricSets[1] = new DMRMetricSet();
        dmrMetricSets[1].setName("dmr metric set 1");
        dmrMetricSets[1].setDmrMetrics(new DMRMetric[2]);
        dmrMetricSets[1].getDmrMetrics()[0] = new DMRMetric();
        dmrMetricSets[1].getDmrMetrics()[0].setName("dmr metric 2");
        dmrMetricSets[1].getDmrMetrics()[1] = new DMRMetric();
        dmrMetricSets[1].getDmrMetrics()[1].setName("dmr metric 3");

        DMRAvailSet[] dmrAvailSets = new DMRAvailSet[2];
        config.setDmrAvailSets(dmrAvailSets);
        dmrAvailSets[0] = new DMRAvailSet();
        dmrAvailSets[0].setName("dmr avail set 0");
        dmrAvailSets[0].setDmrAvails(new DMRAvail[2]);
        dmrAvailSets[0].getDmrAvails()[0] = new DMRAvail();
        dmrAvailSets[0].getDmrAvails()[0].setName("dmr avail 0");
        dmrAvailSets[0].getDmrAvails()[1] = new DMRAvail();
        dmrAvailSets[0].getDmrAvails()[1].setName("dmr avail 1");
        dmrAvailSets[1] = new DMRAvailSet();
        dmrAvailSets[1].setName("dmr avail set 1");
        dmrAvailSets[1].setDmrAvails(new DMRAvail[2]);
        dmrAvailSets[1].getDmrAvails()[0] = new DMRAvail();
        dmrAvailSets[1].getDmrAvails()[0].setName("dmr avail 2");
        dmrAvailSets[1].getDmrAvails()[1] = new DMRAvail();
        dmrAvailSets[1].getDmrAvails()[1].setName("dmr avail 3");

        DMRResourceTypeSet[] dmrResourceTypeSets = new DMRResourceTypeSet[2];
        config.setDmrResourceTypeSets(dmrResourceTypeSets);
        dmrResourceTypeSets[0] = new DMRResourceTypeSet();
        dmrResourceTypeSets[0].setName("dmr resource type set 0");
        dmrResourceTypeSets[0].setDmrResourceTypes(new DMRResourceType[2]);
        dmrResourceTypeSets[0].getDmrResourceTypes()[0] = new DMRResourceType();
        dmrResourceTypeSets[0].getDmrResourceTypes()[0].setName("dmr resource type 0");
        dmrResourceTypeSets[0].getDmrResourceTypes()[0].setDmrResourceConfigs(new DMRResourceConfig[1]);
        dmrResourceTypeSets[0].getDmrResourceTypes()[0].getDmrResourceConfigs()[0] = new DMRResourceConfig();
        dmrResourceTypeSets[0].getDmrResourceTypes()[0].getDmrResourceConfigs()[0].setName("dmr res config 0");
        dmrResourceTypeSets[0].getDmrResourceTypes()[1] = new DMRResourceType();
        dmrResourceTypeSets[0].getDmrResourceTypes()[1].setName("dmr resource type 1");
        dmrResourceTypeSets[0].getDmrResourceTypes()[1].setDmrResourceConfigs(new DMRResourceConfig[1]);
        dmrResourceTypeSets[0].getDmrResourceTypes()[1].getDmrResourceConfigs()[0] = new DMRResourceConfig();
        dmrResourceTypeSets[0].getDmrResourceTypes()[1].getDmrResourceConfigs()[0].setName("dmr res config 1");
        dmrResourceTypeSets[1] = new DMRResourceTypeSet();
        dmrResourceTypeSets[1].setName("dmr resource type set 1");
        dmrResourceTypeSets[1].setDmrResourceTypes(new DMRResourceType[2]);
        dmrResourceTypeSets[1].getDmrResourceTypes()[0] = new DMRResourceType();
        dmrResourceTypeSets[1].getDmrResourceTypes()[0].setName("dmr resource type 2");
        dmrResourceTypeSets[1].getDmrResourceTypes()[1] = new DMRResourceType();
        dmrResourceTypeSets[1].getDmrResourceTypes()[1].setName("dmr resource type 3");

        // JMX

        JMXMetricSet[] jmxMetricSets = new JMXMetricSet[2];
        config.setJmxMetricSets(jmxMetricSets);
        jmxMetricSets[0] = new JMXMetricSet();
        jmxMetricSets[0].setName("jmx metric set 0");
        jmxMetricSets[0].setJmxMetrics(new JMXMetric[2]);
        jmxMetricSets[0].getJmxMetrics()[0] = new JMXMetric();
        jmxMetricSets[0].getJmxMetrics()[0].setName("jmx metric 0");
        jmxMetricSets[0].getJmxMetrics()[1] = new JMXMetric();
        jmxMetricSets[0].getJmxMetrics()[1].setName("jmx metric 1");
        jmxMetricSets[1] = new JMXMetricSet();
        jmxMetricSets[1].setName("jmx metric set 1");
        jmxMetricSets[1].setJmxMetrics(new JMXMetric[2]);
        jmxMetricSets[1].getJmxMetrics()[0] = new JMXMetric();
        jmxMetricSets[1].getJmxMetrics()[0].setName("jmx metric 2");
        jmxMetricSets[1].getJmxMetrics()[1] = new JMXMetric();
        jmxMetricSets[1].getJmxMetrics()[1].setName("jmx metric 3");

        JMXAvailSet[] jmxAvailSets = new JMXAvailSet[2];
        config.setJmxAvailSets(jmxAvailSets);
        jmxAvailSets[0] = new JMXAvailSet();
        jmxAvailSets[0].setName("jmx avail set 0");
        jmxAvailSets[0].setJmxAvails(new JMXAvail[2]);
        jmxAvailSets[0].getJmxAvails()[0] = new JMXAvail();
        jmxAvailSets[0].getJmxAvails()[0].setName("jmx avail 0");
        jmxAvailSets[0].getJmxAvails()[1] = new JMXAvail();
        jmxAvailSets[0].getJmxAvails()[1].setName("jmx avail 1");
        jmxAvailSets[1] = new JMXAvailSet();
        jmxAvailSets[1].setName("jmx avail set 1");
        jmxAvailSets[1].setJmxAvails(new JMXAvail[2]);
        jmxAvailSets[1].getJmxAvails()[0] = new JMXAvail();
        jmxAvailSets[1].getJmxAvails()[0].setName("jmx avail 2");
        jmxAvailSets[1].getJmxAvails()[1] = new JMXAvail();
        jmxAvailSets[1].getJmxAvails()[1].setName("jmx avail 3");

        JMXResourceTypeSet[] jmxResourceTypeSets = new JMXResourceTypeSet[2];
        config.setJmxResourceTypeSets(jmxResourceTypeSets);
        jmxResourceTypeSets[0] = new JMXResourceTypeSet();
        jmxResourceTypeSets[0].setName("jmx resource type set 0");
        jmxResourceTypeSets[0].setJmxResourceTypes(new JMXResourceType[2]);
        jmxResourceTypeSets[0].getJmxResourceTypes()[0] = new JMXResourceType();
        jmxResourceTypeSets[0].getJmxResourceTypes()[0].setName("jmx resource type 0");
        jmxResourceTypeSets[0].getJmxResourceTypes()[0].setJmxResourceConfigs(new JMXResourceConfig[1]);
        jmxResourceTypeSets[0].getJmxResourceTypes()[0].getJmxResourceConfigs()[0] = new JMXResourceConfig();
        jmxResourceTypeSets[0].getJmxResourceTypes()[0].getJmxResourceConfigs()[0].setName("jmx res config 0");
        jmxResourceTypeSets[0].getJmxResourceTypes()[1] = new JMXResourceType();
        jmxResourceTypeSets[0].getJmxResourceTypes()[1].setName("jmx resource type 1");
        jmxResourceTypeSets[0].getJmxResourceTypes()[1].setJmxResourceConfigs(new JMXResourceConfig[1]);
        jmxResourceTypeSets[0].getJmxResourceTypes()[1].getJmxResourceConfigs()[0] = new JMXResourceConfig();
        jmxResourceTypeSets[0].getJmxResourceTypes()[1].getJmxResourceConfigs()[0].setName("jmx res config 1");
        jmxResourceTypeSets[1] = new JMXResourceTypeSet();
        jmxResourceTypeSets[1].setName("jmx resource type set 1");
        jmxResourceTypeSets[1].setJmxResourceTypes(new JMXResourceType[2]);
        jmxResourceTypeSets[1].getJmxResourceTypes()[0] = new JMXResourceType();
        jmxResourceTypeSets[1].getJmxResourceTypes()[0].setName("jmx resource type 2");
        jmxResourceTypeSets[1].getJmxResourceTypes()[1] = new JMXResourceType();
        jmxResourceTypeSets[1].getJmxResourceTypes()[1].setName("jmx resource type 3");

        config.setManagedServers(new ManagedServers());
        config.getManagedServers().setLocalDmr(new LocalDMR());
        config.getManagedServers().getLocalDmr().setName("Local DMR");
        config.getManagedServers().getLocalDmr().setResourceTypeSets(new String[] { "dmr resource type set 0",
                "dmr resource type set 1" });
        config.getManagedServers().setLocalJmx(new LocalJMX());
        config.getManagedServers().getLocalJmx().setName("Local JMX");
        config.getManagedServers().getLocalJmx().setResourceTypeSets(new String[] { "jmx resource type set 0",
                "jmx resource type set 1" });
        config.getManagedServers().setRemoteDmrs(new RemoteDMR[1]);
        config.getManagedServers().getRemoteDmrs()[0] = new RemoteDMR();
        config.getManagedServers().getRemoteDmrs()[0].setName("Remote DMR");
        config.getManagedServers().getRemoteDmrs()[0].setResourceTypeSets(new String[] { "dmr resource type set 0",
                "dmr resource type set 1" });
        config.getManagedServers().setRemoteJmxs(new RemoteJMX[1]);
        config.getManagedServers().getRemoteJmxs()[0] = new RemoteJMX();
        config.getManagedServers().getRemoteJmxs()[0].setName("Remote JMX");
        config.getManagedServers().getRemoteJmxs()[0].setResourceTypeSets(new String[] { "jmx resource type set 0",
                "jmx resource type set 1" });

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

        Assert.assertEquals(2, config.getDmrMetricSets().length);
        Assert.assertEquals(2, config.getDmrAvailSets().length);
        Assert.assertEquals(2, config.getDmrResourceTypeSets().length);

        Assert.assertEquals(2, config.getDmrMetricSets()[0].getDmrMetrics().length);
        Assert.assertEquals(2, config.getDmrAvailSets()[0].getDmrAvails().length);
        Assert.assertEquals(2, config.getDmrResourceTypeSets()[0].getDmrResourceTypes().length);

        Assert.assertEquals(2, config.getDmrMetricSets()[1].getDmrMetrics().length);
        Assert.assertEquals(2, config.getDmrAvailSets()[1].getDmrAvails().length);
        Assert.assertEquals(2, config.getDmrResourceTypeSets()[1].getDmrResourceTypes().length);

        Assert.assertEquals("first metric set d", config.getDmrMetricSets()[0].getName());
        Assert.assertEquals("second metric set d", config.getDmrMetricSets()[1].getName());

        Assert.assertEquals("first metric d", config.getDmrMetricSets()[0].getDmrMetrics()[0].getName());
        Assert.assertEquals("second metric d", config.getDmrMetricSets()[0].getDmrMetrics()[1].getName());
        Assert.assertEquals("third metric d", config.getDmrMetricSets()[1].getDmrMetrics()[0].getName());
        Assert.assertEquals("fourth metric d", config.getDmrMetricSets()[1].getDmrMetrics()[1].getName());

        Assert.assertEquals("first avail set d", config.getDmrAvailSets()[0].getName());
        Assert.assertEquals("second avail set d", config.getDmrAvailSets()[1].getName());

        Assert.assertEquals("first avail d", config.getDmrAvailSets()[0].getDmrAvails()[0].getName());
        Assert.assertEquals("second avail d", config.getDmrAvailSets()[0].getDmrAvails()[1].getName());
        Assert.assertEquals("third avail d", config.getDmrAvailSets()[1].getDmrAvails()[0].getName());
        Assert.assertEquals("fourth avail d", config.getDmrAvailSets()[1].getDmrAvails()[1].getName());

        Assert.assertEquals("first resource type set d", config.getDmrResourceTypeSets()[0].getName());
        Assert.assertEquals("second resource type set d", config.getDmrResourceTypeSets()[1].getName());

        Assert.assertEquals("first resource type d",
                config.getDmrResourceTypeSets()[0].getDmrResourceTypes()[0].getName());
        Assert.assertEquals("second resource type d",
                config.getDmrResourceTypeSets()[0].getDmrResourceTypes()[1].getName());
        Assert.assertEquals("third resource type d",
                config.getDmrResourceTypeSets()[1].getDmrResourceTypes()[0].getName());
        Assert.assertEquals("fourth resource type d",
                config.getDmrResourceTypeSets()[1].getDmrResourceTypes()[1].getName());

        Assert.assertEquals("/metric=one", config.getDmrMetricSets()[0].getDmrMetrics()[0].getPath());
        Assert.assertEquals("attrib1", config.getDmrMetricSets()[0].getDmrMetrics()[0].getAttribute());
        Assert.assertEquals(12345, config.getDmrMetricSets()[0].getDmrMetrics()[0].getInterval().intValue());
        Assert.assertEquals(TimeUnits.milliseconds, config.getDmrMetricSets()[0].getDmrMetrics()[0].getTimeUnits());
        Assert.assertEquals(MeasurementUnit.MEGABYTES,
                config.getDmrMetricSets()[0].getDmrMetrics()[0].getMetricUnits());
        Assert.assertEquals(MetricType.COUNTER, config.getDmrMetricSets()[0].getDmrMetrics()[0].getMetricType());
        Assert.assertEquals("the template", config.getDmrMetricSets()[0].getDmrMetrics()[0].getMetricIdTemplate());
        Assert.assertEquals("{tag1=value1, tag2=value2}",
                config.getDmrMetricSets()[0].getDmrMetrics()[0].getMetricTags().toString());

        Assert.assertEquals(2,
                config.getDmrResourceTypeSets()[0].getDmrResourceTypes()[0].getDmrResourceConfigs().length);
        Assert.assertEquals("first resconfig d",
                config.getDmrResourceTypeSets()[0].getDmrResourceTypes()[0].getDmrResourceConfigs()[0].getName());
        Assert.assertEquals("/",
                config.getDmrResourceTypeSets()[0].getDmrResourceTypes()[0].getDmrResourceConfigs()[0].getPath());
        Assert.assertEquals("attrib1#subattrib1",
                config.getDmrResourceTypeSets()[0].getDmrResourceTypes()[0].getDmrResourceConfigs()[0].getAttribute());
        Assert.assertEquals("second resconfig d",
                config.getDmrResourceTypeSets()[0].getDmrResourceTypes()[0].getDmrResourceConfigs()[1].getName());
        Assert.assertEquals("/config=two",
                config.getDmrResourceTypeSets()[0].getDmrResourceTypes()[0].getDmrResourceConfigs()[1].getPath());
        Assert.assertEquals("attrib2#subattrib2",
                config.getDmrResourceTypeSets()[0].getDmrResourceTypes()[0].getDmrResourceConfigs()[1].getAttribute());

        Assert.assertEquals("Test Local DMR", config.getManagedServers().getLocalDmr().getName());
        Assert.assertEquals("first resource type set d",
                config.getManagedServers().getLocalDmr().getResourceTypeSets()[0]);
        Assert.assertEquals("second resource type set d",
                config.getManagedServers().getLocalDmr().getResourceTypeSets()[1]);
    }

    @Test
    public void testFullConfigJmx() throws Exception {
        File file = loadTestConfigFile("/test-config.yaml");
        ConfigManager configManager = new ConfigManager(file);
        Configuration config = configManager.getConfiguration(false);
        Assert.assertTrue(configManager.hasConfiguration());

        Assert.assertEquals(2, config.getJmxMetricSets().length);
        Assert.assertEquals(2, config.getJmxAvailSets().length);
        Assert.assertEquals(2, config.getJmxResourceTypeSets().length);

        Assert.assertEquals(2, config.getJmxMetricSets()[0].getJmxMetrics().length);
        Assert.assertEquals(2, config.getJmxAvailSets()[0].getJmxAvails().length);
        Assert.assertEquals(2, config.getJmxResourceTypeSets()[0].getJmxResourceTypes().length);

        Assert.assertEquals(2, config.getJmxMetricSets()[1].getJmxMetrics().length);
        Assert.assertEquals(2, config.getJmxAvailSets()[1].getJmxAvails().length);
        Assert.assertEquals(2, config.getJmxResourceTypeSets()[1].getJmxResourceTypes().length);

        Assert.assertEquals("first metric set", config.getJmxMetricSets()[0].getName());
        Assert.assertEquals("second metric set", config.getJmxMetricSets()[1].getName());

        Assert.assertEquals("first metric", config.getJmxMetricSets()[0].getJmxMetrics()[0].getName());
        Assert.assertEquals("second metric", config.getJmxMetricSets()[0].getJmxMetrics()[1].getName());
        Assert.assertEquals("third metric", config.getJmxMetricSets()[1].getJmxMetrics()[0].getName());
        Assert.assertEquals("fourth metric", config.getJmxMetricSets()[1].getJmxMetrics()[1].getName());

        Assert.assertEquals("first avail set", config.getJmxAvailSets()[0].getName());
        Assert.assertEquals("second avail set", config.getJmxAvailSets()[1].getName());

        Assert.assertEquals("first avail", config.getJmxAvailSets()[0].getJmxAvails()[0].getName());
        Assert.assertEquals("second avail", config.getJmxAvailSets()[0].getJmxAvails()[1].getName());
        Assert.assertEquals("third avail", config.getJmxAvailSets()[1].getJmxAvails()[0].getName());
        Assert.assertEquals("fourth avail", config.getJmxAvailSets()[1].getJmxAvails()[1].getName());

        Assert.assertEquals("first resource type set", config.getJmxResourceTypeSets()[0].getName());
        Assert.assertEquals("second resource type set", config.getJmxResourceTypeSets()[1].getName());

        Assert.assertEquals("first resource type",
                config.getJmxResourceTypeSets()[0].getJmxResourceTypes()[0].getName());
        Assert.assertEquals("second resource type",
                config.getJmxResourceTypeSets()[0].getJmxResourceTypes()[1].getName());
        Assert.assertEquals("third resource type",
                config.getJmxResourceTypeSets()[1].getJmxResourceTypes()[0].getName());
        Assert.assertEquals("fourth resource type",
                config.getJmxResourceTypeSets()[1].getJmxResourceTypes()[1].getName());

        Assert.assertEquals("domain:metric=one", config.getJmxMetricSets()[0].getJmxMetrics()[0].getObjectName());
        Assert.assertEquals("attrib1", config.getJmxMetricSets()[0].getJmxMetrics()[0].getAttribute());
        Assert.assertEquals(12345, config.getJmxMetricSets()[0].getJmxMetrics()[0].getInterval().intValue());
        Assert.assertEquals(TimeUnits.seconds, config.getJmxMetricSets()[0].getJmxMetrics()[0].getTimeUnits());
        Assert.assertEquals(MeasurementUnit.BYTES, config.getJmxMetricSets()[0].getJmxMetrics()[0].getMetricUnits());
        Assert.assertEquals(MetricType.GAUGE, config.getJmxMetricSets()[0].getJmxMetrics()[0].getMetricType());
        Assert.assertEquals("the template", config.getJmxMetricSets()[0].getJmxMetrics()[0].getMetricIdTemplate());
        Assert.assertEquals("{tag1=value1, tag2=value2}",
                config.getJmxMetricSets()[0].getJmxMetrics()[0].getMetricTags().toString());

        Assert.assertEquals(2,
                config.getJmxResourceTypeSets()[0].getJmxResourceTypes()[0].getJmxResourceConfigs().length);
        Assert.assertEquals("first resconfig",
                config.getJmxResourceTypeSets()[0].getJmxResourceTypes()[0].getJmxResourceConfigs()[0].getName());
        Assert.assertNull(config.getJmxResourceTypeSets()[0].getJmxResourceTypes()[0].getJmxResourceConfigs()[0]
                .getObjectName());
        Assert.assertEquals("attrib1#subattrib1",
                config.getJmxResourceTypeSets()[0].getJmxResourceTypes()[0].getJmxResourceConfigs()[0].getAttribute());
        Assert.assertEquals("second resconfig",
                config.getJmxResourceTypeSets()[0].getJmxResourceTypes()[0].getJmxResourceConfigs()[1].getName());
        Assert.assertEquals("domain:type=two",
                config.getJmxResourceTypeSets()[0].getJmxResourceTypes()[0].getJmxResourceConfigs()[1]
                        .getObjectName());
        Assert.assertEquals("attrib2#subattrib2",
                config.getJmxResourceTypeSets()[0].getJmxResourceTypes()[0].getJmxResourceConfigs()[1].getAttribute());

        Assert.assertEquals("Test Local JMX", config.getManagedServers().getLocalJmx().getName());
        Assert.assertEquals("first resource type set",
                config.getManagedServers().getLocalJmx().getResourceTypeSets()[0]);
        Assert.assertEquals("second resource type set",
                config.getManagedServers().getLocalJmx().getResourceTypeSets()[1]);
    }

    @Test
    public void testDefaults() {
        // subsystem
        Subsystem s = new Subsystem();
        Assert.assertEquals(true, s.getEnabled());
        Assert.assertEquals(Integer.valueOf(600), s.getAutoDiscoveryScanPeriodSecs());

        // storage adapter
        StorageAdapter sa = new StorageAdapter();
        Assert.assertEquals(StorageAdapter.Type.HAWKULAR, sa.getType());
        Assert.assertEquals("hawkular", sa.getTenantId());
        Assert.assertEquals("/hawkular/metrics/", sa.getMetricsContext());
        Assert.assertEquals("/hawkular/command-gateway/", sa.getFeedcommContext());
        Assert.assertEquals(10, sa.getConnectTimeoutSecs().intValue());
        Assert.assertEquals(120, sa.getReadTimeoutSecs().intValue());

        // platform
        Platform p = new Platform();
        Assert.assertEquals(false, p.getEnabled());
        Assert.assertEquals(5, p.getInterval().intValue());
        Assert.assertEquals(TimeUnits.minutes, p.getTimeUnits());

        Assert.assertEquals(true, p.getMemory().getEnabled());
        Assert.assertEquals(5, p.getMemory().getInterval().intValue());
        Assert.assertEquals(TimeUnits.minutes, p.getMemory().getTimeUnits());

        Assert.assertEquals(true, p.getFileStores().getEnabled());
        Assert.assertEquals(5, p.getFileStores().getInterval().intValue());
        Assert.assertEquals(TimeUnits.minutes, p.getFileStores().getTimeUnits());

        Assert.assertEquals(true, p.getProcessors().getEnabled());
        Assert.assertEquals(5, p.getProcessors().getInterval().intValue());
        Assert.assertEquals(TimeUnits.minutes, p.getProcessors().getTimeUnits());

        Assert.assertEquals(false, p.getPowerSources().getEnabled());
        Assert.assertEquals(5, p.getPowerSources().getInterval().intValue());
        Assert.assertEquals(TimeUnits.minutes, p.getPowerSources().getTimeUnits());

        // managed servers
        LocalDMR ldmr = new LocalDMR();
        Assert.assertEquals(true, ldmr.getEnabled());

        LocalJMX ljmx = new LocalJMX();
        Assert.assertEquals(true, ljmx.getEnabled());
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

        config.getStorageAdapter().setUsername("CHANGE");
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
        Assert.assertEquals(true, config.getSubsystem().getEnabled());
        Assert.assertEquals(Integer.valueOf(600), config.getSubsystem().getAutoDiscoveryScanPeriodSecs());

        // security-realm
        SecurityRealm securityRealm = config.getSecurityRealms()[0];
        Assert.assertEquals("h-server-security-realm", securityRealm.getName());
        Assert.assertEquals("/my/keystore/path", securityRealm.getKeystorePath());
        Assert.assertEquals("my keystore password", securityRealm.getKeystorePassword());
        securityRealm = config.getSecurityRealms()[1];
        Assert.assertEquals("some-truststore", securityRealm.getName());
        Assert.assertEquals("/my/truststore/path", securityRealm.getKeystorePath());
        Assert.assertEquals("my truststore password", securityRealm.getKeystorePassword());

        // storage-adapter
        Assert.assertEquals(StorageAdapter.Type.HAWKULAR, config.getStorageAdapter().getType());
        Assert.assertEquals("http://127.0.0.1:8080", config.getStorageAdapter().getUrl());
        Assert.assertEquals("hawkular", config.getStorageAdapter().getTenantId());
        Assert.assertEquals(expectedStorageAdapterUsername, config.getStorageAdapter().getUsername());
        Assert.assertEquals("password", config.getStorageAdapter().getPassword());
        Assert.assertEquals("autogenerate", config.getStorageAdapter().getFeedId());
        Assert.assertEquals("h-server-security-realm", config.getStorageAdapter().getSecurityRealmName());
        Assert.assertEquals("/my-metrics", config.getStorageAdapter().getMetricsContext());
        Assert.assertEquals("/my-feedcomm", config.getStorageAdapter().getFeedcommContext());
        Assert.assertEquals(Integer.valueOf(123), config.getStorageAdapter().getConnectTimeoutSecs());
        Assert.assertEquals(Integer.valueOf(456), config.getStorageAdapter().getReadTimeoutSecs());

        // managed-servers - local dmr
        LocalDMR ldmr = config.getManagedServers().getLocalDmr();
        Assert.assertEquals("Local WildFly", ldmr.getName());
        Assert.assertEquals(Boolean.FALSE, ldmr.getEnabled());
        Assert.assertEquals("local wildfly tenant", ldmr.getTenantId());
        Assert.assertEquals("LocalTypeSet1", ldmr.getResourceTypeSets()[0]);
        Assert.assertEquals("TypeSet2", ldmr.getResourceTypeSets()[1]);
        Assert.assertEquals("local feed id is %FeedId and metric name is %MetricName", ldmr.getMetricIdTemplate());
        Assert.assertEquals(2, ldmr.getMetricTags().size());
        Assert.assertEquals("val1", ldmr.getMetricTags().get("localdmrtag1"));
        Assert.assertEquals("val2", ldmr.getMetricTags().get("dmrtag2"));
        Assert.assertEquals(Avail.DOWN, ldmr.getSetAvailOnShutdown());

        // managed-servers - local jmx
        LocalJMX ljmx = config.getManagedServers().getLocalJmx();
        Assert.assertEquals("Local JMX", ljmx.getName());
        Assert.assertEquals(Boolean.FALSE, ljmx.getEnabled());
        Assert.assertEquals("jmx local wildfly tenant", ljmx.getTenantId());
        Assert.assertEquals("jmx LocalTypeSet1", ljmx.getResourceTypeSets()[0]);
        Assert.assertEquals("jmx TypeSet2", ljmx.getResourceTypeSets()[1]);
        Assert.assertEquals("jmx local feed id is %FeedId and metric name is %MetricName", ljmx.getMetricIdTemplate());
        Assert.assertEquals(2, ljmx.getMetricTags().size());
        Assert.assertEquals("val1", ljmx.getMetricTags().get("localjmxtag1"));
        Assert.assertEquals("val2", ljmx.getMetricTags().get("jmxtag2"));
        Assert.assertEquals(Avail.UNKNOWN, ljmx.getSetAvailOnShutdown());
        Assert.assertEquals("some-mbs-name", ljmx.getMbeanServerName());

        // managed-servers - remote dmr
        RemoteDMR rdmr = config.getManagedServers().getRemoteDmrs()[0];
        Assert.assertEquals("Remote WildFly", rdmr.getName());
        Assert.assertEquals(Boolean.FALSE, rdmr.getEnabled());
        Assert.assertEquals("remote wildfly tenant", rdmr.getTenantId());
        Assert.assertEquals("RemoteTypeSet1", rdmr.getResourceTypeSets()[0]);
        Assert.assertEquals("TypeSet2", rdmr.getResourceTypeSets()[1]);
        Assert.assertEquals("remote feed id is %FeedId and metric name is %MetricName", rdmr.getMetricIdTemplate());
        Assert.assertEquals(2, rdmr.getMetricTags().size());
        Assert.assertEquals("val1", rdmr.getMetricTags().get("remotedmrtag1"));
        Assert.assertEquals("val2", rdmr.getMetricTags().get("dmrtag2"));
        Assert.assertEquals(Avail.DOWN, rdmr.getSetAvailOnShutdown());

        // managed-servers - remote jmx
        RemoteJMX rjmx = config.getManagedServers().getRemoteJmxs()[0];
        Assert.assertEquals("Remote JMX", rjmx.getName());
        Assert.assertEquals(Boolean.FALSE, rjmx.getEnabled());
        Assert.assertEquals("jmx remote wildfly tenant", rjmx.getTenantId());
        Assert.assertEquals("jmx RemoteTypeSet1", rjmx.getResourceTypeSets()[0]);
        Assert.assertEquals("jmx TypeSet2", rjmx.getResourceTypeSets()[1]);
        Assert.assertEquals("jmx remote feed id is %FeedId and metric name is %MetricName",
                rjmx.getMetricIdTemplate());
        Assert.assertEquals(2, rjmx.getMetricTags().size());
        Assert.assertEquals("val1", rjmx.getMetricTags().get("remotejmxtag1"));
        Assert.assertEquals("val2", rjmx.getMetricTags().get("jmxtag2"));
        Assert.assertEquals(Avail.UNKNOWN, rjmx.getSetAvailOnShutdown());

        // platform
        Assert.assertEquals(true, config.getPlatform().getEnabled());
        Assert.assertEquals(Integer.valueOf(1234), config.getPlatform().getInterval());
        Assert.assertEquals(TimeUnits.seconds, config.getPlatform().getTimeUnits());
        Assert.assertEquals("my-machine-id-here", config.getPlatform().getMachineId());
        Assert.assertEquals("my-container-id-here", config.getPlatform().getContainerId());
        Assert.assertEquals(Boolean.TRUE, config.getPlatform().getFileStores().getEnabled());
        Assert.assertEquals(Integer.valueOf(5000), config.getPlatform().getFileStores().getInterval());
        Assert.assertEquals(TimeUnits.milliseconds, config.getPlatform().getFileStores().getTimeUnits());
        Assert.assertEquals(Boolean.TRUE, config.getPlatform().getMemory().getEnabled());
        Assert.assertEquals(Integer.valueOf(30), config.getPlatform().getMemory().getInterval());
        Assert.assertEquals(TimeUnits.seconds, config.getPlatform().getMemory().getTimeUnits());
        Assert.assertEquals(Boolean.TRUE, config.getPlatform().getProcessors().getEnabled());
        Assert.assertEquals(Integer.valueOf(1), config.getPlatform().getProcessors().getInterval());
        Assert.assertEquals(TimeUnits.minutes, config.getPlatform().getProcessors().getTimeUnits());
        Assert.assertEquals(Boolean.FALSE, config.getPlatform().getPowerSources().getEnabled());
        Assert.assertEquals(Integer.valueOf(5), config.getPlatform().getPowerSources().getInterval());
        Assert.assertEquals(TimeUnits.minutes, config.getPlatform().getPowerSources().getTimeUnits());
    }
}
