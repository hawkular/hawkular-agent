/*
 * Copyright 2015-2016 Red Hat, Inc. and/or its affiliates
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
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

import org.hawkular.agent.monitor.log.AgentLoggers;
import org.hawkular.agent.monitor.log.MsgLogger;
import org.hawkular.agent.monitor.protocol.LocationResolver;
import org.jgrapht.alg.DirectedNeighborIndex;
import org.jgrapht.event.GraphVertexChangeEvent;
import org.jgrapht.event.VertexSetListener;
import org.jgrapht.graph.DefaultEdge;
import org.jgrapht.graph.ListenableDirectedGraph;
import org.jgrapht.traverse.BreadthFirstIterator;
import org.jgrapht.traverse.DepthFirstIterator;
import org.jgrapht.traverse.GraphIterator;

/**
 * Holds the graph of resources. The graph itself is never exposed externally - {@link ResourceManager} rather provides
 * methods to retrieve data from the graph and to change the data in the graph.
 * <p>
 * Concurrency:
 * <ul>
 * <li>All data and collections returned from {@link ResourceManager} is immutable
 * <li>{@link ResourceManager} uses a {@link ReadWriteLock} internally so that all public read and write operations can
 * be performed from concurrent threads
 *
 * @author John Mazzitelli
 * @author <a href="https://github.com/ppalaga">Peter Palaga</a>
 *
 * @param <L> the type of the protocol specific location, typically a subclass of {@link NodeLocation}
 */
public final class ResourceManager<L> {

    /**
     * Indicates the results of the {@link ResourceManager#addResource(Resource)} method.
     */
    public static class AddResult<L> {
        public enum Effect {
            ADDED, MODIFIED, UNCHANGED
        }

        private final Resource<L> resource;
        private final Effect effect;

        public AddResult(Effect effect, Resource<L> resource) {
            this.resource = resource;
            this.effect = effect;
        }

        /**
         * Indicates the effect that the add method had on the inventory.
         */
        public Effect getEffect() {
            return effect;
        }

        /**
         * @return the resource that was actually populated into inventory. This could be
         *         different than the one given to the resource manager to add.
         */
        public Resource<L> getResource() {
            return resource;
        }
    }

    /**
     * This class listens for resources getting added and removed from the graph and updates its internal cache to
     * reflect the changes. The internal cache lets us retrieve resources quickly by resource ID.
     */
    private class VertexCacheListener implements VertexSetListener<Resource<L>> {
        @Override
        public void vertexAdded(GraphVertexChangeEvent<Resource<L>> e) {
            resourceCache.put(e.getVertex().getID(), e.getVertex());
        }

        @Override
        public void vertexRemoved(GraphVertexChangeEvent<Resource<L>> e) {
            resourceCache.remove(e.getVertex().getID());
        }
    }

    /**
     * This is used to see if a new resource is actually the same as a resource already
     * in inventory. This only checks those things that, if changed, warrant the inventory to
     * be updated.
     */
    private class ResourceComparator implements Comparator<Resource<L>> {

        @Override
        public int compare(Resource<L> r1, Resource<L> r2) {
            // make sure we are looking at the same resource
            int c = r1.getID().compareTo(r2.getID());
            if (c != 0) {
                return c;
            }

            // see if the names changed
            c = r1.getName().compareTo(r2.getName());
            if (c != 0) {
                return c;
            }

            // see if the resource configuration property values are the same
            Collection<ResourceConfigurationPropertyInstance<L>> rcp1 = r1.getResourceConfigurationProperties();
            Collection<ResourceConfigurationPropertyInstance<L>> rcp2 = r2.getResourceConfigurationProperties();
            if (rcp1.size() == rcp2.size()) {
                if (!rcp1.isEmpty()) {
                    Map<ResourceConfigurationPropertyInstance<L>, String> rcp1Map = new HashMap<>(rcp1.size());
                    for (ResourceConfigurationPropertyInstance<L> rcp1Item : rcp1) {
                        rcp1Map.put(rcp1Item, rcp1Item.getValue());
                    }
                    Map<ResourceConfigurationPropertyInstance<L>, String> rcp2Map = new HashMap<>(rcp2.size());
                    for (ResourceConfigurationPropertyInstance<L> rcp2Item : rcp2) {
                        rcp2Map.put(rcp2Item, rcp2Item.getValue());
                    }
                    if (!rcp1Map.equals(rcp2Map)) {
                        return rcp1Map.hashCode() < rcp2Map.hashCode() ? -1 : 1;
                    }
                }
            } else {
                return rcp1.size() < rcp2.size() ? -1 : 1;
            }

            // see if the general properties are the same
            if (!r1.getProperties().equals(r2.getProperties())) {
                return r1.getProperties().hashCode() < r2.getProperties().hashCode() ? -1 : 1;
            }

            // everything we care about didn't change - consider them the same resource
            return 0;
        }
    }

    private static final MsgLogger log = AgentLoggers.getLogger(ResourceManager.class);
    private final ReadWriteLock graphLock = new ReentrantReadWriteLock(true);
    private final Lock graphLockRead = graphLock.readLock();
    private final Lock graphLockWrite = graphLock.writeLock();
    private volatile DirectedNeighborIndex<Resource<L>, DefaultEdge> neighborIndex;
    private volatile Map<ID, Resource<L>> resourceCache;

    private volatile ListenableDirectedGraph<Resource<L>, DefaultEdge> resourcesGraph;

    public ResourceManager() {
        reinitializeIfNecessary();
    }

    /**
     * @return the total number of resources currently in the graph.
     */
    public int size() {
        return resourceCache.size();
    }

    /**
     * @return the total number of resources currently in the graph relative to the given resource (that is,
     *         it counts that resource and all of its descendants).
     */
    public int size(Resource<L> relativeTo) {
        graphLockRead.lock();
        try {
            if (getResource(relativeTo.getID()) == null) {
                return 0; // the resource doesn't even exist
            } else {
                List<Resource<L>> descendants = new ArrayList<>();
                getAllDescendants(relativeTo, descendants);
                return 1 + descendants.size();
            }
        } finally {
            graphLockRead.unlock();
        }
    }

    /**
     * Adds the given resource to the resource hierarchy, replacing the resource if it already exist but
     * has changed.
     *
     * If the resource is a child of a parent, that parent must already be known or an exception is thrown.
     *
     * The return value's {@link AddResult#getEffect() effect} has the following semantics:
     * <ul>
     * <li>ADDED means the resource was new and was added to the inventory.</li>
     * <li>MODIFIED means the resource was already in inventory but is different from before
     *              and so inventory was updated.</li>
     * <li>UNCHANGED means the resource was already in inventory and is the same.</li>
     * </ul>
     *
     * The return value's {@link AddResult#getResource() resource} is the resource object stored in the
     * internal hierarchical graph, which may or may not be the same as the <code>newResource</code>
     * that was passed into this method.
     *
     * @param newResource the new resource to be added
     * @return the results - see above for a detailed description of these results
     * @throws IllegalArgumentException if the new resource's parent does not yet exist in the hierarchy
     */
    public AddResult<L> addResource(Resource<L> newResource) throws IllegalArgumentException {
        AddResult<L> result;

        graphLockWrite.lock();
        try {
            // Need to make sure we keep our resources consistent. If the newResource has a parent,
            // and that parent is not the same instance we have in our graph, we need to recreate the
            // newResource such that it refers to our instance of the parent.
            // Do this BEFORE we attempt to add the new resource to the graph.
            if (newResource.getParent() != null) {
                Resource<L> parentInGraph = getResource(newResource.getParent().getID());
                if (parentInGraph == null) {
                    throw new IllegalArgumentException(
                            String.format("The new resource [%s] has a parent [%s] that has not been added yet",
                                    newResource, newResource.getParent()));
                }

                // if parents are not the same instance, create a new resource with the parent we have in the graph
                if (parentInGraph != newResource.getParent()) {
                    newResource = Resource.<L> builder(newResource).parent(parentInGraph).build();
                }
            }

            boolean added = this.resourcesGraph.addVertex(newResource);

            if (!added) {
                // Looks like this resource already exists.
                // If the resource changed, we want to replace it but keep all edges intact.
                // If the resource did not change, we don't do anything.
                Resource<L> oldResource = getResource(newResource.getID());

                if (new ResourceComparator().compare(oldResource, newResource) != 0) {
                    Set<Resource<L>> children = getChildren(oldResource);
                    this.resourcesGraph.removeVertex(oldResource); // removes all edges! remember to put parent back
                    this.resourcesGraph.addVertex(newResource);
                    for (Resource<L> child : children) {
                        ResourceManager.this.resourcesGraph.addEdge(newResource, child);
                    }
                    result = new AddResult<>(AddResult.Effect.MODIFIED, newResource);
                } else {
                    result = new AddResult<>(AddResult.Effect.UNCHANGED, oldResource);
                }
            } else {
                result = new AddResult<>(AddResult.Effect.ADDED, newResource);
            }

            if ((result.getEffect() != AddResult.Effect.UNCHANGED) && (newResource.getParent() != null)) {
                this.resourcesGraph.addEdge(newResource.getParent(), newResource);
            }

            return result;

        } finally {
            graphLockWrite.unlock();
        }
    }

    /**
     * Remove the resource from {@link #resourcesGraph}, including all its descendants.
     *
     * @param doomedResource the resource to remove
     * @return an unmodifiable list of {@link Resources} that were removed by this method
     */
    public List<Resource<L>> removeResource(Resource<L> doomedResource) {
        graphLockWrite.lock();
        try {
            List<Resource<L>> removedResources = new ArrayList<Resource<L>>();
            Resource<L> resourceToRemove = getResource(doomedResource.getID());
            if (resourceToRemove != null) {
                getAllDescendants(resourceToRemove, removedResources);
                removedResources.add(resourceToRemove);
                removedResources.forEach(r -> this.resourcesGraph.removeVertex(r));
            }
            return Collections.unmodifiableList(removedResources);
        } finally {
            graphLockWrite.unlock();
        }
    }

    /**
     * Find the resources in {@link #resourcesGraph} matching the given {@code query}.
     *
     * @param query a location eventually containing wildcards
     * @param locationResolver the {@link LocationResolver} to perform the matching of graph nodes against the given
     *            {@code query}
     * @return an unmodifiable list of {@link Resources} that match the given {@code query}
     */
    public List<Resource<L>> findResources(L query, LocationResolver<L> locationResolver) {
        graphLockRead.lock();
        try {
            List<Resource<L>> result = new ArrayList<Resource<L>>();
            GraphIterator<Resource<L>, DefaultEdge> it = new BreadthFirstIterator<Resource<L>, DefaultEdge>(
                    this.resourcesGraph);
            while (it.hasNext()) {
                Resource<L> resource = it.next();
                if (locationResolver.matches(query, resource.getLocation())) {
                    result.add(resource);
                }
            }
            return Collections.unmodifiableList(result);
        } finally {
            graphLockRead.unlock();
        }
    }

    /**
     * Returns an immutable {@link Set} of the direct children of the given resource.
     *
     * @param resource the resource whose children are to be returned
     *
     * @return a {@link Set} if direct children of the given resource
     */
    public Set<Resource<L>> getChildren(Resource<L> resource) {
        graphLockRead.lock();
        try {
            Set<Resource<L>> directChildren = neighborIndex.successorsOf(resource);
            return Collections.unmodifiableSet(new HashSet<>(directChildren));
        } finally {
            graphLockRead.unlock();
        }
    }

    /**
     * Returns the direct parent of the given resource. This examines the internal hierarchical graph
     * to determine parentage.
     *
     * @param resource the resource whose parent is to be returned
     *
     * @return the direct parent of the given resource, or null if this is a root resource without a parent
     *
     * @throws IllegalArgumentException if the resource itself is not found in the graph
     */
    public Resource<L> getParent(Resource<L> resource) {
        // do NOT call resource.getParent(), we want the one in our graph, not the one in the resource object
        graphLockRead.lock();
        try {
            Set<Resource<L>> directParents = neighborIndex.predecessorsOf(resource);
            if (directParents.isEmpty()) {
                return null;
            }
            return directParents.iterator().next();
        } finally {
            graphLockRead.unlock();
        }
    }

    /**
     * Given a resource ID this will return the resource with that ID that is found in the internal
     * hierarchical graph or <code>null</code> if there is no resource with that ID in the graph.
     *
     * @param resourceId the ID of the resource to retrieve
     * @return the resource as found in the internal graph or <code>null</code>
     */
    public Resource<L> getResource(ID resourceId) {
        graphLockRead.lock();
        try {
            return resourceCache.get(resourceId);
        } finally {
            graphLockRead.unlock();
        }
    }

    /**
     * Returns an immutable {@link List} of all {@link Resource}s contained in {@link #resourcesGraph} in breadth-first
     * order.
     *
     * @return the list of all {@link Resource}s
     */
    public List<Resource<L>> getResourcesBreadthFirst() {
        graphLockRead.lock();
        try {
            List<Resource<L>> result = new ArrayList<Resource<L>>();

            Set<Resource<L>> roots = getRootResources();
            if (roots.isEmpty()) {
                return Collections.emptyList();
            }

            // loop over each root resource and traverse their tree hierarchy breadth-first
            // roots.forEach(root -> new BreadthFirstIterator<>(ResourceManager.this.resourcesGraph, root)
            //     .forEachRemaining(it -> result.add(it)));
            for (Resource<L> root : roots) {
                GraphIterator<Resource<L>, DefaultEdge> it = new BreadthFirstIterator<Resource<L>, DefaultEdge>(
                        ResourceManager.this.resourcesGraph, root);
                while (it.hasNext()) {
                    result.add(it.next());
                }
            }

            return Collections.unmodifiableList(result);
        } finally {
            graphLockRead.unlock();
        }
    }

    /**
     * Returns an immutable {@link Set} of {@link Resource}s that are at the top of the hierarchy (that is, they do not
     * have a parent).
     *
     * @return a {@link Set} of root {@link Resource}s
     */
    public Set<Resource<L>> getRootResources() {
        graphLockRead.lock();
        try {
            Set<Resource<L>> roots = new HashSet<>();
            Set<Resource<L>> allResources = resourcesGraph.vertexSet();
            for (Resource<L> resource : allResources) {
                if (neighborIndex.predecessorsOf(resource).isEmpty()) {
                    roots.add(resource);
                }
            }
            return Collections.unmodifiableSet(roots);
        } finally {
            graphLockRead.unlock();
        }
    }

    public void logTreeGraph(String logMsg, long duration) {
        if (!log.isDebugEnabled()) {
            return;
        }

        try {
            StringBuilder graphString = new StringBuilder();
            for (Resource<L> resource : getResourcesBreadthFirst()) {

                // append some indents based on depth of resource in tree
                Resource<L> parent = resource.getParent();
                while (parent != null) {
                    graphString.append("...");
                    parent = parent.getParent();
                }

                // append resource to string
                graphString.append(resource).append("\n");
            }

            log.debugf("%s\n%s\nDiscovery duration: [%d]ms", logMsg, graphString, duration);
        } catch (Exception e) {
            log.debugf(e, "Cannot log tree graph");
        }
    }

    /**
     * Remove the resources from {@link #resourcesGraph} matching the given {@code query} including all direct and
     * indirect descendants.
     *
     * @param query a location eventually containing wildcards
     * @param locationResolver the {@link LocationResolver} to perform the matching of graph nodes against the given
     *            {@code query}
     * @return an unmodifiable list of {@link Resources} that were removed by this method
     */
    public List<Resource<L>> removeResources(L query, LocationResolver<L> locationResolver) {
        graphLockWrite.lock();
        try {
            List<Resource<L>> doomedResources = new ArrayList<Resource<L>>();
            GraphIterator<Resource<L>, DefaultEdge> it = new DepthFirstIterator<>(this.resourcesGraph);
            while (it.hasNext()) {
                Resource<L> resource = it.next();
                if (locationResolver.matches(query, resource.getLocation())) {
                    getAllDescendants(resource, doomedResources);
                    doomedResources.add(resource);
                }
            }

            // we couldn't do this while iterating (a ConcurrentModificationException would have resulted)
            // but now that we have the doomed resources, we can remove them from the graph now
            for (Resource<L> doomedResource : doomedResources) {
                this.resourcesGraph.removeVertex(doomedResource);
            }

            return Collections.unmodifiableList(doomedResources);
        } finally {
            graphLockWrite.unlock();
        }
    }

    /**
     * Always call with {@link #graphLockWrite} locked.
     */
    private void reinitializeIfNecessary() {
        if (this.resourceCache == null || this.resourceCache.size() > 0) {
            this.resourcesGraph = new ListenableDirectedGraph<>(DefaultEdge.class);
            this.neighborIndex = new DirectedNeighborIndex<>(this.resourcesGraph);
            this.resourcesGraph.addGraphListener(neighborIndex);
            this.resourceCache = new HashMap<>();
            this.resourcesGraph.addVertexSetListener(new VertexCacheListener());
        }
    }

    // make sure you call this with a graph lock - either read or write
    private void getAllDescendants(Resource<L> parent, List<Resource<L>> descendants) {
        for (Resource<L> child : getChildren(parent)) {
            if (!descendants.contains(child)) {
                getAllDescendants(child, descendants);
                descendants.add(child);
            }
        }
        return;
    }
}
