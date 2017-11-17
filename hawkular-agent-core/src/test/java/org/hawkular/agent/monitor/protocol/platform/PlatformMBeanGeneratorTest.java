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
package org.hawkular.agent.monitor.protocol.platform;

import java.lang.management.ManagementFactory;

import javax.management.InstanceNotFoundException;
import javax.management.MBeanServer;
import javax.management.ObjectName;

import org.hawkular.agent.monitor.config.AgentCoreEngineConfiguration.PlatformConfiguration;
import org.hawkular.agent.monitor.inventory.ID;
import org.junit.Assert;
import org.junit.Test;

/**
 * This is just a simple test that lets us see the platform MBeans working.
 */
public class PlatformMBeanGeneratorTest {

    @Test
    public void testMetrics() throws Exception {
        PlatformConfiguration platConfig = new PlatformConfiguration(true, true, true, true, true,
                "testMachineId", "testContainerId");

        PlatformMBeanGenerator gen = new PlatformMBeanGenerator("testFeed", platConfig);
        gen.registerAllMBeans();

        Assert.assertEquals("testContainerId",
                getMBeanAttrib(gen, gen.getOperatingSystemObjectName(), new ID(Constants.CONTAINER_ID)));
        Assert.assertEquals("testMachineId",
                getMBeanAttrib(gen, gen.getOperatingSystemObjectName(), new ID(Constants.MACHINE_ID)));

        Assert.assertNotNull(getMBeanAttrib(gen, gen.getOperatingSystemObjectName(),
                Constants.PlatformMetricType.OS_PROCESS_COUNT.getMetricTypeId()));
        Assert.assertNotNull(getMBeanAttrib(gen, gen.getOperatingSystemObjectName(),
                Constants.PlatformMetricType.OS_SYS_CPU_LOAD.getMetricTypeId()));
        Assert.assertNotNull(getMBeanAttrib(gen, gen.getOperatingSystemObjectName(),
                Constants.PlatformMetricType.OS_SYS_LOAD_AVG.getMetricTypeId()));

        Assert.assertNotNull(getMBeanAttrib(gen, gen.getMemoryObjectName(),
                Constants.PlatformMetricType.MEMORY_TOTAL.getMetricTypeId()));
        Assert.assertNotNull(getMBeanAttrib(gen, gen.getMemoryObjectName(),
                Constants.PlatformMetricType.MEMORY_AVAILABLE.getMetricTypeId()));

        gen.unregisterAllMBeans();

        try {
            getMBeanAttrib(gen, gen.getOperatingSystemObjectName(),
                    Constants.PlatformMetricType.OS_PROCESS_COUNT.getMetricTypeId());
            Assert.fail("Should not have MBeans anymore");
        } catch (InstanceNotFoundException expected) {
        }
    }

    private Object getMBeanAttrib(PlatformMBeanGenerator gen, ObjectName on, ID attrib)
            throws Exception {
        MBeanServer mbs = ManagementFactory.getPlatformMBeanServer();
        Object metricValue = mbs.getAttribute(on, attrib.getIDString());
        System.out.println(String.format("PLATFORM METRIC VALUE: %s/%s ==> %s", on, attrib, metricValue));
        return metricValue;
    }
}
