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

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import javax.net.ssl.SSLContext;

import org.hawkular.agent.monitor.api.InventoryListener;
import org.hawkular.agent.monitor.diagnostics.Diagnostics;
import org.hawkular.agent.monitor.extension.MonitorServiceConfiguration.ProtocolConfiguration;
import org.hawkular.agent.monitor.inventory.MonitoredEndpoint;
import org.hawkular.agent.monitor.inventory.ResourceTypeManager;
import org.hawkular.agent.monitor.inventory.TypeSets;
import org.hawkular.agent.monitor.log.AgentLoggers;
import org.hawkular.agent.monitor.log.MsgLogger;
import org.hawkular.agent.monitor.protocol.dmr.DMREndpoint;
import org.hawkular.agent.monitor.protocol.dmr.DMREndpointService;
import org.hawkular.agent.monitor.protocol.dmr.DMRManagedServer;
import org.hawkular.agent.monitor.protocol.dmr.DMRNodeLocation;
import org.hawkular.agent.monitor.protocol.dmr.DMRSession;
import org.hawkular.agent.monitor.protocol.dmr.LocalDMRManagedServer;
import org.hawkular.agent.monitor.protocol.dmr.ModelControllerClientFactory;
import org.hawkular.agent.monitor.protocol.dmr.RemoteDMRManagedServer;
import org.hawkular.agent.monitor.protocol.jmx.JMXEndpoint;
import org.hawkular.agent.monitor.protocol.jmx.JMXEndpointService;
import org.hawkular.agent.monitor.protocol.jmx.JMXNodeLocation;
import org.hawkular.agent.monitor.protocol.jmx.JMXSession;
import org.hawkular.agent.monitor.protocol.jmx.RemoteJMXManagedServer;
import org.hawkular.agent.monitor.protocol.platform.PlatformEndpoint;
import org.hawkular.agent.monitor.protocol.platform.PlatformEndpointService;
import org.hawkular.agent.monitor.protocol.platform.PlatformManagedServer;
import org.hawkular.agent.monitor.protocol.platform.PlatformNodeLocation;
import org.hawkular.agent.monitor.protocol.platform.PlatformSession;
import org.jboss.msc.value.InjectedValue;

/**
 * A bunch of {@link ProtocolService}s.
 *
 * @author <a href="https://github.com/ppalaga">Peter Palaga</a>
 */
public class ProtocolServices {
    public static class Builder {
        private final String feedId;
        private ProtocolService<DMRNodeLocation, DMREndpoint, DMRSession> dmrProtocolService;
        private ProtocolService<JMXNodeLocation, JMXEndpoint, JMXSession> jmxProtocolService;
        private ProtocolService<PlatformNodeLocation, PlatformEndpoint, PlatformSession> platformProtocolService;
        private final Map<String, InjectedValue<SSLContext>> sslContexts;
        private final Diagnostics diagnostics;

        public Builder(String feedId, Map<String, InjectedValue<SSLContext>> sslContexts, Diagnostics diagnostics) {
            this.feedId = feedId;
            this.sslContexts = sslContexts;
            this.diagnostics = diagnostics;
        }

        public ProtocolServices build() {
            return new ProtocolServices(dmrProtocolService, jmxProtocolService, platformProtocolService);
        }

        public Builder dmrProtocolService(
                ModelControllerClientFactory localModelControllerClientFactory,
                ProtocolConfiguration<DMRNodeLocation, DMRManagedServer> dmrConfiguration) {

            ProtocolService.Builder<DMRNodeLocation, DMREndpoint, DMRSession> builder = ProtocolService.builder();

            TypeSets<DMRNodeLocation> typeSets = dmrConfiguration.getTypeSets();
            // TODO: we need to provide the set of types to use and pass as second parameter
            ResourceTypeManager<DMRNodeLocation> resourceTypeManager = new ResourceTypeManager<>(typeSets, null);

            builder.resourceTypeManager(resourceTypeManager);

            for (DMRManagedServer server : dmrConfiguration.getManagedServers().values()) {
                if (!server.isEnabled()) {
                    log.infoManagedServerDisabled(server.getName().toString());
                } else {
                    final DMREndpoint endpoint;
                    final ModelControllerClientFactory clientFactory;
                    if (server instanceof RemoteDMRManagedServer) {
                        RemoteDMRManagedServer dmrServer = (RemoteDMRManagedServer) server;
                        SSLContext sslContext = dmrServer.getUseSSL()
                                ? sslContexts.get(dmrServer.getSecurityRealm()).getOptionalValue() : null;
                        endpoint = DMREndpoint.of(dmrServer, sslContext);
                        clientFactory = ModelControllerClientFactory.createRemote(endpoint);
                    } else if (server instanceof LocalDMRManagedServer) {
                        endpoint = DMREndpoint.of((LocalDMRManagedServer) server);
                        clientFactory = localModelControllerClientFactory;
                    } else {
                        throw new IllegalStateException("Unexpected subclass of [" + DMRManagedServer.class.getName()
                                + "] : [" + server.getClass().getName() + "]. Expected ["
                                + RemoteDMRManagedServer.class.getName() + "] or ["
                                + LocalDMRManagedServer.class.getName()
                                + "]. Please report this bug.");
                    }
                    DMREndpointService endpointService = new DMREndpointService(feedId, endpoint, resourceTypeManager,
                            clientFactory, diagnostics);
                    builder.endpointService(endpointService);
                }
            }

            this.dmrProtocolService = builder.build();
            return this;
        }

        public Builder jmxProtocolService(
                ProtocolConfiguration<JMXNodeLocation, RemoteJMXManagedServer> jmxConfiguration) {

            ProtocolService.Builder<JMXNodeLocation, JMXEndpoint, JMXSession> builder = ProtocolService.builder();

            TypeSets<JMXNodeLocation> typeSets = jmxConfiguration.getTypeSets();
            // TODO: we need to provide the set of types to use and pass as second parameter
            ResourceTypeManager<JMXNodeLocation> resourceTypeManager = new ResourceTypeManager<>(typeSets, null);

            builder.resourceTypeManager(resourceTypeManager);

            for (RemoteJMXManagedServer server : jmxConfiguration.getManagedServers().values()) {
                if (server.isEnabled()) {
                    SSLContext sslContext = server.getUrl().getProtocol().equalsIgnoreCase("https")
                            ? sslContexts.get(server.getSecurityRealm()).getOptionalValue() : null;
                    final JMXEndpoint endpoint = JMXEndpoint.of(server, sslContext);
                    JMXEndpointService endpointService = new JMXEndpointService(feedId, endpoint, resourceTypeManager,
                            diagnostics);
                    builder.endpointService(endpointService);
                }
            }

            this.jmxProtocolService = builder.build();
            return this;
        }

        public Builder platformProtocolService(
                ProtocolConfiguration<PlatformNodeLocation, PlatformManagedServer> platformConfiguration) {

            ProtocolService.Builder<PlatformNodeLocation, PlatformEndpoint, PlatformSession> builder = ProtocolService
                    .builder();

            TypeSets<PlatformNodeLocation> typeSets = platformConfiguration.getTypeSets();
            // TODO: we need to provide the set of types to use and pass as second parameter
            ResourceTypeManager<PlatformNodeLocation> resourceTypeManager = new ResourceTypeManager<>(typeSets, null);

            builder.resourceTypeManager(resourceTypeManager);

            for (PlatformManagedServer server : platformConfiguration.getManagedServers().values()) {
                if (server.isEnabled()) {
                    final PlatformEndpoint endpoint = new PlatformEndpoint(feedId);
                    PlatformEndpointService endpointService = new PlatformEndpointService(feedId, endpoint,
                            resourceTypeManager);
                    builder.endpointService(endpointService);
                }
            }

            this.platformProtocolService = builder.build();
            return this;
        }
    }

    private static final MsgLogger log = AgentLoggers.getLogger(ProtocolServices.class);

    public static Builder builder(String feedId, Diagnostics diagnostics,
            Map<String, InjectedValue<SSLContext>> sslContexts) {
        return new Builder(feedId, sslContexts, diagnostics);
    }

    private final ProtocolService<DMRNodeLocation, DMREndpoint, DMRSession> dmrProtocolService;
    private final ProtocolService<JMXNodeLocation, JMXEndpoint, JMXSession> jmxProtocolService;
    private final ProtocolService<PlatformNodeLocation, PlatformEndpoint, PlatformSession> platformProtocolService;
    private final List<ProtocolService<?, ?, ?>> services;

    public ProtocolServices(
            ProtocolService<DMRNodeLocation, DMREndpoint, DMRSession> dmrProtocolService,
            ProtocolService<JMXNodeLocation, JMXEndpoint, JMXSession> jmxProtocolService,
            ProtocolService<PlatformNodeLocation, PlatformEndpoint, PlatformSession> platformProtocolService) {
        this.dmrProtocolService = dmrProtocolService;
        this.jmxProtocolService = jmxProtocolService;
        this.platformProtocolService = platformProtocolService;
        this.services = Collections.unmodifiableList(Arrays.asList(dmrProtocolService, jmxProtocolService,
                platformProtocolService));
    }

    @SuppressWarnings("unchecked")
    public <L, E extends MonitoredEndpoint, S extends Session<L, E>> EndpointService<L, E, S> getEndpointService(
            String endpointName) {
        for (ProtocolService<?, ?, ?> service : services) {
            EndpointService<?, ?, ?> result = service.getEndpointServices().get(endpointName);
            if (result != null) {
                return (EndpointService<L, E, S>) result;
            }
        }
        return null;
    }

    public void start() {
        for (ProtocolService<?, ?, ?> service : services) {
            service.start();
        }
    }

    public void discoverAll() {
        for (ProtocolService<?, ?, ?> service : services) {
            service.discoverAll();
        }
    }

    public void stop() {
        for (ProtocolService<?, ?, ?> service : services) {
            service.stop();
        }
    }

    public void addInventoryListener(InventoryListener listener) {
        for (ProtocolService<?, ?, ?> service : services) {
            service.addInventoryListener(listener);
        }
    }

    public void removeInventoryListener(InventoryListener listener) {
        for (ProtocolService<?, ?, ?> service : services) {
            service.removeInventoryListener(listener);
        }
    }

    public ProtocolService<DMRNodeLocation, DMREndpoint, DMRSession> getDmrProtocolService() {
        return dmrProtocolService;
    }

    public ProtocolService<JMXNodeLocation, JMXEndpoint, JMXSession> getJmxProtocolService() {
        return jmxProtocolService;
    }

    public ProtocolService<PlatformNodeLocation, PlatformEndpoint, PlatformSession> getPlatformProtocolService() {
        return platformProtocolService;
    }

    public List<ProtocolService<?, ?, ?>> getServices() {
        return services;
    }
}
