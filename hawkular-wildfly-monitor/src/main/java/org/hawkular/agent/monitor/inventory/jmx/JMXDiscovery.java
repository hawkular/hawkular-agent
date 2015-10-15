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
package org.hawkular.agent.monitor.inventory.jmx;

import java.util.Collection;
import java.util.Map;
import java.util.Set;

import javax.management.MalformedObjectNameException;
import javax.management.ObjectName;

import org.hawkular.agent.monitor.inventory.ID;
import org.hawkular.agent.monitor.inventory.InventoryIdUtil;
import org.hawkular.agent.monitor.inventory.Name;
import org.hawkular.agent.monitor.inventory.ResourceManager;
import org.hawkular.agent.monitor.log.AgentLoggers;
import org.hawkular.agent.monitor.log.MsgLogger;
import org.hawkular.agent.monitor.scheduler.JmxClientFactory;
import org.hawkular.agent.monitor.scheduler.config.AvailJMXPropertyReference;
import org.hawkular.agent.monitor.scheduler.config.Interval;
import org.hawkular.agent.monitor.scheduler.config.JMXPropertyReference;
import org.jgrapht.event.VertexSetListener;
import org.jgrapht.graph.DefaultEdge;
import org.jgrapht.traverse.DepthFirstIterator;
import org.jolokia.client.J4pClient;
import org.jolokia.client.request.J4pReadRequest;
import org.jolokia.client.request.J4pReadResponse;
import org.jolokia.client.request.J4pSearchRequest;
import org.jolokia.client.request.J4pSearchResponse;

/**
 * Discovers resources for a given DMR endpoint.
 */
public class JMXDiscovery {
    private static final MsgLogger log = AgentLoggers.getLogger(JMXDiscovery.class);

    private final JMXInventoryManager inventoryManager;
    private final JmxClientFactory clientFactory;

    /**
     * Creates the discovery object for the given inventory manager.
     * Only resources of known types will be discovered.
     * To connect to and query the server endpoint, the client factory provided
     * by the inventory manager will be used to create clients
     * (see {@link JMXInventoryManager#getJmxClientFactory()}).
     *
     * @param im the inventory manager that holds information about the server to be queried and
     *           the known types to be discovered
     * @param clientFactory will create clients used to communicate with the endpoint
     */
    public JMXDiscovery(JMXInventoryManager im) {
        this.inventoryManager = im;
        this.clientFactory = im.getJmxClientFactory();
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
    public void discoverAllResources(final VertexSetListener<JMXResource> listener) throws Exception {
        ResourceManager<JMXResource> resourceManager = this.inventoryManager.getResourceManager();

        if (listener != null) {
            resourceManager.getResourcesGraph().addVertexSetListener(listener);
        }

        J4pClient client = clientFactory.createClient();

        try {
            Set<JMXResourceType> rootTypes;
            rootTypes = this.inventoryManager.getMetadataManager().getResourceTypeManager().getRootResourceTypes();

            long start = System.currentTimeMillis();
            for (JMXResourceType rootType : rootTypes) {
                discoverChildrenOfResourceType(null, rootType, client);
            }
            long duration = System.currentTimeMillis() - start;

            logTreeGraph("Discovered JMX resources", resourceManager, duration);
        } catch (Exception e) {
            throw new Exception("Failed to execute discovery for endpoint [" + this.inventoryManager.getEndpoint()
                    + "]", e);
        } finally {
            if (listener != null) {
                resourceManager.getResourcesGraph().removeVertexSetListener(listener);
            }
        }
    }

    private void discoverChildrenOfResourceType(JMXResource parent, JMXResourceType type, J4pClient client) {
        try {
            String objectNameQuery = type.getObjectName();
            log.debugf("Discovering children of [%s] of type [%s] using query [%s]", parent, type, objectNameQuery);

            J4pSearchRequest searchReq = new J4pSearchRequest(objectNameQuery);
            J4pSearchResponse searchResponse = client.execute(searchReq);

            ResourceManager<JMXResource> resourceManager = this.inventoryManager.getResourceManager();

            for (ObjectName objectName : searchResponse.getObjectNames()) {
                Name resourceName = generateResourceName(type, objectName);
                ID id = InventoryIdUtil.generateResourceId(
                        this.inventoryManager.getFeedId(),
                        this.inventoryManager.getManagedServer(),
                        objectName.getCanonicalName());
                JMXResource resource = new JMXResource(id, resourceName, this.inventoryManager.getEndpoint(), type,
                        parent, objectName);
                log.debugf("Discovered [%s]", resource);

                // get the configuration of the resource
                discoverResourceConfiguration(resource, client);

                // populate the metrics/avails based on the resource's type
                addMetricAndAvailInstances(resource);

                // add it to our tree graph
                resourceManager.addResource(resource);

                // recursively discover children of child types
                Set<JMXResourceType> childTypes;
                childTypes = this.inventoryManager.getMetadataManager().getResourceTypeManager().getChildren(type);
                for (JMXResourceType childType : childTypes) {
                    discoverChildrenOfResourceType(resource, childType, client);
                }
            }
        } catch (Exception e) {
            log.errorf(e, "Failed to discover resources in [%s]", this.inventoryManager.getEndpoint());
        }
    }

    private void discoverResourceConfiguration(JMXResource resource, J4pClient client) {
        JMXResourceType rt = resource.getResourceType();
        Collection<JMXResourceConfigurationPropertyType> configPropTypes = rt.getResourceConfigurationPropertyTypes();
        for (JMXResourceConfigurationPropertyType configPropType : configPropTypes) {
            try {
                ObjectName configObjectName;
                if (configPropType.getObjectName() == null || configPropType.getObjectName().isEmpty()) {
                    configObjectName = resource.getObjectName();
                } else {
                    configObjectName = new ObjectName(configPropType.getObjectName());
                }

                // jolokia API allows for sub-references using "/" notation
                String attribute = configPropType.getAttribute().replaceFirst("#", "/");
                J4pReadRequest request = new J4pReadRequest(configObjectName, attribute);
                J4pReadResponse response = client.execute(request);
                String value = response.getValue();
                JMXResourceConfigurationPropertyInstance cpi = new JMXResourceConfigurationPropertyInstance(
                        ID.NULL_ID, configPropType.getName(), configPropType);
                cpi.setValue(value);
                resource.addResourceConfigurationProperty(cpi);
            } catch (Exception e) {
                log.warnf(e, "Failed to discover config [%s] for resource [%s]", configPropType, resource);
            }
        }
    }

    private void logTreeGraph(String logMsg, ResourceManager<JMXResource> resourceManager, long duration) {
        if (!log.isDebugEnabled()) {
            return;
        }

        StringBuilder graphString = new StringBuilder();
        DepthFirstIterator<JMXResource, DefaultEdge> iter = resourceManager.getDepthFirstIterator();
        while (iter.hasNext()) {
            JMXResource resource = iter.next();

            // append some indents based on depth of resource in tree
            JMXResource parent = resource.getParent();
            while (parent != null) {
                graphString.append("...");
                parent = parent.getParent();
            }

            // append resource to string
            graphString.append(resource).append("\n");
        }

        log.debugf("%s\n%s\nDiscovery duration: [%d]ms", logMsg, graphString, duration);
    }

    private Name generateResourceName(JMXResourceType type, ObjectName objectName) {
        // The name template can have %X% where X is a key in the object name.
        // This will be substituted with the value of that key in the resource name.
        // For example, suppose a resource has an object name of "domain:abc=xyz" and the template is "Name [%abc%]".
        // The %abc% will get substituted with the value of that "abc" key from the object name
        // (in this case, the value is "xyz") so the resource name would be generated as "Name [xyz]".
        // Also supported is the substitution key "%_ManagedServerName%" which can help distinguish similar
        // resources running in different servers.
        String nameTemplate = type.getResourceNameTemplate();
        nameTemplate = nameTemplate.replace("%_ManagedServerName%",
                inventoryManager.getManagedServer().getName().getNameString());
        for (Map.Entry<String, String> entry : objectName.getKeyPropertyList().entrySet()) {
            if (nameTemplate.indexOf("%") == -1) {
                break; // no sense continuing if the nameTemplate doesn't have any tokens left
            }
            String key = entry.getKey();
            String value = entry.getValue();
            nameTemplate = nameTemplate.replace("%" + key + "%", value);
        }
        return new Name(nameTemplate);
    }

    private void addMetricAndAvailInstances(JMXResource resource) {

        for (JMXMetricType metricType : resource.getResourceType().getMetricTypes()) {
            Interval interval = new Interval(metricType.getInterval(), metricType.getTimeUnits());
            ObjectName objectName;
            if (metricType.getObjectName() == null || metricType.getObjectName().isEmpty()) {
                objectName = resource.getObjectName();
            } else {
                try {
                    objectName = new ObjectName(metricType.getObjectName());
                } catch (MalformedObjectNameException e) {
                    log.errorf(e, "Bad object name [%s] for metric type [%s]", metricType.getObjectName(), metricType);
                    continue;
                }
            }

            JMXPropertyReference prop = new JMXPropertyReference(objectName, metricType.getAttribute(), interval);
            ID id = InventoryIdUtil.generateMetricInstanceId(resource, metricType);
            Name name = metricType.getName();
            JMXMetricInstance metricInstance = new JMXMetricInstance(id, name, resource, metricType, prop);
            resource.getMetrics().add(metricInstance);
        }

        for (JMXAvailType availType : resource.getResourceType().getAvailTypes()) {
            Interval interval = new Interval(availType.getInterval(), availType.getTimeUnits());
            ObjectName objectName;
            if (availType.getObjectName() == null || availType.getObjectName().isEmpty()) {
                objectName = resource.getObjectName();
            } else {
                try {
                    objectName = new ObjectName(availType.getObjectName());
                } catch (MalformedObjectNameException e) {
                    log.errorf(e, "Bad object name [%s] for avail type [%s]", availType.getObjectName(), availType);
                    continue;
                }
            }
            AvailJMXPropertyReference prop = new AvailJMXPropertyReference(objectName, availType.getAttribute(),
                    interval, availType.getUpRegex());
            ID id = InventoryIdUtil.generateAvailInstanceId(resource, availType);
            Name name = availType.getName();
            JMXAvailInstance availInstance = new JMXAvailInstance(id, name, resource, availType, prop);
            resource.getAvails().add(availInstance);
        }
    }
}
