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
import org.hawkular.agent.monitor.extension.MonitorServiceConfiguration.EndpointConfiguration;
import org.hawkular.agent.monitor.extension.MonitorServiceConfiguration.ProtocolConfiguration;
import org.hawkular.agent.monitor.inventory.MonitoredEndpoint;
import org.hawkular.agent.monitor.inventory.ResourceTypeManager;
import org.hawkular.agent.monitor.log.AgentLoggers;
import org.hawkular.agent.monitor.log.MsgLogger;
import org.hawkular.agent.monitor.protocol.dmr.DMREndpointService;
import org.hawkular.agent.monitor.protocol.dmr.DMRNodeLocation;
import org.hawkular.agent.monitor.protocol.dmr.DMRSession;
import org.hawkular.agent.monitor.protocol.dmr.ModelControllerClientFactory;
import org.hawkular.agent.monitor.protocol.jmx.JMXEndpointService;
import org.hawkular.agent.monitor.protocol.jmx.JMXNodeLocation;
import org.hawkular.agent.monitor.protocol.jmx.JMXSession;
import org.hawkular.agent.monitor.protocol.platform.PlatformEndpointService;
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
        private ProtocolService<DMRNodeLocation, DMRSession> dmrProtocolService;
        private ProtocolService<JMXNodeLocation, JMXSession> jmxProtocolService;
        private ProtocolService<PlatformNodeLocation, PlatformSession> platformProtocolService;
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
                ProtocolConfiguration<DMRNodeLocation> protocolConfig) {

            ProtocolService.Builder<DMRNodeLocation, DMRSession> builder = ProtocolService.builder();

            for (EndpointConfiguration server : protocolConfig.getEndpoints().values()) {
                if (!server.isEnabled()) {
                    log.infoManagedServerDisabled(server.getName().toString());
                } else {
                    final ModelControllerClientFactory clientFactory;
                    final String securityRealm = server.getSecurityRealm();
                    final SSLContext sslContext = securityRealm != null
                            ? sslContexts.get(server.getSecurityRealm()).getOptionalValue() : null;
                    final MonitoredEndpoint endpoint = MonitoredEndpoint.of(server, sslContext);
                    if (server.isLocal()) {
                        /* local */
                        clientFactory = localModelControllerClientFactory;
                    } else {
                        /* remote */
                        clientFactory = ModelControllerClientFactory.createRemote(endpoint);
                    }
                    ResourceTypeManager<DMRNodeLocation> resourceTypeManager = new ResourceTypeManager<>(
                            protocolConfig.getTypeSets().getResourceTypeSets(), server.getResourceTypeSets());
                    DMREndpointService endpointService = new DMREndpointService(feedId, endpoint, resourceTypeManager,
                            clientFactory, diagnostics.getDMRDiagnostics());
                    builder.endpointService(endpointService);

                    log.debugf("[%s] created with resource type sets [%s]", endpointService,
                            server.getResourceTypeSets());
                }
            }

            this.dmrProtocolService = builder.build();
            return this;
        }

        public Builder jmxProtocolService(ProtocolConfiguration<JMXNodeLocation> protocolConfig) {

            ProtocolService.Builder<JMXNodeLocation, JMXSession> builder = ProtocolService.builder();

            for (EndpointConfiguration server : protocolConfig.getEndpoints().values()) {
                if (server.isEnabled()) {
                    final String securityRealm = server.getSecurityRealm();
                    final SSLContext sslContext = securityRealm != null
                            ? sslContexts.get(server.getSecurityRealm()).getOptionalValue() : null;
                    final MonitoredEndpoint endpoint = MonitoredEndpoint.of(server, sslContext);
                    ResourceTypeManager<JMXNodeLocation> resourceTypeManager = new ResourceTypeManager<>(
                            protocolConfig.getTypeSets().getResourceTypeSets(), server.getResourceTypeSets());
                    JMXEndpointService endpointService = new JMXEndpointService(feedId, endpoint, resourceTypeManager,
                            diagnostics.getJMXDiagnostics());
                    builder.endpointService(endpointService);

                    log.debugf("[%s] created with resource type sets [%s]", endpointService,
                            server.getResourceTypeSets());
                }
            }

            this.jmxProtocolService = builder.build();
            return this;
        }

        public Builder platformProtocolService(
                ProtocolConfiguration<PlatformNodeLocation> protocolConfig) {

            ProtocolService.Builder<PlatformNodeLocation, PlatformSession> builder = ProtocolService
                    .builder();

            for (EndpointConfiguration server : protocolConfig.getEndpoints().values()) {
                if (server.isEnabled()) {
                    final String securityRealm = server.getSecurityRealm();
                    final SSLContext sslContext = securityRealm != null
                            ? sslContexts.get(server.getSecurityRealm()).getOptionalValue() : null;
                    final MonitoredEndpoint endpoint = MonitoredEndpoint.of(server, sslContext);
                    ResourceTypeManager<PlatformNodeLocation> resourceTypeManager = new ResourceTypeManager<>(
                            protocolConfig.getTypeSets().getResourceTypeSets(), server.getResourceTypeSets());
                    PlatformEndpointService endpointService = new PlatformEndpointService(feedId, endpoint,
                            resourceTypeManager, diagnostics.getPlatformDiagnostics());
                    builder.endpointService(endpointService);

                    log.debugf("[%s] created with resource type sets [%s]", endpointService,
                            server.getResourceTypeSets());
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

    private final ProtocolService<DMRNodeLocation, DMRSession> dmrProtocolService;
    private final ProtocolService<JMXNodeLocation, JMXSession> jmxProtocolService;
    private final ProtocolService<PlatformNodeLocation, PlatformSession> platformProtocolService;
    private final List<ProtocolService<?, ?>> services;

    public ProtocolServices(
            ProtocolService<DMRNodeLocation, DMRSession> dmrProtocolService,
            ProtocolService<JMXNodeLocation, JMXSession> jmxProtocolService,
            ProtocolService<PlatformNodeLocation, PlatformSession> platformProtocolService) {
        this.dmrProtocolService = dmrProtocolService;
        this.jmxProtocolService = jmxProtocolService;
        this.platformProtocolService = platformProtocolService;
        this.services = Collections.unmodifiableList(Arrays.asList(dmrProtocolService, jmxProtocolService,
                platformProtocolService));
    }

    @SuppressWarnings("unchecked")
    public <L, S extends Session<L>> EndpointService<L, S> getEndpointService(
            String endpointName) {
        for (ProtocolService<?, ?> service : services) {
            EndpointService<?, ?> result = service.getEndpointServices().get(endpointName);
            if (result != null) {
                return (EndpointService<L, S>) result;
            }
        }
        return null;
    }

    public void start() {
        for (ProtocolService<?, ?> service : services) {
            service.start();
        }
    }

    public void discoverAll() {
        for (ProtocolService<?, ?> service : services) {
            service.discoverAll();
        }
    }

    public void stop() {
        for (ProtocolService<?, ?> service : services) {
            service.stop();
        }
    }

    public void addInventoryListener(InventoryListener listener) {
        for (ProtocolService<?, ?> service : services) {
            service.addInventoryListener(listener);
        }
    }

    public void removeInventoryListener(InventoryListener listener) {
        for (ProtocolService<?, ?> service : services) {
            service.removeInventoryListener(listener);
        }
    }

    public ProtocolService<DMRNodeLocation, DMRSession> getDmrProtocolService() {
        return dmrProtocolService;
    }

    public ProtocolService<JMXNodeLocation, JMXSession> getJmxProtocolService() {
        return jmxProtocolService;
    }

    public ProtocolService<PlatformNodeLocation, PlatformSession> getPlatformProtocolService() {
        return platformProtocolService;
    }

    public List<ProtocolService<?, ?>> getServices() {
        return services;
    }
}
