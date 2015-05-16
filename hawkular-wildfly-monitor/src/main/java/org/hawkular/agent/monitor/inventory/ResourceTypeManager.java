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
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.hawkular.agent.monitor.log.MsgLogger;
import org.jgrapht.DirectedGraph;
import org.jgrapht.Graphs;
import org.jgrapht.graph.DefaultEdge;
import org.jgrapht.graph.SimpleDirectedGraph;

public class ResourceTypeManager<T extends ResourceType, S extends ResourceTypeSet<T>> {

    private final Map<Name, S> resourceTypeSetMap;
    private final DirectedGraph<T, DefaultEdge> resourceTypesGraph;

    /**
     * Adds the given types to the manager, building a graph to represent the type hierarchy.
     *
     * @param resourceTypeSetMap a full set of types to use
     * @throws IllegalStateException if types are missing (e.g. a type needs a parent but the parent is missing)
     */
    public ResourceTypeManager(Map<Name, S> resourceTypeSetMap) throws IllegalStateException {
        this(resourceTypeSetMap, null);
    }

    /**
     * Adds the given types to the manager, building a graph to represent the type hierarchy.
     *
     * @param resourceTypeSetMap a full set of types
     * @param setsToUse optional set of type names that the manager to care about - it will ignore others it finds.
     *                  If null, then the full set is used (by "full set" it means the resourceTypeSetMap param).
     * @throws IllegalStateException if types are missing (e.g. a type needs a parent but the parent is missing)
     */
    public ResourceTypeManager(Map<Name, S> resourceTypeSetMap, Collection<Name> setsToUse)
            throws IllegalStateException {
        // If setsToUse is null, that means we need to use all the ones in the incoming map.
        // If setsToUse is not null, just use those named sets and ignore the others.
        if (setsToUse == null) {
            this.resourceTypeSetMap = new HashMap<>(resourceTypeSetMap);
        } else {
            this.resourceTypeSetMap = new HashMap<>(setsToUse.size());
            for (Name setToUse : setsToUse) {
                if (resourceTypeSetMap.containsKey(setToUse)) {
                    this.resourceTypeSetMap.put(setToUse, resourceTypeSetMap.get(setToUse));
                }
            }
        }

        this.resourceTypesGraph = new SimpleDirectedGraph<>(DefaultEdge.class);
        prepareGraph();
    }

    /**
     * Returns the resource type hierarchy. The graph is directed with outgoing edges pointing
     * to parents. If a graph vertex has an incoming edge, that means it is a parent of a child type.
     *
     * @return resource type hierarchy as a graph
     */
    public DirectedGraph<T, DefaultEdge> getResourceTypesGraph() {
        return resourceTypesGraph;
    }

    /**
     * Returns resource types that are at the top of the hierarchy (that is, they do not have any parent types).
     *
     * @return root types
     */
    public Set<T> getRootResourceTypes() {
        Set<T> roots = new HashSet<>();
        Set<T> allTypes = resourceTypesGraph.vertexSet();
        for (T type : allTypes) {
            if (resourceTypesGraph.outgoingEdgesOf(type).isEmpty()) {
                roots.add(type);
            }
        }
        return roots;
    }

    /**
     * Prepares the graph.
     *
     * @throws IllegalStateException if there are missing types
     */
    private void prepareGraph() throws IllegalStateException {
        List<T> disabledTypes = new ArrayList<>();

        // flattened list of types, all sets are collapsed into one
        Map<Name, T> allResourceTypes = new HashMap<>();

        // add all resource types as vertices in the graph
        for (S rTypeSet : resourceTypeSetMap.values()) {
            for (T rType : rTypeSet.getResourceTypeMap().values()) {
                if (null != allResourceTypes.put(rType.getName(), rType)) {
                    throw new IllegalStateException("Multiple resource types have the same name: " + rType.getName());
                }
                resourceTypesGraph.addVertex(rType);

                if (!rTypeSet.isEnabled()) {
                    disabledTypes.add(rType);
                }
            }
        }

        // now add the parent hierarchy to the graph
        for (S rTypeSet : resourceTypeSetMap.values()) {
            for (T rType : rTypeSet.getResourceTypeMap().values()) {
                for (Name parent : rType.getParents()) {
                    T parentResourceType = allResourceTypes.get(parent);
                    if (parentResourceType == null) {
                        throw new IllegalStateException("Resource type [" + rType.getName()
                                + "] has an unknown parent [" + parent + "]");
                    }
                    resourceTypesGraph.addEdge(rType, parentResourceType);
                }
            }
        }

        // now strip all disabled types - if a type has an ancestor that is disabled, it too will be disabled
        List<T> toBeDisabled = new ArrayList<>();
        for (T disabledType : disabledTypes) {
            toBeDisabled.add(disabledType);
            getDeepChildrenList(disabledType, toBeDisabled);
            MsgLogger.LOG.infoDisablingResourceTypes(disabledType, toBeDisabled);
        }
        resourceTypesGraph.removeAllVertices(toBeDisabled);
    }

    private void getDeepChildrenList(T resourceType, List<T> children) {
        List<T> directChildren = Graphs.predecessorListOf(resourceTypesGraph, resourceType);
        children.addAll(directChildren);
        for (T child : directChildren) {
            getDeepChildrenList(child, children);
        }
    }
}
