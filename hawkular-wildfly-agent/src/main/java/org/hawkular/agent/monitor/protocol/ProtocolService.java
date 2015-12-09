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
import org.hawkular.agent.monitor.inventory.NodeLocation;
import org.hawkular.agent.monitor.inventory.ResourceTypeManager;

/**
 * A collection of {@link EndpointService}s that all handle a single protocol and share a single
 * {@link ResourceTypeManager}.
 *
 * @author <a href="https://github.com/ppalaga">Peter Palaga</a>
 *
 * @param <L> the type of the protocol specific location, typically a subclass of {@link NodeLocation}
 * @param <S> the protocol specific {@link Session}
 */
public class ProtocolService<L, S extends Session<L>> {

    public static class Builder<L, S extends Session<L>> {
        private Map<String, EndpointService<L, S>> endpointServices = new HashMap<>();

        public ProtocolService<L, S> build() {
            return new ProtocolService<L, S>(Collections.unmodifiableMap(endpointServices));
        }

        public Builder<L, S> endpointService(EndpointService<L, S> endpointService) {
            endpointServices.put(endpointService.getEndpoint().getName(), endpointService);
            return this;
        }
    }

    public static <L, S extends Session<L>> Builder<L, S> builder() {
        return new Builder<L, S>();
    }

    private final Map<String, EndpointService<L, S>> endpointServices;
    public ProtocolService(Map<String, EndpointService<L, S>> endpointServices) {
        this.endpointServices = endpointServices;
    }

    public Map<String, EndpointService<L, S>> getEndpointServices() {
        return endpointServices;
    }

    public void discoverAll() {
        for (EndpointService<L, S> service : endpointServices.values()) {
            service.discoverAll();
        }
    }

    public void start() {
        for (EndpointService<L, S> service : endpointServices.values()) {
            service.start();
        }
    }

    public void stop() {
        for (EndpointService<L, S> service : endpointServices.values()) {
            service.stop();
        }
    }

    public void addInventoryListener(InventoryListener listener) {
        for (EndpointService<L, S> service : endpointServices.values()) {
            service.addInventoryListener(listener);
        }
    }

    public void removeInventoryListener(InventoryListener listener) {
        for (EndpointService<L, S> service : endpointServices.values()) {
            service.removeInventoryListener(listener);
        }
    }

}
