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
package org.hawkular.agent.monitor.api;

import java.util.List;
import java.util.Map;

import org.hawkular.agent.monitor.inventory.ResourceType;

/**
 * A listener for changes in the inventory of resources maintained by the present agent.
 *
 * @author <a href="https://github.com/ppalaga">Peter Palaga</a>
 */
public interface InventoryListener {
    /**
     * Notifies this listener that the resources in {@link InventoryEvent#getPayload()}
     * were added to the monitored endpoint.
     *
     * @param event the {@link InventoryEvent}
     */
    <L> void resourcesAdded(InventoryEvent<L> event);

    /**
     * Notifies this listener that the resources in {@link InventoryEvent#getPayload()}
     * were removed from the monitored endpoint.
     *
     * @param event the {@link InventoryEvent}
     */
    <L> void resourcesRemoved(InventoryEvent<L> event);

    /**
     * Notifies this listener that a discovery scan has completed. The resource tree
     * in {@link DiscoveryEvent#getResourceManager()} were updated as part of the completed
     * discovery scan.
     *
     * @param event the {@link DiscoveryEvent}
     */
    <L> void discoveryCompleted(DiscoveryEvent<L> event);

    /**
     * Notifies the listener that the full set of resource types known to the agent has been refreshed.
     * These are all the known types - some may be new, some may be the same from a prior refresh, and some
     * may have been removed this time as compared to a previous refresh.
     *
     * @param typesByTenantId a collection of all resource types known to the agent organized by tenant ID
     */
    <L> void allResourceTypes(Map<String, List<ResourceType<L>>> typesByTenantId);
}
