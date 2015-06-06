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

import java.util.HashSet;
import java.util.Set;

import org.jgrapht.alg.DirectedNeighborIndex;
import org.jgrapht.graph.DefaultEdge;
import org.jgrapht.graph.ListenableDirectedGraph;
import org.jgrapht.traverse.BreadthFirstIterator;
import org.jgrapht.traverse.DepthFirstIterator;

/**
 * Manages an inventory of resources.
 *
 * @param <T> the kind of resource (DMR, JMX, etc)
 *
 * @author John Mazzitelli
 */
public class ResourceManager<T extends Resource<?, ?, ?, ?, ?>> {

    private final ListenableDirectedGraph<T, DefaultEdge> resourcesGraph;
    private final DirectedNeighborIndex<T, DefaultEdge> index;

    public ResourceManager() {
        this.resourcesGraph = new ListenableDirectedGraph<>(DefaultEdge.class);
        this.index = new DirectedNeighborIndex<>(this.resourcesGraph);
        this.resourcesGraph.addGraphListener(index);
    }

    /**
     * @return the internal graph of the resources being managed. Use with caution, changes to this graph
     *         are backed by this manager.
     */
    public ListenableDirectedGraph<T, DefaultEdge> getResourcesGraph() {
        return this.resourcesGraph;
    }

    /**
     * Returns an iterator that let's you walk the tree of resources breadth-first.
     * Do NOT modify the resource hierarchy while iterating.
     *
     * @return an iterator that iterates the resource hierarchy breadth-first
     */
    public BreadthFirstIterator<T, DefaultEdge> getBreadthFirstIterator() {
        return new BreadthFirstIterator<T, DefaultEdge>(this.resourcesGraph);
    }

    /**
     * Returns an iterator that let's you walk the tree of resources depth-first.
     * Do NOT modify the resource hierarchy while iterating.
     *
     * @return an iterator that iterates the resource hierarchy depth-first
     */
    public DepthFirstIterator<T, DefaultEdge> getDepthFirstIterator() {
        return new DepthFirstIterator<T, DefaultEdge>(this.resourcesGraph);
    }

    /**
     * Returns resources that are at the top of the hierarchy (that is, they do not have a parent).
     *
     * @return root resources
     */
    public Set<T> getRootResources() {
        Set<T> roots = new HashSet<>();
        Set<T> allTypes = resourcesGraph.vertexSet();
        for (T type : allTypes) {
            if (index.successorsOf(type).isEmpty()) {
                roots.add(type);
            }
        }
        return roots;
    }

    /**
     * Returns the direct children of the given resource.
     *
     * @param resource the resource whose children are to be returned
     *
     * @return the direct children of the given resource
     */
    public Set<T> getChildren(T resource) {
        Set<T> directChildren = index.predecessorsOf(resource);
        return directChildren;
    }

    /**
     * Returns the direct parent of the given resource.
     *
     * @param resource the resource whose parent is to be returned
     *
     * @return the direct parent of the given resource, or null if this is a root resource without a parent
     */
    public T getParent(T resource) {
        // We could do resource.getParent(), but so could our caller. Here, let's go through the graph to get it.
        // We know all resources have at most one parent.
        Set<T> directParents = index.successorsOf(resource);
        if (directParents.isEmpty()) {
            return null;
        }
        return directParents.iterator().next();
    }

    /**
     * Returns an unordered set of all resources currently being managed.
     *
     * @return all resources
     */
    public Set<T> getAllResources() {
        return this.resourcesGraph.vertexSet();
    }

    /**
     * Adds the given resource to the resource hierarchy. If the resource is a child of a parent,
     * that parent must already be known or an exception is thrown.
     *
     * @param newResource the new resource to be added
     * @throws IllegalArgumentException if the new resource's parent does not yet exist in the hierarchy
     */
    public void addResource(T newResource) throws IllegalArgumentException {
        this.resourcesGraph.addVertex(newResource);
        if (newResource.getParent() != null) {
            this.resourcesGraph.addEdge(newResource, newResource.getParent());
        }
    }
}
