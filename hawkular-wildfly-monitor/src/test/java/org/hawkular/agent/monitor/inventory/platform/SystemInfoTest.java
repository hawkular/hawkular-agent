/*
 * Copyright 2015 Red Hat, Inc. and/or its affiliates
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
package org.hawkular.agent.monitor.inventory.platform;

import org.junit.Assert;
import org.junit.Test;

import oshi.SystemInfo;
import oshi.hardware.HardwareAbstractionLayer;
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
public class SystemInfoTest {

    @Test
    public void getOperatingSystemInfo() {
        SystemInfo si = new SystemInfo();

        OperatingSystem os = si.getOperatingSystem();

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
        SystemInfo si = new SystemInfo();

        HardwareAbstractionLayer hardware = si.getHardware();
        Assert.assertNotNull(hardware);

        OSFileStore[] filestores = hardware.getFileStores();
        Assert.assertNotNull(filestores);

        int i = 0;
        for (OSFileStore filestore : filestores) {
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
        SystemInfo si = new SystemInfo();

        HardwareAbstractionLayer hardware = si.getHardware();
        Assert.assertNotNull(hardware);

        Memory memory = hardware.getMemory();
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
        SystemInfo si = new SystemInfo();

        HardwareAbstractionLayer hardware = si.getHardware();
        Assert.assertNotNull(hardware);

        Processor[] processors = hardware.getProcessors();
        Assert.assertNotNull(processors);

        Util.sleep(2000L); // sleep to let processors be able to calculate load

        int i = 0;
        for (Processor processor : processors) {
            String name = processor.getName();
            String family = processor.getFamily();
            String identifier = processor.getIdentifier();
            String model = processor.getModel();
            String stepping = processor.getStepping();
            String vendor = processor.getVendor();
            double systemCpuLoad = processor.getSystemCpuLoad();
            double systemLoadAverage = processor.getSystemLoadAverage();
            int processorNumber = processor.getProcessorNumber();
            long systemUpTime = processor.getSystemUptime();
            long vendorFrequency = processor.getVendorFreq();
            boolean isCpu64bit = processor.isCpu64bit();
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
        SystemInfo si = new SystemInfo();

        HardwareAbstractionLayer hardware = si.getHardware();
        Assert.assertNotNull(hardware);

        PowerSource[] powersources = hardware.getPowerSources();
        Assert.assertNotNull(powersources);

        if (powersources.length == 0) {
            print("===NO POWER SOURCES ON THIS MACHINE===");
        } else {
            int i = 0;
            for (PowerSource powersource : powersources) {
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

    private void print(String msg, Object... args) {
        System.out.println(String.format(msg, args));
    }
}
