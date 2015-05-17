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
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.hawkular.agent.monitor.inventory.ResourceTypeManager;
import org.hawkular.agent.monitor.scheduler.ModelControllerClientFactory;
import org.hawkular.agent.monitor.scheduler.config.DMREndpoint;
import org.hawkular.dmrclient.Address;
import org.hawkular.dmrclient.CoreJBossASClient;
import org.jboss.as.controller.client.ModelControllerClient;
import org.jboss.dmr.ModelNode;
import org.jboss.dmr.ModelType;
import org.jboss.dmr.Property;
import org.jboss.logging.Logger;
import org.jgrapht.DirectedGraph;
import org.jgrapht.graph.DefaultEdge;
import org.jgrapht.graph.SimpleDirectedGraph;

public class DMRDiscovery {
    private static final Logger LOG = Logger.getLogger(DMRDiscovery.class);

    private final ResourceTypeManager<DMRResourceType, DMRResourceTypeSet> resourceTypeManager;
    private final DMREndpoint dmrEndpoint;
    private final ModelControllerClientFactory clientFactory;
    private final DirectedGraph<DMRResource, DefaultEdge> resourcesGraph;

    public DMRDiscovery(ResourceTypeManager<DMRResourceType, DMRResourceTypeSet> rtm,
            DMREndpoint dmrEndpoint, ModelControllerClientFactory clientFactory) {
        this.resourceTypeManager = rtm;
        this.dmrEndpoint = dmrEndpoint;
        this.clientFactory = clientFactory;
        this.resourcesGraph = new SimpleDirectedGraph<>(DefaultEdge.class);

        LOG.debugf("Endpoint [%s] resource type graph -> %s", dmrEndpoint, rtm.getResourceTypesGraph());
    }

    public void discoverAllResources() {
        try (ModelControllerClient mcc = clientFactory.createClient()) {
            Set<DMRResourceType> rootTypes = this.resourceTypeManager.getRootResourceTypes();
            for (DMRResourceType rootType : rootTypes) {
                discoverChildrenOfResourceType(null, rootType, mcc);
            }
        } catch (Exception e) {
            // TODO
        }
    }

    private void discoverChildrenOfResourceType(DMRResource parent, DMRResourceType type, ModelControllerClient mcc) {

        try {
            Map<Address, ModelNode> resources;

            CoreJBossASClient client = new CoreJBossASClient(mcc);
            Address parentAddr = (parent == null) ? Address.root() : parent.getAddress().clone();
            Address addr = parentAddr.add(Address.parse(type.getPath()));

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
                    resources.put(Address.fromModelNodeWrapper(item, "address"), item);
                }
            } else {
                throw new IllegalStateException("Invalid type - please report this bug: " + results.getType()
                        + " [[" + results.toString() + "]]");
            }

            for (Map.Entry<Address, ModelNode> entry : resources.entrySet()) {
                String resourceName = generateResourceName(type, entry.getKey());
                DMRResource resource = new DMRResource(resourceName, type, entry.getKey());
                this.resourcesGraph.addVertex(resource);
                if (parent != null) {
                    this.resourcesGraph.addEdge(resource, parent);
                }

                // TODO get all child types and recursively discover children of those types
            }
        } catch (Exception e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }
    }

    private String generateResourceName(DMRResourceType type, Address address) {
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
        String nameTemplate = type.getResourceNameTemplate();
        nameTemplate = nameTemplate.replaceAll("%(\\d+)", "%$1\\$s");

        return String.format(nameTemplate, args.toArray());
    }
}
