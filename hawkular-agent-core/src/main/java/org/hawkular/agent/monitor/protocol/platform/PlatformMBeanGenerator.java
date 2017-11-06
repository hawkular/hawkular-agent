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
import java.util.List;

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

import org.hawkular.agent.monitor.api.InventoryEvent;
import org.hawkular.agent.monitor.api.InventoryListener;
import org.hawkular.agent.monitor.inventory.AttributeLocation;
import org.hawkular.agent.monitor.inventory.Name;
import org.hawkular.agent.monitor.inventory.Resource;
import org.hawkular.agent.monitor.log.AgentLoggers;
import org.hawkular.agent.monitor.log.MsgLogger;
import org.hawkular.agent.monitor.protocol.EndpointService;
import org.hawkular.agent.monitor.protocol.Session;

public class PlatformMBeanGenerator implements InventoryListener {
    private static final MsgLogger log = AgentLoggers.getLogger(PlatformMBeanGenerator.class);

    public class PlatformMBean implements DynamicMBean {

        private final MBeanInfo mbeanInfo;
        private final Resource<?> resource;
        private final EndpointService<?, ?> service;

        public <L> PlatformMBean(MBeanInfo mbeanInfo, Resource<L> resource, EndpointService<?, ?> service) {
            this.mbeanInfo = mbeanInfo;
            this.resource = resource;
            this.service = service;
        }

        @Override
        public Object getAttribute(String attribute)
                throws AttributeNotFoundException, MBeanException, ReflectionException {

            try (Session<?> session = this.service.openSession()) {
                return session
                        .getDriver()
                        .fetchAttribute(new AttributeLocation(this.resource.getLocation(), attribute));
            } catch (Exception e) {
                throw new MBeanException(e);
            }
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
                        Object value = getAttribute((String) attributeNames[i]);
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

    }

    private final List<ObjectName> registeredMBeans;

    public PlatformMBeanGenerator() {
        registeredMBeans = new ArrayList<>();
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

    @Override
    public <L, S extends Session<L>> void receivedEvent(InventoryEvent<L, S> event) {
        for (Resource<L> resource : event.getAddedOrModified()) {
            processAddedOrModifiedResource(resource, event);
        }
        for (Resource<L> resource : event.getRemoved()) {
            processRemovedResource(resource, event);
        }
    }

    private <L, S extends Session<L>> void processAddedOrModifiedResource(Resource<L> resource,
            InventoryEvent<L, S> event) {

        PlatformMBean mbean;

        ObjectName objectName = createObjectName(resource);
        if (objectName == null) {
            return;
        }

        Name typeName = resource.getResourceType().getName();
        if (typeName.equals(Constants.PlatformResourceType.OPERATING_SYSTEM.getResourceTypeName())) {
            mbean = new PlatformMBean(buildOperatingSystemMBeanInfo(), resource, event.getEndpointService());
        } else if (typeName.equals(Constants.PlatformResourceType.FILE_STORE.getResourceTypeName())) {
            mbean = new PlatformMBean(buildFileStoreMBeanInfo(), resource, event.getEndpointService());
        } else if (typeName.equals(Constants.PlatformResourceType.MEMORY.getResourceTypeName())) {
            mbean = new PlatformMBean(buildMemoryMBeanInfo(), resource, event.getEndpointService());
        } else if (typeName.equals(Constants.PlatformResourceType.PROCESSOR.getResourceTypeName())) {
            mbean = new PlatformMBean(buildProcessorMBeanInfo(), resource, event.getEndpointService());
        } else if (typeName.equals(Constants.PlatformResourceType.POWER_SOURCE.getResourceTypeName())) {
            mbean = new PlatformMBean(buildPowerSourceMBeanInfo(), resource, event.getEndpointService());
        } else {
            return; // not a platform resource
        }

        try {
            MBeanServer mbs = ManagementFactory.getPlatformMBeanServer();
            if (mbs.isRegistered(objectName)) {
                mbs.unregisterMBean(objectName);
            }
            mbs.registerMBean(mbean, objectName);
            log.debugf("Registered platform MBean [%s] for platform resource [%s]", objectName, resource);
        } catch (Exception e) {
            log.errorf(e, "Cannot register platform MBean [%s]", objectName);
        }
    }

    private <L, S extends Session<L>> void processRemovedResource(Resource<L> resource, InventoryEvent<L, S> event) {

        ObjectName objectName = createObjectName(resource);
        if (objectName == null) {
            return;
        }

        try {
            ManagementFactory.getPlatformMBeanServer().unregisterMBean(objectName);
            log.debugf("Unregistered platform MBean [%s] for removed platform resource [%s]", objectName, resource);
        } catch (Exception e) {
            log.errorf(e, "Cannot unregister platform MBean [%s]", objectName);
        }
    }

    private <L> ObjectName createObjectName(Resource<L> resource) {
        String partialMBeanNameString;
        Name typeName = resource.getResourceType().getName();
        if (typeName.equals(Constants.PlatformResourceType.OPERATING_SYSTEM.getResourceTypeName())) {
            partialMBeanNameString = "org.hawkular.agent:type=platform,subtype=operatingsystem";
        } else if (typeName.equals(Constants.PlatformResourceType.FILE_STORE.getResourceTypeName())) {
            partialMBeanNameString = "org.hawkular.agent:type=platform,subtype=filestore";
        } else if (typeName.equals(Constants.PlatformResourceType.MEMORY.getResourceTypeName())) {
            partialMBeanNameString = "org.hawkular.agent:type=platform,subtype=memory";
        } else if (typeName.equals(Constants.PlatformResourceType.PROCESSOR.getResourceTypeName())) {
            partialMBeanNameString = "org.hawkular.agent:type=platform,subtype=processor";
        } else if (typeName.equals(Constants.PlatformResourceType.POWER_SOURCE.getResourceTypeName())) {
            partialMBeanNameString = "org.hawkular.agent:type=platform,subtype=powersource";
        } else {
            return null; // not a platform resource
        }

        String resName = resource.getName().getNameString();
        String objectNameString = String.format("%s,name=%s", partialMBeanNameString, resName);
        try {
            return ObjectName.getInstance(objectNameString);
        } catch (Exception e) {
            log.debugf(e, "Cannot create object name [%s] for resource [%s]", objectNameString, resource);
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
