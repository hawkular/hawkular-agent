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
package org.hawkular.agent.monitor.inventory;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.hawkular.agent.monitor.extension.MonitorServiceConfiguration.ResourceTypeDMR;
import org.hawkular.agent.monitor.extension.MonitorServiceConfiguration.ResourceTypeSetDMR;
import org.hawkular.agent.monitor.log.MsgLogger;
import org.jgrapht.DirectedGraph;
import org.jgrapht.Graphs;
import org.jgrapht.graph.DefaultEdge;
import org.jgrapht.graph.SimpleDirectedGraph;

public class ResourceTypeManager {

    private final Map<String, ResourceTypeSetDMR> resourceTypeSetDMRMap;
    private final DirectedGraph<ResourceTypeDMR, DefaultEdge> resourceTypesGraphDMR;

    public ResourceTypeManager(Map<String, ResourceTypeSetDMR> resourceTypeSetDMRMap) {
        this.resourceTypeSetDMRMap = resourceTypeSetDMRMap;
        this.resourceTypesGraphDMR = new SimpleDirectedGraph<ResourceTypeDMR, DefaultEdge>(DefaultEdge.class);

        prepareResourceTypesDMR();
    }

    /**
     * Returns the resource type hierarchy. The graph is directed with outgoing edges pointing
     * to parents. If a graph vertex has an incoming edge, that means it is a parent of a child type.
     *
     * @return resource type hierarchy as a graph
     */
    public DirectedGraph<ResourceTypeDMR, DefaultEdge> getResourceTypesGraphDMR() {
        return resourceTypesGraphDMR;
    }

    public Set<ResourceTypeDMR> getRootResourceTypesDMR() {
        Set<ResourceTypeDMR> roots = new HashSet<>();
        Set<ResourceTypeDMR> allTypes = resourceTypesGraphDMR.vertexSet();
        for (ResourceTypeDMR type : allTypes) {
            if (resourceTypesGraphDMR.outgoingEdgesOf(type).isEmpty()) {
                roots.add(type);
            }
        }
        return roots;
    }

    private void prepareResourceTypesDMR() {
        List<ResourceTypeDMR> disabledTypes = new ArrayList<>();

        // flattened list of types, all sets are collapsed into one
        Map<String, ResourceTypeDMR> allResourceTypes = new HashMap<>();

        // add all resource types as vertices in the graph
        for (ResourceTypeSetDMR rTypeSet : resourceTypeSetDMRMap.values()) {
            for (ResourceTypeDMR rType : rTypeSet.resourceTypeDmrMap.values()) {
                if (null != allResourceTypes.put(rType.name, rType)) {
                    throw new IllegalStateException("Multiple resource types have the same name: " + rType.name);
                }
                resourceTypesGraphDMR.addVertex(rType);

                if (!rTypeSet.enabled) {
                    disabledTypes.add(rType);
                }
            }
        }

        // now add the parent hierarchy to the graph
        for (ResourceTypeSetDMR rTypeSet : resourceTypeSetDMRMap.values()) {
            for (ResourceTypeDMR rType : rTypeSet.resourceTypeDmrMap.values()) {
                for (String parent : rType.parents) {
                    ResourceTypeDMR parentResourceType = allResourceTypes.get(parent);
                    if (parentResourceType == null) {
                        throw new IllegalStateException("Resource type [" + rType.name
                                + "] has an unknown parent [" + parent + "]");
                    }
                    resourceTypesGraphDMR.addEdge(rType, parentResourceType);
                }
            }
        }

        // now strip all disabled types - if a type has an ancestor that is disabled, it too will be disabled
        List<ResourceTypeDMR> toBeDisabled = new ArrayList<>();
        for (ResourceTypeDMR disabledType : disabledTypes) {
            toBeDisabled.add(disabledType);
            getDeepChildrenListDMR(disabledType, toBeDisabled);
            MsgLogger.LOG.infoDisablingResourceTypes(disabledType, toBeDisabled);
        }
        resourceTypesGraphDMR.removeAllVertices(toBeDisabled);
    }

    private void getDeepChildrenListDMR(ResourceTypeDMR resourceTypeDMR, List<ResourceTypeDMR> children) {
        List<ResourceTypeDMR> directChildren = Graphs.predecessorListOf(resourceTypesGraphDMR, resourceTypeDMR);
        children.addAll(directChildren);
        for (ResourceTypeDMR child : directChildren) {
            getDeepChildrenListDMR(child, children);
        }
    }
}
