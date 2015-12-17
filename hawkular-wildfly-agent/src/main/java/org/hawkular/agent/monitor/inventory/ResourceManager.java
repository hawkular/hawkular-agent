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
import java.util.Collections;
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
     * Adds the given resource to the resource hierarchy. If the resource is a child of a parent, that parent must
     * already be known or an exception is thrown.
     *
     * @param newResource the new resource to be added
     * @throws IllegalArgumentException if the new resource's parent does not yet exist in the hierarchy
     */
    public void addResource(Resource<L> newResource) throws IllegalArgumentException {
        graphLockWrite.lock();
        try {
            this.resourcesGraph.addVertex(newResource);
            if (newResource.getParent() != null) {
                this.resourcesGraph.addEdge(newResource.getParent(), newResource);
            }
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
     * Returns the direct parent of the given resource.
     *
     * @param resource the resource whose parent is to be returned
     *
     * @return the direct parent of the given resource, or null if this is a root resource without a parent
     */
    public Resource<L> getParent(Resource<L> resource) {
        // We could do resource.getParent(), but so could our caller. Here, let's go through the graph to get it.
        // We know all resources have at most one parent.
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
     * Given a resource ID this will return the resource with that ID or <code>null</code> if there is no resource with
     * that ID.
     *
     * @param resourceId the ID of the resource to retrieve
     * @return the resource or null
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
            GraphIterator<Resource<L>, DefaultEdge> it = new BreadthFirstIterator<Resource<L>, DefaultEdge>(
                    this.resourcesGraph);
            while (it.hasNext()) {
                result.add(it.next());
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
            Set<Resource<L>> allTypes = resourcesGraph.vertexSet();
            for (Resource<L> type : allTypes) {
                if (neighborIndex.predecessorsOf(type).isEmpty()) {
                    roots.add(type);
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
                    doomedResources.add(resource);
                    getAllDescendants(resource, doomedResources);
                }
            }

            // we couldn't do this while iterating (a ConcurrentModificationException would have resulted)
            // but now that we have the doomed resources, we can remove them from the graph now
            for (Resource<L> doomedResource : doomedResources) {
                this.resourcesGraph.removeVertex(doomedResource);
                this.resourcesGraph.removeEdge(doomedResource.getParent(), doomedResource);
            }

            return Collections.unmodifiableList(doomedResources);
        } finally {
            graphLockWrite.unlock();
        }
    }

    // make sure you call this with a graph lock - either read or write
    private void getAllDescendants(Resource<L> parent, List<Resource<L>> descendants) {
        for (Resource<L> child : getChildren(parent)) {
            if (!descendants.contains(child)) {
                descendants.add(child);
                getAllDescendants(child, descendants);
            }
        }
        return;
    }

    /**
     * Replaces the set of {@link Resources} currently available in {@link #resourcesGraph} with the resources from the
     * given {@code newResources} list. Note that this method eventually replaces the graph instance in
     * {@link #resourcesGraph} together with {@link #neighborIndex} and {@link #resourceCache}.
     *
     * @param newResources a {@link List} of resources to replace the set of {@link Resources} currently available in
     *            {@link #resourcesGraph}. {@code newResources} must be in breadth first order
     * @throws IllegalArgumentException if the parent of any of the added resources is not in the graph.
     */
    public void replaceResources(List<Resource<L>> newResources) throws IllegalArgumentException {
        graphLockWrite.lock();
        try {
            /* nuke the old graph */
            reinitializeIfNecessary();
            for (Resource<L> resource : newResources) {
                this.resourcesGraph.addVertex(resource);
                if (resource.getParent() != null) {
                    this.resourcesGraph.addEdge(resource.getParent(), resource);
                }
            }
        } finally {
            graphLockWrite.unlock();
        }
    }

}
