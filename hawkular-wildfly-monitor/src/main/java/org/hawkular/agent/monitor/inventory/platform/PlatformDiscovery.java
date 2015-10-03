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

import java.util.Set;

import org.hawkular.agent.monitor.inventory.ID;
import org.hawkular.agent.monitor.inventory.InventoryIdUtil;
import org.hawkular.agent.monitor.inventory.Name;
import org.hawkular.agent.monitor.inventory.ResourceManager;
import org.hawkular.agent.monitor.log.AgentLoggers;
import org.hawkular.agent.monitor.log.MsgLogger;
import org.hawkular.agent.monitor.scheduler.config.Interval;
import org.hawkular.agent.monitor.scheduler.config.PlatformEndpoint;
import org.hawkular.agent.monitor.scheduler.config.PlatformPropertyReference;
import org.jgrapht.event.VertexSetListener;
import org.jgrapht.graph.DefaultEdge;
import org.jgrapht.traverse.DepthFirstIterator;

import oshi.SystemInfo;
import oshi.hardware.PowerSource;
import oshi.hardware.Processor;
import oshi.software.os.OSFileStore;
import oshi.software.os.OperatingSystem;

/**
 * Discovers resources for the platform.
 */
public class PlatformDiscovery {
    private static final MsgLogger log = AgentLoggers.getLogger(PlatformDiscovery.class);

    private final PlatformInventoryManager inventoryManager;

    /**
     * Creates the discovery object for the given inventory manager.
     * Only resources of known types will be discovered.
     *
     * @param im the inventory manager that holds information about the platform to be queried and
     *           the known types to be discovered
     */
    public PlatformDiscovery(PlatformInventoryManager im) {
        this.inventoryManager = im;
    }

    /**
     * Performs the discovery and stores the discovered inventory in this object's inventory manager.
     * This discovers a tree with parent resources at the top of the tree and
     * children at the bottom (that is to say, a resource will have an outgoing edge to its parent
     * and incoming edges from its children).
     *
     * @param listener if not null, will be a listener that gets notified when resources are discovered
     *
     * @throws Exception if discovery failed
     */
    public void discoverAllResources(final VertexSetListener<PlatformResource> listener) throws Exception {
        ResourceManager<PlatformResource> resourceManager = this.inventoryManager.getResourceManager();

        if (listener != null) {
            resourceManager.getResourcesGraph().addVertexSetListener(listener);
        }

        try {
            Set<PlatformResourceType> rootTypes;
            rootTypes = this.inventoryManager.getMetadataManager().getResourceTypeManager().getRootResourceTypes();

            long start = System.currentTimeMillis();
            for (PlatformResourceType rootType : rootTypes) {
                discoverChildrenOfResourceType(null, rootType);
            }
            long duration = System.currentTimeMillis() - start;

            logTreeGraph("Discovered platform resources", resourceManager, duration);
        } catch (Exception e) {
            throw new Exception("Failed to execute platform discovery for endpoint ["
                    + this.inventoryManager.getEndpoint() + "]", e);
        } finally {
            if (listener != null) {
                resourceManager.getResourcesGraph().removeVertexSetListener(listener);
            }
        }
    }

    private void discoverChildrenOfResourceType(PlatformResource parent, PlatformResourceType type) {
        try {
            log.debugf("Discovering children of [%s] of type [%s]", parent, type);

            ResourceManager<PlatformResource> resourceManager = this.inventoryManager.getResourceManager();
            PlatformEndpoint endpoint = this.inventoryManager.getEndpoint();
            SystemInfo sysInfo = this.inventoryManager.getSystemInfo();

            // The type hierarchy is fixed; it is all based on what SystemInfo provides us. So alot of our discovery
            // is really hardwired since we already know what resources we should be expecting.
            // We know we will get a top level operating system resource. It will always be discovered.
            // We know all the resources remaining will have this top level operating system resource as their parent.
            // There are no deeper level resources in the hierarchy - so if we have a null parent, we know we are to
            // discover the top OS resource; if we have a non-null parent we know we are to discover one of the
            // different sub-types like memory, processors, file stores, etc.

            if (parent == null) {
                // we are being asked to discover the top-most resource - the operating system resource
                OperatingSystem os = sysInfo.getOperatingSystem();
                PlatformResource osResource = new PlatformResource(
                        null, new Name(os.toString()), endpoint, type, null);
                log.debugf("Discovered [%s]", osResource);

                // add it to our tree graph
                resourceManager.addResource(osResource);

                // recursively discover children of child types
                Set<PlatformResourceType> childTypes;
                childTypes = this.inventoryManager.getMetadataManager().getResourceTypeManager().getChildren(type);
                for (PlatformResourceType childType : childTypes) {
                    discoverChildrenOfResourceType(osResource, childType);
                }
            } else {
                // we are being asked to discover children of the top-level resource
                if (type.getName().equals(Constants.FILE_STORE)) {
                    OSFileStore[] fileStores = sysInfo.getHardware().getFileStores();
                    for (OSFileStore fileStore : fileStores) {
                        PlatformResource fileStoreResource = new PlatformResource(
                                null, new Name(fileStore.getName()), endpoint, type, parent);
                        addMetricInstances(fileStoreResource);
                        resourceManager.addResource(fileStoreResource);
                    }
                } else if (type.getName().equals(Constants.MEMORY)) {
                    PlatformResource memoryResource = new PlatformResource(null, Constants.MEMORY,
                            endpoint, type, parent);
                    addMetricInstances(memoryResource);
                    resourceManager.addResource(memoryResource);
                } else if (type.getName().equals(Constants.PROCESSOR)) {
                    Processor[] processors = sysInfo.getHardware().getProcessors();
                    for (Processor processor : processors) {
                        PlatformResource processorResource = new PlatformResource(
                                null, new Name(String.valueOf(processor.getProcessorNumber())),
                                endpoint, type, parent);
                        addMetricInstances(processorResource);
                        resourceManager.addResource(processorResource);
                    }
                } else if (type.getName().equals(Constants.POWER_SOURCE)) {
                    PowerSource[] powerSources = sysInfo.getHardware().getPowerSources();
                    for (PowerSource powerSource : powerSources) {
                        PlatformResource powerSourceResource = new PlatformResource(
                                null, new Name(powerSource.getName()), endpoint, type, parent);
                        addMetricInstances(powerSourceResource);
                        resourceManager.addResource(powerSourceResource);
                    }
                }
            }

        } catch (Exception e) {
            log.errorf(e, "Failed to discover platform resources");
        }
    }

    private void addMetricInstances(PlatformResource resource) {

        for (PlatformMetricType metricType : resource.getResourceType().getMetricTypes()) {
            Interval interval = new Interval(metricType.getInterval(), metricType.getTimeUnits());
            PlatformPropertyReference prop = new PlatformPropertyReference(interval);
            ID id = InventoryIdUtil.generateMetricInstanceId(resource, metricType);
            Name name = metricType.getName();
            PlatformMetricInstance metricInstance = new PlatformMetricInstance(id, name, resource, metricType, prop);
            resource.getMetrics().add(metricInstance);
        }
    }

    private void logTreeGraph(String logMsg, ResourceManager<PlatformResource> resourceManager, long duration) {
        if (!log.isDebugEnabled()) {
            return;
        }

        StringBuilder graphString = new StringBuilder();
        DepthFirstIterator<PlatformResource, DefaultEdge> iter = resourceManager.getDepthFirstIterator();
        while (iter.hasNext()) {
            PlatformResource resource = iter.next();

            // append some indents based on depth of resource in tree
            PlatformResource parent = resource.getParent();
            while (parent != null) {
                graphString.append("...");
                parent = parent.getParent();
            }

            // append resource to string
            graphString.append(resource).append("\n");
        }

        log.debugf("%s\n%s\nDiscovery duration: [%d]ms", logMsg, graphString, duration);
    }
}
