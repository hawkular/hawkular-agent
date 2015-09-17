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
package org.hawkular.agent.monitor.inventory.dmr;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.hawkular.agent.monitor.inventory.ID;
import org.hawkular.agent.monitor.inventory.InventoryIdUtil;
import org.hawkular.agent.monitor.inventory.ManagedServer;
import org.hawkular.agent.monitor.inventory.Name;
import org.hawkular.agent.monitor.inventory.ResourceManager;
import org.hawkular.agent.monitor.log.MsgLogger;
import org.hawkular.agent.monitor.scheduler.ModelControllerClientFactory;
import org.hawkular.agent.monitor.scheduler.config.AvailDMRPropertyReference;
import org.hawkular.agent.monitor.scheduler.config.DMRPropertyReference;
import org.hawkular.agent.monitor.scheduler.config.Interval;
import org.hawkular.dmrclient.Address;
import org.hawkular.dmrclient.CoreJBossASClient;
import org.hawkular.dmrclient.JBossASClient;
import org.jboss.as.controller.client.ModelControllerClient;
import org.jboss.dmr.ModelNode;
import org.jboss.dmr.ModelType;
import org.jboss.dmr.Property;
import org.jboss.logging.Logger;
import org.jgrapht.event.VertexSetListener;
import org.jgrapht.graph.DefaultEdge;
import org.jgrapht.traverse.DepthFirstIterator;

/**
 * Discovers resources for a given DMR endpoint.
 */
public class DMRDiscovery {
    private static final Logger LOG = Logger.getLogger(DMRDiscovery.class);

    private final DMRInventoryManager inventoryManager;
    private final ModelControllerClientFactory clientFactory;

    /**
     * Creates the discovery object for the given inventory manager.
     * Only resources of known types will be discovered.
     * To connect to and query the server endpoint, the client factory provided
     * by the inventory manager will be used to create clients
     * (see {@link DMRInventoryManager#getModelControllerClientFactory()}).
     *
     * @param im the inventory manager that holds information about the server to be queried and
     *           the known types to be discovered
     * @param clientFactory will create clients used to communicate with the endpoint
     */
    public DMRDiscovery(DMRInventoryManager im) {
        this.inventoryManager = im;
        this.clientFactory = im.getModelControllerClientFactory();
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
    public void discoverAllResources(final VertexSetListener<DMRResource> listener) throws Exception {
        ResourceManager<DMRResource> resourceManager = this.inventoryManager.getResourceManager();

        if (listener != null) {
            resourceManager.getResourcesGraph().addVertexSetListener(listener);
        }

        try (ModelControllerClient mcc = clientFactory.createClient()) {
            Set<DMRResourceType> rootTypes;
            rootTypes = this.inventoryManager.getMetadataManager().getResourceTypeManager().getRootResourceTypes();

            long start = System.currentTimeMillis();
            for (DMRResourceType rootType : rootTypes) {
                discoverChildrenOfResourceType(null, rootType, mcc);
            }
            long duration = System.currentTimeMillis() - start;

            logTreeGraph("Discovered resources", resourceManager, duration);
        } catch (Exception e) {
            throw new Exception("Failed to execute discovery for endpoint [" + this.inventoryManager.getEndpoint()
                    + "]", e);
        } finally {
            if (listener != null) {
                resourceManager.getResourcesGraph().removeVertexSetListener(listener);
            }
        }
    }

    private void discoverChildrenOfResourceType(DMRResource parent, DMRResourceType type, ModelControllerClient mcc) {
        try {
            Map<Address, ModelNode> resources;

            CoreJBossASClient client = new CoreJBossASClient(mcc); // don't close this - the caller will
            Address parentAddr = (parent == null) ? Address.root() : parent.getAddress().clone();
            Address addr = parentAddr.add(Address.parse(type.getPath()));

            LOG.debugf("Discovering children of [%s] of type [%s] using address query [%s]", parent, type, addr);

            // can return a single resource (type of OBJECT) or a list of them (type of LIST whose items are OBJECTS)
            ModelNode results = client.readResource(addr);
            if (results == null) {
                resources = Collections.emptyMap();
            } else if (results.getType() == ModelType.OBJECT) {
                resources = new HashMap<>(1);
                resources.put(addr, results);
            } else if (results.getType() == ModelType.LIST) {
                resources = new HashMap<>();
                List<ModelNode> list = results.asList();
                for (ModelNode item : list) {
                    resources.put(Address.fromModelNodeWrapper(item, "address"), JBossASClient.getResults(item));
                }
            } else {
                throw new IllegalStateException("Invalid type - please report this bug: " + results.getType()
                        + " [[" + results.toString() + "]]");
            }

            ResourceManager<DMRResource> resourceManager = this.inventoryManager.getResourceManager();

            for (Map.Entry<Address, ModelNode> entry : resources.entrySet()) {
                Address address = entry.getKey(); // this is the unique DMR address for this resource
                Name resourceName = generateResourceName(type, address);
                ID id = InventoryIdUtil.generateResourceId(
                        this.inventoryManager.getFeedId(),
                        this.inventoryManager.getManagedServer(),
                        address.toAddressPathString());
                DMRResource resource = new DMRResource(id, resourceName, this.inventoryManager.getEndpoint(), type,
                        parent, address, entry.getValue());
                LOG.debugf("Discovered [%s]", resource);

                // get the configuration of the resource
                discoverResourceConfiguration(resource, mcc);
                postProcessResourceConfiguration(resource);

                // populate the metrics/avails based on the resource's type
                addMetricAndAvailInstances(resource);

                // add it to our tree graph
                resourceManager.addResource(resource);

                // recursively discover children of child types
                Set<DMRResourceType> childTypes;
                childTypes = this.inventoryManager.getMetadataManager().getResourceTypeManager().getChildren(type);
                for (DMRResourceType childType : childTypes) {
                    discoverChildrenOfResourceType(resource, childType, mcc);
                }
            }
        } catch (Exception e) {
            LOG.errorf(e, "Failed to discover resources in [%s]", this.inventoryManager.getEndpoint());
        }
    }

    private void discoverResourceConfiguration(DMRResource resource, ModelControllerClient mcc) {
        DMRResourceType rt = resource.getResourceType();
        Collection<DMRResourceConfigurationPropertyType> configPropTypes = rt.getResourceConfigurationPropertyTypes();
        for (DMRResourceConfigurationPropertyType configPropType : configPropTypes) {
            try {
                ModelNode value;
                String configPath = configPropType.getPath();
                String[] attribute = configPropType.getAttribute().split("#");
                if (configPath == null || configPath.equals("/")) {
                    value = resource.getModelNode().get(attribute[0]);
                } else {
                    Address addr = resource.getAddress().clone().add(Address.parse(configPath));
                    value = new CoreJBossASClient(mcc).getAttribute(true, attribute[0], addr);
                }

                if (attribute.length > 1 && value != null && value.isDefined()) {
                    value = value.get(attribute[1]);
                }

                DMRResourceConfigurationPropertyInstance cpi = new DMRResourceConfigurationPropertyInstance(
                        ID.NULL_ID, configPropType.getName(), configPropType);
                cpi.setValue((value != null && value.isDefined()) ? value.asString() : null);
                resource.addResourceConfigurationProperty(cpi);
            } catch (Exception e) {
                LOG.warnf(e, "Failed to discover config [%s] for resource [%s]", configPropType, resource);
            }
        }
    }

    private void postProcessResourceConfiguration(DMRResource resource) {
        // rather than check ("WildFly Server".equals(resource.getResourceType().getName().getNameString()))
        // instead we just know that (resource.getParent() == null) should select the same node.
        if (resource.getParent() == null) {
            final String IP_ADDRESSES_PROPERTY_NAME = "Bound Address";
            DMRResourceConfigurationPropertyInstance adrProp = null;
            for (DMRResourceConfigurationPropertyInstance p : resource.getResourceConfigurationProperties()) {
                if (IP_ADDRESSES_PROPERTY_NAME.equals(p.getName().getNameString())) {
                    adrProp = p;
                    break;
                }
            }
            if (adrProp != null) {
                String displayAddresses = null;
                try {
                    // Replaces 0.0.0.0 server address with the list of addresses received from
                    // InetAddress.getByName(String) where the argument of getByName(String) is the host the agent
                    // uses to query the AS'es DMR.
                    InetAddress dmrAddr = InetAddress.getByName(adrProp.getValue());
                    if (dmrAddr.isAnyLocalAddress()) {
                        String host = null;
                        ManagedServer server = inventoryManager.getManagedServer();
                        if (server instanceof RemoteDMRManagedServer) {
                            RemoteDMRManagedServer remoteServer = (RemoteDMRManagedServer) server;
                            host = remoteServer.getHost();
                        } else if (server instanceof LocalDMRManagedServer) {
                            host = InetAddress.getLocalHost().getCanonicalHostName();
                        } else {
                            throw new IllegalStateException("Unexpected type of managed server [" + server.getClass()
                                    + "]. Please report this bug.");
                        }
                        InetAddress[] resolvedAddresses = InetAddress.getAllByName(host);
                        displayAddresses = Stream.of(resolvedAddresses).map(a -> a.getHostAddress())
                                .collect(Collectors.joining(", "));
                        adrProp.setValue(displayAddresses);
                    }
                } catch (UnknownHostException e) {
                    MsgLogger.LOG.warnf(e, "Could not parse IP address [%s]", adrProp.getValue());
                }
            }
        }

        return;
    }

    private void logTreeGraph(String logMsg, ResourceManager<DMRResource> resourceManager, long duration) {
        if (!LOG.isDebugEnabled()) {
            return;
        }

        StringBuilder graphString = new StringBuilder();
        DepthFirstIterator<DMRResource, DefaultEdge> iter = resourceManager.getDepthFirstIterator();
        while (iter.hasNext()) {
            DMRResource resource = iter.next();

            // append some indents based on depth of resource in tree
            DMRResource parent = resource.getParent();
            while (parent != null) {
                graphString.append("...");
                parent = parent.getParent();
            }

            // append resource to string
            graphString.append(resource).append("\n");
        }

        LOG.debugf("%s\n%s\nDiscovery duration: [%d]ms", logMsg, graphString, duration);
    }

    private Name generateResourceName(DMRResourceType type, Address address) {
        ArrayList<String> args = new ArrayList<>();
        if (!address.isRoot()) {
            List<Property> parts = address.getAddressNode().asPropertyList();
            for (Property part : parts) {
                args.add(part.getName());
                args.add(part.getValue().asString());
            }
        }

        // The name template can have %# where # is the index number of the address part that should be substituted.
        // For example, suppose a resource has an address of "/hello=world/foo=bar" and the template is "Name [%2]".
        // The %2 will get substituted with the second address part (which is "world" - indices start at 1).
        // String.format() requires "$s" after the "%#" to denote the type of value is a string (all our address
        // parts are strings, so we know "$s" is what we want).
        // This replaceAll just replaces all occurrances of "%#" with "%#$s" so String.format will work.
        // We also allow for the special %- notation to mean "the last address part" since that's usually the one we
        // want and sometimes you can't know its positional value.
        // We also support %ManagedServerName which can help distinguish similar resources running in different servers.
        String nameTemplate = type.getResourceNameTemplate();
        nameTemplate = nameTemplate.replaceAll("%(\\d+)", "%$1\\$s");
        nameTemplate = nameTemplate.replaceAll("%(-)", "%" + args.size() + "\\$s");
        nameTemplate = nameTemplate.replaceAll("%ManagedServerName", inventoryManager.getManagedServer().getName()
                .getNameString());
        String nameStr = String.format(nameTemplate, args.toArray());
        return new Name(nameStr);
    }

    private void addMetricAndAvailInstances(DMRResource resource) {

        for (DMRMetricType metricType : resource.getResourceType().getMetricTypes()) {
            Interval interval = new Interval(metricType.getInterval(), metricType.getTimeUnits());
            Address relativeAddress = Address.parse(metricType.getPath());
            Address fullAddress = getFullAddressOfChild(resource, relativeAddress);
            if (fullAddress != null) {
                DMRPropertyReference prop = new DMRPropertyReference(fullAddress, metricType.getAttribute(), interval);
                ID id = InventoryIdUtil.generateMetricInstanceId(resource, metricType);
                Name name = metricType.getName();
                DMRMetricInstance metricInstance = new DMRMetricInstance(id, name, resource, metricType, prop);
                resource.getMetrics().add(metricInstance);
            }
        }

        for (DMRAvailType availType : resource.getResourceType().getAvailTypes()) {
            Interval interval = new Interval(availType.getInterval(), availType.getTimeUnits());
            Address relativeAddress = Address.parse(availType.getPath());
            Address fullAddress = getFullAddressOfChild(resource, relativeAddress);
            if (fullAddress != null) {
                AvailDMRPropertyReference prop = new AvailDMRPropertyReference(fullAddress, availType.getAttribute(),
                        interval, availType.getUpRegex());
                ID id = InventoryIdUtil.generateAvailInstanceId(resource, availType);
                Name name = availType.getName();
                DMRAvailInstance availInstance = new DMRAvailInstance(id, name, resource, availType, prop);
                resource.getAvails().add(availInstance);
            }
        }
    }

    private Address getFullAddressOfChild(DMRResource parentResource, Address childRelativePath) {
        // Some metrics/avails are collected from child resources. But sometimes resources
        // don't have those child resources (e.g. ear deployments don't have an undertow subsystem).
        // This means those metrics/avails cannot be collected (i.e. they are optional).
        // We don't want to fail with errors in this case; we just want to ignore those metrics/avails
        // since they don't exist.
        // If the child does exist (by examining the parent resource's model), then this method
        // will return the full address to that child resource.

        Address fullAddress = null;
        if (childRelativePath.isRoot()) {
            fullAddress = parentResource.getAddress(); // there really is no child; it is the resource itself
        } else {
            boolean childResourceExists = false;
            String[] addressParts = childRelativePath.toAddressParts();
            if (addressParts.length > 2) {
                // we didn't query the parent's model for recursive data - so we only know direct children.
                // if a metric/avail gets data from grandchildren or deeper, we don't know if it exists,
                // so just assume it does.
                childResourceExists = true;
                MsgLogger.LOG.tracef("Cannot test long child path [%s] under resource [%s] "
                        + "for existence so it will be assumed to exist", childRelativePath, parentResource);
            } else {
                ModelNode haystackNode = parentResource.getModelNode().get(addressParts[0]);
                if (haystackNode.getType() != ModelType.UNDEFINED) {
                    final List<ModelNode> haystackList = haystackNode.asList();
                    for (ModelNode needleNode : haystackList) {
                        if (needleNode.has(addressParts[1])) {
                            childResourceExists = true;
                            break;
                        }
                    }
                }
            }
            if (childResourceExists) {
                fullAddress = parentResource.getAddress().clone().add(childRelativePath);
            }
        }
        return fullAddress;
    }
}
