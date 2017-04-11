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
package org.hawkular.agent.monitor.api;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.hawkular.agent.monitor.inventory.Resource;
import org.hawkular.agent.monitor.inventory.ResourceManager;
import org.hawkular.agent.monitor.inventory.ResourceTypeManager;

/**
 * A event for discovery scans.
 *
 * @see InventoryListener
 */
public class InventoryEvent<L> {

    private final SamplingService<L> samplingService;
    private final ResourceManager<L> resourceManager;
    private final Optional<ResourceTypeManager<L>> resourceTypeManager;
    private final Map<String, Resource<L>> addedOrModifiedRootResources;
    private final Map<String, Resource<L>> removedRootResources;
    private final List<Resource<L>> addedOrModified;
    private final List<Resource<L>> removed;

    /**
     * Creates an inventory event.
     * @param samplingService a service that provides details such as feed ID and endpoint information that helps
     *                        identify the resources in the event, plus has methods that can be used to monitor
     *                        the resources in the event.
     * @param resourceManager the resources associated with the event
     * @param resourceTypeManager the resource types associated with the event. Omit if resource type sync is not
     *                           needed
     * @param addedOrModified list of added or modified resources
     * @param removed         list of removed resources
     */
    private InventoryEvent(SamplingService<L> samplingService,
                          ResourceManager<L> resourceManager,
                          Optional<ResourceTypeManager<L>> resourceTypeManager,
                          List<Resource<L>> addedOrModified,
                          List<Resource<L>> removed) {
        if (samplingService == null) {
            throw new IllegalArgumentException("Sampling service cannot be null");
        }

        if (resourceManager == null) {
            throw new IllegalArgumentException("Resource manager cannot be null");
        }

        if (resourceTypeManager == null) {
            throw new IllegalArgumentException("Resource type manager cannot be null");
        }

        this.samplingService = samplingService;
        this.resourceManager = resourceManager;
        this.resourceTypeManager = resourceTypeManager;
        this.addedOrModified = addedOrModified;
        this.removed = removed;

        // Distribute 'addedOrModified' and 'removed' in 'addedOrModifiedRootResources' and 'removedRootResources'
        addedOrModifiedRootResources = new HashMap<>();
        removedRootResources = new HashMap<>();
        addedOrModified.forEach(r -> {
            Resource<L> root = getRootResource(r);
            addedOrModifiedRootResources.put(root.getID().getIDString(), root);
        });
        removed.forEach(r -> {
            if (r.getParent() == null) {
                // Root resource removed
                removedRootResources.put(r.getID().getIDString(), r);
            } else {
                Resource<L> root = getRootResource(r);
                addedOrModifiedRootResources.put(root.getID().getIDString(), root);
            }
        });
    }

    /**
     * Build an {@link InventoryEvent} for removed resources
     * @param samplingService a service that provides details such as feed ID and endpoint information that helps
     *                        identify the resources in the event, plus has methods that can be used to monitor
     *                        the resources in the event.
     * @param resourceManager the resources associated with the event
     * @param removed         list of removed resources
     */
    public static <L> InventoryEvent<L> removed(SamplingService<L> samplingService,
                                              ResourceManager<L> resourceManager,
                                              List<Resource<L>> removed) {
        return new InventoryEvent<>(samplingService, resourceManager, Optional.empty(), new ArrayList<>(), removed);
    }

    /**
     * Build an {@link InventoryEvent} for added or modified resources
     * @param samplingService a service that provides details such as feed ID and endpoint information that helps
     *                        identify the resources in the event, plus has methods that can be used to monitor
     *                        the resources in the event.
     * @param resourceManager the resources associated with the event
     * @param addedOrModified list of added or modified resources
     */
    public static <L> InventoryEvent<L> addedOrModified(SamplingService<L> samplingService,
                                              ResourceManager<L> resourceManager,
                                              List<Resource<L>> addedOrModified) {
        return new InventoryEvent<>(samplingService, resourceManager, Optional.empty(), addedOrModified, new ArrayList<>());
    }

    /**
     * Build an {@link InventoryEvent} for added or modified resources
     * @param samplingService a service that provides details such as feed ID and endpoint information that helps
     *                        identify the resources in the event, plus has methods that can be used to monitor
     *                        the resources in the event.
     * @param resourceManager the resources associated with the event
     * @param resourceTypeManager the resource types associated with the event
     * @param addedOrModified list of added or modified resources
     * @param removed         list of removed resources
     */
    public static <L> InventoryEvent<L> discovery(SamplingService<L> samplingService,
                                                     ResourceManager<L> resourceManager,
                                                     ResourceTypeManager<L> resourceTypeManager,
                                                     List<Resource<L>> addedOrModified,
                                                     List<Resource<L>> removed) {
        return new InventoryEvent<>(
                samplingService,
                resourceManager,
                Optional.of(resourceTypeManager),
                addedOrModified,
                removed);
    }

    private static <T> Resource<T> getRootResource(Resource<T> resource) {
        if (resource.getParent() == null) {
            return resource;
        }
        return getRootResource(resource.getParent());
    }

    /**
     * @return the contextual sampling service associated with the event
     */
    public SamplingService<L> getSamplingService() {
        return samplingService;
    }

    /**
     * @return the resource manager that was populated for this event
     */
    public ResourceManager<L> getResourceManager() {
        return resourceManager;
    }

    /**
     * @return when relevant, the resource type manager containing all the types of resources that discovery scans
     * might find
     */
    public Optional<ResourceTypeManager<L>> getResourceTypeManager() {
        return resourceTypeManager;
    }

    /**
     * @return the list of added or modified root resources. Note that if a non-root resource was removed, its root
     * resource will be listed here.
     */
    public Collection<Resource<L>> getAddedOrModifiedRootResources() {
        return addedOrModifiedRootResources.values();
    }

    /**
     * @return the list of removed root resources
     */
    public Collection<Resource<L>> getRemovedRootResources() {
        return removedRootResources.values();
    }

    /**
     * @return the list of added or modified resources, including non-root resources
     */
    public List<Resource<L>> getAddedOrModified() {
        return addedOrModified;
    }

    /**
     * @return the list of removed resources, including non-root resources
     */
    public List<Resource<L>> getRemoved() {
        return removed;
    }
}
