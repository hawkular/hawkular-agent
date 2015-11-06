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
package org.hawkular.agent.monitor.api;

import org.hawkular.agent.monitor.inventory.MonitoredEndpoint;

/**
 * A listener for changes in the inventory of resources maintained by the present agent.
 *
 * @author <a href="https://github.com/ppalaga">Peter Palaga</a>
 */
public interface InventoryListener {
    /**
     * Notifies the listener that the full discovery of resources has finished. The list in
     * {@link InventoryEvent#getPayload()} is immutable and contains all resources discovered in breadth first
     * order.
     * <p>
     * If the listener maintains a list of resources, this notification means that that the content of the old list
     * should be thrown away and replaced by the list in {@link InventoryEvent#getPayload()}.
     * <p>
     * Note that during full discovery {@link #resourceAdded(InventoryEvent)} is not
     * invoked for resources delivered via {@link #discoverAllFinished(InventoryEvent)}.
     *
     * @param event the {@link InventoryEvent}
     */
    <L, E extends MonitoredEndpoint> void discoverAllFinished(InventoryEvent<L, E> event);

    /**
     * Notifies this listener that the resource in {@link InventoryEvent#getPayload()} was added to the monitored
     * endpoint. Note that this event is fired only for resources added by the present agent. Resources added by other
     * means can only be detected by a full discovery - see {@link #discoverAllFinished(InventoryEvent)}.
     *
     * @param event the {@link InventoryEvent}
     */
    <L, E extends MonitoredEndpoint> void resourcesAdded(InventoryEvent<L, E> event);

    /**
     * Notifies this listener that the resource in {@link InventoryEvent#getPayload()} was removed from the monitored
     * endpoint. Note that this event is fired only for resources removed by the present agent. Resources removed by
     * other means can only be detected by a full discovery - see {@link #discoverAllFinished(InventoryEvent)}.
     *
     * @param event the {@link InventoryEvent}
     */
    <L, E extends MonitoredEndpoint> void resourceRemoved(InventoryEvent<L, E> event);
}
