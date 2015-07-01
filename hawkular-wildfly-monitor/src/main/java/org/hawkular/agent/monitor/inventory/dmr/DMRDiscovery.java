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

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.hawkular.agent.monitor.inventory.ID;
import org.hawkular.agent.monitor.inventory.Name;
import org.hawkular.agent.monitor.inventory.ResourceManager;
import org.hawkular.agent.monitor.scheduler.ModelControllerClientFactory;
import org.hawkular.dmrclient.Address;
import org.hawkular.dmrclient.CoreJBossASClient;
import org.hawkular.dmrclient.JBossASClient;
import org.jboss.as.controller.client.ModelControllerClient;
import org.jboss.dmr.ModelNode;
import org.jboss.dmr.ModelType;
import org.jboss.dmr.Property;
import org.jboss.logging.Logger;
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
     * Creates the discovery object for the given inventory manager. Only resources of known types
     * will be discovered. To connect to and query the server endpoint, the given client factory will
     * be used to create clients.
     *
     * @param im the inventory manager that holds information about the server to be queried and
     *           the known types to be discovered
     * @param clientFactory will create clients used to communicate with the endpoint
     */
    public DMRDiscovery(DMRInventoryManager im, ModelControllerClientFactory clientFactory) {
        this.inventoryManager = im;
        this.clientFactory = clientFactory;
    }

    /**
     * Performs the discovery. A graph is returned that contains all the discovered resources.
     * The graph is nothing more than a tree with parent resources at the top of the tree and
     * children at the bottom (that is to say, a resource will have an outgoing edge to its parent
     * and incoming edges from its children).
     *
     * @param resourceManager tree graph where all discovered resources will be stored
     *
     * @throws Exception if discovery failed
     */
    public void discoverAllResources(ResourceManager<DMRResource> resourceManager) throws Exception {
        try (ModelControllerClient mcc = clientFactory.createClient()) {
            Set<DMRResourceType> rootTypes = this.inventoryManager.getResourceTypeManager().getRootResourceTypes();
            for (DMRResourceType rootType : rootTypes) {
                discoverChildrenOfResourceType(null, rootType, mcc, resourceManager);
            }
            logTreeGraph("Discovered resources", resourceManager);
            return;
        } catch (Exception e) {
            throw new Exception("Failed to execute discovery for endpoint [" + this.inventoryManager.getEndpoint()
                    + "]", e);
        }
    }

    private void discoverChildrenOfResourceType(DMRResource parent, DMRResourceType type, ModelControllerClient mcc,
            ResourceManager<DMRResource> resourceManager) {
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

            for (Map.Entry<Address, ModelNode> entry : resources.entrySet()) {
                Address address = entry.getKey(); // this is the unique DMR address for this resource
                Name resourceName = generateResourceName(type, address);
                ID id = new ID(String.format("[%s~%s~%s]",
                        this.inventoryManager.getFeedId(),
                        this.inventoryManager.getManagedServer().getName(),
                        address));
                DMRResource resource = new DMRResource(id, resourceName, this.inventoryManager.getEndpoint(), type,
                        parent, address, entry.getValue());
                LOG.debugf("Discovered [%s]", resource);

                resourceManager.addResource(resource);

                // get the configuration of the resource
                discoverResourceConfiguration(resource, mcc);

                // recursively discover children of child types
                Set<DMRResourceType> childTypes = this.inventoryManager.getResourceTypeManager().getChildren(type);
                for (DMRResourceType childType : childTypes) {
                    discoverChildrenOfResourceType(resource, childType, mcc, resourceManager);
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
                    value = new CoreJBossASClient(mcc).getAttribute(attribute[0], addr);
                }

                if (attribute.length > 1 && value != null && value.isDefined()) {
                    value = value.get(attribute[1]);
                }

                DMRResourceConfigurationPropertyInstance cpi = new DMRResourceConfigurationPropertyInstance(
                        ID.NULL_ID, configPropType.getName(), configPropType);
                cpi.setValue((value != null && value.isDefined()) ? value.asString() : null);
                resource.addConfigurationProperty(cpi);
            } catch (Exception e) {
                LOG.warnf(e, "Failed to discover config [%s] for resource [%s]", configPropType, resource);
            }
        }
    }

    private void logTreeGraph(String logMsg, ResourceManager<DMRResource> resourceManager) {
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

        LOG.debugf("%s\n%s", logMsg, graphString);
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
}
