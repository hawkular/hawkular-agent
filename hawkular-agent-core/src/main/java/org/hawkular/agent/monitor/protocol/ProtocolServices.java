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

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;

import javax.net.ssl.SSLContext;

import org.hawkular.agent.monitor.api.AvailListener;
import org.hawkular.agent.monitor.api.InventoryListener;
import org.hawkular.agent.monitor.config.AgentCoreEngineConfiguration.EndpointConfiguration;
import org.hawkular.agent.monitor.config.AgentCoreEngineConfiguration.ProtocolConfiguration;
import org.hawkular.agent.monitor.diagnostics.Diagnostics;
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
import org.hawkular.agent.monitor.util.ThreadFactoryGenerator;

/**
 * This object contains all the {@link ProtocolService}s and their inventories (that is, all the managed
 * DMR endpoints, platform endpoint, etc).
 *
 * This object will also periodically trigger auto-discovery scans on all managed endpoints to help
 * keep the inventory up-to-date.
 *
 * @author <a href="https://github.com/ppalaga">Peter Palaga</a>
 * @author John Mazzitelli
 */
public class ProtocolServices {
    public static final int DEFAULT_AUTO_DISCOVERY_SCAN_PERIOD_SECS = 600;

    public static class Builder {
        private final String feedId;
        private ProtocolService<DMRNodeLocation, DMRSession> dmrProtocolService;
        private ProtocolService<JMXNodeLocation, JMXSession> jmxProtocolService;
        private ProtocolService<PlatformNodeLocation, PlatformSession> platformProtocolService;
        private final Map<String, SSLContext> sslContexts;
        private final Diagnostics diagnostics;
        private int autoDiscoveryScanPeriodSecs;

        public Builder(String feedId, Map<String, SSLContext> sslContexts, Diagnostics diagnostics) {
            this.feedId = feedId;
            this.sslContexts = sslContexts;
            this.diagnostics = diagnostics;
            this.autoDiscoveryScanPeriodSecs = DEFAULT_AUTO_DISCOVERY_SCAN_PERIOD_SECS;
        }

        public ProtocolServices build() {
            return new ProtocolServices(dmrProtocolService, jmxProtocolService, platformProtocolService,
                    autoDiscoveryScanPeriodSecs);
        }

        public Builder autoDiscoveryScanPeriodSecs(int periodSecs) {
            this.autoDiscoveryScanPeriodSecs = periodSecs;
            return this;
        }

        public Builder dmrProtocolService(
                ModelControllerClientFactory localModelControllerClientFactory, // may be null; only needed for local
                ProtocolConfiguration<DMRNodeLocation> protocolConfig) {

            ProtocolService.Builder<DMRNodeLocation, DMRSession> builder = ProtocolService.builder("DMR");

            for (EndpointConfiguration server : protocolConfig.getEndpoints().values()) {
                if (!server.isEnabled()) {
                    log.infoManagedServerDisabled(server.getName().toString());
                } else {
                    final ModelControllerClientFactory clientFactory;
                    final String securityRealm = server.getSecurityRealm();
                    SSLContext sslContext = null;
                    if (securityRealm != null) {
                        sslContext = sslContexts.get(securityRealm);
                        if (sslContext == null) {
                            throw new IllegalArgumentException("Unknown security realm: " + securityRealm);
                        }
                    }
                    final MonitoredEndpoint<EndpointConfiguration> endpoint = MonitoredEndpoint
                            .<EndpointConfiguration> of(server, sslContext);
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

            ProtocolService.Builder<JMXNodeLocation, JMXSession> builder = ProtocolService.builder("JMX");

            for (EndpointConfiguration server : protocolConfig.getEndpoints().values()) {
                if (server.isEnabled()) {
                    final String securityRealm = server.getSecurityRealm();
                    SSLContext sslContext = null;
                    if (securityRealm != null) {
                        sslContext = sslContexts.get(securityRealm);
                        if (sslContext == null) {
                            throw new IllegalArgumentException("Unknown security realm: " + securityRealm);
                        }
                    }
                    final MonitoredEndpoint<EndpointConfiguration> endpoint = MonitoredEndpoint
                            .<EndpointConfiguration> of(server, sslContext);
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

        public Builder platformProtocolService(ProtocolConfiguration<PlatformNodeLocation> protocolConfig) {

            ProtocolService.Builder<PlatformNodeLocation, PlatformSession> builder = ProtocolService
                    .builder("Platform");

            for (EndpointConfiguration server : protocolConfig.getEndpoints().values()) {
                if (server.isEnabled()) {
                    final String securityRealm = server.getSecurityRealm();
                    SSLContext sslContext = null;
                    if (securityRealm != null) {
                        sslContext = sslContexts.get(securityRealm);
                        if (sslContext == null) {
                            throw new IllegalArgumentException("Unknown security realm: " + securityRealm);
                        }
                    }
                    final MonitoredEndpoint<EndpointConfiguration> endpoint = MonitoredEndpoint
                            .<EndpointConfiguration> of(server, sslContext);
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

    public static Builder builder(String feedId, Diagnostics diagnostics, Map<String, SSLContext> sslContexts) {
        return new Builder(feedId, sslContexts, diagnostics);
    }

    private final ProtocolService<DMRNodeLocation, DMRSession> dmrProtocolService;
    private final ProtocolService<JMXNodeLocation, JMXSession> jmxProtocolService;
    private final ProtocolService<PlatformNodeLocation, PlatformSession> platformProtocolService;
    private final List<ProtocolService<?, ?>> services;

    // used to execute auto-discovery scans periodically
    private final int autoDiscoveryScanPeriodSecs;
    private ScheduledExecutorService autoDiscoveryExecutor = null;

    public ProtocolServices(
            ProtocolService<DMRNodeLocation, DMRSession> dmrProtocolService,
            ProtocolService<JMXNodeLocation, JMXSession> jmxProtocolService,
            ProtocolService<PlatformNodeLocation, PlatformSession> platformProtocolService,
            int autoDiscoveryScanPeriodSecs) {
        this.dmrProtocolService = dmrProtocolService;
        this.jmxProtocolService = jmxProtocolService;
        this.platformProtocolService = platformProtocolService;
        this.services = Collections.unmodifiableList(Arrays.asList(dmrProtocolService, jmxProtocolService,
                platformProtocolService));
        this.autoDiscoveryScanPeriodSecs = autoDiscoveryScanPeriodSecs;
    }

    public void start() {
        // Note that any protocol service start method may block!
        // It may wait for resources to come up first before returning.
        for (ProtocolService<?, ?> service : services) {
            service.start();
        }

        // only start auto discovery after all services have started
        startAutoDiscovery();
    }

    public void discoverAll() {
        for (ProtocolService<?, ?> service : services) {
            service.discoverAll();
        }
    }

    public void stop() {
        stopAutoDiscovery();

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

    public void addAvailListener(AvailListener listener) {
        for (ProtocolService<?, ?> service : services) {
            service.addAvailListener(listener);
        }
    }

    public void removeAvailListener(AvailListener listener) {
        for (ProtocolService<?, ?> service : services) {
            service.removeAvailListener(listener);
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

    private void startAutoDiscovery() {
        if (this.autoDiscoveryScanPeriodSecs > 0) {
            log.infoAutoDiscoveryEnabled(this.autoDiscoveryScanPeriodSecs);

            ThreadFactory threadFactory = ThreadFactoryGenerator.generateFactory(true,
                    "Hawkular WildFly Agent Auto-Discovery Scan");
            this.autoDiscoveryExecutor = Executors.newSingleThreadScheduledExecutor(threadFactory);

            Runnable job = new Runnable() {
                @Override
                public void run() {
                    // make sure we don't let exceptions bubble out - that would stop all future scans from executing
                    try {
                        ProtocolServices.this.discoverAll();
                    } catch (Throwable t) {
                        log.errorAutoDiscoveryFailed(t);
                    }
                }
            };

            // perform an initial discovery now, and then periodically thereafter
            this.autoDiscoveryExecutor.scheduleAtFixedRate(job, 0, autoDiscoveryScanPeriodSecs, TimeUnit.SECONDS);
        } else {
            log.infoAutoDiscoveryDisabled();
            this.autoDiscoveryExecutor = null;

            // we still must perform an initial discovery to obtain our inventory
            try {
                discoverAll();
            } catch (Throwable t) {
                log.errorAutoDiscoveryFailed(t);
            }
        }
    }

    private void stopAutoDiscovery() {
        if (this.autoDiscoveryExecutor != null) {
            log.debugf("Shutting down auto-discovery job");
            this.autoDiscoveryExecutor.shutdownNow();
        }
    }
}
