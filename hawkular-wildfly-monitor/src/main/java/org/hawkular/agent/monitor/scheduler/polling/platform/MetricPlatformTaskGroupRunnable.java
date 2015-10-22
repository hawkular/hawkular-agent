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
package org.hawkular.agent.monitor.scheduler.polling.platform;

import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.hawkular.agent.monitor.diagnostics.Diagnostics;
import org.hawkular.agent.monitor.inventory.ID;
import org.hawkular.agent.monitor.inventory.Name;
import org.hawkular.agent.monitor.inventory.platform.Constants;
import org.hawkular.agent.monitor.inventory.platform.Constants.PlatformResourceType;
import org.hawkular.agent.monitor.inventory.platform.PlatformMetricInstance;
import org.hawkular.agent.monitor.scheduler.polling.MetricCompletionHandler;
import org.hawkular.agent.monitor.scheduler.polling.Task;
import org.hawkular.agent.monitor.scheduler.polling.TaskGroup;
import org.hawkular.agent.monitor.storage.MetricDataPoint;
import org.hawkular.metrics.client.common.MetricType;
import org.jboss.logging.Logger;

import oshi.SystemInfo;
import oshi.hardware.Memory;
import oshi.hardware.PowerSource;
import oshi.hardware.Processor;
import oshi.software.os.OSFileStore;

public class MetricPlatformTaskGroupRunnable implements Runnable {
    private static final Logger LOG = Logger.getLogger(MetricPlatformTaskGroupRunnable.class);

    private static final Pattern BRACKETED_NAME_PATTERN = Pattern.compile(".*\\[(.*)\\].*");

    private final TaskGroup group;
    private final MetricCompletionHandler completionHandler;

    public MetricPlatformTaskGroupRunnable(TaskGroup group, MetricCompletionHandler completionHandler,
            Diagnostics diagnostics) {
        this.group = group;
        this.completionHandler = completionHandler;
    }

    @Override
    public void run() {
        try {
            SystemInfo sysInfo = new SystemInfo();
            Map<Constants.PlatformResourceType, Object> sysInfoCache = new HashMap<>(4);

            for (Task groupTask : group) {
                final MetricPlatformTask platformTask = (MetricPlatformTask) groupTask;
                final PlatformMetricInstance metricInstance = platformTask.getMetricInstance();
                final MetricType metricType = metricInstance.getMetricType().getMetricType();

                String itemName = parseBracketedNameValue(metricInstance.getResource().getID());
                Name typeName = metricInstance.getResource().getResourceType().getName();
                Name metricToCollect = metricInstance.getMeasurementType().getName();

                Double value = null;

                if (typeName.equals(Constants.PlatformResourceType.OPERATING_SYSTEM.getName())) {
                    value = getOperatingSystemMetric(metricToCollect, sysInfoCache, sysInfo);
                } else if (typeName.equals(Constants.PlatformResourceType.FILE_STORE.getName())) {
                    value = getFileStoreMetric(itemName, metricToCollect, sysInfoCache, sysInfo);
                } else if (typeName.equals(Constants.PlatformResourceType.MEMORY.getName())) {
                    value = getMemoryMetric(metricToCollect, sysInfoCache, sysInfo);
                } else if (typeName.equals(Constants.PlatformResourceType.PROCESSOR.getName())) {
                    value = getProcessorMetric(itemName, metricToCollect, sysInfoCache, sysInfo);
                } else if (typeName.equals(Constants.PlatformResourceType.POWER_SOURCE.getName())) {
                    value = getPowerSourceMetric(itemName, metricToCollect, sysInfoCache, sysInfo);
                } else {
                    LOG.errorf("Invalid platform type [%s]; cannot collect metric: [%s]", typeName, metricInstance);
                }

                if (value != null) {
                    completionHandler.onCompleted(new MetricDataPoint(platformTask, value.doubleValue(), metricType));
                } else {
                    completionHandler.onFailed(new Exception(
                            String.format("Cannot collect platform metric [%s][%s][%s]", typeName, metricToCollect,
                                    itemName)));
                }
            }
        } catch (Throwable e) {
            completionHandler.onFailed(e);
        }
    }

    private String parseBracketedNameValue(ID id) {
        // most have named like "File Store [/opt]" or "Processor [1]"
        Matcher m = BRACKETED_NAME_PATTERN.matcher(id.getIDString());
        if (m.matches()) {
            return m.group(1);
        } else {
            return id.getIDString(); // Memory doesn't have a bracketed name
        }
    }

    private Double getOperatingSystemMetric(Name metricToCollect, Map<PlatformResourceType, Object> sysInfoCache,
            SystemInfo sysInfo) {

        Processor processor = getProcessor(null, sysInfoCache, sysInfo);
        if (processor == null) {
            return null;
        }

        if (Constants.OPERATING_SYSTEM_SYS_CPU_LOAD.equals(metricToCollect)) {
            return processor.getSystemCpuLoad();
        } else if (Constants.OPERATING_SYSTEM_SYS_LOAD_AVG.equals(metricToCollect)) {
            return processor.getSystemLoadAverage();
        } else {
            throw new UnsupportedOperationException("Invalid processor metric to collect: " + metricToCollect);
        }
    }

    private Double getPowerSourceMetric(String itemName, Name metricToCollect,
            Map<PlatformResourceType, Object> sysInfoCache, SystemInfo sysInfo) {

        PowerSource powerSource = getPowerSource(itemName, sysInfoCache, sysInfo);
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

    private Double getProcessorMetric(String itemName, Name metricToCollect,
            Map<PlatformResourceType, Object> sysInfoCache, SystemInfo sysInfo) {

        Processor processor = getProcessor(itemName, sysInfoCache, sysInfo);
        if (processor == null) {
            return null;
        }

        if (Constants.PROCESSOR_CPU_USAGE.equals(metricToCollect)) {
            return processor.getProcessorCpuLoadBetweenTicks();
        } else {
            throw new UnsupportedOperationException("Invalid processor metric to collect: " + metricToCollect);
        }
    }

    private Double getFileStoreMetric(String itemName, Name metricToCollect,
            Map<PlatformResourceType, Object> sysInfoCache, SystemInfo sysInfo) {

        OSFileStore fileStore = getFileStore(itemName, sysInfoCache, sysInfo);
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

    private Double getMemoryMetric(Name metricToCollect, Map<Constants.PlatformResourceType, Object> sysInfoCache,
            SystemInfo sysInfo) {

        Memory mem = getMemory(sysInfoCache, sysInfo);

        if (Constants.MEMORY_AVAILABLE.equals(metricToCollect)) {
            return Double.valueOf(mem.getAvailable());
        } else if (Constants.MEMORY_TOTAL.equals(metricToCollect)) {
            return Double.valueOf(mem.getTotal());
        } else {
            throw new UnsupportedOperationException("Invalid memory metric to collect: " + metricToCollect);
        }
    }

    private PowerSource getPowerSource(String itemName, Map<PlatformResourceType, Object> sysInfoCache,
            SystemInfo sysInfo) {
        Map<String, PowerSource> cache =
                (Map<String, PowerSource>) sysInfoCache.get(Constants.PlatformResourceType.POWER_SOURCE);
        if (cache == null) {
            cache = new HashMap<>();
            PowerSource[] arr = sysInfo.getHardware().getPowerSources();
            for (PowerSource item : arr) {
                cache.put(item.getName(), item);
            }
            sysInfoCache.put(Constants.PlatformResourceType.POWER_SOURCE, cache);
        }

        PowerSource powerSource = cache.get(itemName);
        return powerSource;
    }

    private Processor getProcessor(String itemName, Map<PlatformResourceType, Object> sysInfoCache,
            SystemInfo sysInfo) {
        Map<String, Processor> cache =
                (Map<String, Processor>) sysInfoCache.get(Constants.PlatformResourceType.PROCESSOR);
        if (cache == null) {
            cache = new HashMap<>();
            Processor[] arr = sysInfo.getHardware().getProcessors();
            for (Processor item : arr) {
                cache.put(String.valueOf(item.getProcessorNumber()), item);
            }
            sysInfoCache.put(Constants.PlatformResourceType.PROCESSOR, cache);
        }

        // note if itemName is null, we just need to get "a" processor, doesn't matter which one.
        Processor processor = null;
        if (!cache.isEmpty()) {
            if (itemName == null) {
                processor = cache.values().iterator().next();
            } else {
                processor = cache.get(itemName);
            }
        }
        return processor;
    }

    private OSFileStore getFileStore(String itemName, Map<PlatformResourceType, Object> sysInfoCache,
            SystemInfo sysInfo) {
        Map<String, OSFileStore> cache =
                (Map<String, OSFileStore>) sysInfoCache.get(Constants.PlatformResourceType.FILE_STORE);
        if (cache == null) {
            cache = new HashMap<>();
            OSFileStore[] arr = sysInfo.getHardware().getFileStores();
            for (OSFileStore item : arr) {
                cache.put(item.getName(), item);
            }
            sysInfoCache.put(Constants.PlatformResourceType.FILE_STORE, cache);
        }

        OSFileStore fileStore = cache.get(itemName);
        return fileStore;
    }

    private Memory getMemory(Map<Constants.PlatformResourceType, Object> sysInfoCache, SystemInfo sysInfo) {
        Memory mem = (Memory) sysInfoCache.get(Constants.PlatformResourceType.MEMORY);
        if (mem == null) {
            mem = sysInfo.getHardware().getMemory();
            sysInfoCache.put(Constants.PlatformResourceType.MEMORY, mem);
        }
        return mem;
    }
}