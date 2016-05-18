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
package org.hawkular.agent.monitor.protocol.platform;

import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.hawkular.agent.monitor.inventory.Name;
import org.hawkular.agent.monitor.protocol.platform.Constants.PlatformResourceType;
import org.junit.Assert;
import org.junit.Test;

import oshi.hardware.Memory;
import oshi.hardware.PowerSource;
import oshi.hardware.Processor;
import oshi.software.os.OSFileStore;
import oshi.software.os.OperatingSystem;
import oshi.software.os.OperatingSystemVersion;
import oshi.util.FormatUtil;
import oshi.util.Util;

/**
 * This is just a simple test that lets us see the oshi API working.
 */
public class OshiPlatformCacheTest {

    @Test
    public void testConcurrency() {
        // Have two threads repeatedly get data and refresh the data
        // to make sure it never blocks and no problems occur.
        // These just test the locking - make sure we won't block concurrent access;
        // we don't actually look at the platform values.

        OshiPlatformCache oshi = newOshiPlatformCache();

        final CountDownLatch goLatch = new CountDownLatch(1);
        final int maxLoops = 10;
        final AtomicInteger t1Attempts = new AtomicInteger(0);
        final AtomicInteger t2Attempts = new AtomicInteger(0);
        final CountDownLatch doneLatch1 = new CountDownLatch(1);
        final CountDownLatch doneLatch2 = new CountDownLatch(1);

        Thread t1 = new Thread(() -> {
            try {
                // should never take this long, but just a precaution
                goLatch.await(10, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                print("thread 1 interrupted");
                return;
            }

            for (int i = 0; i < maxLoops; i++) {
                getOshiData(oshi);
                t1Attempts.incrementAndGet();
            }

            doneLatch1.countDown();
        });

        Thread t2 = new Thread(() -> {
            try {
                // should never take this long, but just a precaution
                goLatch.await(10, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                print("thread 2 interrupted");
                return;
            }
            for (int i = 0; i < maxLoops; i++) {
                getOshiData(oshi);
                t2Attempts.incrementAndGet();
            }

            doneLatch2.countDown();
        });

        t1.start();
        t2.start();
        goLatch.countDown();
        try {
            doneLatch1.await(30, TimeUnit.SECONDS);
            doneLatch2.await(30, TimeUnit.SECONDS);
        } catch (InterruptedException ie) {
            Assert.fail("Concurrency test was interrupted and did not finish");
        }

        // make sure we executed everything we expected - if not, we must have blocked somewhere
        Assert.assertEquals(maxLoops, t1Attempts.get());
        Assert.assertEquals(maxLoops, t2Attempts.get());
    }

    private void getOshiData(OshiPlatformCache oshi) {
        oshi.getOperatingSystem();
        oshi.getFileStores();
        oshi.getMemory();
        oshi.getProcessors();
        oshi.getPowerSources();
        oshi.refresh();
    }

    @Test
    public void testDiscover() {
        OshiPlatformCache oshi = newOshiPlatformCache();
        Map<PlatformPath, PlatformResourceNode> map;
        PlatformPath query;

        // OS
        query = PlatformPath.builder().any(PlatformResourceType.OPERATING_SYSTEM).build();
        map = oshi.discoverResources(query);
        Assert.assertEquals("Should only have 1 operating system resource", map.size(), 1);
        PlatformPath osPath = map.keySet().iterator().next();
        PlatformResourceNode osNode = map.values().iterator().next();
        print("---> OS path=[%s], node=[%s]", osPath, osNode);
        Assert.assertEquals(PlatformResourceType.OPERATING_SYSTEM, osNode.getType());

        // find the OS using a non-wildcard, explicit query path
        query = PlatformPath.builder().segment(PlatformResourceType.OPERATING_SYSTEM, osNode.getId()).build();
        map = oshi.discoverResources(query);
        Assert.assertEquals("Should only have 1 operating system resource", map.size(), 1);

        // Memory
        query = PlatformPath.builder().segments(osPath).any(PlatformResourceType.MEMORY).build();
        map = oshi.discoverResources(query);
        Assert.assertEquals("Should only have 1 memory resource", map.size(), 1);
        PlatformPath memPath = map.keySet().iterator().next();
        PlatformResourceNode memNode = map.values().iterator().next();
        print("---> Memory path=[%s], node=[%s]", memPath, memNode);
        Assert.assertEquals(PlatformResourceType.MEMORY, memNode.getType());

        // find the Memory using a non-wildcard, explicit query path
        query = PlatformPath.builder()
                .segment(PlatformResourceType.MEMORY, PlatformResourceType.MEMORY.getName().getNameString()).build();
        map = oshi.discoverResources(query);
        Assert.assertEquals("Should only have 1 memory resource", map.size(), 1);

        // File Stores
        query = PlatformPath.builder().segments(osPath).any(PlatformResourceType.FILE_STORE).build();
        map = oshi.discoverResources(query);
        Assert.assertTrue("Should have at least 1 file store resource", !map.isEmpty());
        for (Map.Entry<PlatformPath, PlatformResourceNode> entry : map.entrySet()) {
            PlatformPath path = entry.getKey();
            PlatformResourceNode node = entry.getValue();
            print("---> FileStore path=[%s], node=[%s]", path, node);
            Assert.assertEquals(PlatformResourceType.FILE_STORE, node.getType());

            // find it using a non-wildcard, explicit query path
            query = PlatformPath.builder().segment(PlatformResourceType.FILE_STORE, node.getId()).build();
            Map<PlatformPath, PlatformResourceNode> singletonMap = oshi.discoverResources(query);
            Assert.assertEquals("Should only have returned 1", singletonMap.size(), 1);
        }

        // Processors
        query = PlatformPath.builder().segments(osPath).any(PlatformResourceType.PROCESSOR).build();
        map = oshi.discoverResources(query);
        Assert.assertTrue("Should have at least 1 processor resource", !map.isEmpty());
        for (Map.Entry<PlatformPath, PlatformResourceNode> entry : map.entrySet()) {
            PlatformPath path = entry.getKey();
            PlatformResourceNode node = entry.getValue();
            print("---> Processor path=[%s], node=[%s]", path, node);
            Assert.assertEquals(PlatformResourceType.PROCESSOR, node.getType());

            // find it using a non-wildcard, explicit query path
            query = PlatformPath.builder().segment(PlatformResourceType.PROCESSOR, node.getId()).build();
            Map<PlatformPath, PlatformResourceNode> singletonMap = oshi.discoverResources(query);
            Assert.assertEquals("Should only have returned 1", singletonMap.size(), 1);
        }

        // Power Sources
        query = PlatformPath.builder().segments(osPath).any(PlatformResourceType.POWER_SOURCE).build();
        map = oshi.discoverResources(query);
        // some computers don't have power sources like batteries so this could be empty
        for (Map.Entry<PlatformPath, PlatformResourceNode> entry : map.entrySet()) {
            PlatformPath path = entry.getKey();
            PlatformResourceNode node = entry.getValue();
            print("---> PowerSource path=[%s], node=[%s]", path, node);
            Assert.assertEquals(PlatformResourceType.POWER_SOURCE, node.getType());

            // find it using a non-wildcard, explicit query path
            query = PlatformPath.builder().segment(PlatformResourceType.POWER_SOURCE, node.getId()).build();
            Map<PlatformPath, PlatformResourceNode> singletonMap = oshi.discoverResources(query);
            Assert.assertEquals("Should only have returned 1", singletonMap.size(), 1);
        }
    }

    @Test
    public void testMetrics() {
        Double val;
        OshiPlatformCache oshi = newOshiPlatformCache();

        // Memory
        for (Name metricName : PlatformResourceType.MEMORY.getMetricNames()) {
            val = oshi.getMemoryMetric(metricName);
            print("Memory metric [%s]=[%s]", metricName, val);
            Assert.assertNotNull(val);
        }
        try {
            oshi.getMemoryMetric(new Name("invalidMetricName"));
            Assert.fail("Exception should have been thrown on bad memory metric name");
        } catch (Exception ok) {
        }

        // File Stores
        Map<String, OSFileStore> fileStores = oshi.getFileStores();
        for (OSFileStore fs : fileStores.values()) {
            for (Name metricName : PlatformResourceType.FILE_STORE.getMetricNames()) {
                val = oshi.getFileStoreMetric(fs.getName(), metricName);
                print("FileStore [%s] metric [%s]=[%s]", fs.getName(), metricName, val);
                Assert.assertNotNull(val);
            }
            try {
                oshi.getFileStoreMetric(fs.getName(), new Name("invalidMetricName"));
                Assert.fail("Exception should have been thrown on bad filestore metric name");
            } catch (Exception ok) {
            }
        }

        // Processors
        Map<String, Processor> processors = oshi.getProcessors();
        for (Processor p : processors.values()) {
            for (Name metricName : PlatformResourceType.PROCESSOR.getMetricNames()) {
                val = oshi.getProcessorMetric("" + p.getProcessorNumber(), metricName);
                print("Processor [%s] metric [%s]=[%s]", "" + p.getProcessorNumber(), metricName, val);
                Assert.assertNotNull(val);
            }
            try {
                oshi.getProcessorMetric("" + p.getProcessorNumber(), new Name("invalidMetricName"));
                Assert.fail("Exception should have been thrown on bad processor metric name");
            } catch (Exception ok) {
            }
        }

        // Power Sources
        Map<String, PowerSource> powersources = oshi.getPowerSources();
        for (PowerSource p : powersources.values()) {
            for (Name metricName : PlatformResourceType.POWER_SOURCE.getMetricNames()) {
                val = oshi.getPowerSourceMetric(p.getName(), metricName);
                print("PowerSource [%s] metric [%s]=[%s]", "" + p.getName(), metricName, val);
                Assert.assertNotNull(val);
            }
            try {
                oshi.getPowerSourceMetric(p.getName(), new Name("invalidMetricName"));
                Assert.fail("Exception should have been thrown on bad powersource metric name");
            } catch (Exception ok) {
            }
        }

        // some more negative testing
        Assert.assertNull(oshi.getFileStoreMetric("invalidName", new Name("invalidMetricName")));
        Assert.assertNull(oshi.getProcessorMetric("invalidName", new Name("invalidMetricName")));
        Assert.assertNull(oshi.getPowerSourceMetric("invalidName", new Name("invalidMetricName")));
    }

    @Test
    public void testRefresh() {
        OshiPlatformCache oshi = newOshiPlatformCache();
        Object os1 = oshi.getOperatingSystem();
        Object mem1 = oshi.getMemory();
        Object fs1 = oshi.getFileStores();
        Object proc1 = oshi.getProcessors();
        Object ps1 = oshi.getPowerSources();

        // see that they are cached - same objects as before
        Object os2 = oshi.getOperatingSystem();
        Object mem2 = oshi.getMemory();
        Object fs2 = oshi.getFileStores();
        Object proc2 = oshi.getProcessors();
        Object ps2 = oshi.getPowerSources();
        Assert.assertSame(os1, os2);
        Assert.assertSame(mem1, mem2);
        Assert.assertSame(fs1, fs2);
        Assert.assertSame(proc1, proc2);
        Assert.assertSame(ps1, ps2);

        // refresh and see that they are now different
        oshi.refresh();
        os2 = oshi.getOperatingSystem();
        mem2 = oshi.getMemory();
        fs2 = oshi.getFileStores();
        proc2 = oshi.getProcessors();
        ps2 = oshi.getPowerSources();
        Assert.assertNotSame(os1, os2);
        Assert.assertNotSame(mem1, mem2);
        Assert.assertNotSame(fs1, fs2);
        Assert.assertNotSame(proc1, proc2);
        Assert.assertNotSame(ps1, ps2);
    }

    @Test
    public void getOperatingSystemInfo() {
        OshiPlatformCache oshi = newOshiPlatformCache();
        OperatingSystem os = oshi.getOperatingSystem();

        Assert.assertNotNull(os);

        String family = os.getFamily();
        String manu = os.getManufacturer();
        OperatingSystemVersion version = os.getVersion();

        Assert.assertNotNull(family);
        Assert.assertNotNull(manu);
        Assert.assertNotNull(version);

        print("===OPERATION SYSTEM==");
        print("  Family=[%s]", family);
        print("  Manufacturer=[%s]", manu);
        print("  Version=[%s](%s)", version, version.getClass());
        print("  toString=[%s]", os.toString());
    }

    @Test
    public void getFileStores() {
        OshiPlatformCache oshi = newOshiPlatformCache();
        Map<String, OSFileStore> filestores = oshi.getFileStores();
        Assert.assertNotNull(filestores);

        int i = 0;
        for (OSFileStore filestore : filestores.values()) {
            String name = filestore.getName();
            String description = filestore.getDescription();
            long usableSpace = filestore.getUsableSpace();
            long totalSpace = filestore.getTotalSpace();

            Assert.assertNotNull(name);
            Assert.assertNotNull(description);
            Assert.assertTrue(usableSpace > -1L);
            Assert.assertTrue(totalSpace > -1L);

            print("===FILE STORE #%d ===", ++i);
            print("  Name=[%s]", name);
            print("  Description=[%s]", description);
            print("  UsableSpace=[%s] (%d)", FormatUtil.formatBytes(usableSpace), usableSpace);
            print("  TotalSpace=[%s] (%d)", FormatUtil.formatBytes(totalSpace), totalSpace);
            print("  toString=[%s]", filestore.toString());
        }
    }

    @Test
    public void getMemory() {
        OshiPlatformCache oshi = newOshiPlatformCache();
        Memory memory = oshi.getMemory();
        Assert.assertNotNull(memory);

        long avail = memory.getAvailable();
        long total = memory.getTotal();

        Assert.assertTrue(avail > -1L);
        Assert.assertTrue(total > -1L);

        print("===MEMORY ===");
        print("  Available=[%s] (%d)", FormatUtil.formatBytes(avail), avail);
        print("  Total=[%s] (%d)", FormatUtil.formatBytes(total), total);
        print("  toString=[%s]", memory.toString());
    }

    @Test
    public void getProcessors() {
        OshiPlatformCache oshi = newOshiPlatformCache();
        Map<String, Processor> processors = oshi.getProcessors();
        Assert.assertNotNull(processors);

        Util.sleep(2000L); // sleep to let processors be able to calculate load

        int i = 0;
        for (Processor processor : processors.values()) {
            String name = processor.getName();
            String family;
            try {
                family = processor.getFamily();
            } catch (UnsupportedOperationException e) {
                family = "";
                print(processor.getClass().getName() + ".getFamily() unsupported on " + System.getProperty("os.name")
                        + " " + System.getProperty("os.version") + " " + System.getProperty("os.arch"));
            }
            String identifier = processor.getIdentifier();
            String model;
            try {
                model = processor.getModel();
            } catch (UnsupportedOperationException e) {
                model = "";
                print(processor.getClass().getName() + ".getModel() unsupported on " + System.getProperty("os.name")
                        + " " + System.getProperty("os.version") + " " + System.getProperty("os.arch"));
            }
            String stepping;
            try {
               stepping =  processor.getStepping();
            } catch (UnsupportedOperationException e) {
                stepping = "";
                print(processor.getClass().getName() + ".getStepping() unsupported on " + System.getProperty("os.name")
                        + " " + System.getProperty("os.version") + " " + System.getProperty("os.arch"));
            }

            String vendor = processor.getVendor();
            double systemCpuLoad = processor.getSystemCpuLoad();
            double systemLoadAverage = processor.getSystemLoadAverage();
            int processorNumber = processor.getProcessorNumber();
            long systemUpTime = processor.getSystemUptime();
            long vendorFrequency = processor.getVendorFreq();
            boolean isCpu64bit;
            try {
                isCpu64bit = processor.isCpu64bit();
            } catch (UnsupportedOperationException e) {
                print(processor.getClass().getName() + ".isCpu64bit() unsupported on " + System.getProperty("os.name")
                        + " " + System.getProperty("os.version") + " " + System.getProperty("os.arch"));
                isCpu64bit = false;
            }
            long[] processorCpuLoadTicks = processor.getProcessorCpuLoadTicks();
            long[] systemCpuLoadTicks = processor.getSystemCpuLoadTicks();

            processor.getProcessorCpuLoadBetweenTicks();
            processor.getSystemCpuLoadBetweenTicks();
            Util.sleep(1100L); // provide some time to let the getXCpuLoadBetweenTicks have good data to use
            double processorCpuLoadBetweenTicks = processor.getProcessorCpuLoadBetweenTicks();
            double systemCpuLoadBetweenTicks = processor.getSystemCpuLoadBetweenTicks();

            Assert.assertNotNull(name);
            Assert.assertNotNull(family);
            Assert.assertNotNull(identifier);
            Assert.assertNotNull(model);
            Assert.assertNotNull(stepping);
            Assert.assertNotNull(vendor);

            print("===PROCESSOR #%d ===", ++i);
            print("  Name=[%s]", name);
            print("  Family=[%s]", family);
            print("  Identifier=[%s]", identifier);
            print("  Model=[%s]", model);
            print("  Stepping=[%s]", stepping);
            print("  Vendor=[%s]", vendor);
            print("  SystemCpuLoad=[%.2f]", systemCpuLoad);
            print("  SystemLoadAverage=[%.2f]", systemLoadAverage);
            print("  ProcessorNumber=[%d]", processorNumber);
            print("  SystemUpTime=[%s] (%d)", FormatUtil.formatElapsedSecs(systemUpTime), systemUpTime);
            print("  VendorFreq=[%s] (%d)", FormatUtil.formatHertz(vendorFrequency), vendorFrequency);
            print("  IsCpu64BitVendor=[%s]", isCpu64bit);
            print("  ProcessorCpuLoadTicks=[user=%d/nice=%d/system=%d/idle=%d]",
                    processorCpuLoadTicks[0], processorCpuLoadTicks[1],
                    processorCpuLoadTicks[2], processorCpuLoadTicks[3]);
            print("  SystemCpuLoadTicks=[user=%d/nice=%d/system=%d/idle=%d]",
                    systemCpuLoadTicks[0], systemCpuLoadTicks[1],
                    systemCpuLoadTicks[2], systemCpuLoadTicks[3]);
            print("  ProcessorCpuLoadBetweenTicks=[%.2f]", processorCpuLoadBetweenTicks);
            print("  SystemCpuLoadBetweenTicks=[%.2f]", systemCpuLoadBetweenTicks);

            print("  toString=[%s]", processor.toString());
        }
    }

    @Test
    public void getPowerSources() {
        OshiPlatformCache oshi = newOshiPlatformCache();
        Map<String, PowerSource> powersources = oshi.getPowerSources();
        Assert.assertNotNull(powersources);

        if (powersources.size() == 0) {
            print("===NO POWER SOURCES ON THIS MACHINE===");
        } else {
            int i = 0;
            for (PowerSource powersource : powersources.values()) {
                String name = powersource.getName();
                double remainingCapacity = powersource.getRemainingCapacity();
                double timeRemaining = powersource.getTimeRemaining();

                Assert.assertNotNull(name);

                print("===POWER SOURCE #%d ===", ++i);
                print("  Name=[%s]", name);
                print("  RemainingCapacity=[%.0f%%] (%.2f)", remainingCapacity * 100, remainingCapacity);
                long roundedTimeRemaining = Math.round(timeRemaining);
                if (roundedTimeRemaining == -1) {
                    print("  TimeRemaining=[calculating] (%.1f)", timeRemaining);
                } else if (roundedTimeRemaining == -2) {
                    print("  TimeRemaining=[unlimited] (%.1f)", timeRemaining);
                } else {
                    print("  TimeRemaining=[%s] (%.1f)", FormatUtil.formatElapsedSecs(roundedTimeRemaining),
                            timeRemaining);
                }
                print("  toString=[%s]", powersource.toString());
            }
        }
    }

    private OshiPlatformCache newOshiPlatformCache() {
        return new OshiPlatformCache("testFeedId", null);
    }

    private void print(String msg, Object... args) {
        System.out.println(String.format(msg, args));
    }
}
