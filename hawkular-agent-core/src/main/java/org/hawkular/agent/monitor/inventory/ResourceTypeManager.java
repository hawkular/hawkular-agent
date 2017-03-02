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
package org.hawkular.agent.monitor.inventory;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.hawkular.agent.monitor.inventory.TypeSet.TypeSetBuilder;
import org.hawkular.agent.monitor.log.AgentLoggers;
import org.hawkular.agent.monitor.log.MsgLogger;
import org.jgrapht.alg.DirectedNeighborIndex;
import org.jgrapht.graph.DefaultEdge;
import org.jgrapht.graph.ListenableDirectedGraph;
import org.jgrapht.traverse.BreadthFirstIterator;
import org.jgrapht.traverse.GraphIterator;

/**
 * Holds the graph of resource types. Instances of {@link ResourceTypeManager} are immutable and can thus be shared
 * among concurrent threads. The graph itself is never exposed externally - {@link ResourceTypeManager} rather provides
 * methods to retrieve data from the graph.
 *
 * @author John Mazzitelli
 * @author <a href="https://github.com/ppalaga">Peter Palaga</a>
 *
 * @param <L> the type of the protocol specific location, typically a subclass of {@link NodeLocation}
 */
public final class ResourceTypeManager<L> {
    private static final MsgLogger log = AgentLoggers.getLogger(ResourceTypeManager.class);
    private final ListenableDirectedGraph<ResourceType<L>, DefaultEdge> resourceTypesGraph;
    private final DirectedNeighborIndex<ResourceType<L>, DefaultEdge> index;
    private final Map<Name, TypeSet<ResourceType<L>>> typeSetMap;

    /**
     * A simpler constructor that accepts a simple collection of all resource types that will be stored
     * in this type manager.
     *
     * @param allTypes all types contained in this manager
     */
    public ResourceTypeManager(Collection<ResourceType<L>> allTypes) {
        this(buildTypeMapForConstructor(allTypes), null);
    }

    // for use by the above constructor
    private static <L> Map<Name, TypeSet<ResourceType<L>>> buildTypeMapForConstructor(
            Collection<ResourceType<L>> allTypes) {
        TypeSetBuilder<ResourceType<L>> bldr = TypeSet.<ResourceType<L>> builder();
        bldr.enabled(true);
        bldr.name(new Name("all"));
        for (ResourceType<L> type : allTypes) {
            bldr.type(type);
        }
        TypeSet<ResourceType<L>> typeSet = bldr.build();

        return Collections.singletonMap(typeSet.getName(), typeSet);
    }

    /**
     * Adds the given types to the manager, building a graph to represent the type hierarchy.
     *
     * @param resourceTypeSetMap a full set of types, must be immutable
     * @param setsToUse optional set of type names that the manager to care about - it will ignore others it finds. If
     *            null, then the full set is used (by "full set" it means the resourceTypeSetMap param).
     * @throws IllegalStateException if types are missing (e.g. a type needs a parent but the parent is missing)
     */
    public ResourceTypeManager(Map<Name, TypeSet<ResourceType<L>>> resourceTypeSetMap, Collection<Name> setsToUse)
            throws IllegalStateException {
        // If setsToUse is null, that means we need to use all the ones in the incoming map.
        // If setsToUse is not null, just use those named sets and ignore the others.
        if (setsToUse == null) {
            this.typeSetMap = resourceTypeSetMap;
        } else {
            Map<Name, TypeSet<ResourceType<L>>> m = new HashMap<>();
            for (Name setToUse : setsToUse) {
                if (resourceTypeSetMap.containsKey(setToUse)) {
                    m.put(setToUse, resourceTypeSetMap.get(setToUse));
                }
            }
            this.typeSetMap = Collections.unmodifiableMap(m);
        }

        this.resourceTypesGraph = new ListenableDirectedGraph<>(DefaultEdge.class);
        this.index = new DirectedNeighborIndex<>(this.resourceTypesGraph);
        this.resourceTypesGraph.addGraphListener(index);

        prepareGraph();
    }

    /**
     * Returns an immutable {@link List} of all {@link ResourceType}s contained in {@link #resourceTypesGraph} in
     * breadth-first order.
     *
     * @return the list of all {@link ResourceType}s
     */
    public List<ResourceType<L>> getResourceTypesBreadthFirst() {
        List<ResourceType<L>> result = new ArrayList<ResourceType<L>>();
        GraphIterator<ResourceType<L>, DefaultEdge> it = new BreadthFirstIterator<ResourceType<L>, DefaultEdge>(
                this.resourceTypesGraph);
        while (it.hasNext()) {
            result.add(it.next());
        }
        return Collections.unmodifiableList(result);
    }

    /**
     * Returns resource types that are at the top of the hierarchy (that is, they do not have any parent types).
     *
     * @return root types
     */
    public Set<ResourceType<L>> getRootResourceTypes() {
        Set<ResourceType<L>> roots = new HashSet<>();
        Set<ResourceType<L>> allTypes = resourceTypesGraph.vertexSet();
        for (ResourceType<L> type : allTypes) {
            if (index.predecessorsOf(type).isEmpty()) {
                if (type.getParents().isEmpty()) {
                    roots.add(type);
                } else {
                    log.errorInvalidRootResourceType(type.getID().getIDString(), type.getParents());
                }
            }
        }
        return Collections.unmodifiableSet(roots);
    }

    /**
     * Returns the direct child types of the given resource type.
     *
     * @param resourceType the type whose children are to be returned
     *
     * @return the direct children of the given resource type
     */
    public Set<ResourceType<L>> getChildren(ResourceType<L> resourceType) {
        Set<ResourceType<L>> directChildren = index.successorsOf(resourceType);
        return Collections.unmodifiableSet(directChildren);
    }

    /**
     * Returns the direct parent types of the given resource type.
     *
     * @param resourceType the type whose parents are to be returned
     *
     * @return the direct parents of the given resource type
     */
    public Set<ResourceType<L>> getParents(ResourceType<L> resourceType) {
        Set<ResourceType<L>> directParents = index.predecessorsOf(resourceType);
        return Collections.unmodifiableSet(directParents);
    }

    /**
     * Prepares the graph.
     *
     * @throws IllegalStateException if there are missing types
     */
    private void prepareGraph() throws IllegalStateException {
        List<ResourceType<L>> disabledTypes = new ArrayList<>();

        // flattened list of types, all sets are collapsed into one
        Map<Name, ResourceType<L>> allResourceTypes = new HashMap<>();

        // add all resource types as vertices in the graph
        for (TypeSet<ResourceType<L>> rTypeSet : typeSetMap.values()) {
            for (ResourceType<L> rType : rTypeSet.getTypeMap().values()) {
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
        for (TypeSet<ResourceType<L>> rTypeSet : typeSetMap.values()) {
            for (ResourceType<L> rType : rTypeSet.getTypeMap().values()) {
                for (Name parent : rType.getParents()) {
                    ResourceType<L> parentResourceType = allResourceTypes.get(parent);
                    if (parentResourceType == null) {
                        log.debugf("Resource type [%s] will ignore unknown parent [%s]", rType.getName(), parent);
                    } else {
                        resourceTypesGraph.addEdge(parentResourceType, rType);
                    }
                }
            }
        }

        // now strip all disabled types - if a type has an ancestor that is disabled, it too will be disabled
        List<ResourceType<L>> toBeDisabled = new ArrayList<>();
        for (ResourceType<L> disabledType : disabledTypes) {
            toBeDisabled.add(disabledType);
            getDeepChildrenList(disabledType, toBeDisabled);
            log.infoDisablingResourceTypes(disabledType, toBeDisabled);
        }
        resourceTypesGraph.removeAllVertices(toBeDisabled);
    }

    private void getDeepChildrenList(ResourceType<L> resourceType, List<ResourceType<L>> children) {
        Set<ResourceType<L>> directChildren = getChildren(resourceType);
        children.addAll(directChildren);
        for (ResourceType<L> child : directChildren) {
            getDeepChildrenList(child, children);
        }
    }

}
