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
package org.hawkular.agent.monitor.extension.config;

import java.io.File;
import java.io.FileNotFoundException;
import java.net.URISyntaxException;
import java.net.URL;

import org.hawkular.agent.monitor.extension.FileStoresAttributes;
import org.hawkular.agent.monitor.extension.LocalDMRAttributes;
import org.hawkular.agent.monitor.extension.MemoryAttributes;
import org.hawkular.agent.monitor.extension.PlatformAttributes;
import org.hawkular.agent.monitor.extension.PowerSourcesAttributes;
import org.hawkular.agent.monitor.extension.ProcessorsAttributes;
import org.hawkular.agent.monitor.extension.RemoteDMRAttributes;
import org.hawkular.agent.monitor.extension.RemotePrometheusAttributes;
import org.hawkular.agent.monitor.extension.StorageAttributes;
import org.hawkular.agent.monitor.extension.SubsystemAttributes;
import org.junit.Assert;
import org.junit.Test;

public class ConfigManagerTest {

    @Test
    public void testDefaults() {
        // subsystem
        Subsystem s = new Subsystem();
        Assert.assertEquals(SubsystemAttributes.ENABLED.getDefaultValue().asBoolean(), s.enabled);
        Assert.assertEquals(
                Integer.valueOf(SubsystemAttributes.AUTO_DISCOVERY_SCAN_PERIOD_SECONDS.getDefaultValue().asInt()),
                s.autoDiscoveryScanPeriodSecs);

        // storage adapter
        StorageAdapter sa = new StorageAdapter();
        Assert.assertEquals(StorageAdapter.Type.HAWKULAR, sa.type);
        Assert.assertEquals(StorageAttributes.TENANT_ID.getDefaultValue().asString(), sa.tenantId);
        Assert.assertEquals(StorageAttributes.INVENTORY_CONTEXT.getDefaultValue().asString(), sa.inventoryContext);
        Assert.assertEquals(StorageAttributes.METRICS_CONTEXT.getDefaultValue().asString(), sa.metricsContext);
        Assert.assertEquals(StorageAttributes.FEEDCOMM_CONTEXT.getDefaultValue().asString(), sa.feedcommContext);
        Assert.assertEquals(StorageAttributes.CONNECT_TIMEOUT_SECONDS.getDefaultValue().asInt(),
                sa.connectTimeoutSecs.intValue());
        Assert.assertEquals(StorageAttributes.READ_TIMEOUT_SECONDS.getDefaultValue().asInt(),
                sa.readTimeoutSecs.intValue());

        // platform
        Platform p = new Platform();
        Assert.assertEquals(PlatformAttributes.ENABLED.getDefaultValue().asBoolean(), p.enabled);
        Assert.assertEquals(PlatformAttributes.INTERVAL.getDefaultValue().asInt(), p.interval.intValue());
        Assert.assertEquals(PlatformAttributes.TIME_UNITS.getDefaultValue().asString(),
                p.timeUnits.toString().toUpperCase());

        Assert.assertEquals(MemoryAttributes.ENABLED.getDefaultValue().asBoolean(), p.memory.enabled);
        Assert.assertEquals(MemoryAttributes.INTERVAL.getDefaultValue().asInt(), p.memory.interval.intValue());
        Assert.assertEquals(MemoryAttributes.TIME_UNITS.getDefaultValue().asString(),
                p.memory.timeUnits.toString().toUpperCase());

        Assert.assertEquals(FileStoresAttributes.ENABLED.getDefaultValue().asBoolean(), p.fileStores.enabled);
        Assert.assertEquals(FileStoresAttributes.INTERVAL.getDefaultValue().asInt(), p.fileStores.interval.intValue());
        Assert.assertEquals(FileStoresAttributes.TIME_UNITS.getDefaultValue().asString(),
                p.fileStores.timeUnits.toString().toUpperCase());

        Assert.assertEquals(ProcessorsAttributes.ENABLED.getDefaultValue().asBoolean(), p.processors.enabled);
        Assert.assertEquals(ProcessorsAttributes.INTERVAL.getDefaultValue().asInt(), p.processors.interval.intValue());
        Assert.assertEquals(ProcessorsAttributes.TIME_UNITS.getDefaultValue().asString(),
                p.processors.timeUnits.toString().toUpperCase());

        Assert.assertEquals(PowerSourcesAttributes.ENABLED.getDefaultValue().asBoolean(), p.powerSources.enabled);
        Assert.assertEquals(PowerSourcesAttributes.INTERVAL.getDefaultValue().asInt(),
                p.powerSources.interval.intValue());
        Assert.assertEquals(PowerSourcesAttributes.TIME_UNITS.getDefaultValue().asString(),
                p.powerSources.timeUnits.toString().toUpperCase());

        // managed servers
        LocalDMR ldmr = new LocalDMR();
        Assert.assertEquals(LocalDMRAttributes.ENABLED.getDefaultValue().asBoolean(), ldmr.enabled);

        RemoteDMR rdmr = new RemoteDMR();
        Assert.assertEquals(RemoteDMRAttributes.ENABLED.getDefaultValue().asBoolean(), rdmr.enabled);

        RemotePrometheus rp = new RemotePrometheus();
        Assert.assertEquals(RemotePrometheusAttributes.ENABLED.getDefaultValue().asBoolean(), rp.enabled);
        Assert.assertEquals(RemotePrometheusAttributes.INTERVAL.getDefaultValue().asInt(), rp.interval.intValue());
        Assert.assertEquals(RemotePrometheusAttributes.TIME_UNITS.getDefaultValue().asString(),
                rp.timeUnits.toString().toUpperCase());
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
    public void testLoad() throws Exception {
        File file = loadTestConfigFile();
        ConfigManager configManager = new ConfigManager(file);
        Assert.assertFalse(configManager.hasConfiguration());
        Configuration config = configManager.getConfiguration(false);
        Assert.assertTrue(configManager.hasConfiguration());
        assertConfig(config, "jdoe");
    }

    @Test
    public void testUpdate() throws Exception {
        File file = loadTestConfigFile();
        ConfigManager configManager = new ConfigManager(file);
        Configuration config = configManager.getConfiguration(false);
        assertConfig(config, "jdoe");

        config.storageAdapter.username = "CHANGE";
        configManager.updateConfiguration(config, true);
        assertConfig(configManager.getConfiguration(false), "CHANGE");

        // see that it really made it to the file
        config = configManager.getConfiguration(true);
        assertConfig(config, "CHANGE");

        // see that a backup was created
        ConfigManager backupConfigManager = new ConfigManager(new File(file.getAbsolutePath() + ".bak"));
        config = backupConfigManager.getConfiguration(false);
        assertConfig(config, "jdoe");

        // put the original back the way it was
        backupConfigManager.getConfigFile().delete(); // remove the backup
        configManager.updateConfiguration(config, false); // don't backup this time
        Assert.assertFalse(backupConfigManager.getConfigFile().exists());
    }

    private File loadTestConfigFile() throws URISyntaxException {
        URL url = ConfigManagerTest.class.getResource("/org/hawkular/agent/monitor/extension/config/simple.yaml");
        Assert.assertNotNull("yaml config file not found", url);
        File file = new File(url.toURI());
        return file;
    }

    private void assertConfig(Configuration config, String expectedStorageAdapterUsername) {
        Assert.assertNotNull("yaml config was null", config);

        // subsystem
        Assert.assertEquals(true, config.subsystem.enabled);
        Assert.assertEquals(Integer.valueOf(600), config.subsystem.autoDiscoveryScanPeriodSecs);

        // storage-adapter
        Assert.assertEquals(StorageAdapter.Type.HAWKULAR, config.storageAdapter.type);
        Assert.assertEquals("http://127.0.0.1:8080", config.storageAdapter.url);
        Assert.assertEquals("hawkular", config.storageAdapter.tenantId);
        Assert.assertEquals(expectedStorageAdapterUsername, config.storageAdapter.username);
        Assert.assertEquals("password", config.storageAdapter.password);
        Assert.assertEquals("autogenerate", config.storageAdapter.feedId);
        Assert.assertEquals("MySecurityRealm", config.storageAdapter.securityRealm);
        Assert.assertEquals("/my/keystore/path", config.storageAdapter.keystorePath);
        Assert.assertEquals("my keystore password", config.storageAdapter.keystorePassword);
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
        Assert.assertEquals("LocalTypeSet1,TypeSet2", ldmr.resourceTypeSets);
        Assert.assertEquals("local feed id is %FeedId and metric name is %MetricName", ldmr.metricIdTemplate);
        Assert.assertEquals("localdmrtag1=val1,dmrtag2=val2", ldmr.metricTags);

        // managed-servers - remote dmrs
        RemoteDMR[] dmrs = config.managedServers.remoteDmr;
        int d = 0;
        Assert.assertEquals("My WildFly", dmrs[d].name);
        Assert.assertEquals(Boolean.TRUE, dmrs[d].enabled);
        Assert.assertEquals("my-host", dmrs[d].host);
        Assert.assertEquals(Integer.valueOf(9999), dmrs[d].port);
        Assert.assertEquals(Boolean.TRUE, dmrs[d].useSsl);
        Assert.assertEquals("dmr user", dmrs[d].username);
        Assert.assertEquals("dmr pass", dmrs[d].password);
        Assert.assertEquals("wildfly tenant", dmrs[d].tenantId);
        Assert.assertEquals("TypeSet1,TypeSet2", dmrs[d].resourceTypeSets);
        Assert.assertEquals("feed id is %FeedId and metric name is %MetricName", dmrs[d].metricIdTemplate);
        Assert.assertEquals("dmrtag1=val1,dmrtag2=val2", dmrs[d].metricTags);

        d = 1;
        Assert.assertEquals("My Other WildFly", dmrs[d].name);
        Assert.assertEquals(Boolean.FALSE, dmrs[d].enabled);
        Assert.assertEquals("my-other-host", dmrs[d].host);
        Assert.assertEquals(Integer.valueOf(19999), dmrs[d].port);
        Assert.assertEquals("dmr2 user", dmrs[d].username);
        Assert.assertEquals("dmr2 pass", dmrs[d].password);

        // managed-servers - prometheus
        RemotePrometheus[] proms = config.managedServers.remotePrometheus;
        int p = 0;
        Assert.assertEquals("My Prometheus", proms[p].name);
        Assert.assertEquals(Boolean.TRUE, proms[p].enabled);
        Assert.assertEquals("http://127.0.0.1:9090/metrics", proms[p].url);
        Assert.assertEquals("foo", proms[p].username);
        Assert.assertEquals("bar", proms[p].password);
        Assert.assertEquals(Integer.valueOf(1), proms[p].interval);
        Assert.assertEquals(TimeUnits.minutes, proms[p].timeUnits);
        Assert.assertEquals("prom-tenant", proms[p].tenantId);
        Assert.assertEquals("metric name is %MetricName", proms[p].metricIdTemplate);
        Assert.assertEquals("tag1=val1,tag2=val2", proms[p].metricTags);
        p = 1;
        Assert.assertEquals("My Other Prometheus", proms[p].name);
        Assert.assertEquals(Boolean.FALSE, proms[p].enabled);
        Assert.assertEquals("http://my-other-prometheus:9090/metrics", proms[p].url);
        Assert.assertNull(proms[p].username);
        Assert.assertNull(proms[p].password);
        Assert.assertEquals(5, proms[p].interval.intValue());
        Assert.assertEquals(TimeUnits.minutes, proms[p].timeUnits);
        Assert.assertNull(proms[p].tenantId);
        Assert.assertNull(proms[p].metricIdTemplate);
        Assert.assertNull(proms[p].metricTags);

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
