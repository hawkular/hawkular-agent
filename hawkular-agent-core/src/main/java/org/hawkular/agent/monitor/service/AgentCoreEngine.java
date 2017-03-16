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
package org.hawkular.agent.monitor.service;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;

import org.hawkular.agent.monitor.api.Avail;
import org.hawkular.agent.monitor.api.HawkularAgentContext;
import org.hawkular.agent.monitor.api.HawkularAgentContextImpl;
import org.hawkular.agent.monitor.cmd.Command;
import org.hawkular.agent.monitor.cmd.FeedCommProcessor;
import org.hawkular.agent.monitor.cmd.WebSocketClientBuilder;
import org.hawkular.agent.monitor.config.AgentCoreEngineConfiguration;
import org.hawkular.agent.monitor.config.AgentCoreEngineConfiguration.AbstractEndpointConfiguration;
import org.hawkular.agent.monitor.config.AgentCoreEngineConfiguration.EndpointConfiguration;
import org.hawkular.agent.monitor.diagnostics.Diagnostics;
import org.hawkular.agent.monitor.diagnostics.DiagnosticsImpl;
import org.hawkular.agent.monitor.diagnostics.JBossLoggingReporter;
import org.hawkular.agent.monitor.diagnostics.JBossLoggingReporter.LoggingLevel;
import org.hawkular.agent.monitor.diagnostics.StorageReporter;
import org.hawkular.agent.monitor.inventory.AvailType;
import org.hawkular.agent.monitor.inventory.MeasurementInstance;
import org.hawkular.agent.monitor.inventory.Resource;
import org.hawkular.agent.monitor.inventory.ResourceManager;
import org.hawkular.agent.monitor.log.AgentLoggers;
import org.hawkular.agent.monitor.log.MsgLogger;
import org.hawkular.agent.monitor.protocol.EndpointService;
import org.hawkular.agent.monitor.protocol.ProtocolService;
import org.hawkular.agent.monitor.protocol.ProtocolServices;
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
import org.hawkular.bus.common.BasicMessage;
import org.jboss.logging.Logger;

import com.codahale.metrics.MetricRegistry;
import com.codahale.metrics.ScheduledReporter;
import com.fasterxml.jackson.databind.ObjectMapper;

import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

/**
 * The core engine of the Agent service.
 *
 * @author John Mazzitelli
 * @author <a href="https://github.com/ppalaga">Peter Palaga</a>
 */
public abstract class AgentCoreEngine {
    private static final MsgLogger log = AgentLoggers.getLogger(AgentCoreEngine.class);

    private AtomicReference<ServiceStatus> agentServiceStatus = new AtomicReference<>(ServiceStatus.INITIAL);

    // the agent configuration
    private AgentCoreEngineConfiguration configuration;

    // this is used to identify us to the Hawkular environment as a particular feed
    private String feedId;

    // used to report our own internal metrics
    private Diagnostics diagnostics;
    private ScheduledReporter diagnosticsReporter;

    // used to send monitored data for storage
    private StorageAdapter storageAdapter;
    private HttpClientBuilder httpClientBuilder;

    // used to send/receive data to the server over the feed communications channel
    private WebSocketClientBuilder webSocketClientBuilder;
    private FeedCommProcessor feedComm;

    // scheduled metric and avail collections
    private SchedulerService schedulerService;

    // proxies that are exposed via JNDI so external apps can emit their own inventory, metrics, and avail checks
    private final MetricStorageProxy metricStorageProxy = new MetricStorageProxy();
    private final AvailStorageProxy availStorageProxy = new AvailStorageProxy();
    private final InventoryStorageProxy inventoryStorageProxy = new InventoryStorageProxy();

    // contains endpoint services for all the different protocols that are supported (dmr, jmx, platform)
    private ProtocolServices protocolServices;

    // Used to talk to the management interface of the WildFly server the agent is deployed in.
    // Will be null if agent is not running within a WildFly server.
    private ModelControllerClientFactory localModelControllerClientFactory;

    // maps whose keys are security realm names and values are SSLContext's and TrustManager's
    private Map<String, SSLContext> trustOnlySSLContextValues;
    private Map<String, TrustManager[]> trustOnlyTrustManagersValues;

    public AgentCoreEngine(AgentCoreEngineConfiguration configuration) {
        this.configuration = configuration;
    }

    public AgentCoreEngineConfiguration getConfiguration() {
        return this.configuration;
    }

    /**
     * @return the context that can be used by others for storing ad-hoc monitoring data
     */
    public HawkularAgentContext getHawkularAgentContext() {
        return new HawkularAgentContextImpl(metricStorageProxy, availStorageProxy, inventoryStorageProxy);
    }

    /**
     * @return the status of the agent service. Will let you know if this service
     *         is {@link #startHawkularAgent() started} or {@link #stopHawkularAgent() stopped}.
     */
    public ServiceStatus getStatus() {
        synchronized (agentServiceStatus) {
            return agentServiceStatus.get();
        }
    }

    private void setStatus(ServiceStatus newStatus) {
        synchronized (agentServiceStatus) {
            agentServiceStatus.set(newStatus);
            agentServiceStatus.notifyAll();
        }
    }

    /**
     * Starts this service. If the service is already started, this method is a no-op.
     */
    public void startHawkularAgent() {
        startHawkularAgent(null);
    }

    /**
     * Starts this service. If the service is already started, this method is a no-op.
     *
     * @param newConfiguration if not null is used to build the runtime configuration. Use this to reflect
     * changes in the persisted configuration (e.g. standalone.xml) since service creation.
     */
    public void startHawkularAgent(AgentCoreEngineConfiguration newConfiguration) {
        synchronized (agentServiceStatus) {

            boolean processStatus = true;
            while (processStatus) {
                switch (agentServiceStatus.get()) {
                    case RUNNING: {
                        return; // we are already started
                    }
                    case STARTING: {
                        // Let our current thread simply wait for the agent to start since some other thread is starting this service.
                        // We abort if we find the agent in the STOPPED state since that means the startup failed for some reason.
                        log.infoAlreadyStarting();
                        while (agentServiceStatus.get() != ServiceStatus.RUNNING
                                && agentServiceStatus.get() != ServiceStatus.STOPPED) {
                            try {
                                agentServiceStatus.wait(30000L);
                            } catch (InterruptedException e) {
                                Thread.currentThread().interrupt();
                                return;
                            }
                        }
                        return; // we are either running or the startup failed; either way, return
                    }
                    case STOPPING: {
                        // In the process of stopping; we want to restart but only after fully stopped.
                        // Once leaving the STOPPING state, we go back up and do what is appropriate for the new status.
                        log.infoAgentWillStartAfterStopping();
                        while (agentServiceStatus.get() == ServiceStatus.STOPPING) {
                            try {
                                agentServiceStatus.wait(30000L);
                            } catch (InterruptedException e) {
                                Thread.currentThread().interrupt();
                                return;
                            }
                        }
                        processStatus = true;
                        break;
                    }
                    case STOPPED:
                    case INITIAL: {
                        processStatus = false;
                        break; // this is the normal case - we are stopped and we are being asked to start now
                    }
                }
            }

            // let's begin starting the agent now
            setStatus(ServiceStatus.STARTING);
        }

        try {
            log.infoStarting();

            // determine the configuration to use
            if (null != newConfiguration) {
                this.configuration = newConfiguration;
            }
            this.configuration = loadRuntimeConfiguration(this.configuration);

            // if the agent has been disabled, abort startup and return immediately
            if (!this.configuration.getGlobalConfiguration().isSubsystemEnabled()) {
                log.infoAgentDisabled();
                setStatus(ServiceStatus.STOPPED);
                return;
            }

            if (this.configuration.getStorageAdapter().getTenantId() == null) {
                log.errorNoTenantIdSpecified();
                throw new Exception("Missing tenant ID");
            }

            this.trustOnlySSLContextValues = buildTrustOnlySSLContextValues(this.configuration);
            this.trustOnlyTrustManagersValues = buildTrustOnlyTrustManagersValues(this.configuration);

            // If we are to talk to the hawkular server securely, get the storage adapter security realm
            // details and use it in the web socket client builder.
            SSLContext ssl = null;
            X509TrustManager x509TrustManager = null;
            String securityRealm = configuration.getStorageAdapter().getSecurityRealm();
            if (securityRealm != null) {
                ssl = trustOnlySSLContextValues.get(securityRealm);
                TrustManager[] tms = trustOnlyTrustManagersValues.get(securityRealm);
                if (tms != null) {
                    for (TrustManager tm : tms) {
                        if (tm instanceof X509TrustManager) {
                            x509TrustManager = (X509TrustManager) tm;
                        }
                    }
                }
            }

            // prepare the builder that will create our HTTP/REST clients to the hawkular server infrastructure
            this.httpClientBuilder = new HttpClientBuilder(this.configuration.getStorageAdapter(), ssl,
                    x509TrustManager);

            // get our self identifiers
            this.localModelControllerClientFactory = buildLocalModelControllerClientFactory();

            if (this.configuration.getStorageAdapter().getFeedId() != null) {
                this.feedId = this.configuration.getStorageAdapter().getFeedId();
            } else {
                this.feedId = autoGenerateFeedId();
            }

            // build the diagnostics object that will be used to track our own performance
            final MetricRegistry metricRegistry = new MetricRegistry();
            this.diagnostics = new DiagnosticsImpl(configuration.getDiagnostics(), metricRegistry, feedId);

            // We need the tenantIds to register our feed (in Hawkular mode) and to schedule pings
            Set<String> tenantIds = getTenantIds();

            // Before we go on, we must make sure the Hawkular Server is up and ready
            waitForHawkularServer();

            // perform some things that are dependent upon what mode the agent is in
            switch (this.configuration.getStorageAdapter().getType()) {
                case HAWKULAR:
                    // if we are participating in a full Hawkular environment, we need to do some additional things:
                    // try to connect to the server via command-gateway channel; keep going on error
                    try {
                        this.webSocketClientBuilder = new WebSocketClientBuilder(
                                this.configuration.getStorageAdapter(), ssl, x509TrustManager);
                        this.feedComm = new FeedCommProcessor(
                                this.webSocketClientBuilder,
                                buildAdditionalCommands(),
                                this.feedId,
                                this);
                        this.feedComm.connect();
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
                startScheduler(tenantIds);
            } catch (Exception e) {
                log.errorCannotInitializeScheduler(e);
                throw new Exception("Agent cannot initialize scheduler");
            }

            // build the protocol services
            ProtocolServices ps = createProtocolServicesBuilder()
                    .dmrProtocolService(this.localModelControllerClientFactory, configuration.getDmrConfiguration())
                    .jmxProtocolService(configuration.getJmxConfiguration())
                    .platformProtocolService(configuration.getPlatformConfiguration())
                    .autoDiscoveryScanPeriodSecs(
                            configuration.getGlobalConfiguration().getAutoDiscoveryScanPeriodSeconds())
                    .build();
            ps.addInventoryListener(inventoryStorageProxy);
            ps.addInventoryListener(schedulerService);
            protocolServices = ps;

            // start all protocol services - this should perform the initial discovery scans
            protocolServices.start();

            setStatus(ServiceStatus.RUNNING);

        } catch (Throwable t) {
            if (t instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }

            log.errorFailedToStartAgent(t);

            // artificially shutdown the agent - agent will be disabled now
            stopHawkularAgent();
        }
    }

    /**
     * @return tenant IDs of the agent and its monitored endpoints (even if those monitored endpoints are not enabled)
     */
    private Set<String> getTenantIds() {
        Set<String> tenantIds = new HashSet<String>();
        List<AbstractEndpointConfiguration> endpoints = new ArrayList<>();
        endpoints.addAll(configuration.getDmrConfiguration().getEndpoints().values());
        endpoints.addAll(configuration.getJmxConfiguration().getEndpoints().values());
        endpoints.addAll(configuration.getPlatformConfiguration().getEndpoints().values());

        tenantIds.add(configuration.getStorageAdapter().getTenantId()); // always register agent's global tenant ID
        for (AbstractEndpointConfiguration endpoint : endpoints) {
            String tenantId = endpoint.getTenantId();
            if (tenantId != null) {
                tenantIds.add(tenantId);
            }
        }
        return tenantIds;
    }

    /**
     * Stops this service. If the service is already stopped, this method is a no-op.
     */
    public void stopHawkularAgent() {
        synchronized (agentServiceStatus) {
            if (agentServiceStatus.get() == ServiceStatus.STOPPED) {
                log.infoStoppedAlready();
                return; // we are already stopped
            } else if (agentServiceStatus.get() == ServiceStatus.STOPPING) {
                // some other thread is already stopping the agent - wait for that to finish and just return
                while (agentServiceStatus.get() == ServiceStatus.STOPPING) {
                    try {
                        agentServiceStatus.wait(30000L);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        return;
                    }
                    return;
                }
            }

            setStatus(ServiceStatus.STOPPING);
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

            // allow subclasses to cleanup
            try {
                cleanupDuringStop();
            } catch (Exception e) {
                error.compareAndSet(null, e);
                log.debug("Cannot shutdown - subclass exception", e);
            }

            // We attempted to clean everything we could. If we hit an error, throw it to log our shutdown wasn't clean
            if (error.get() != null) {
                throw error.get();
            }
        } catch (Throwable t) {
            log.warnFailedToStopAgent(t);
        } finally {
            setStatus(ServiceStatus.STOPPED);
        }
    }

    private void changeAvails(Map<EndpointService<?, ?>, List<MeasurementInstance<?, AvailType<?>>>> availsToChange) {
        if (availsToChange != null && !availsToChange.isEmpty() && storageAdapter != null) {
            long now = System.currentTimeMillis();
            Set<AvailDataPoint> datapoints = new HashSet<AvailDataPoint>();
            for (EndpointService<?, ?> endpointService : availsToChange.keySet()) {
                EndpointConfiguration config = endpointService.getMonitoredEndpoint()
                        .getEndpointConfiguration();
                Avail setAvailOnShutdown = config.getSetAvailOnShutdown();
                if (setAvailOnShutdown != null) {
                    List<MeasurementInstance<?, AvailType<?>>> avails = availsToChange.get(endpointService);
                    for (MeasurementInstance avail : avails) {
                        AvailDataPoint availDataPoint = new AvailDataPoint(
                                avail.getAssociatedMetricId(),
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
        for (ProtocolService<?, ?> protocolService : protocolServices.getServices()) {
            for (EndpointService<?, ?> endpointService : protocolService.getEndpointServices().values()) {
                EndpointConfiguration config = endpointService.getMonitoredEndpoint()
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
                        .convertDurationsTo(TimeUnit.MILLISECONDS)
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
                        .convertDurationsTo(TimeUnit.MILLISECONDS)
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
     * Builds the scheduler's configuration and starts the scheduler.
     *
     * @param tenantIds the tenants our feed is using
     * @throws Exception on error
     */
    private void startScheduler(Set<String> tenantIds) throws Exception {
        if (this.schedulerService == null) {
            SchedulerConfiguration schedulerConfig = new SchedulerConfiguration();
            schedulerConfig.setDiagnosticsConfig(this.configuration.getDiagnostics());
            schedulerConfig.setStorageAdapterConfig(this.configuration.getStorageAdapter());
            schedulerConfig.setMetricDispatcherBufferSize(
                    this.configuration.getGlobalConfiguration().getMetricDispatcherBufferSize());
            schedulerConfig.setMetricDispatcherMaxBatchSize(
                    this.configuration.getGlobalConfiguration().getMetricDispatcherMaxBatchSize());
            schedulerConfig.setAvailDispatcherBufferSize(
                    this.configuration.getGlobalConfiguration().getAvailDispatcherBufferSize());
            schedulerConfig.setAvailDispatcherMaxBatchSize(
                    this.configuration.getGlobalConfiguration().getAvailDispatcherMaxBatchSize());
            schedulerConfig.setPingDispatcherPeriodSeconds(
                    this.configuration.getGlobalConfiguration().getPingDispatcherPeriodSeconds());
            schedulerConfig.setFeedId(this.feedId);
            schedulerConfig.setTenantIds(tenantIds);

            this.schedulerService = new SchedulerService(schedulerConfig, this.diagnostics, this.storageAdapter);
        }

        this.schedulerService.start();
    }

    private void waitForHawkularServer() throws Exception {
        OkHttpClient httpclient = this.httpClientBuilder.getHttpClient();

        String statusUrl = Util.getContextUrlString(configuration.getStorageAdapter().getUrl(),
                configuration.getStorageAdapter().getMetricsContext()).append("status").toString();
        Request request = this.httpClientBuilder.buildJsonGetRequest(statusUrl, null);
        int counter = 0;
        while (true) {
            Response response = null;
            try {
                response = httpclient.newCall(request).execute();
                if (response.code() != 200) {
                    if (response.code() != 401) {
                        log.debugf("Hawkular Metrics is not ready yet: %d/%s", response.code(), response.message());
                    } else {
                        log.warnBadHawkularCredentials(response.code(), response.message());
                    }
                } else {
                    String bodyString = response.body().string();
                    if (checkReallyUp(bodyString)) {
                        log.debugf("Hawkular Metrics is ready: %s", bodyString);
                        break;
                    } else {
                        log.debugf("Hawkular Metrics is still starting: %s", bodyString);
                    }
                }
            } catch (Exception e) {
                log.debugf("Hawkular Metrics is not ready yet: %s", e.toString());
            } finally {
                if (response != null) {
                    response.body().close();
                }
            }
            Thread.sleep(5000L);
            counter++;
            if (counter % 12 == 0) {
                log.warnConnectionDelayed(counter, "metrics", statusUrl);
            }
        }
    }

    /**
     * If the server returns a 200 OK, we still need to check the content if the server
     * is really up. This is explained here: https://twitter.com/heiglandreas/status/801137903149654017
     * @param bodyString String representation of the body
     * @return true if it is really up, false otherwise (still starting).
     */
    private boolean checkReallyUp(String bodyString) {

        ObjectMapper mapper = new ObjectMapper(); // We don't need it later
        Map result = null;
        try {
            result = mapper.readValue(bodyString, Map.class);
        } catch (IOException e) {
            return false;
        }
        String status = (String) result.get("MetricsService");

        return "STARTED".equals(status);
    }

    /**
     * @return feed ID of the agent if the agent has started and the feed was registered; null otherwise
     */
    public String getFeedId() {
        return this.feedId;
    }

    /**
     * @return tenant ID of the agent
     */
    public String getTenantId() {
        return this.configuration.getStorageAdapter().getTenantId();
    }

    public SchedulerService getSchedulerService() {
        return schedulerService;
    }

    /**
     * @return a factory that can create clients which can talk to the local management interface
     *         of the app server we are running in. Will be null if agent is not running in a WildFly server.
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
     * @return the current set of protocol services
     */
    public ProtocolServices getProtocolServices() {
        return protocolServices;
    }

    /**
     * @return true if the agent is to be considered immutable and no config changes are allowed. This should
     *         also disallow operation execution on managed resources if those operations modify the remote resource.
     */
    public boolean isImmutable() {
        return this.configuration.getGlobalConfiguration().isImmutable();
    }

    /**
     * Subclasses need to build the SSL contexts for all security realms it supports.
     * @param config the agent configuration
     * @return map of security realm names to SSL contexts
     */
    protected abstract Map<String, SSLContext> buildTrustOnlySSLContextValues(AgentCoreEngineConfiguration config);

    /**
    * Subclasses need to build the trust managers for all security realms it supports.
    * @param config the agent configuration
    * @return map of security realm names to trust managers
    */
    protected abstract Map<String, TrustManager[]> buildTrustOnlyTrustManagersValues(
            AgentCoreEngineConfiguration config);

    /**
     * @return If the agent is running in a WildFly container, this should return
     * a non-null client to the agent's own container. Null should be returned if the agent is
     * not running within a WildFly controller or it cannot be determined if it is.
     */
    protected abstract ModelControllerClientFactory buildLocalModelControllerClientFactory();

    /**
     * This is called when the agent is starting up and needs to load runtime configuration
     * based on the given static configuration.
     *
     * Subclass implementations are free to tweak the given static configuration and return the true
     * runtime configuration the agent needs to use while running. If no changes are needed, subclasses
     * should simply return <code>config</code>.
     *
     * @param config the boot configuration as it is known now at startup
     * @return the new runtime configuration to be used by the running agent
     */
    protected abstract AgentCoreEngineConfiguration loadRuntimeConfiguration(AgentCoreEngineConfiguration config);

    /**
     * Subclasses are free to override if there are things that need to be done while shutting down.
     */
    protected abstract void cleanupDuringStop();

    /**
     * When the configuration does not specify a feed ID, this method will be called to create one.
     * It is best if this feed ID can be generated the same across agent restarts.
     *
     * @return the autogenerated feed ID
     *
     * @throws Exception if the feed ID cannot be generated
     */
    protected abstract String autoGenerateFeedId() throws Exception;

    /**
     * If the agent can support additional command gateway commands (above and beyond the default
     * ones that you get for free with the core agent engine), subclasses can return those
     * command definitions here.
     *
     * @return additional command gateway commands that can be processed by the agent
     */
    protected abstract Map<String, Class<? extends Command<? extends BasicMessage, ? extends BasicMessage>>> //
            buildAdditionalCommands();
}
