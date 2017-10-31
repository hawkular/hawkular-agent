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

import java.io.File;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;

import org.hawkular.agent.monitor.api.HawkularAgentContext;
import org.hawkular.agent.monitor.api.HawkularAgentContextImpl;
import org.hawkular.agent.monitor.cmd.Command;
import org.hawkular.agent.monitor.cmd.FeedCommProcessor;
import org.hawkular.agent.monitor.cmd.WebSocketClientBuilder;
import org.hawkular.agent.monitor.config.AgentCoreEngineConfiguration;
import org.hawkular.agent.monitor.config.AgentCoreEngineConfiguration.MetricsExporterConfiguration;
import org.hawkular.agent.monitor.config.AgentCoreEngineConfiguration.StorageAdapterConfiguration;
import org.hawkular.agent.monitor.diagnostics.Diagnostics;
import org.hawkular.agent.monitor.diagnostics.DiagnosticsImpl;
import org.hawkular.agent.monitor.diagnostics.JBossLoggingReporter;
import org.hawkular.agent.monitor.diagnostics.JBossLoggingReporter.LoggingLevel;
import org.hawkular.agent.monitor.log.AgentLoggers;
import org.hawkular.agent.monitor.log.MsgLogger;
import org.hawkular.agent.monitor.prometheus.WebServer;
import org.hawkular.agent.monitor.protocol.ProtocolServices;
import org.hawkular.agent.monitor.protocol.dmr.ModelControllerClientFactory;
import org.hawkular.agent.monitor.storage.HawkularStorageAdapter;
import org.hawkular.agent.monitor.storage.HttpClientBuilder;
import org.hawkular.agent.monitor.storage.InventoryStorageProxy;
import org.hawkular.agent.monitor.storage.NotificationDispatcher;
import org.hawkular.agent.monitor.storage.StorageAdapter;
import org.hawkular.agent.monitor.util.Util;
import org.hawkular.bus.common.BasicMessage;
import org.hawkular.inventory.api.model.MetricsEndpoint;
import org.jboss.logging.Logger;

import com.codahale.metrics.MetricRegistry;
import com.codahale.metrics.ScheduledReporter;
import com.fasterxml.jackson.databind.ObjectMapper;

import okhttp3.Call;
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

    // used to send notifications to the server
    private NotificationDispatcher notificationDispatcher;

    // proxies if exposed that will allow external apps to store their own inventory
    private final InventoryStorageProxy inventoryStorageProxy = new InventoryStorageProxy();

    // contains endpoint services for all the different protocols that are supported (dmr, jmx, platform)
    private ProtocolServices protocolServices;

    // Used to talk to the management interface of the WildFly server the agent is deployed in.
    // Will be null if agent is not running within a WildFly server.
    private ModelControllerClientFactory localModelControllerClientFactory;

    // maps whose keys are security realm names and values are SSLContext's and TrustManager's
    private Map<String, SSLContext> trustOnlySSLContextValues;
    private Map<String, TrustManager[]> trustOnlyTrustManagersValues;

    // the endpoint that emits metrics
    private WebServer metricsExporter;

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
        return new HawkularAgentContextImpl(inventoryStorageProxy);
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
            Package agentPackage = this.getClass().getPackage();
            if (agentPackage != null) {
                log.infoTypeAndVersion(agentPackage.getImplementationTitle(), agentPackage.getImplementationVersion());
            }

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

            // Before we go on, we must make sure the Hawkular Server is up and ready
            waitForHawkularServer();

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

            // start the storage adapter
            try {
                startStorageAdapter();
            } catch (Exception e) {
                log.errorCannotStartStorageAdapter(e);
                throw new Exception("Agent cannot start storage adapter");
            }

            // now that we started the storage adapter, we can create our dispatcher
            this.notificationDispatcher = new NotificationDispatcher(this.storageAdapter, this.feedId);

            // build the protocol services
            ProtocolServices ps = createProtocolServicesBuilder()
                    .dmrProtocolService(this.localModelControllerClientFactory, configuration.getDmrConfiguration())
                    .jmxProtocolService(configuration.getJmxConfiguration())
                    .platformProtocolService(configuration.getPlatformConfiguration())
                    .autoDiscoveryScanPeriodSecs(
                            configuration.getGlobalConfiguration().getAutoDiscoveryScanPeriodSeconds())
                    .build();
            ps.addInventoryListener(inventoryStorageProxy);
            if (notificationDispatcher != null) {
                ps.addInventoryListener(notificationDispatcher);
            }
            protocolServices = ps;

            // start all protocol services - this should perform the initial discovery scans
            protocolServices.start();

            // start the metrics exporter if enabled
            try {
                startMetricsExporter();
            } catch (Exception e) {
                log.errorf(e, "Cannot prepare metrics exporter");
            }

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
            // disconnect from the feed comm channel
            try {
                if (metricsExporter != null) {
                    log.infoStopMetricsExporter();
                    metricsExporter.stop();
                    metricsExporter = null;
                }
            } catch (Throwable t) {
                error.compareAndSet(null, t);
                log.debug("Cannot shutdown metrics exporter but will continue shutdown", t);
            }

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
            try {
                if (protocolServices != null) {
                    protocolServices.stop();
                    protocolServices.removeInventoryListener(inventoryStorageProxy);
                    if (notificationDispatcher != null) {
                        protocolServices.removeInventoryListener(notificationDispatcher);
                    }
                    protocolServices = null;
                }
            } catch (Throwable t) {
                error.compareAndSet(null, t);
                log.debug("Cannot shutdown protocol services but will continue shutdown", t);
            }

            // now stop the storage adapter
            try {
                if (storageAdapter != null) {
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

    /**
     * Creates and starts the storage adapter that will be used to store our inventory data and monitoring data.
     *
     * @throws Exception if failed to start the storage adapter
     */
    private void startStorageAdapter() throws Exception {
        // create the storage adapter that will write our metrics/inventory data to backend storage on server
        this.storageAdapter = new HawkularStorageAdapter();
        this.storageAdapter.initialize(
                feedId,
                configuration.getStorageAdapter(),
                diagnostics,
                httpClientBuilder);

        // provide our storage adapter to the proxies - allows external apps to use them to store its own data
        inventoryStorageProxy.setStorageAdapter(storageAdapter);

        // log our own diagnostic reports
        this.diagnosticsReporter = JBossLoggingReporter.forRegistry(this.diagnostics.getMetricRegistry())
                .convertRatesTo(TimeUnit.SECONDS)
                .convertDurationsTo(TimeUnit.MILLISECONDS)
                .outputTo(Logger.getLogger(getClass()))
                .withLoggingLevel(LoggingLevel.DEBUG)
                .build();

        if (this.configuration.getDiagnostics().isEnabled()) {
            diagnosticsReporter.start(this.configuration.getDiagnostics().getInterval(),
                    this.configuration.getDiagnostics().getTimeUnits());
        }
    }

    private void startMetricsExporter() throws Exception {
        MetricsExporterConfiguration meConfig = configuration.getMetricsExporterConfiguration();
        if (meConfig.isEnabled()) {
            String hostPort;
            if (meConfig.getHost() != null) {
                hostPort = String.format("%s:%d", meConfig.getHost(), meConfig.getPort());
            } else {
                hostPort = String.format("%d", meConfig.getPort());
            }
            File configFile = downloadMetricsExporterConfigFile();
            if (configFile != null) {
                String[] args = new String[] { hostPort, configFile.getAbsolutePath() };
                log.infoStartMetricsExporter(args[0], args[1]);
                metricsExporter = new WebServer();
                metricsExporter.start(args);
                sendMetricsEndpointRegistrationRequest();
            } else {
                log.infoMetricsExporterDisabled();
            }
        } else {
            log.infoMetricsExporterDisabled();
        }
    }

    private void waitForHawkularServer() throws Exception {
        waitForHawkularInventory();
    }

    private void waitForHawkularInventory() throws Exception {
        OkHttpClient httpclient = this.httpClientBuilder.getHttpClient();
        String statusUrl = Util.getContextUrlString(configuration.getStorageAdapter().getUrl(),
                configuration.getStorageAdapter().getInventoryContext()).append("status").toString();
        Request request = this.httpClientBuilder.buildJsonGetRequest(statusUrl, null);
        int counter = 0;
        while (true) {
            Response response = null;
            try {
                response = httpclient.newCall(request).execute();
                if (response.code() != 200) {
                    if (response.code() != 401) {
                        log.debugf("Hawkular Inventory is not ready yet: %d/%s", response.code(), response.message());
                    } else {
                        log.warnBadHawkularCredentials(response.code(), response.message());
                    }
                } else {
                    String bodyString = response.body().string();
                    if (checkStatusReallyUp(bodyString)) {
                        log.infof("Hawkular Inventory is ready: %s", bodyString);
                        break;
                    } else {
                        log.debugf("Hawkular Inventory is still starting: %s", bodyString);
                    }
                }
            } catch (Exception e) {
                log.debugf("Hawkular Inventory is not ready yet: %s", e.toString());
            } finally {
                if (response != null) {
                    response.body().close();
                }
            }
            Thread.sleep(5000L);
            counter++;
            if (counter % 12 == 0) {
                log.warnConnectionDelayed(counter, "inventory", statusUrl);
            }
        }
    }

    private File downloadMetricsExporterConfigFile() throws Exception {
        MetricsExporterConfiguration meConfig = configuration.getMetricsExporterConfiguration();
        OkHttpClient httpclient = this.httpClientBuilder.getHttpClient();
        String url = Util.getContextUrlString(
                configuration.getStorageAdapter().getUrl(),
                configuration.getStorageAdapter().getInventoryContext())
                .append("get-jmx-exporter-config")
                .append("/")
                .append(meConfig.getConfigFile())
                .toString();
        Request request = this.httpClientBuilder.buildGetRequest(url, null);
        Response response = null;
        File configFileToWrite = null;
        try {
            response = httpclient.newCall(request).execute();
            if (response.code() != 200) {
                log.errorf("Cannot download metrics exporter config file [%s]: %d/%s",
                        meConfig.getConfigFile(),
                        response.code(),
                        response.message());
            } else {
                String bodyString = response.body().string();
                configFileToWrite = expectedMetricsExporterFile();
                Util.write(bodyString, configFileToWrite);
            }
        } catch (Exception e) {
            log.errorf(e, "Failed to download metrics exporter config file [%s]", meConfig.getConfigFile());
            configFileToWrite = null;
        } finally {
            if (response != null) {
                response.body().close();
            }
        }

        if (configFileToWrite == null) {
            // if we couldn't download the current version, attempt to use an old version if we have one already
            File oldConfigFile = expectedMetricsExporterFile();
            if (oldConfigFile.canRead()) {
                log.warnf("Using existing metrics exporter config file at [%s]", oldConfigFile.getAbsolutePath());
                configFileToWrite = oldConfigFile;
            }
        }

        return configFileToWrite;
    }

    private File expectedMetricsExporterFile() {
        String configFileName = configuration.getMetricsExporterConfiguration().getConfigFile();
        if (!configFileName.endsWith("-jmx-exporter.yaml")) {
            configFileName += "-jmx-exporter.yaml";
        }
        return new File(configuration.getMetricsExporterConfiguration().getConfigDir(), configFileName);
    }

    /**
     * If the server returns a 200 OK, we still need to check the content if the server
     * is really up. This is explained here: https://twitter.com/heiglandreas/status/801137903149654017
     * @param bodyString String representation of the body
     * @return true if it is really up, false otherwise (still starting).
     */
    private boolean checkStatusReallyUp(String bodyString) {
        Map<?, ?> result = null;
        try {
            result = new ObjectMapper().readValue(bodyString, Map.class);
        } catch (Exception e) {
            return false;
        }
        String status = (String) result.get("status");
        return "UP".equals(status);
    }

    private void sendMetricsEndpointRegistrationRequest() throws Exception {
        StorageAdapterConfiguration sac = configuration.getStorageAdapter();
        MetricsExporterConfiguration mec = configuration.getMetricsExporterConfiguration();

        MetricsEndpoint me = MetricsEndpoint.builder()
                .feedId(getFeedId())
                .host(mec.getHost())
                .port(mec.getPort())
                .build();

        StringBuilder url = Util.getContextUrlString(sac.getUrl(), sac.getInventoryContext())
                .append("register-metrics-endpoint");
        Request request = httpClientBuilder.buildJsonPostRequest(url.toString(), null, Util.toJson(me));
        Call call = httpClientBuilder.getHttpClient().newCall(request);

        try (Response response = call.execute()) {
            if (!response.isSuccessful()) {
                throw new Exception("status-code=[" + response.code() + "], reason=["
                        + response.message() + "], url=[" + request.url().toString() + "]");
            }
        }
        log.debugf("Metrics endpoint registered");
    }

    /**
     * @return feed ID of the agent if the agent has started and the feed was registered; null otherwise
     */
    public String getFeedId() {
        return this.feedId;
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
