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
package org.hawkular.agent.monitor.protocol;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import org.hawkular.agent.monitor.api.InventoryListener;
import org.hawkular.agent.monitor.inventory.MonitoredEndpoint;
import org.hawkular.agent.monitor.inventory.ResourceTypeManager;

/**
 * A collection of {@link EndpointService}s that all handle a single protocol and share a single
 * {@link ResourceTypeManager}.
 *
 * @author <a href="https://github.com/ppalaga">Peter Palaga</a>
 *
 * @param <L> the type of the protocol specific location, typically a subclass of {@link NodeLocation}
 * @param <E> the protocol specific {@link MonitoredEndpoint}
 * @param <S> the protocol specific {@link Session}
 */
public class ProtocolService<L, E extends MonitoredEndpoint, S extends Session<L, E>> {
    public static class Builder<L, E extends MonitoredEndpoint, //
    C extends Session<L, E>> {
        private Map<String, EndpointService<L, E, C>> endpointServices = new HashMap<>();
        private ResourceTypeManager<L> resourceTypeManager;

        public ProtocolService<L, E, C> build() {
            return new ProtocolService<>(Collections.unmodifiableMap(endpointServices), resourceTypeManager);
        }

        public Builder<L, E, C> endpointService(EndpointService<L, E, C> endpointService) {
            endpointServices.put(endpointService.getEndpoint().getName(), endpointService);
            return this;
        }

        public Builder<L, E, C> resourceTypeManager(ResourceTypeManager<L> resourceTypeManager) {
            this.resourceTypeManager = resourceTypeManager;
            return this;
        }
    }

    public static <L, E extends MonitoredEndpoint, //
    C extends Session<L, E>> Builder<L, E, C> builder() {
        return new Builder<L, E, C>();
    }

    private final Map<String, EndpointService<L, E, S>> endpointServices;
    private final ResourceTypeManager<L> resourceTypeManager;

    public ProtocolService(Map<String, EndpointService<L, E, S>> endpointServices,
            ResourceTypeManager<L> resourceTypeManager) {
        super();
        this.endpointServices = endpointServices;
        this.resourceTypeManager = resourceTypeManager;
    }

    public Map<String, EndpointService<L, E, S>> getEndpointServices() {
        return endpointServices;
    }

    /**
     * @return a {@link ResourceTypeManager} shared over all {@link MonitoredEndpoint}s of the given protocol.
     */
    public ResourceTypeManager<L> getResourceTypeManager() {
        return resourceTypeManager;
    }

    public void discoverAll() {
        for (EndpointService<L, E, S> service : endpointServices.values()) {
            service.discoverAll();
        }
    }

    public void start() {
        for (EndpointService<L, E, S> service : endpointServices.values()) {
            service.start();
        }
    }

    public void stop() {
        for (EndpointService<L, E, S> service : endpointServices.values()) {
            service.stop();
        }
    }

    public void addInventoryListener(InventoryListener listener) {
        for (EndpointService<L, E, S> service : endpointServices.values()) {
            service.addInventoryListener(listener);
        }
    }

    public void removeInventoryListener(InventoryListener listener) {
        for (EndpointService<L, E, S> service : endpointServices.values()) {
            service.removeInventoryListener(listener);
        }
    }

}
