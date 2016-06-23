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

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock.ReadLock;
import java.util.concurrent.locks.ReentrantReadWriteLock.WriteLock;

import org.hawkular.agent.monitor.inventory.Name;
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
    private SystemInfo sysInfo;
    private final Map<Constants.PlatformResourceType, Map<String, ? extends Object>> sysInfoCache;
    private final ReentrantReadWriteLock rwLock = new ReentrantReadWriteLock();
    private final ReadLock rLock = rwLock.readLock();
    private final WriteLock wLock = rwLock.writeLock();
    private final String feedId;
    private final String machineId;

    /**
     * Creates the cache of OSHi platform data.
     *
     * @param feedId       he feed ID
     * @param machineId    the machine ID - if null, one will be attempted to be discovered
     *
     * @see Util#getSystemId()
     */
    public OshiPlatformCache(String feedId, String machineId) {
        sysInfo = new SystemInfo();
        sysInfoCache = new HashMap<>(5);
        this.feedId = feedId;
        this.machineId = (machineId != null) ? machineId : Util.getSystemId();
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
                cache.put(PlatformResourceType.OPERATING_SYSTEM.getName().getNameString(), os);
                sysInfoCache.put(PlatformResourceType.OPERATING_SYSTEM, cache);
            }
        } finally {
            // downgrade to a read-only lock since we just need it to read from the cache
            rLock.lock();
            try {
                wLock.unlock();
                ret = (OperatingSystem) sysInfoCache.get(PlatformResourceType.OPERATING_SYSTEM)
                        .get(PlatformResourceType.OPERATING_SYSTEM.getName().getNameString());

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
                OSFileStore[] arr = sysInfo.getHardware().getFileStores();
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
                cache.put(PlatformResourceType.MEMORY.getName().getNameString(), mem);
                sysInfoCache.put(PlatformResourceType.MEMORY, cache);
            }
        } finally {
            // downgrade to a read-only lock since we just need it to read from the cache
            rLock.lock();
            try {
                wLock.unlock();
                ret = (GlobalMemory) sysInfoCache.get(PlatformResourceType.MEMORY)
                        .get(PlatformResourceType.MEMORY.getName().getNameString());
            } finally {
                rLock.unlock();
            }
        }

        return ret;
    }

    /**
     * @return information about all processors/CPUs on the platform
     */
    @SuppressWarnings("unchecked")
    public CentralProcessor getProcessor() {
        CentralProcessor ret;

        wLock.lock();
        try {

            if (!sysInfoCache.containsKey(PlatformResourceType.PROCESSOR)) {
                HashMap<String, CentralProcessor> cache = new HashMap<>(1);
                CentralProcessor cp = sysInfo.getHardware().getProcessor();
                cache.put(PlatformResourceType.PROCESSOR.getName().getNameString(), cp);
                sysInfoCache.put(PlatformResourceType.PROCESSOR, cache);
            }
        } finally {
            // downgrade to a read-only lock since we just need it to read from the cache
            rLock.lock();
            try {
                wLock.unlock();
                ret = (CentralProcessor) sysInfoCache.get(PlatformResourceType.PROCESSOR)
                        .get(PlatformResourceType.PROCESSOR.getName().getNameString());
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
    public Double getPowerSourceMetric(String powerSourceName, Name metricToCollect) {

        Map<String, PowerSource> cache = getPowerSources();
        PowerSource powerSource = cache.get(powerSourceName);
        if (powerSource == null) {
            return null;
        }

        if (Constants.POWER_SOURCE_REMAINING_CAPACITY.equals(metricToCollect)) {
            return powerSource.getRemainingCapacity();
        } else if (Constants.POWER_SOURCE_TIME_REMAINING.equals(metricToCollect)) {
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
    public Double getProcessorMetric(String processorNumber, Name metricToCollect) {

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


        if (Constants.PROCESSOR_CPU_USAGE.equals(metricToCollect)) {
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
    public Double getFileStoreMetric(String fileStoreNameName, Name metricToCollect) {

        Map<String, OSFileStore> cache = getFileStores();
        OSFileStore fileStore = cache.get(fileStoreNameName);
        if (fileStore == null) {
            return null;
        }

        if (Constants.FILE_STORE_TOTAL_SPACE.equals(metricToCollect)) {
            return Double.valueOf(fileStore.getTotalSpace());
        } else if (Constants.FILE_STORE_USABLE_SPACE.equals(metricToCollect)) {
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
    public Double getMemoryMetric(Name metricToCollect) {

        GlobalMemory mem = getMemory();

        if (Constants.MEMORY_AVAILABLE.equals(metricToCollect)) {
            return Double.valueOf(mem.getAvailable());
        } else if (Constants.MEMORY_TOTAL.equals(metricToCollect)) {
            return Double.valueOf(mem.getTotal());
        } else {
            throw new UnsupportedOperationException("Invalid memory metric to collect: " + metricToCollect);
        }
    }

    /**
     * Returns the given OS metric's value.
     *
     * @param metricToCollect the metric to collect
     * @return the value of the metric
     */
    public Double getOperatingSystemMetric(Name metricToCollect) {

        CentralProcessor cp = getProcessor();

        if (Constants.OPERATING_SYSTEM_SYS_CPU_LOAD.equals(metricToCollect)) {
            return Double.valueOf(cp.getSystemCpuLoad());
        } else if (Constants.OPERATING_SYSTEM_SYS_LOAD_AVG.equals(metricToCollect)) {
            return Double.valueOf(cp.getSystemLoadAverage());
        } else if (Constants.OPERATING_SYSTEM_PROCESS_COUNT.equals(metricToCollect)) {
            return Double.valueOf(cp.getProcessCount());
        } else {
            throw new UnsupportedOperationException("Invalid OS metric to collect: " + metricToCollect);
        }
    }

    /**
     * Given a platform resource node and a metric name, this will return that metric's value,
     * or null if there is no resource that can be identified by the node.
     *
     * @param node identifies the platform resource whose metric is to be collected
     * @param metricToCollect the metric to collect
     * @return the value of the metric, or null if there is no resource identified by the node
     */
    public Double getMetric(PlatformResourceNode node, Name metricToCollect) {
        switch (node.getType()) {
            case OPERATING_SYSTEM: {
                return getOperatingSystemMetric(metricToCollect);
            }
            case MEMORY: {
                return getMemoryMetric(metricToCollect);
            }
            case FILE_STORE: {
                return getFileStoreMetric(node.getId(), metricToCollect);
            }
            case PROCESSOR: {
                return getProcessorMetric(node.getId(), metricToCollect);
            }
            case POWER_SOURCE: {
                return getPowerSourceMetric(node.getId(), metricToCollect);
            }
            default: {
                throw new IllegalArgumentException("Platform resource node [" + node + "] does not have metrics");
            }
        }
    }

    /**
     * Helps with discovery of platform resources by obtaining all the resources of the given path.
     * Since we know the OSHI hierarchy is only one level deep (OS is the root parent, and all the rest
     * are underneath (e.g. file stores, memory, processors, powersources), this method looks at the
     * given path's last segment name to determine what it should return.
     *
     * @param platformPath the path of the resources to find.
     * @return the resources found
     */
    public Map<PlatformPath, PlatformResourceNode> discoverResources(PlatformPath platformPath) {
        HashMap<PlatformPath, PlatformResourceNode> results = new HashMap<>();

        // we will need the os path regardless of what we do in this method, so build it now
        OperatingSystem os = getOperatingSystem();
        String osId = this.feedId + "_OperatingSystem";
        PlatformPath osPath = PlatformPath.builder()
                .segment(PlatformResourceType.OPERATING_SYSTEM, osId)
                .build();

        // The type hierarchy is fixed; it is all based on what SystemInfo provides us. So alot of our discovery
        // is really hardwired since we already know what resources we should be expecting.
        // We know we will get a top level operating system resource. It will always be discovered.
        // We know all the resources remaining will have this top level operating system resource as their parent.
        // There are no deeper level resources in the hierarchy - so if we have a null parent, we know we are to
        // discover the top OS resource; if we have a non-null parent we know we are to discover one of the
        // different sub-types like memory, processors, file stores, etc.

        PlatformResourceType searchType = platformPath.getLastSegment().getType();
        String searchName = platformPath.getLastSegment().getName();

        if (searchType == PlatformResourceType.OPERATING_SYSTEM) {

            if (PlatformPath.ANY_NAME.equals(searchName) || searchName.equals(osId)) {
                // we are being asked to discover the top-most resource - the operating system resource
                PlatformResourceNode resNode = new PlatformResourceNode(PlatformResourceType.OPERATING_SYSTEM, osId);
                results.put(osPath, resNode);
            }

        } else {

            // we are being asked to discover children of the top-level OS resource
            if (searchType == PlatformResourceType.FILE_STORE) {

                Map<String, OSFileStore> fileStores = getFileStores();
                for (OSFileStore fileStore : fileStores.values()) {
                    String id = fileStore.getName();
                    if (PlatformPath.ANY_NAME.equals(searchName) || searchName.equals(id)) {
                        PlatformPath resourcePath = PlatformPath.builder()
                                .segments(osPath)
                                .segment(PlatformResourceType.FILE_STORE, id)
                                .build();
                        PlatformResourceNode resNode = new PlatformResourceNode(PlatformResourceType.FILE_STORE, id);
                        results.put(resourcePath, resNode);
                    }
                }

            } else if (searchType == PlatformResourceType.MEMORY) {

                String id = PlatformResourceType.MEMORY.getName().getNameString();
                if (PlatformPath.ANY_NAME.equals(searchName) || searchName.equals(id)) {
                    PlatformPath resourcePath = PlatformPath.builder()
                            .segments(osPath)
                            .segment(PlatformResourceType.MEMORY, id)
                            .build();
                    PlatformResourceNode resNode = new PlatformResourceNode(PlatformResourceType.MEMORY, id);
                    results.put(resourcePath, resNode);
                }

            } else if (searchType == PlatformResourceType.PROCESSOR) {

                CentralProcessor centralProcessor = getProcessor();
                for (int i = 0; i < centralProcessor.getLogicalProcessorCount(); i++) {
                    String id = String.valueOf(i);
                    if (PlatformPath.ANY_NAME.equals(searchName) || searchName.equals(id)) {
                        PlatformPath resourcePath = PlatformPath.builder()
                                .segments(osPath)
                                .segment(PlatformResourceType.PROCESSOR, id)
                                .build();
                        PlatformResourceNode resNode = new PlatformResourceNode(PlatformResourceType.PROCESSOR, id);
                        results.put(resourcePath, resNode);
                    }
                }

            } else if (searchType == PlatformResourceType.POWER_SOURCE) {

                Map<String, PowerSource> powerSources = getPowerSources();
                for (PowerSource powerSource : powerSources.values()) {
                    String id = powerSource.getName();
                    if (PlatformPath.ANY_NAME.equals(searchName) || searchName.equals(id)) {
                        PlatformPath resourcePath = PlatformPath.builder()
                                .segments(osPath)
                                .segment(PlatformResourceType.POWER_SOURCE, id)
                                .build();
                        PlatformResourceNode resNode = new PlatformResourceNode(PlatformResourceType.POWER_SOURCE, id);
                        results.put(resourcePath, resNode);
                    }
                }

            } else {
                throw new IllegalArgumentException("Invalid type - please report this: " + searchType);
            }
        }

        return results;
    }

    /**
     * @return the unique machine ID for this platform if it is known. Otherwise, null is returned.
     */
    public String getMachineId() {
        return machineId;
    }
}
