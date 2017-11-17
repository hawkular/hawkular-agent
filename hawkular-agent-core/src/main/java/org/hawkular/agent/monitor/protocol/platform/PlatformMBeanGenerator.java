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
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.management.Attribute;
import javax.management.AttributeList;
import javax.management.AttributeNotFoundException;
import javax.management.DynamicMBean;
import javax.management.InvalidAttributeValueException;
import javax.management.MBeanAttributeInfo;
import javax.management.MBeanConstructorInfo;
import javax.management.MBeanException;
import javax.management.MBeanInfo;
import javax.management.MBeanNotificationInfo;
import javax.management.MBeanOperationInfo;
import javax.management.MBeanServer;
import javax.management.ObjectName;
import javax.management.ReflectionException;

import org.hawkular.agent.monitor.config.AgentCoreEngineConfiguration.PlatformConfiguration;
import org.hawkular.agent.monitor.inventory.ID;
import org.hawkular.agent.monitor.log.AgentLoggers;
import org.hawkular.agent.monitor.log.MsgLogger;
import org.hawkular.agent.monitor.protocol.platform.Constants.PlatformResourceType;

public class PlatformMBeanGenerator {
    private static final MsgLogger log = AgentLoggers.getLogger(PlatformMBeanGenerator.class);

    public class PlatformMBean implements DynamicMBean {

        private final MBeanInfo mbeanInfo;
        private final PlatformResourceType type;
        private final String name;

        public <L> PlatformMBean(MBeanInfo mbeanInfo, PlatformResourceType type, String name) {
            this.mbeanInfo = mbeanInfo;
            this.type = type;
            this.name = name;
        }

        @Override
        public Object getAttribute(String attribute)
                throws AttributeNotFoundException, MBeanException, ReflectionException {
            return getAttributeFromPlatformCache(attribute, true);
        }

        @Override
        public void setAttribute(Attribute attribute) throws AttributeNotFoundException,
                InvalidAttributeValueException, MBeanException, ReflectionException {
            throw new MBeanException(new IllegalStateException("This is a read-only MBean"));
        }

        @Override
        public AttributeList getAttributes(String[] attributeNames) {
            AttributeList resultList = new AttributeList();
            if (attributeNames != null) {
                for (int i = 0; i < attributeNames.length; i++) {
                    try {
                        // make sure to refresh the cache but only on the first call - no need to refresh thereafter
                        Object value = getAttributeFromPlatformCache((String) attributeNames[i], i == 0);
                        resultList.add(new Attribute(attributeNames[i], value));
                    } catch (Exception e) {
                        log.errorf(e, "Cannot get platform attribute: " + attributeNames[i]);
                    }
                }
            }
            return (resultList);
        }

        @Override
        public AttributeList setAttributes(AttributeList attributes) {
            throw new IllegalStateException("This is a read-only MBean");
        }

        @Override
        public Object invoke(String actionName, Object[] params, String[] signature)
                throws MBeanException, ReflectionException {
            throw new MBeanException(new IllegalStateException("This MBean does not support operations"));
        }

        @Override
        public MBeanInfo getMBeanInfo() {
            return this.mbeanInfo;
        }

        private Object getAttributeFromPlatformCache(String attribute, boolean refreshCache)
                throws AttributeNotFoundException, MBeanException, ReflectionException {

            if (refreshCache) {
                platformCache.refresh();
            }

            ID attribId = new ID(attribute);
            try {
                switch (type) {
                    case OPERATING_SYSTEM: {
                        if (attribute.equals(Constants.CONTAINER_ID)) {
                            return platformCache.getContainerId();
                        } else if (attribute.equals(Constants.MACHINE_ID)) {
                            return platformCache.getMachineId();
                        } else if (attribute.equals(Constants.OS_VERSION)) {
                            return platformCache.getOperatingSystem().toString();
                        } else {
                            return platformCache.getOperatingSystemMetric(attribId);
                        }
                    }
                    case FILE_STORE: {
                        return platformCache.getFileStoreMetric(name, attribId);
                    }
                    case MEMORY: {
                        return platformCache.getMemoryMetric(attribId);
                    }
                    case PROCESSOR: {
                        return platformCache.getProcessorMetric(name, attribId);
                    }
                    case POWER_SOURCE: {
                        return platformCache.getPowerSourceMetric(name, attribId);
                    }
                    default: {
                        throw new IllegalArgumentException("Bad type - please report this bug: " + type);
                    }
                }
            } catch (Exception e) {
                throw new MBeanException(e);
            }
        }
    }

    private final List<ObjectName> registeredMBeans;
    private final OshiPlatformCache platformCache;
    private final PlatformConfiguration platformConfiguration;

    public PlatformMBeanGenerator(String feedId, PlatformConfiguration pc) {
        this.platformConfiguration = pc;
        this.registeredMBeans = new ArrayList<>();
        this.platformCache = new OshiPlatformCache(feedId, pc.getMachineId(), pc.getContainerId());
    }

    public void registerAllMBeans() {

        if (!platformConfiguration.isEnabled()) {
            log.debugf("Platform monitoring is disabled; no MBeans will be registered");
            return;
        }

        ObjectName objectName;
        PlatformMBean mbean;
        Map<ObjectName, PlatformMBean> mbeans = new HashMap<>();

        // there is only one operating system mbean
        objectName = getOperatingSystemObjectName();
        mbean = new PlatformMBean(buildOperatingSystemMBeanInfo(), PlatformResourceType.OPERATING_SYSTEM, "os");
        mbeans.put(objectName, mbean);

        // there is only one memory mbean
        if (platformConfiguration.isMemoryEnabled()) {
            objectName = getMemoryObjectName();
            mbean = new PlatformMBean(buildMemoryMBeanInfo(), PlatformResourceType.MEMORY, "memory");
            mbeans.put(objectName, mbean);
        } else {
            log.debugf("Platform memory monitoring is disabled; no memory MBeans will be registered");
        }

        // file stores
        if (platformConfiguration.isFileStoresEnabled()) {
            Map<String, ?> fileStores = platformCache.getFileStores();
            if (fileStores != null) {
                for (String name : fileStores.keySet()) {
                    objectName = getFileStoreObjectName(name);
                    mbean = new PlatformMBean(buildFileStoreMBeanInfo(), PlatformResourceType.FILE_STORE, name);
                    mbeans.put(objectName, mbean);
                }
            }
        } else {
            log.debugf("Platform file store monitoring is disabled; no file store MBeans will be registered");
        }

        // processors
        if (platformConfiguration.isProcessorsEnabled()) {
            int processorCount = platformCache.getProcessor().getLogicalProcessorCount();
            for (int processor = 0; processor < processorCount; processor++) {
                objectName = getProcessorObjectName(processor);
                mbean = new PlatformMBean(buildProcessorMBeanInfo(), PlatformResourceType.PROCESSOR,
                        String.valueOf(processor));
                mbeans.put(objectName, mbean);
            }
        } else {
            log.debugf("Platform processor monitoring is disabled; no processor MBeans will be registered");
        }

        // power sources
        if (platformConfiguration.isPowerSourcesEnabled()) {
            Map<String, ?> powerSources = platformCache.getPowerSources();
            if (powerSources != null) {
                for (String name : powerSources.keySet()) {
                    objectName = getPowerSourceObjectName(name);
                    mbean = new PlatformMBean(buildPowerSourceMBeanInfo(), PlatformResourceType.POWER_SOURCE, name);
                    mbeans.put(objectName, mbean);
                }
            }
        } else {
            log.debugf("Platform power source monitoring is disabled; no power source MBeans will be registered");
        }

        // register all the mbeans
        MBeanServer mbs = ManagementFactory.getPlatformMBeanServer();
        for (Map.Entry<ObjectName, PlatformMBean> entry : mbeans.entrySet()) {
            if (!mbs.isRegistered(entry.getKey())) {
                try {
                    mbs.registerMBean(entry.getValue(), entry.getKey());
                    registeredMBeans.add(entry.getKey());
                    log.debugf("Registered platform MBean [%s]", entry.getKey());
                } catch (Exception e) {
                    log.errorf(e, "Cannot register platform MBean [%s]", entry.getKey());
                }
            }
        }
    }

    public void unregisterAllMBeans() {
        for (ObjectName doomed : registeredMBeans) {
            try {
                ManagementFactory.getPlatformMBeanServer().unregisterMBean(doomed);
            } catch (Exception e) {
                log.errorf(e, "Cannot unregister MBean: " + doomed);
            }
        }
    }

    public ObjectName getOperatingSystemObjectName() {
        return createObjectName(PlatformResourceType.OPERATING_SYSTEM, null);
    }

    public ObjectName getMemoryObjectName() {
        return createObjectName(PlatformResourceType.MEMORY, null);
    }

    public ObjectName getFileStoreObjectName(String name) {
        return createObjectName(PlatformResourceType.FILE_STORE, name);
    }

    public ObjectName getProcessorObjectName(int processorNumber) {
        return createObjectName(PlatformResourceType.PROCESSOR, String.valueOf(processorNumber));
    }

    public ObjectName getPowerSourceObjectName(String name) {
        return createObjectName(PlatformResourceType.POWER_SOURCE, name);
    }

    private String getSubtypeObjectNameString(PlatformResourceType type) {
        switch (type) {
            case OPERATING_SYSTEM: {
                return "org.hawkular.agent:type=platform,subtype=operatingsystem";
            }
            case FILE_STORE: {
                return "org.hawkular.agent:type=platform,subtype=filestore";
            }
            case MEMORY: {
                return "org.hawkular.agent:type=platform,subtype=memory";
            }
            case PROCESSOR: {
                return "org.hawkular.agent:type=platform,subtype=processor";
            }
            case POWER_SOURCE: {
                return "org.hawkular.agent:type=platform,subtype=powersource";
            }
            default: {
                throw new IllegalArgumentException("Bad type - please report this bug: " + type);
            }
        }
    }

    private ObjectName createObjectName(PlatformResourceType type, String name) {
        StringBuilder objectNameString = new StringBuilder(getSubtypeObjectNameString(type));
        if (name != null) {
            objectNameString.append(",name=").append(name);
        }
        try {
            return ObjectName.getInstance(objectNameString.toString());
        } catch (Exception e) {
            log.debugf(e, "Cannot create object name [%s] for type [%s] and name [%s]", objectNameString, type, name);
            return null;
        }
    }

    private MBeanInfo buildOperatingSystemMBeanInfo() {
        ArrayList<MBeanAttributeInfo> info = new ArrayList<>();

        info.add(new MBeanAttributeInfo(
                Constants.PlatformMetricType.OS_PROCESS_COUNT.getMetricTypeId().getIDString(),
                Double.class.getCanonicalName(),
                "Number of processors on the system",
                true,
                false,
                false));

        info.add(new MBeanAttributeInfo(
                Constants.PlatformMetricType.OS_SYS_CPU_LOAD.getMetricTypeId().getIDString(),
                Double.class.getCanonicalName(),
                "System CPU Load",
                true,
                false,
                false));

        info.add(new MBeanAttributeInfo(
                Constants.PlatformMetricType.OS_SYS_LOAD_AVG.getMetricTypeId().getIDString(),
                Double.class.getCanonicalName(),
                "System Load Average",
                true,
                false,
                false));

        info.add(new MBeanAttributeInfo(
                Constants.MACHINE_ID,
                String.class.getCanonicalName(),
                "ID of the machine if known",
                true,
                false,
                false));

        info.add(new MBeanAttributeInfo(
                Constants.CONTAINER_ID,
                String.class.getCanonicalName(),
                "ID of the container if the OS is inside of one and the ID can be known",
                true,
                false,
                false));

        info.add(new MBeanAttributeInfo(
                Constants.OS_VERSION,
                String.class.getCanonicalName(),
                "Operating system version information",
                true,
                false,
                false));

        return new MBeanInfo(
                PlatformMBean.class.getCanonicalName(),
                "Operating System information",
                info.toArray(new MBeanAttributeInfo[0]),
                new MBeanConstructorInfo[0],
                new MBeanOperationInfo[0],
                new MBeanNotificationInfo[0]);
    }

    private MBeanInfo buildFileStoreMBeanInfo() {
        ArrayList<MBeanAttributeInfo> info = new ArrayList<>();

        info.add(new MBeanAttributeInfo(
                Constants.PlatformMetricType.FILE_STORE_TOTAL_SPACE.getMetricTypeId().getIDString(),
                Double.class.getCanonicalName(),
                "Total space on the file store",
                true,
                false,
                false));

        info.add(new MBeanAttributeInfo(
                Constants.PlatformMetricType.FILE_STORE_USABLE_SPACE.getMetricTypeId().getIDString(),
                Double.class.getCanonicalName(),
                "Usable space on the file store",
                true,
                false,
                false));

        return new MBeanInfo(
                PlatformMBean.class.getCanonicalName(),
                "File store information",
                info.toArray(new MBeanAttributeInfo[0]),
                new MBeanConstructorInfo[0],
                new MBeanOperationInfo[0],
                new MBeanNotificationInfo[0]);
    }

    private MBeanInfo buildMemoryMBeanInfo() {
        ArrayList<MBeanAttributeInfo> info = new ArrayList<>();

        info.add(new MBeanAttributeInfo(
                Constants.PlatformMetricType.MEMORY_TOTAL.getMetricTypeId().getIDString(),
                Double.class.getCanonicalName(),
                "Total memory on the system",
                true,
                false,
                false));

        info.add(new MBeanAttributeInfo(
                Constants.PlatformMetricType.MEMORY_AVAILABLE.getMetricTypeId().getIDString(),
                Double.class.getCanonicalName(),
                "Memory that is available on the system",
                true,
                false,
                false));

        return new MBeanInfo(
                PlatformMBean.class.getCanonicalName(),
                "Memory information",
                info.toArray(new MBeanAttributeInfo[0]),
                new MBeanConstructorInfo[0],
                new MBeanOperationInfo[0],
                new MBeanNotificationInfo[0]);
    }

    private MBeanInfo buildProcessorMBeanInfo() {
        ArrayList<MBeanAttributeInfo> info = new ArrayList<>();

        info.add(new MBeanAttributeInfo(
                Constants.PlatformMetricType.PROCESSOR_CPU_USAGE.getMetricTypeId().getIDString(),
                Double.class.getCanonicalName(),
                "CPU usage",
                true,
                false,
                false));

        return new MBeanInfo(
                PlatformMBean.class.getCanonicalName(),
                "Processor information",
                info.toArray(new MBeanAttributeInfo[0]),
                new MBeanConstructorInfo[0],
                new MBeanOperationInfo[0],
                new MBeanNotificationInfo[0]);
    }

    private MBeanInfo buildPowerSourceMBeanInfo() {
        ArrayList<MBeanAttributeInfo> info = new ArrayList<>();

        info.add(new MBeanAttributeInfo(
                Constants.PlatformMetricType.POWER_SOURCE_REMAINING_CAPACITY.getMetricTypeId().getIDString(),
                Double.class.getCanonicalName(),
                "Percentage of power remaining",
                true,
                false,
                false));

        info.add(new MBeanAttributeInfo(
                Constants.PlatformMetricType.POWER_SOURCE_TIME_REMAINING.getMetricTypeId().getIDString(),
                Double.class.getCanonicalName(),
                "Time left remaining of power",
                true,
                false,
                false));

        return new MBeanInfo(
                PlatformMBean.class.getCanonicalName(),
                "Power Source information",
                info.toArray(new MBeanAttributeInfo[0]),
                new MBeanConstructorInfo[0],
                new MBeanOperationInfo[0],
                new MBeanNotificationInfo[0]);
    }
}
