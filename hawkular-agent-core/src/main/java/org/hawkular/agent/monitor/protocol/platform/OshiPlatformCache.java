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

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock.ReadLock;
import java.util.concurrent.locks.ReentrantReadWriteLock.WriteLock;

import org.hawkular.agent.monitor.inventory.ID;
import org.hawkular.agent.monitor.log.AgentLoggers;
import org.hawkular.agent.monitor.log.MsgLogger;
import org.hawkular.agent.monitor.protocol.platform.Constants.PlatformMetricType;
import org.hawkular.agent.monitor.protocol.platform.Constants.PlatformResourceType;
import org.hawkular.agent.monitor.util.Util;

import oshi.SystemInfo;
import oshi.hardware.CentralProcessor;
import oshi.hardware.GlobalMemory;
import oshi.hardware.PowerSource;
import oshi.software.os.OSFileStore;
import oshi.software.os.OperatingSystem;

/**
 * This caches a snapshot of platform data as provided by the OSHI library.
 * If you want to refresh the cache with new values, you must call {@link #refresh()}, otherwise,
 * the same cached values will be used across calls.
 *
 * @author John Mazzitelli
 */
public class OshiPlatformCache {
    private static final MsgLogger log = AgentLoggers.getLogger(OshiPlatformCache.class);

    private SystemInfo sysInfo;
    private final Map<PlatformResourceType, Map<String, ? extends Object>> sysInfoCache;
    private final ReentrantReadWriteLock rwLock = new ReentrantReadWriteLock();
    private final ReadLock rLock = rwLock.readLock();
    private final WriteLock wLock = rwLock.writeLock();
    private final String feedId;
    private final String machineId;
    private final String containerId;

    /**
     * Creates the cache of OSHi platform data.
     *
     * @param feedId       he feed ID
     * @param machineId    the machine ID - if null, one will be attempted to be discovered
     * @param containerId  the container ID - if null, one will be attempted to be discovered
     *
     * @see Util#getMachineId()
     */
    public OshiPlatformCache(String feedId, String machineId, String containerId) {
        sysInfo = new SystemInfo();
        sysInfoCache = new HashMap<>(5);
        this.feedId = feedId;
        this.machineId = (machineId != null) ? machineId : Util.getMachineId();
        this.containerId = (containerId != null) ? containerId : Util.getContainerId();
    }

    /**
     * Clears the cache of all OSHI data. Subsequent calls to retrieve data will be refreshed
     * with the latest data (but will be more expensive since the data has to be recollected
     * from the underlying operating system).
     */
    public void refresh() {
        wLock.lock();
        try {
            sysInfo = new SystemInfo();
            sysInfoCache.clear();
        } finally {
            wLock.unlock();
        }
    }

    /**
     * @return information about the operating system.
     */
    public OperatingSystem getOperatingSystem() {
        OperatingSystem ret;

        wLock.lock();
        try {
            if (!sysInfoCache.containsKey(PlatformResourceType.OPERATING_SYSTEM)) {
                HashMap<String, OperatingSystem> cache = new HashMap<>(1);
                OperatingSystem os = sysInfo.getOperatingSystem();
                cache.put(PlatformResourceType.OPERATING_SYSTEM.getResourceTypeId().getIDString(), os);
                sysInfoCache.put(PlatformResourceType.OPERATING_SYSTEM, cache);
            }
        } finally {
            // downgrade to a read-only lock since we just need it to read from the cache
            rLock.lock();
            try {
                wLock.unlock();
                ret = (OperatingSystem) sysInfoCache.get(PlatformResourceType.OPERATING_SYSTEM)
                        .get(PlatformResourceType.OPERATING_SYSTEM.getResourceTypeId().getIDString());

            } finally {
                rLock.unlock();
            }
        }

        return ret;
    }

    /**
     * @return information about all file stores on the platform
     */
    @SuppressWarnings("unchecked")
    public Map<String, OSFileStore> getFileStores() {
        Map<String, OSFileStore> ret;

        wLock.lock();
        try {
            if (!sysInfoCache.containsKey(PlatformResourceType.FILE_STORE)) {
                HashMap<String, OSFileStore> cache = new HashMap<>();
                OSFileStore[] arr = sysInfo.getOperatingSystem().getFileSystem().getFileStores();
                if (arr != null) {
                    for (OSFileStore item : arr) {
                        cache.put(item.getName(), item);
                    }
                }
                sysInfoCache.put(PlatformResourceType.FILE_STORE, cache);
            }
        } finally {
            // downgrade to a read-only lock since we just need it to read from the cache
            rLock.lock();
            try {
                wLock.unlock();
                ret = (Map<String, OSFileStore>) sysInfoCache.get(PlatformResourceType.FILE_STORE);
            } finally {
                rLock.unlock();
            }
        }

        return ret;
    }

    /**
     * @return information about all the platform's memory
     */
    public GlobalMemory getMemory() {
        GlobalMemory ret;

        wLock.lock();
        try {

            if (!sysInfoCache.containsKey(PlatformResourceType.MEMORY)) {
                HashMap<String, GlobalMemory> cache = new HashMap<>(1);
                GlobalMemory mem = sysInfo.getHardware().getMemory();
                cache.put(PlatformResourceType.MEMORY.getResourceTypeId().getIDString(), mem);
                sysInfoCache.put(PlatformResourceType.MEMORY, cache);
            }
        } finally {
            // downgrade to a read-only lock since we just need it to read from the cache
            rLock.lock();
            try {
                wLock.unlock();
                ret = (GlobalMemory) sysInfoCache.get(PlatformResourceType.MEMORY)
                        .get(PlatformResourceType.MEMORY.getResourceTypeId().getIDString());
            } finally {
                rLock.unlock();
            }
        }

        return ret;
    }

    /**
     * @return information about all processors/CPUs on the platform
     */
    public CentralProcessor getProcessor() {
        CentralProcessor ret;

        wLock.lock();
        try {

            if (!sysInfoCache.containsKey(PlatformResourceType.PROCESSOR)) {
                HashMap<String, CentralProcessor> cache = new HashMap<>(1);
                CentralProcessor cp = sysInfo.getHardware().getProcessor();
                cache.put(PlatformResourceType.PROCESSOR.getResourceTypeId().getIDString(), cp);
                sysInfoCache.put(PlatformResourceType.PROCESSOR, cache);
            }
        } finally {
            // downgrade to a read-only lock since we just need it to read from the cache
            rLock.lock();
            try {
                wLock.unlock();
                ret = (CentralProcessor) sysInfoCache.get(PlatformResourceType.PROCESSOR)
                        .get(PlatformResourceType.PROCESSOR.getResourceTypeId().getIDString());
            } finally {
                rLock.unlock();
            }
        }

        return ret;
    }

    /**
     * @return information about all power sources (e.g. batteries) on the platform.
     */
    @SuppressWarnings("unchecked")
    public Map<String, PowerSource> getPowerSources() {
        Map<String, PowerSource> ret;

        wLock.lock();
        try {

            if (!sysInfoCache.containsKey(PlatformResourceType.POWER_SOURCE)) {
                HashMap<String, PowerSource> cache = new HashMap<>();
                PowerSource[] arr = sysInfo.getHardware().getPowerSources();
                if (arr != null) {
                    for (PowerSource item : arr) {
                        cache.put(item.getName(), item);
                    }
                }
                sysInfoCache.put(PlatformResourceType.POWER_SOURCE, cache);
            }
        } finally {
            // downgrade to a read-only lock since we just need it to read from the cache
            rLock.lock();
            try {
                wLock.unlock();
                ret = (Map<String, PowerSource>) sysInfoCache.get(PlatformResourceType.POWER_SOURCE);
            } finally {
                rLock.unlock();
            }
        }

        return ret;
    }

    /**
     * Returns the given metric's value, or null if there is no power source with the given name.
     *
     * @param powerSourceName name of power source
     * @param metricToCollect the metric to collect
     * @return the value of the metric, or null if there is no power source with the given name
     */
    public Double getPowerSourceMetric(String powerSourceName, ID metricToCollect) {

        Map<String, PowerSource> cache = getPowerSources();
        PowerSource powerSource = cache.get(powerSourceName);
        if (powerSource == null) {
            return null;
        }

        if (PlatformMetricType.POWER_SOURCE_REMAINING_CAPACITY.getMetricTypeId().equals(metricToCollect)) {
            return powerSource.getRemainingCapacity();
        } else if (PlatformMetricType.POWER_SOURCE_TIME_REMAINING.getMetricTypeId().equals(metricToCollect)) {
            return powerSource.getTimeRemaining();
        } else {
            throw new UnsupportedOperationException("Invalid power source metric to collect: " + metricToCollect);
        }
    }

    /**
     * Returns the given metric's value, or null if there is no processor with the given number.
     *
     * @param processorNumber number of the processor, as a String
     * @param metricToCollect the metric to collect
     * @return the value of the metric, or null if there is no processor with the given number
     */
    public Double getProcessorMetric(String processorNumber, ID metricToCollect) {

        CentralProcessor processor = getProcessor();
        if (processor == null) {
            return null;
        }

        int processorIndex;
        try {
            processorIndex = Integer.parseInt(processorNumber);
            if (processorIndex < 0 || processorIndex >= processor.getLogicalProcessorCount()) {
                return null;
            }
        } catch (Exception e) {
            return null;
        }

        if (PlatformMetricType.PROCESSOR_CPU_USAGE.getMetricTypeId().equals(metricToCollect)) {
            return processor.getProcessorCpuLoadBetweenTicks()[processorIndex];
        } else {
            throw new UnsupportedOperationException("Invalid processor metric to collect: " + metricToCollect);
        }
    }

    /**
     * Returns the given metric's value, or null if there is no file store with the given name.
     *
     * @param fileStoreNameName name of file store
     * @param metricToCollect the metric to collect
     * @return the value of the metric, or null if there is no file store with the given name
     */
    public Double getFileStoreMetric(String fileStoreNameName, ID metricToCollect) {

        Map<String, OSFileStore> cache = getFileStores();
        OSFileStore fileStore = cache.get(fileStoreNameName);
        if (fileStore == null) {
            return null;
        }

        if (PlatformMetricType.FILE_STORE_TOTAL_SPACE.getMetricTypeId().equals(metricToCollect)) {
            return Double.valueOf(fileStore.getTotalSpace());
        } else if (PlatformMetricType.FILE_STORE_USABLE_SPACE.getMetricTypeId().equals(metricToCollect)) {
            return Double.valueOf(fileStore.getUsableSpace());
        } else {
            throw new UnsupportedOperationException("Invalid file store metric to collect: " + metricToCollect);
        }
    }

    /**
     * Returns the given memory metric's value.
     *
     * @param metricToCollect the metric to collect
     * @return the value of the metric
     */
    public Double getMemoryMetric(ID metricToCollect) {

        GlobalMemory mem = getMemory();

        if (PlatformMetricType.MEMORY_AVAILABLE.getMetricTypeId().equals(metricToCollect)) {
            return Double.valueOf(mem.getAvailable());
        } else if (PlatformMetricType.MEMORY_TOTAL.getMetricTypeId().equals(metricToCollect)) {
            return Double.valueOf(mem.getTotal());
        } else {
            throw new UnsupportedOperationException("Invalid memory metric to collect: " + metricToCollect);
        }
    }

    /**
     * Returns the given OS metric's value.
     *
     * @param metricId the metric to collect
     * @return the value of the metric
     */
    public Double getOperatingSystemMetric(ID metricId) {
        if (PlatformMetricType.OS_SYS_CPU_LOAD.getMetricTypeId().equals(metricId)) {
            return Double.valueOf(getProcessor().getSystemCpuLoad());
        } else if (PlatformMetricType.OS_SYS_LOAD_AVG.getMetricTypeId().equals(metricId)) {
            return Double.valueOf(getProcessor().getSystemLoadAverage());
        } else if (PlatformMetricType.OS_PROCESS_COUNT.getMetricTypeId().equals(metricId)) {
            return Double.valueOf(getOperatingSystem().getProcessCount());
        } else {
            throw new UnsupportedOperationException("Invalid OS metric to collect: " + metricId);
        }
    }

    /**
     * Given a platform resource type, a name, and a metric name, this will return that metric's value,
     * or null if there is no resource that can be identified by the name and type.
     *
     * @param type identifies the platform resource whose metric is to be collected
     * @param name name of the resource whose metric is to be collected
     * @param metricToCollect the metric to collect
     * @return the value of the metric, or null if there is no resource identified by the name
     */
    public Double getMetric(PlatformResourceType type, String name, ID metricToCollect) {
        switch (type) {
            case OPERATING_SYSTEM: {
                return getOperatingSystemMetric(metricToCollect);
            }
            case MEMORY: {
                return getMemoryMetric(metricToCollect);
            }
            case FILE_STORE: {
                return getFileStoreMetric(name, metricToCollect);
            }
            case PROCESSOR: {
                return getProcessorMetric(name, metricToCollect);
            }
            case POWER_SOURCE: {
                return getPowerSourceMetric(name, metricToCollect);
            }
            default: {
                throw new IllegalArgumentException(
                        "Platform resource [" + type + "][" + name + "] does not have metrics");
            }
        }
    }

    /**
     * @return the unique machine ID for this platform if it is known. Otherwise, null is returned.
     */
    public String getMachineId() {
        return machineId;
    }

    /**
     * @return the unique container ID for this platform if it is known. Otherwise, null is returned.
     */
    public String getContainerId() {
        return containerId;
    }
}
