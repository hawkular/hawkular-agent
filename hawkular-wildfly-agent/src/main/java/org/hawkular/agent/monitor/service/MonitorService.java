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
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.net.ssl.SSLContext;

import org.hawkular.agent.monitor.api.HawkularWildFlyAgentContext;
import org.hawkular.agent.monitor.api.HawkularWildFlyAgentContextImpl;
import org.hawkular.agent.monitor.cmd.FeedCommProcessor;
import org.hawkular.agent.monitor.diagnostics.Diagnostics;
import org.hawkular.agent.monitor.diagnostics.DiagnosticsImpl;
import org.hawkular.agent.monitor.diagnostics.JBossLoggingReporter;
import org.hawkular.agent.monitor.diagnostics.JBossLoggingReporter.LoggingLevel;
import org.hawkular.agent.monitor.diagnostics.StorageReporter;
import org.hawkular.agent.monitor.extension.MonitorServiceConfiguration;
import org.hawkular.agent.monitor.extension.MonitorServiceConfiguration.EndpointConfiguration;
import org.hawkular.agent.monitor.extension.MonitorServiceConfiguration.StorageReportTo;
import org.hawkular.agent.monitor.log.AgentLoggers;
import org.hawkular.agent.monitor.log.MsgLogger;
import org.hawkular.agent.monitor.protocol.ProtocolServices;
import org.hawkular.agent.monitor.protocol.dmr.DMREndpointService;
import org.hawkular.agent.monitor.protocol.dmr.ModelControllerClientFactory;
import org.hawkular.agent.monitor.scheduler.SchedulerConfiguration;
import org.hawkular.agent.monitor.scheduler.SchedulerService;
import org.hawkular.agent.monitor.storage.AvailStorageProxy;
import org.hawkular.agent.monitor.storage.HawkularStorageAdapter;
import org.hawkular.agent.monitor.storage.HttpClientBuilder;
import org.hawkular.agent.monitor.storage.InventoryStorageProxy;
import org.hawkular.agent.monitor.storage.MetricStorageProxy;
import org.hawkular.agent.monitor.storage.MetricsOnlyStorageAdapter;
import org.hawkular.agent.monitor.storage.StorageAdapter;
import org.hawkular.agent.monitor.util.Util;
import org.hawkular.inventory.api.model.Feed;
import org.jboss.as.controller.ControlledProcessState;
import org.jboss.as.controller.ControlledProcessStateService;
import org.jboss.as.controller.ModelController;
import org.jboss.as.controller.client.ModelControllerClient;
import org.jboss.as.domain.management.SecurityRealm;
import org.jboss.as.domain.management.security.SSLContextService;
import org.jboss.as.network.OutboundSocketBinding;
import org.jboss.as.network.SocketBinding;
import org.jboss.as.server.ServerEnvironment;
import org.jboss.as.server.ServerEnvironmentService;
import org.jboss.as.server.Services;
import org.jboss.logging.Logger;
import org.jboss.msc.service.Service;
import org.jboss.msc.service.ServiceBuilder;
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
     * On cetrain circumstances, this method may return the {@code bootConfiguration} instance without any modification.
     *
     * @param bootConfiguration the boot configuration
     * @param httpSocketBindingValue the httpSocketBindingValue
     * @param httpsSocketBindingValue the httpsSocketBindingValue
     * @param serverOutboundSocketBindingValue the serverOutboundSocketBindingValue
     * @param trustOnlySSLContextValues the serverOutboundSocketBindingValue
     * @return the runtime configuration
     */
    private static MonitorServiceConfiguration buildRuntimeConfiguration(
            MonitorServiceConfiguration bootConfiguration,
            InjectedValue<SocketBinding> httpSocketBindingValue,
            InjectedValue<SocketBinding> httpsSocketBindingValue,
            InjectedValue<OutboundSocketBinding> serverOutboundSocketBindingValue,
            Map<String, InjectedValue<SSLContext>> trustOnlySSLContextValues) {

        final MonitorServiceConfiguration.StorageAdapterConfiguration bootStorageAdapter = bootConfiguration
                .getStorageAdapter();

        if (bootStorageAdapter.getTenantId() != null && bootStorageAdapter.getUrl() != null) {
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
                        address = socketBinding.getAddress().getHostAddress();
                        if (address.equals("0.0.0.0") || address.equals("::/128")) {
                            address = InetAddress.getLocalHost().getCanonicalHostName();
                        }
                        port = socketBinding.getAbsolutePort();
                    } else {
                        OutboundSocketBinding serverBinding = serverOutboundSocketBindingValue
                                .getValue();
                        address = serverBinding.getResolvedDestinationAddress().getHostAddress();
                        port = serverBinding.getDestinationPort();
                    }
                    String protocol = (bootStorageAdapter.isUseSSL()) ? "https" : "http";
                    useUrl = String.format("%s://%s:%d", protocol, address, port);
                } catch (UnknownHostException uhe) {
                    throw new IllegalArgumentException("Cannot determine Hawkular server host", uhe);
                }
            }

            String useTenantId = bootStorageAdapter.getTenantId();

            if (bootStorageAdapter.getType() == StorageReportTo.HAWKULAR) {
                try {
                    StringBuilder url = Util.getContextUrlString(useUrl,
                            bootStorageAdapter.getAccountsContext());
                    url.append("personas/current");

                    SSLContext sslContext = getSslContext(bootConfiguration, trustOnlySSLContextValues);

                    HttpClientBuilder httpClientBuilder = new HttpClientBuilder(bootConfiguration.getStorageAdapter(),
                            sslContext);

                    OkHttpClient httpclient = httpClientBuilder.getHttpClient();

                    // TODO: next three lines are only temporary and should be deleted when inventory no longer needs
                    // this. make the call to the inventory to pre-create the test environment and other assumed
                    // entities
                    String tenantUrl = Util.getContextUrlString(useUrl,
                            bootStorageAdapter.getInventoryContext()).append("tenant").toString();
                    httpclient.newCall(httpClientBuilder.buildJsonGetRequest(tenantUrl, null)).execute();

                    Request request = httpClientBuilder.buildJsonGetRequest(url.toString(), null);
                    Response httpResponse = httpclient.newCall(request).execute();

                    if (!httpResponse.isSuccessful()) {
                        throw new Exception("status-code=[" + httpResponse.code() + "], reason=["
                                + httpResponse.message() + "], url=[" + url + "]");
                    }

                    final String fromServer = httpResponse.body().string();
                    // depending on accounts is probably overkill because of 1 REST call, so let's process the
                    // JSON via regex
                    Matcher matcher = Pattern.compile("\"id\":\"(.*?)\"").matcher(fromServer);
                    if (matcher.find()) {
                        String tenantIdFromAccounts = matcher.group(1);
                        if (useTenantId != null && !tenantIdFromAccounts.equals(useTenantId)) {
                            log.warnIgnoringTenantIdFromXml(useTenantId, tenantIdFromAccounts);
                        }
                        useTenantId = tenantIdFromAccounts;
                    }
                    log.debugf("Tenant ID [%s]", useTenantId == null ? "unknown" : useTenantId);
                } catch (Throwable t) {
                    throw new RuntimeException("Cannot get tenant ID", t);
                }
            }
            MonitorServiceConfiguration.StorageAdapterConfiguration runtimeStorageAdapter = //
            new MonitorServiceConfiguration.StorageAdapterConfiguration(
                    bootStorageAdapter.getType(),
                    bootStorageAdapter.getUsername(),
                    bootStorageAdapter.getPassword(),
                    bootStorageAdapter.getSecurityKey(),
                    bootStorageAdapter.getSecuritySecret(),
                    useTenantId,
                    useUrl,
                    bootStorageAdapter.isUseSSL(),
                    bootStorageAdapter.getServerOutboundSocketBindingRef(),
                    bootStorageAdapter.getAccountsContext(),
                    bootStorageAdapter.getInventoryContext(),
                    bootStorageAdapter.getMetricsContext(),
                    bootStorageAdapter.getFeedcommContext(),
                    bootStorageAdapter.getKeystorePath(),
                    bootStorageAdapter.getKeystorePassword(),
                    bootStorageAdapter.getSecurityRealm());

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

    private ProtocolServices protocolServices;

    private ModelControllerClientFactory localModelControllerClientFactory;

    public MonitorService(MonitorServiceConfiguration bootConfiguration) {
        super();
        this.bootConfiguration = bootConfiguration;
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
     * @param bldr the service builder used to add dependencies
     */
    public void addDependencies(ServiceBuilder<MonitorService> bldr) {
        bldr.addDependency(ServerEnvironmentService.SERVICE_NAME, ServerEnvironment.class, serverEnvironmentValue);
        bldr.addDependency(Services.JBOSS_SERVER_CONTROLLER, ModelController.class, modelControllerValue);
        bldr.addDependency(ControlledProcessStateService.SERVICE_NAME, ControlledProcessStateService.class,
                processStateValue);
        bldr.addDependency(SocketBinding.JBOSS_BINDING_NAME.append("http"), SocketBinding.class,
                httpSocketBindingValue);
        bldr.addDependency(SocketBinding.JBOSS_BINDING_NAME.append("https"), SocketBinding.class,
                httpsSocketBindingValue);
        if (this.bootConfiguration.getStorageAdapter().getServerOutboundSocketBindingRef() != null) {
            bldr.addDependency(OutboundSocketBinding.OUTBOUND_SOCKET_BINDING_BASE_SERVICE_NAME
                    .append(this.bootConfiguration.getStorageAdapter().getServerOutboundSocketBindingRef()),
                    OutboundSocketBinding.class, serverOutboundSocketBindingValue);
        }

        // get the security realm ssl context for the storage adapter
        if (this.bootConfiguration.getStorageAdapter().getSecurityRealm() != null) {
            InjectedValue<SSLContext> iv = new InjectedValue<>();
            this.trustOnlySSLContextValues.put(this.bootConfiguration.getStorageAdapter().getSecurityRealm(), iv);

            // if we ever need our own private key, we can add another dependency with trustStoreOnly=false
            boolean trustStoreOnly = true;
            SSLContextService.ServiceUtil.addDependency(
                    bldr,
                    iv,
                    SecurityRealm.ServiceUtil
                            .createServiceName(this.bootConfiguration.getStorageAdapter().getSecurityRealm()),
                    trustStoreOnly);
        }

        // get the security realms for any configured remote DMR and JMX servers that require ssl
        for (EndpointConfiguration endpoint : this.bootConfiguration.getDmrConfiguration().getEndpoints()
                .values()) {
            String securityRealm = endpoint.getSecurityRealm();
            if (securityRealm != null) {
                addSslContext(securityRealm, bldr);
            }
        }
        for (EndpointConfiguration endpoint : this.bootConfiguration.getJmxConfiguration().getEndpoints()
                .values()) {
            String securityRealm = endpoint.getSecurityRealm();
            if (securityRealm != null) {
                addSslContext(securityRealm, bldr);
            }
        }

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
        // deferred startup: must wait for server to be running before we can monitor the subsystems
        ControlledProcessStateService stateService = processStateValue.getValue();
        serverStateListener = new PropertyChangeListener() {
            @Override
            public void propertyChange(PropertyChangeEvent evt) {
                if (ControlledProcessState.State.RUNNING.equals(evt.getNewValue())) {
                    startMonitorService();
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

            this.configuration = buildRuntimeConfiguration(this.bootConfiguration,
                    this.httpSocketBindingValue,
                    this.httpsSocketBindingValue,
                    this.serverOutboundSocketBindingValue,
                    this.trustOnlySSLContextValues);
            log.infoUsingServerSideUrl(this.configuration.getStorageAdapter().getUrl());

            // prepare the builder that will create our HTTP/REST clients to the hawkular server infrastructure
            SSLContext ssl = getSslContext(this.configuration, this.trustOnlySSLContextValues);
            this.httpClientBuilder = new HttpClientBuilder(this.configuration.getStorageAdapter(), ssl);

            // get our self identifiers
            this.localModelControllerClientFactory = ModelControllerClientFactory
                    .createLocal(modelControllerValue.getValue());
            try (ModelControllerClient c = localModelControllerClientFactory.createClient()) {
                this.feedId = DMREndpointService.lookupServerIdentifier(c);
            } catch (Exception e) {
                throw new Exception("Could not obtain local feed ID", e);
            }

            // build the diagnostics object that will be used to track our own performance
            final MetricRegistry metricRegistry = new MetricRegistry();
            this.diagnostics = new DiagnosticsImpl(configuration.getDiagnostics(), metricRegistry, feedId);

            // if we are participating in a full Hawkular environment, we need to do some additional things:
            // 1. determine our tenant ID dynamically
            // 2. register our feed ID
            // 3. connect to the server's feed comm channel
            // 4. prepare the thread pool that will store discovered resources into inventory
            if (this.configuration.getStorageAdapter().getType() == StorageReportTo.HAWKULAR) {
                if (this.configuration.getStorageAdapter().getTenantId() == null) {
                    log.errorNoTenantIdFromAccounts();
                    throw new Exception("Failed to get tenant ID");
                }
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
                    log.errorCannotEstablishFeedComm(e);
                }

            } else {
                if (this.configuration.getStorageAdapter().getTenantId() == null) {
                    log.errorMustHaveTenantIdConfigured();
                    throw new Exception("Agent needs a tenant ID to run");
                }
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

            ProtocolServices ps = ProtocolServices.builder(feedId, diagnostics, trustOnlySSLContextValues) //
                    .dmrProtocolService(localModelControllerClientFactory, configuration.getDmrConfiguration())
                    .jmxProtocolService(configuration.getJmxConfiguration())
                    .platformProtocolService(configuration.getPlatformConfiguration())
                    .autoDiscoveryScanPeriodSecs(configuration.getAutoDiscoveryScanPeriodSecs())
                    .build();
            ps.addInventoryListener(inventoryStorageProxy);
            ps.addInventoryListener(schedulerService);
            protocolServices = ps;
            protocolServices.start();

            started = true;

        } catch (Throwable t) {
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
                    feedComm.disconnect();
                    feedComm = null;
                }
            } catch (Throwable t) {
                error.compareAndSet(null, t);
                log.debug("Cannot shutdown feed comm but will continue shutdown", t);
            }

            try {
                if (protocolServices != null) {
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
                stopScheduler();
            } catch (Throwable t) {
                error.compareAndSet(null, t);
                log.debug("Cannot shutdown scheduler but will continue shutdown", t);
            }

            // now stop the storage adapter
            try {
                stopStorageAdapter();
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

    /**
     * @return the directory where our agent service can write data files. This directory survives restarts.
     */
    private File getDataDirectory() {
        File dataDir = new File(this.serverEnvironmentValue.getValue().getServerDataDir(), "hawkular-agent");
        dataDir.mkdirs();
        return dataDir;
    }

    /**
     * Creates and starts the storage adapter that will be used to store our inventory data and monitoring data.
     *
     * @throws Exception if failed to start the storage adapter
     */
    private void startStorageAdapter() throws Exception {
        // determine what our backend storage should be and create its associated adapter
        switch (configuration.getStorageAdapter().getType()) {
            case HAWKULAR: {
                this.storageAdapter = new HawkularStorageAdapter();
                break;
            }
            case METRICS: {
                this.storageAdapter = new MetricsOnlyStorageAdapter();
                break;
            }
            default: {
                throw new IllegalArgumentException("Invalid storage adapter: "
                        + configuration.getStorageAdapter());
            }
        }

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
     * Shuts down the internals of the storage adapter.
     */
    private void stopStorageAdapter() {
        if (storageAdapter != null) {
            storageAdapter.shutdown();
            storageAdapter = null;
        }
    }

    /**
     * Builds the scheduler's configuraton and starts the scheduler.
     * Do NOT call this until you know all resources have been discovered
     * and the inventories have been built.
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
     * Stops the scheduler, which means no more metric collections or avail checks will be performed.
     */
    private void stopScheduler() {
        if (schedulerService != null) {
            schedulerService.stop();
            schedulerService = null;
        }
    }

    /**
     * Registers our feed with the Hawkular system.
     *
     * @throws Exception if failed to register feed
     */
    private void registerFeed() throws Exception {
        String desiredFeedId = this.feedId;
        // this.feedId = desiredFeedId; // assume we will get what we want

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

            // now send the REST request
            OkHttpClient httpclient = this.httpClientBuilder.getHttpClient();
            Request request = this.httpClientBuilder.buildJsonPostRequest(url.toString(), null, jsonPayload);
            Response httpResponse = httpclient.newCall(request).execute();

            // HTTP status of 201 means success; 409 means it already exists, anything else is an error
            if (httpResponse.code() == 201) {

                // success - store our feed ID so we remember it the next time
                final String feedObjectFromServer = httpResponse.body().string();
                final Feed feed = Util.fromJson(feedObjectFromServer, Feed.class);
                if (desiredFeedId.equals(feed.getId())) {
                    log.infof("Feed ID registered [%s]", feed.getId());
                } else {
                    log.errorf("Server gave us a feed ID [%s] but we wanted [%s]", feed.getId(), desiredFeedId);
                    // should we throw an error here or just use the feed ID we were given?
                    log.errorf("Using feed ID [%s]; make sure the agent doesn't lose its data file", feed.getId());
                }

                this.feedId = feed.getId();

            } else if (httpResponse.code() == 409) {
                log.infof("Feed ID [%s] was already registered; it will be reused", this.feedId);
            } else {
                throw new Exception("status-code=[" + httpResponse.code() + "], reason=["
                        + httpResponse.message() + "], url=[" + request.urlString() + "]");
            }

            // persist our feed ID so we can remember it the next time we start up
            Util.write(feedId, feedFile);

        } catch (Throwable t) {
            throw new Exception(String.format("Cannot create feed [%s]", desiredFeedId), t);
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

    public ProtocolServices getProtocolServices() {
        return protocolServices;
    }
}
