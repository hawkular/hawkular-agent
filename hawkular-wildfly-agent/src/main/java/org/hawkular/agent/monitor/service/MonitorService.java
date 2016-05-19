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
package org.hawkular.agent.monitor.service;

import static java.util.concurrent.TimeUnit.MILLISECONDS;

import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.io.File;
import java.io.FileNotFoundException;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import javax.net.ssl.SSLContext;

import org.hawkular.agent.monitor.api.Avail;
import org.hawkular.agent.monitor.api.HawkularWildFlyAgentContext;
import org.hawkular.agent.monitor.api.HawkularWildFlyAgentContextImpl;
import org.hawkular.agent.monitor.cmd.FeedCommProcessor;
import org.hawkular.agent.monitor.diagnostics.Diagnostics;
import org.hawkular.agent.monitor.diagnostics.DiagnosticsImpl;
import org.hawkular.agent.monitor.diagnostics.JBossLoggingReporter;
import org.hawkular.agent.monitor.diagnostics.JBossLoggingReporter.LoggingLevel;
import org.hawkular.agent.monitor.diagnostics.StorageReporter;
import org.hawkular.agent.monitor.dynamicprotocol.DynamicProtocolServices;
import org.hawkular.agent.monitor.extension.MonitorServiceConfiguration;
import org.hawkular.agent.monitor.extension.MonitorServiceConfiguration.AbstractEndpointConfiguration;
import org.hawkular.agent.monitor.extension.MonitorServiceConfiguration.DynamicEndpointConfiguration;
import org.hawkular.agent.monitor.extension.MonitorServiceConfiguration.EndpointConfiguration;
import org.hawkular.agent.monitor.extension.MonitorServiceConfiguration.StorageAdapterConfiguration;
import org.hawkular.agent.monitor.inventory.AvailType;
import org.hawkular.agent.monitor.inventory.MeasurementInstance;
import org.hawkular.agent.monitor.inventory.Resource;
import org.hawkular.agent.monitor.inventory.ResourceManager;
import org.hawkular.agent.monitor.log.AgentLoggers;
import org.hawkular.agent.monitor.log.MsgLogger;
import org.hawkular.agent.monitor.protocol.EndpointService;
import org.hawkular.agent.monitor.protocol.ProtocolService;
import org.hawkular.agent.monitor.protocol.ProtocolServices;
import org.hawkular.agent.monitor.protocol.dmr.DMREndpointService;
import org.hawkular.agent.monitor.protocol.dmr.ModelControllerClientFactory;
import org.hawkular.agent.monitor.scheduler.SchedulerConfiguration;
import org.hawkular.agent.monitor.scheduler.SchedulerService;
import org.hawkular.agent.monitor.storage.AvailDataPoint;
import org.hawkular.agent.monitor.storage.AvailStorageProxy;
import org.hawkular.agent.monitor.storage.HawkularStorageAdapter;
import org.hawkular.agent.monitor.storage.HttpClientBuilder;
import org.hawkular.agent.monitor.storage.InventoryStorageProxy;
import org.hawkular.agent.monitor.storage.MetricStorageProxy;
import org.hawkular.agent.monitor.storage.StorageAdapter;
import org.hawkular.agent.monitor.util.Util;
import org.hawkular.inventory.api.model.Feed;
import org.jboss.as.controller.ControlledProcessState;
import org.jboss.as.controller.ControlledProcessStateService;
import org.jboss.as.controller.ModelController;
import org.jboss.as.controller.ProcessType;
import org.jboss.as.controller.client.ModelControllerClient;
import org.jboss.as.domain.management.SecurityRealm;
import org.jboss.as.domain.management.security.SSLContextService;
import org.jboss.as.host.controller.DomainModelControllerService;
import org.jboss.as.host.controller.HostControllerEnvironment;
import org.jboss.as.naming.ImmediateManagedReferenceFactory;
import org.jboss.as.naming.ManagedReferenceFactory;
import org.jboss.as.naming.ServiceBasedNamingStore;
import org.jboss.as.naming.deployment.ContextNames;
import org.jboss.as.naming.service.BinderService;
import org.jboss.as.network.OutboundSocketBinding;
import org.jboss.as.network.SocketBinding;
import org.jboss.as.server.ServerEnvironment;
import org.jboss.as.server.ServerEnvironmentService;
import org.jboss.as.server.Services;
import org.jboss.logging.Logger;
import org.jboss.msc.service.AbstractServiceListener;
import org.jboss.msc.service.Service;
import org.jboss.msc.service.ServiceBuilder;
import org.jboss.msc.service.ServiceController;
import org.jboss.msc.service.ServiceName;
import org.jboss.msc.service.ServiceTarget;
import org.jboss.msc.service.StartContext;
import org.jboss.msc.service.StartException;
import org.jboss.msc.service.StopContext;
import org.jboss.msc.value.InjectedValue;

import com.codahale.metrics.MetricRegistry;
import com.codahale.metrics.ScheduledReporter;
import com.squareup.okhttp.OkHttpClient;
import com.squareup.okhttp.Request;
import com.squareup.okhttp.Response;

/**
 * The main Agent service.
 *
 * @author John Mazzitelli
 * @author <a href="https://github.com/ppalaga">Peter Palaga</a>
 */
public class MonitorService implements Service<MonitorService> {
    private static final MsgLogger log = AgentLoggers.getLogger(MonitorService.class);

    /**
     * Builds the runtime configuration, typically out of the boot configuration. It is static so that always stays
     * clear what data it relies on.
     *
     * On certain circumstances, this method may return the {@code bootConfiguration} instance without any modification.
     *
     * @param bootConfiguration the boot configuration
     * @param httpSocketBindingValue the httpSocketBindingValue (not available if agent is inside host controller)
     * @param httpsSocketBindingValue the httpsSocketBindingValue (not available if agent is inside host controller)
     * @param serverOutboundSocketBindingValue the serverOutboundSocketBindingValue
     * @return the runtime configuration
     */
    private static MonitorServiceConfiguration buildRuntimeConfiguration(
            MonitorServiceConfiguration bootConfiguration,
            InjectedValue<SocketBinding> httpSocketBindingValue,
            InjectedValue<SocketBinding> httpsSocketBindingValue,
            InjectedValue<OutboundSocketBinding> serverOutboundSocketBindingValue) {

        final MonitorServiceConfiguration.StorageAdapterConfiguration bootStorageAdapter = bootConfiguration
                .getStorageAdapter();

        log.infoStorageAdapterMode(bootStorageAdapter.getType());
        log.infoTenantId(bootStorageAdapter.getTenantId());

        if (bootStorageAdapter.getUrl() != null) {
            return bootConfiguration;
        } else {

            // determine where our Hawkular server is
            // If the user gave us a URL explicitly, that overrides everything and we use it.
            // If no URL is configured, but we are given a server outbound socket binding name,
            // we use that to determine the remote Hawkular URL.
            // If neither URL nor output socket binding name is provided, we assume we are running
            // co-located with the Hawkular server and we use local bindings.
            String useUrl = bootStorageAdapter.getUrl();
            if (useUrl == null) {
                try {
                    String address;
                    int port;

                    if (bootStorageAdapter.getServerOutboundSocketBindingRef() == null) {
                        // no URL or output socket binding - assume we are running co-located with server
                        SocketBinding socketBinding;
                        if (bootStorageAdapter.isUseSSL()) {
                            socketBinding = httpsSocketBindingValue.getValue();
                        } else {
                            socketBinding = httpSocketBindingValue.getValue();
                        }
                        address = socketBinding.getAddress().getHostName();
                        if (address.equals("0.0.0.0") || address.equals("::/128")) {
                            address = InetAddress.getLocalHost().getCanonicalHostName();
                        }
                        port = socketBinding.getAbsolutePort();
                    } else {
                        OutboundSocketBinding serverBinding = serverOutboundSocketBindingValue.getValue();
                        address = serverBinding.getResolvedDestinationAddress().getHostName();
                        port = serverBinding.getDestinationPort();
                    }
                    String protocol = (bootStorageAdapter.isUseSSL()) ? "https" : "http";
                    useUrl = String.format("%s://%s:%d", protocol, address, port);
                } catch (UnknownHostException uhe) {
                    throw new IllegalArgumentException("Cannot determine Hawkular server host", uhe);
                }
            }

            log.infoUsingServerSideUrl(useUrl);

            MonitorServiceConfiguration.StorageAdapterConfiguration runtimeStorageAdapter = //
            new MonitorServiceConfiguration.StorageAdapterConfiguration(
                    bootStorageAdapter.getType(),
                    bootStorageAdapter.getUsername(),
                    bootStorageAdapter.getPassword(),
                    bootStorageAdapter.getSecurityKey(),
                    bootStorageAdapter.getSecuritySecret(),
                    bootStorageAdapter.getTenantId(),
                    bootStorageAdapter.getFeedId(),
                    useUrl,
                    bootStorageAdapter.isUseSSL(),
                    bootStorageAdapter.getServerOutboundSocketBindingRef(),
                    bootStorageAdapter.getAccountsContext(),
                    bootStorageAdapter.getInventoryContext(),
                    bootStorageAdapter.getMetricsContext(),
                    bootStorageAdapter.getFeedcommContext(),
                    bootStorageAdapter.getKeystorePath(),
                    bootStorageAdapter.getKeystorePassword(),
                    bootStorageAdapter.getSecurityRealm(),
                    bootStorageAdapter.getConnectTimeoutSeconds(),
                    bootStorageAdapter.getReadTimeoutSeconds());

            return bootConfiguration.cloneWith(runtimeStorageAdapter);
        }

    }

    private static SSLContext getSslContext(MonitorServiceConfiguration configuration,
            Map<String, InjectedValue<SSLContext>> trustOnlySSLContextValues) {
        SSLContext result = null;
        String bootSecurityRealm = configuration.getStorageAdapter().getSecurityRealm();
        if (bootSecurityRealm != null) {
            result = trustOnlySSLContextValues.get(bootSecurityRealm).getOptionalValue();
        }
        return result;
    }

    private final InjectedValue<ModelController> modelControllerValue = new InjectedValue<>();
    private final InjectedValue<ServerEnvironment> serverEnvironmentValue = new InjectedValue<>();
    private final InjectedValue<ControlledProcessStateService> processStateValue = new InjectedValue<>();
    private final InjectedValue<SocketBinding> httpSocketBindingValue = new InjectedValue<>();
    private final InjectedValue<SocketBinding> httpsSocketBindingValue = new InjectedValue<>();
    private final InjectedValue<OutboundSocketBinding> serverOutboundSocketBindingValue = new InjectedValue<>();
    // key=securityRealm name as a String
    private final Map<String, InjectedValue<SSLContext>> trustOnlySSLContextValues = new HashMap<>();

    private boolean started = false;

    private PropertyChangeListener serverStateListener;

    // Declared config found in standalone.xml. Only used to build the runtime configuration (see #configuration).
    private final MonitorServiceConfiguration bootConfiguration;

    // Indicates if we are running in a standalone server or in a host controller (or something similar)
    private final ProcessType processType;

    // A version of bootConfiguration with defaults properly set. This is build in startMonitorService().
    private MonitorServiceConfiguration configuration;

    // this is used to identify us to the Hawkular environment as a particular feed
    private String feedId;

    // used to report our own internal metrics
    private Diagnostics diagnostics;
    private ScheduledReporter diagnosticsReporter;

    // used to send monitored data for storage
    private StorageAdapter storageAdapter;
    private HttpClientBuilder httpClientBuilder;

    // used to send/receive data to the server over the feed communications channel
    private FeedCommProcessor feedComm;

    // scheduled metric and avail collections
    private SchedulerService schedulerService;

    // proxies that are exposed via JNDI so external apps can emit their own inventory, metrics, and avail checks
    private final MetricStorageProxy metricStorageProxy = new MetricStorageProxy();
    private final AvailStorageProxy availStorageProxy = new AvailStorageProxy();
    private final InventoryStorageProxy inventoryStorageProxy = new InventoryStorageProxy();

    // contains endpoint services for all the different protocols that are supported (dmr, jmx, prometheus, platform)
    private ProtocolServices protocolServices;
    private DynamicProtocolServices dynamicProtocolServices;

    // used to talk to the management interface of the WildFly server the agent is deployed in
    private ModelControllerClientFactory localModelControllerClientFactory;

    public MonitorService(MonitorServiceConfiguration bootConfiguration, ProcessType processType) {
        super();
        this.bootConfiguration = bootConfiguration;
        this.processType = processType;
    }

    @Override
    public MonitorService getValue() {
        return this;
    }

    /**
     * @return the context that can be used by others for storing ad-hoc monitoring data
     */
    public HawkularWildFlyAgentContext getHawkularMonitorContext() {
        return new HawkularWildFlyAgentContextImpl(metricStorageProxy, availStorageProxy, inventoryStorageProxy);
    }

    /**
     * When this service is being built, this method is called to allow this service
     * to add whatever dependencies it needs.
     *
     * @param target the service target
     * @param bldr the service builder used to add dependencies
     */
    public void addDependencies(ServiceTarget target, ServiceBuilder<MonitorService> bldr) {
        if (processType.isManagedDomain()) {
            // we are in the host controller
            // NOTE: host controller does not yet have an equivalent for ServerEnvironment, we workaround this later
            bldr.addDependency(DomainModelControllerService.SERVICE_NAME, ModelController.class, modelControllerValue);
        } else {
            // we are in standalone mode
            bldr.addDependency(ServerEnvironmentService.SERVICE_NAME, ServerEnvironment.class, serverEnvironmentValue);
            bldr.addDependency(Services.JBOSS_SERVER_CONTROLLER, ModelController.class, modelControllerValue);
        }
        bldr.addDependency(ControlledProcessStateService.SERVICE_NAME, ControlledProcessStateService.class,
                processStateValue);

        StorageAdapterConfiguration storageAdapterConfig = this.bootConfiguration.getStorageAdapter();

        // if the URL is not explicitly defined, we need some dependencies to help us build it
        if (storageAdapterConfig.getUrl() == null || storageAdapterConfig.getUrl().isEmpty()) {
            if (storageAdapterConfig.getServerOutboundSocketBindingRef() == null ||
                    storageAdapterConfig.getServerOutboundSocketBindingRef().isEmpty()) {
                // The outbound binding isn't given, so we'll assume we are co-located with the server.
                // In this case, we need our own http/https binding so we know what our local server is bound to.
                // Note that this is an invalid configuration if we are in host controller, so error out in that case
                if (processType.isManagedDomain()) {
                    throw new IllegalStateException("Do not know where the external Hawkular server is. Aborting.");
                }
                bldr.addDependency(SocketBinding.JBOSS_BINDING_NAME.append("http"), SocketBinding.class,
                        httpSocketBindingValue);
                bldr.addDependency(SocketBinding.JBOSS_BINDING_NAME.append("https"), SocketBinding.class,
                        httpsSocketBindingValue);
            } else {
                // TODO: broken when deployed in host controller. see https://issues.jboss.org/browse/WFCORE-1505
                // When that is fixed, remove this if-statement entirely.
                if (processType.isManagedDomain()) {
                    throw new IllegalStateException("When deployed in host controller, you must use the URL attribute"
                            + " and not the outbound socket binding. "
                            + "See bug https://issues.jboss.org/browse/WFCORE-1505 for more.");
                }
                bldr.addDependency(OutboundSocketBinding.OUTBOUND_SOCKET_BINDING_BASE_SERVICE_NAME
                        .append(storageAdapterConfig.getServerOutboundSocketBindingRef()),
                        OutboundSocketBinding.class, serverOutboundSocketBindingValue);
            }
        }

        // get the security realm ssl context for the storage adapter
        if (storageAdapterConfig.getSecurityRealm() != null) {
            InjectedValue<SSLContext> iv = new InjectedValue<>();
            trustOnlySSLContextValues.put(storageAdapterConfig.getSecurityRealm(), iv);

            // if we ever need our own private key, we can add another dependency with trustStoreOnly=false
            boolean trustStoreOnly = true;
            SSLContextService.ServiceUtil.addDependency(
                    bldr,
                    iv,
                    SecurityRealm.ServiceUtil.createServiceName(storageAdapterConfig.getSecurityRealm()),
                    trustStoreOnly);
        }

        // get the security realms for any configured remote DMR and JMX and Prometheus servers that require ssl
        for (EndpointConfiguration endpoint : bootConfiguration.getDmrConfiguration().getEndpoints().values()) {
            String securityRealm = endpoint.getSecurityRealm();
            if (securityRealm != null) {
                addSslContext(securityRealm, bldr);
            }
        }
        for (EndpointConfiguration endpoint : bootConfiguration.getJmxConfiguration().getEndpoints().values()) {
            String securityRealm = endpoint.getSecurityRealm();
            if (securityRealm != null) {
                addSslContext(securityRealm, bldr);
            }
        }
        for (DynamicEndpointConfiguration endpoint : bootConfiguration.getPrometheusConfiguration().getEndpoints()
                .values()) {
            String securityRealm = endpoint.getSecurityRealm();
            if (securityRealm != null) {
                addSslContext(securityRealm, bldr);
            }
        }

        // bind the API to JNDI so other apps can use it, and prepare to build the binder service
        // Note that if we are running in host controller or similiar, JNDI binding is not available.
        String jndiName = bootConfiguration.getApiJndi();
        boolean bindJndi = (jndiName == null || jndiName.isEmpty() || processType.isManagedDomain()) ? false : true;
        if (bindJndi) {
            class JndiBindListener extends AbstractServiceListener<Object> {
                private final String jndiName;
                private final String jndiObjectClassName;

                public JndiBindListener(String jndiName, String jndiObjectClassName) {
                    this.jndiName = jndiName;
                    this.jndiObjectClassName = jndiObjectClassName;
                }

                public void transition(final ServiceController<? extends Object> controller,
                        final ServiceController.Transition transition) {
                    switch (transition) {
                        case STARTING_to_UP: {
                            log.infoBindJndiResource(jndiName, jndiObjectClassName);
                            break;
                        }
                        case START_REQUESTED_to_DOWN: {
                            log.infoUnbindJndiResource(jndiName);
                            break;
                        }
                        case REMOVING_to_REMOVED: {
                            log.infoUnbindJndiResource(jndiName);
                            break;
                        }
                        default:
                            break;
                    }
                }
            }
            Object jndiObject = getHawkularMonitorContext();
            ContextNames.BindInfo bindInfo = ContextNames.bindInfoFor(jndiName);
            BinderService binderService = new BinderService(bindInfo.getBindName());
            ManagedReferenceFactory valueMRF = new ImmediateManagedReferenceFactory(jndiObject);
            String jndiObjectClassName = HawkularWildFlyAgentContext.class.getName();
            ServiceName binderServiceName = bindInfo.getBinderServiceName();
            ServiceBuilder<?> binderBuilder = target
                    .addService(binderServiceName, binderService)
                    .addInjection(binderService.getManagedObjectInjector(), valueMRF)
                    .setInitialMode(ServiceController.Mode.ACTIVE)
                    .addDependency(bindInfo.getParentContextServiceName(),
                            ServiceBasedNamingStore.class,
                            binderService.getNamingStoreInjector())
                    .addListener(new JndiBindListener(jndiName, jndiObjectClassName));

            // our monitor service will depend on the binder service
            bldr.addDependency(binderServiceName);

            // install the binder service
            binderBuilder.install();
        }

        return; // deps added
    }

    private void addSslContext(String securityRealm, ServiceBuilder<MonitorService> bldr) {
        if (securityRealm != null && !this.trustOnlySSLContextValues.containsKey(securityRealm)) {
            // if we haven't added a dependency on the security realm yet, add it now
            InjectedValue<SSLContext> iv = new InjectedValue<>();
            this.trustOnlySSLContextValues.put(securityRealm, iv);

            boolean trustStoreOnly = true;
            SSLContextService.ServiceUtil.addDependency(
                    bldr,
                    iv,
                    SecurityRealm.ServiceUtil.createServiceName(securityRealm),
                    trustStoreOnly);
        }
    }

    /**
     * @return true if this service is {@link #startMonitorService() started};
     *         false if this service is {@link #stopMonitorService() stopped}.
     */
    public boolean isMonitorServiceStarted() {
        return started;
    }

    @Override
    public void start(final StartContext startContext) throws StartException {
        final AtomicReference<Thread> startThread = new AtomicReference<Thread>();

        // deferred startup: must wait for server to be running before we can monitor the subsystems
        ControlledProcessStateService stateService = processStateValue.getValue();
        serverStateListener = new PropertyChangeListener() {
            @Override
            public void propertyChange(PropertyChangeEvent evt) {
                if (ControlledProcessState.State.RUNNING.equals(evt.getNewValue())) {
                    // see HWKAGENT-74 for why we need to do this in a separate thread
                    Thread newThread = new Thread(new Runnable() {
                        @Override
                        public void run() {
                            try {
                                startMonitorService();
                            } catch (Throwable t) {
                            }
                        }
                    }, "Hawkular WildFly Agent Startup Thread");
                    newThread.setDaemon(true);

                    Thread oldThread = startThread.getAndSet(newThread);
                    if (oldThread != null) {
                        oldThread.interrupt();
                    }

                    newThread.start();
                } else if (ControlledProcessState.State.STOPPING.equals(evt.getNewValue())) {
                    Thread oldThread = startThread.get();
                    if (oldThread != null) {
                        oldThread.interrupt();
                    }
                }
            }
        };
        stateService.addPropertyChangeListener(serverStateListener);
    }

    @Override
    public void stop(StopContext stopContext) {
        stopMonitorService();
    }

    /**
     * Starts this service. If the service is already started, this method is a no-op.
     */
    public void startMonitorService() {
        if (isMonitorServiceStarted()) {
            return; // we are already started
        }

        try {
            log.infoStarting();

            this.configuration = buildRuntimeConfiguration(
                    this.bootConfiguration,
                    this.httpSocketBindingValue,
                    this.httpsSocketBindingValue,
                    this.serverOutboundSocketBindingValue);

            if (this.configuration.getStorageAdapter().getTenantId() == null) {
                log.errorNoTenantIdSpecified();
                throw new Exception("Missing tenant ID");
            }

            // prepare the builder that will create our HTTP/REST clients to the hawkular server infrastructure
            SSLContext ssl = getSslContext(this.configuration, this.trustOnlySSLContextValues);
            this.httpClientBuilder = new HttpClientBuilder(this.configuration.getStorageAdapter(), ssl);

            // get our self identifiers
            this.localModelControllerClientFactory = ModelControllerClientFactory
                    .createLocal(modelControllerValue.getValue());

            if (this.configuration.getStorageAdapter().getFeedId() != null) {
                this.feedId = this.configuration.getStorageAdapter().getFeedId();
            } else {
                try (ModelControllerClient c = this.localModelControllerClientFactory.createClient()) {
                    this.feedId = DMREndpointService.lookupServerIdentifier(c);
                } catch (Exception e) {
                    throw new Exception("Could not obtain local feed ID", e);
                }
            }

            // build the diagnostics object that will be used to track our own performance
            final MetricRegistry metricRegistry = new MetricRegistry();
            this.diagnostics = new DiagnosticsImpl(configuration.getDiagnostics(), metricRegistry, feedId);

            // perform some things that are dependent upon what mode the agent is in
            switch (this.configuration.getStorageAdapter().getType()) {
                case HAWKULAR:
                    // if we are participating in a full Hawkular environment, we need to do some additional things:
                    // 2. register our feed ID
                    // 3. connect to the server's feed comm channel
                    try {
                        registerFeed();
                    } catch (Exception e) {
                        log.errorCannotDoAnythingWithoutFeed(e);
                        throw new Exception("Agent needs a feed to run");
                    }

                    // try to connect to the server via command-gateway channel; keep going on error
                    try {
                        connectToCommandGatewayCommChannel();
                    } catch (Exception e) {
                        if (e instanceof InterruptedException) {
                            Thread.currentThread().interrupt();
                        }
                        log.errorCannotEstablishFeedComm(e);
                    }
                    break;

                case METRICS:
                    // nothing special needs to be done
                    break;
                default:
                    throw new IllegalStateException(
                            "Unknown storage adapter type: " + this.configuration.getStorageAdapter().getType());
            }

            // start the storage adapter
            try {
                startStorageAdapter();
            } catch (Exception e) {
                log.errorCannotStartStorageAdapter(e);
                throw new Exception("Agent cannot start storage adapter");
            }

            try {
                startScheduler();
            } catch (Exception e) {
                log.errorCannotInitializeScheduler(e);
                throw new Exception("Agent cannot initialize scheduler");
            }

            ProtocolServices ps = createProtocolServicesBuilder()
                    .dmrProtocolService(this.localModelControllerClientFactory, configuration.getDmrConfiguration())
                    .jmxProtocolService(configuration.getJmxConfiguration())
                    .platformProtocolService(configuration.getPlatformConfiguration())
                    .autoDiscoveryScanPeriodSecs(configuration.getAutoDiscoveryScanPeriodSecs())
                    .build();
            ps.addInventoryListener(inventoryStorageProxy);
            ps.addInventoryListener(schedulerService);
            protocolServices = ps;
            protocolServices.start();

            DynamicProtocolServices dps = createDynamicProtocolServicesBuilder()
                    .prometheusDynamicProtocolService(configuration.getPrometheusConfiguration(),
                            getHawkularMonitorContext())
                    .build();
            dynamicProtocolServices = dps;
            dynamicProtocolServices.start();

            started = true;

        } catch (Throwable t) {
            if (t instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }

            log.errorFailedToStartAgent(t);

            // artifically shutdown the agent - agent will be disabled now
            started = true;
            stopMonitorService();
        }
    }

    /**
     * Stops this service. If the service is already stopped, this method is a no-op.
     */
    public void stopMonitorService() {
        if (!isMonitorServiceStarted()) {
            log.infoStoppedAlready();
            return; // we are already stopped
        }

        log.infoStopping();

        AtomicReference<Throwable> error = new AtomicReference<>(null);  // will hold the first error we encountered

        try {
            // We must do a few things first before we can shutdown the scheduler.
            // But we also must make sure we shutdown the scheduler so we kill its threads.
            // Otherwise we hang the shutdown of the entire server. So make sure we get to "stopScheduler".

            // disconnect from the feed comm channel
            try {
                if (feedComm != null) {
                    feedComm.destroy();
                    feedComm = null;
                }
            } catch (Throwable t) {
                error.compareAndSet(null, t);
                log.debug("Cannot shutdown feed comm but will continue shutdown", t);
            }

            // stop our dynamic protocol services
            try {
                if (dynamicProtocolServices != null) {
                    dynamicProtocolServices.stop();
                    dynamicProtocolServices = null;
                }
            } catch (Throwable t) {
                error.compareAndSet(null, t);
                log.debug("Cannot shutdown dynamic protocol services but will continue shutdown", t);
            }

            // stop our normal protocol services
            Map<EndpointService<?, ?>, List<MeasurementInstance<?, AvailType<?>>>> availsToChange = null;

            try {
                if (protocolServices != null) {
                    availsToChange = getAvailsToChange();
                    protocolServices.stop();
                    protocolServices.removeInventoryListener(inventoryStorageProxy);
                    protocolServices.removeInventoryListener(schedulerService);
                    protocolServices = null;
                }
            } catch (Throwable t) {
                error.compareAndSet(null, t);
                log.debug("Cannot shutdown protocol services but will continue shutdown", t);
            }

            // shutdown scheduler and then the storage adapter - make sure we always attempt both
            try {
                if (schedulerService != null) {
                    schedulerService.stop();
                    schedulerService = null;
                }
            } catch (Throwable t) {
                error.compareAndSet(null, t);
                log.debug("Cannot shutdown scheduler but will continue shutdown", t);
            }

            // now stop the storage adapter
            try {
                if (storageAdapter != null) {
                    changeAvails(availsToChange); // notice we do this AFTER we shutdown the scheduler!
                    storageAdapter.shutdown();
                    storageAdapter = null;
                }
            } catch (Throwable t) {
                error.compareAndSet(null, t);
                log.debug("Cannot shutdown storage adapter but will continue shutdown", t);
            }

            // stop diagnostic reporting and spit out a final diagnostics report
            if (diagnosticsReporter != null) {
                diagnosticsReporter.stop();
                if (configuration.getDiagnostics().isEnabled()) {
                    diagnosticsReporter.report();
                }
                diagnosticsReporter = null;
            }

            // cleanup the state listener
            if (serverStateListener != null) {
                processStateValue.getValue().removePropertyChangeListener(serverStateListener);
                serverStateListener = null;
            }

            // We attempted to clean everything we could. If we hit an error, throw it to log our shutdown wasn't clean
            if (error.get() != null) {
                throw error.get();
            }
        } catch (Throwable t) {
            log.warnFailedToStopAgent(t);
        } finally {
            started = false;
        }
    }

    private void changeAvails(Map<EndpointService<?, ?>, List<MeasurementInstance<?, AvailType<?>>>> availsToChange) {
        if (availsToChange != null && !availsToChange.isEmpty() && storageAdapter != null) {
            long now = System.currentTimeMillis();
            Set<AvailDataPoint> datapoints = new HashSet<AvailDataPoint>();
            for (EndpointService<?, ?> endpointService : availsToChange.keySet()) {
                EndpointConfiguration config = (EndpointConfiguration) endpointService.getMonitoredEndpoint()
                        .getEndpointConfiguration();
                Avail setAvailOnShutdown = config.getSetAvailOnShutdown();
                if (setAvailOnShutdown != null) {
                    List<MeasurementInstance<?, AvailType<?>>> avails = availsToChange.get(endpointService);
                    for (MeasurementInstance avail : avails) {
                        AvailDataPoint availDataPoint = new AvailDataPoint(
                                endpointService.generateMeasurementKey(avail),
                                now,
                                setAvailOnShutdown,
                                config.getTenantId());
                        datapoints.add(availDataPoint);
                    }
                }
            }
            storageAdapter.storeAvails(datapoints, 60_000L); // wait for the store to complete, but not forever
        }
    }

    private Map<EndpointService<?, ?>, List<MeasurementInstance<?, AvailType<?>>>> getAvailsToChange() {
        Map<EndpointService<?, ?>, List<MeasurementInstance<?, AvailType<?>>>> avails = new HashMap<>();
        for (ProtocolService<?, ?> protocolService : protocolServices.getServices()){
            for (EndpointService<?, ?> endpointService : protocolService.getEndpointServices().values()) {
                EndpointConfiguration config = (EndpointConfiguration) endpointService.getMonitoredEndpoint()
                        .getEndpointConfiguration();
                Avail setAvailOnShutdown = config.getSetAvailOnShutdown();
                if (setAvailOnShutdown != null) {
                    ResourceManager<?> rm = endpointService.getResourceManager();
                    if (!rm.getRootResources().isEmpty()) {
                        List<MeasurementInstance<?, AvailType<?>>> esAvails = new ArrayList<>();
                        avails.put(endpointService, esAvails);
                        List<Resource<?>> resources = (List<Resource<?>>) (List<?>) rm.getResourcesBreadthFirst();
                        for (Resource<?> resource : resources) {
                            Collection<?> resourceAvails = resource.getAvails();
                            esAvails.addAll((Collection<MeasurementInstance<?, AvailType<?>>>) resourceAvails);
                        }
                    }
                }
            }
        }
        return avails;
    }

    /**
     * @return the directory where our agent service can write data files. This directory survives restarts.
     */
    private File getDataDirectory() {
        File dataDir;

        if (this.processType.isManagedDomain()) {
            // TODO use the host environment once its available: https://issues.jboss.org/browse/WFCORE-1506
            dataDir = new File(System.getProperty(HostControllerEnvironment.DOMAIN_DATA_DIR));
        } else {
            dataDir = this.serverEnvironmentValue.getValue().getServerDataDir();
        }

        File agentDataDir = new File(dataDir, "hawkular-agent");

        agentDataDir.mkdirs();
        return agentDataDir;
    }

    /**
     * Creates and starts the storage adapter that will be used to store our inventory data and monitoring data.
     *
     * @throws Exception if failed to start the storage adapter
     */
    private void startStorageAdapter() throws Exception {
        // create the storage adapter that will write our metrics/inventory data to backend storage on server
        this.storageAdapter = new HawkularStorageAdapter();
        this.storageAdapter.initialize(feedId, configuration.getStorageAdapter(), diagnostics, httpClientBuilder);

        // provide our storage adapter to the proxies - allows external apps to use them to store its own data
        metricStorageProxy.setStorageAdapter(storageAdapter);
        availStorageProxy.setStorageAdapter(storageAdapter);
        inventoryStorageProxy.setStorageAdapter(storageAdapter);

        // determine where we are to store our own diagnostic reports
        switch (configuration.getDiagnostics().getReportTo()) {
            case LOG: {
                this.diagnosticsReporter = JBossLoggingReporter.forRegistry(this.diagnostics.getMetricRegistry())
                        .convertRatesTo(TimeUnit.SECONDS)
                        .convertDurationsTo(MILLISECONDS)
                        .outputTo(Logger.getLogger(getClass()))
                        .withLoggingLevel(LoggingLevel.DEBUG)
                        .build();
                break;
            }
            case STORAGE: {
                this.diagnosticsReporter = StorageReporter
                        .forRegistry(this.diagnostics.getMetricRegistry(), configuration.getDiagnostics(),
                                storageAdapter)
                        .feedId(feedId)
                        .convertRatesTo(TimeUnit.SECONDS)
                        .convertDurationsTo(MILLISECONDS)
                        .build();
                break;
            }
            default: {
                throw new Exception("Invalid diagnostics type: " + configuration.getDiagnostics().getReportTo());
            }
        }

        if (this.configuration.getDiagnostics().isEnabled()) {
            diagnosticsReporter.start(this.configuration.getDiagnostics().getInterval(),
                    this.configuration.getDiagnostics().getTimeUnits());
        }
    }

    /**
     * Builds the scheduler's configuraton and starts the scheduler.
     *
     * @throws Exception on error
     */
    private void startScheduler() throws Exception {
        if (this.schedulerService == null) {
            SchedulerConfiguration schedulerConfig = new SchedulerConfiguration();
            schedulerConfig.setDiagnosticsConfig(this.configuration.getDiagnostics());
            schedulerConfig.setStorageAdapterConfig(this.configuration.getStorageAdapter());
            schedulerConfig.setMetricDispatcherBufferSize(this.configuration.getMetricDispatcherBufferSize());
            schedulerConfig.setMetricDispatcherMaxBatchSize(this.configuration.getMetricDispatcherMaxBatchSize());
            schedulerConfig.setAvailDispatcherBufferSize(this.configuration.getAvailDispatcherBufferSize());
            schedulerConfig.setAvailDispatcherMaxBatchSize(this.configuration.getAvailDispatcherMaxBatchSize());

            this.schedulerService = new SchedulerService(schedulerConfig, this.diagnostics, this.storageAdapter);
        }

        this.schedulerService.start();
    }

    /**
     * Registers our feed with the Hawkular system.
     *
     * @throws Exception if failed to register feed
     */
    private void registerFeed() throws Exception {
        String desiredFeedId = this.feedId;

        try {
            File feedFile = new File(getDataDirectory(), "feedId.txt");
            try {
                String feedIdFromDataFile = Util.read(feedFile);
                feedIdFromDataFile = feedIdFromDataFile.trim();
                if (!desiredFeedId.equals(feedIdFromDataFile)) {
                    log.warnf("Will use feed ID [%s] found in [%s];"
                            + " note that it is different than our desired feed ID [%s].",
                            feedIdFromDataFile, feedFile, desiredFeedId);
                    feedId = feedIdFromDataFile;
                }
                return; // we already have a feed ID - we can return now since there is nothing else to do
            } catch (FileNotFoundException e) {
                // probably just haven't been registered yet, keep going
            }

            // get the payload in JSON format
            Feed.Blueprint feedPojo = new Feed.Blueprint(desiredFeedId, null);
            String jsonPayload = Util.toJson(feedPojo);

            // build the REST URL...
            // start with the protocol, host, and port, plus context
            StringBuilder url = Util.getContextUrlString(configuration.getStorageAdapter().getUrl(),
                    configuration.getStorageAdapter().getInventoryContext());

            // rest of the URL says we want the feeds API
            url.append("feeds");

            // because we can support multiple tenants, we need to register our feed under all tenants
            List<AbstractEndpointConfiguration> endpoints = new ArrayList<>();
            endpoints.addAll(configuration.getDmrConfiguration().getEndpoints().values());
            endpoints.addAll(configuration.getJmxConfiguration().getEndpoints().values());
            endpoints.addAll(configuration.getPlatformConfiguration().getEndpoints().values());
            endpoints.addAll(configuration.getPrometheusConfiguration().getEndpoints().values());

            List<String> tenantIds = new ArrayList<String>();
            tenantIds.add(configuration.getStorageAdapter().getTenantId()); // always register agent's global tenant ID
            for (AbstractEndpointConfiguration endpoint : endpoints) {
                String tenantId = endpoint.getTenantId();
                if (tenantId != null) {
                    tenantIds.add(tenantId);
                }
            }

            // now send the REST requests - one for each tenant to register
            OkHttpClient httpclient = this.httpClientBuilder.getHttpClient();

            for (String tenantId : tenantIds) {
                Map<String, String> header = Collections.singletonMap("Hawkular-Tenant", tenantId);
                Request request = this.httpClientBuilder.buildJsonPostRequest(url.toString(), header, jsonPayload);
                Response httpResponse = httpclient.newCall(request).execute();

                // HTTP status of 201 means success; 409 means it already exists, anything else is an error
                if (httpResponse.code() == 201) {
                    final String feedObjectFromServer = httpResponse.body().string();
                    final Feed feed = Util.fromJson(feedObjectFromServer, Feed.class);
                    if (desiredFeedId.equals(feed.getId())) {
                        log.infoUsingFeedId(feed.getId(), tenantId);
                    } else {
                        log.errorUnwantedFeedId(feed.getId(), desiredFeedId, tenantId);
                        // should we throw an error here or just use the feed ID we were given?
                        log.debugf("Using feed ID [%s] with tenant ID [%s];"
                                + "make sure the agent doesn't lose its data file", feed.getId(), tenantId);
                    }

                    this.feedId = feed.getId();

                } else if (httpResponse.code() == 409) {
                    log.infoFeedIdAlreadyRegistered(this.feedId, tenantId);
                } else {
                    throw new Exception(String.format("Cannot register feed ID [%s] under tenant ID [%s]. "
                            + "status-code=[%d], reason=[%s], url=[%s]",
                            desiredFeedId,
                            tenantId,
                            httpResponse.code(),
                            httpResponse.message(),
                            request.urlString()));
                }
            }

            // persist our feed ID so we can remember it the next time we start up
            Util.write(feedId, feedFile);

        } catch (Throwable t) {
            throw new Exception(String.format("Cannot create feed ID [%s]", desiredFeedId), t);
        }
    }

    /**
     * Connects to the Hawkular server over the websocket command gateway.
     *
     * @throws Exception if failed to connect to the Hawkular server
     */
    private void connectToCommandGatewayCommChannel() throws Exception {
        feedComm = new FeedCommProcessor(this.httpClientBuilder, this.configuration, this.feedId, this);
        feedComm.connect();
    }

    public SchedulerService getSchedulerService() {
        return schedulerService;
    }

    /**
     * @return a factory that can create clients which can talk to the local management interface
     *         of the app server we are running in
     */
    public ModelControllerClientFactory getLocalModelControllerClientFactory() {
        return localModelControllerClientFactory;
    }

    /**
     * @return builder that let's you create protocol services and their endpoints
     */
    public ProtocolServices.Builder createProtocolServicesBuilder() {
        return ProtocolServices.builder(feedId, diagnostics, trustOnlySSLContextValues);
    }

    /**
     * @return builder that let's you create dynamic protocol services and their endpoints
     */
    public DynamicProtocolServices.Builder createDynamicProtocolServicesBuilder() {
        return DynamicProtocolServices.builder(feedId, trustOnlySSLContextValues);
    }

    /**
     * @return the current set of protocol services
     */
    public ProtocolServices getProtocolServices() {
        return protocolServices;
    }

    /**
     * @return the current set of dynamic protocol services
     */
    public DynamicProtocolServices getDynamicProtocolServices() {
        return dynamicProtocolServices;
    }
}
