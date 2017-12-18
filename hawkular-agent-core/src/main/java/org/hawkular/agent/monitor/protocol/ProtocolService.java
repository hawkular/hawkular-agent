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
package org.hawkular.agent.monitor.protocol;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.hawkular.agent.monitor.api.InventoryListener;
import org.hawkular.agent.monitor.inventory.NodeLocation;
import org.hawkular.agent.monitor.log.AgentLoggers;
import org.hawkular.agent.monitor.log.MsgLogger;

/**
 * A collection of {@link EndpointService}s that all handle a single protocol.
 *
 * @author <a href="https://github.com/ppalaga">Peter Palaga</a>
 *
 * @param <L> the type of the protocol specific location, typically a subclass of {@link NodeLocation}
 * @param <S> the protocol specific {@link Session}
 */
public class ProtocolService<L, S extends Session<L>> {
    private static final MsgLogger log = AgentLoggers.getLogger(ProtocolService.class);

    public static class Builder<L, S extends Session<L>> {
        private String name;
        private Map<String, EndpointService<L, S>> endpointServices = new HashMap<>();

        private Builder() {
        }

        public ProtocolService<L, S> build() {
            return new ProtocolService<L, S>(name, Collections.synchronizedMap(endpointServices));
        }

        public Builder<L, S> name(String name) {
            this.name = name;
            return this;
        }

        public Builder<L, S> endpointService(EndpointService<L, S> endpointService) {
            endpointServices.put(endpointService.getMonitoredEndpoint().getName(), endpointService);
            return this;
        }
    }

    public static <L, S extends Session<L>> Builder<L, S> builder(String name) {
        return new Builder<L, S>().name(name);
    }

    private final String name;
    private final Map<String, EndpointService<L, S>> endpointServices;

    // need to remember the listeners in case new endpoints are added after things have started
    private final List<InventoryListener> inventoryListeners = Collections.synchronizedList(new ArrayList<>());

    public ProtocolService(String name, Map<String, EndpointService<L, S>> endpointServices) {
        this.name = name;
        this.endpointServices = endpointServices;
    }

    /**
     * @return the protocol name
     */
    public String getName() {
        return name;
    }

    /**
     * @return a shallow copy of all endpoint services
     */
    public Map<String, EndpointService<L, S>> getEndpointServices() {
        synchronized (endpointServices) {
            return new HashMap<>(endpointServices);
        }
    }

    public void discoverAll() {
        for (EndpointService<L, S> service : getEndpointServices().values()) {
            service.discoverAll();
        }
    }

    public void start() {
        // Note that any endpoint service start method may block!
        // It may wait for resources to come up first before returning.
        for (EndpointService<L, S> service : getEndpointServices().values()) {
            service.start();
        }
    }

    public void stop() {
        for (EndpointService<L, S> service : getEndpointServices().values()) {
            service.stop();
        }
    }

    public void addInventoryListener(InventoryListener listener) {
        for (EndpointService<L, S> service : getEndpointServices().values()) {
            service.addInventoryListener(listener);
        }
        this.inventoryListeners.add(listener);
    }

    public void removeInventoryListener(InventoryListener listener) {
        for (EndpointService<L, S> service : getEndpointServices().values()) {
            service.removeInventoryListener(listener);
        }
        this.inventoryListeners.remove(listener);
    }

    /**
     * This will add a new endpoint service to the list. Once added, the new service
     * will immediately be started.
     *
     * @param newEndpointService the new service to add and start
     */
    public void add(EndpointService<L, S> newEndpointService) {
        if (newEndpointService == null) {
            throw new IllegalArgumentException("New endpoint service must not be null");
        }

        synchronized (this.inventoryListeners) {
            for (InventoryListener listener : this.inventoryListeners) {
                newEndpointService.addInventoryListener(listener);
            }
        }

        endpointServices.put(newEndpointService.getMonitoredEndpoint().getName(), newEndpointService);
        newEndpointService.start();
        log.infoAddedEndpointService(newEndpointService.toString());

        newEndpointService.discoverAll();
    }

    /**
     * This will stop the given endpoint service and remove it from the list of endpoint services.
     *
     * @param name identifies the endpoint service to remove
     */
    public void remove(String name) {
        EndpointService<L, S> service = endpointServices.remove(name);
        if (service != null) {
            service.stop();
            log.infoRemovedEndpointService(service.toString());
        }
    }
}
