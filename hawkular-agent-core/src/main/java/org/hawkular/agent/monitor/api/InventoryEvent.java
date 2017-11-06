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
import java.util.List;
import java.util.Optional;

import org.hawkular.agent.monitor.inventory.Resource;
import org.hawkular.agent.monitor.inventory.ResourceManager;
import org.hawkular.agent.monitor.inventory.ResourceTypeManager;
import org.hawkular.agent.monitor.protocol.EndpointService;
import org.hawkular.agent.monitor.protocol.Session;

/**
 * A event for discovery scans.
 *
 * @see InventoryListener
 */
public class InventoryEvent<L, S extends Session<L>> {

    private final EndpointService<L, S> service;
    private final ResourceManager<L> resourceManager;
    private final Optional<ResourceTypeManager<L>> resourceTypeManager;
    private final List<Resource<L>> addedOrModified;
    private final List<Resource<L>> removed;

    /**
     * Creates an inventory event.
     *
     * @param service a service that provides details such as feed ID and endpoint information that helps
     *                identify the resources in the event, plus has methods that can be used to monitor
     *                the resources in the event.
     * @param resourceManager the resources associated with the event
     * @param resourceTypeManager the resource types associated with the event. Omit if resource type sync is not
     *                            needed
     * @param addedOrModified list of added or modified resources
     * @param removed list of removed resources
     */
    private InventoryEvent(
            EndpointService<L, S> service,
            ResourceManager<L> resourceManager,
            Optional<ResourceTypeManager<L>> resourceTypeManager,
            List<Resource<L>> addedOrModified,
            List<Resource<L>> removed) {

        if (service == null) {
            throw new IllegalArgumentException("service cannot be null");
        }

        if (resourceManager == null) {
            throw new IllegalArgumentException("Resource manager cannot be null");
        }

        if (resourceTypeManager == null) {
            throw new IllegalArgumentException("Resource type manager cannot be null");
        }

        this.service = service;
        this.resourceManager = resourceManager;
        this.resourceTypeManager = resourceTypeManager;
        this.addedOrModified = addedOrModified;
        this.removed = removed;
    }

    /**
     * Build an {@link InventoryEvent} for removed resources.
     *
     * @param service a service that provides details such as feed ID and endpoint information that helps
     *                identify the resources in the event, plus has methods that can be used to monitor
     *                the resources in the event.
     * @param resourceManager the resources associated with the event
     * @param removed list of removed resources
     */
    public static <L, S extends Session<L>> InventoryEvent<L, S> removed(
            EndpointService<L, S> service,
            ResourceManager<L> resourceManager,
            List<Resource<L>> removed) {
        return new InventoryEvent<>(service, resourceManager, Optional.empty(), new ArrayList<>(), removed);
    }

    /**
     * Build an {@link InventoryEvent} for added or modified resources.
     *
     * @param service a service that provides details such as feed ID and endpoint information that helps
     *                identify the resources in the event, plus has methods that can be used to monitor
     *                the resources in the event.
     * @param resourceManager the resources associated with the event
     * @param addedOrModified list of added or modified resources
     */
    public static <L, S extends Session<L>> InventoryEvent<L, S> addedOrModified(
            EndpointService<L, S> service,
            ResourceManager<L> resourceManager,
            List<Resource<L>> addedOrModified) {
        return new InventoryEvent<>(service, resourceManager, Optional.empty(), addedOrModified, new ArrayList<>());
    }

    /**
     * Build an {@link InventoryEvent} for added or modified resources.
     *
     * @param service a service that provides details such as feed ID and endpoint information that helps
     *                identify the resources in the event, plus has methods that can be used to monitor
     *                the resources in the event.
     * @param resourceManager the resources associated with the event
     * @param resourceTypeManager the resource types associated with the event
     * @param addedOrModified list of added or modified resources
     * @param removed list of removed resources
     */
    public static <L, S extends Session<L>> InventoryEvent<L, S> discovery(
            EndpointService<L, S> service,
            ResourceManager<L> resourceManager,
            ResourceTypeManager<L> resourceTypeManager,
            List<Resource<L>> addedOrModified,
            List<Resource<L>> removed) {
        return new InventoryEvent<>(
                service,
                resourceManager,
                Optional.of(resourceTypeManager),
                addedOrModified,
                removed);
    }

    /**
     * @return the contextual sampling service associated with the event
     */
    public EndpointService<L, S> getEndpointService() {
        return service;
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
