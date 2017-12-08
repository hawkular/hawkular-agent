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
import java.io.FileReader;
import java.io.FileWriter;
import java.io.InputStream;
import java.util.Collections;
import java.util.HashSet;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ThreadFactory;
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
import org.hawkular.agent.monitor.protocol.platform.PlatformMBeanGenerator;
import org.hawkular.agent.monitor.storage.HawkularStorageAdapter;
import org.hawkular.agent.monitor.storage.HttpClientBuilder;
import org.hawkular.agent.monitor.storage.InventoryStorageProxy;
import org.hawkular.agent.monitor.storage.NotificationDispatcher;
import org.hawkular.agent.monitor.storage.StorageAdapter;
import org.hawkular.agent.monitor.util.ThreadFactoryGenerator;
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

    // used to send notifications to the server
    private NotificationDispatcher notificationDispatcher;

    // used to wrap platform resources with MBeans
    private PlatformMBeanGenerator platformMBeanGenerator;

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

    // job to check a directory for metric exporters and adds any it finds to the set of proxied metrics exporters
    private ScheduledExecutorService metricsExporterDiscoveryExecutor;
    private Set<String> metricsExportersThatAreProxied = Collections.synchronizedSet(new HashSet<>());

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
            log.infoVersion(Version.getVersionString());

            log.infoStarting();

            // Determine the configuration to use immediately.
            // WARNING! Do not use any inventory metadata (e.g. metric types, resource types) from this
            // configuration yet. We have not attempted to download the full configuration from the server.
            // Until we do, we might have non-existent or out-of-date inventory metadata.
            // But we need this configuration now for things like getting the server endpoint information
            // so we can connect to the server in the first place (which is needed in order to download
            // the rest of the configuration).
            if (null != newConfiguration) {
                this.configuration = newConfiguration;
            }

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

            // Before we go on, we must make sure the Hawkular Server is up and ready
            waitForHawkularServer(this.httpClientBuilder, this.configuration.getStorageAdapter());

            // Now attempt to download the inventory metadata configuration from the server,
            // overlaying it over the current configuration. Once this call completes, we will
            // have our full configuration and can use even the inventory metadata configuration.
            this.configuration = downloadAndOverlayConfiguration(this.httpClientBuilder, this.configuration);

            // this wraps the platform resources with MBeans so their metrics can be exposed via JMX
            this.platformMBeanGenerator = new PlatformMBeanGenerator(this.feedId,
                    configuration.getPlatformConfiguration());
            this.platformMBeanGenerator.registerAllMBeans();

            // IF the agent is in full non-metrics-only mode, initialize everything needed for inventory
            if (!isMetricsOnlyMode(this.configuration)) {

                // get our self identifiers
                this.localModelControllerClientFactory = buildLocalModelControllerClientFactory();

                if (this.configuration.getStorageAdapter().getFeedId() != null) {
                    this.feedId = this.configuration.getStorageAdapter().getFeedId();
                } else {
                    this.feedId = autoGenerateFeedId();
                }
                log.infoAgentFeedId(this.feedId);

                // build the diagnostics object that will be used to track our own performance
                final MetricRegistry metricRegistry = new MetricRegistry();
                this.diagnostics = new DiagnosticsImpl(configuration.getDiagnostics(), metricRegistry, feedId);

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
                        .dmrProtocolService(this.localModelControllerClientFactory,
                                configuration.getDmrConfiguration())
                        .jmxProtocolService(configuration.getJmxConfiguration())
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
            } else {
                log.infoMetricsOnlyMode();
            }

            // start the metrics exporter if enabled
            try {
                startMetricsExporter();
            } catch (Exception e) {
                log.errorf(e, "Cannot start metrics exporter - continuing but no metrics will be available");
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
            // stop the metrics exporter endpoint
            try {
                stopMetricsExporter();
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
                    if (inventoryStorageProxy != null) {
                        protocolServices.removeInventoryListener(inventoryStorageProxy);
                    }
                    if (platformMBeanGenerator != null) {
                        platformMBeanGenerator.unregisterAllMBeans();
                    }
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
                hostPort = String.format("127.0.0.1:%d", meConfig.getPort());
            }
            File configFile = downloadMetricsExporterConfigFile();
            if (configFile != null) {
                String[] args = new String[] { hostPort, configFile.getAbsolutePath() };
                log.infoStartMetricsExporter(args[0], args[1]);
                metricsExporter = new WebServer();
                metricsExporter.start(args);
            } else {
                log.infoMetricsExporterDisabled();
            }

            // The below is mainly to support WildFly domain mode where we need a metrics-only agent in each
            // slave server but a normal agent in the host controller to obtain inventory.
            // In proxy mode of "slave" this agent is probably running in a slave server so we need to write
            // a file in the proxy data directory to let the master know what our metrics exporter endpoint is.
            // In proxy mode of "master" this agent will look at all files in the proxy data directory and will
            // put all the endpoint information in a set to be picked up later by an MBean attribute. That MBean
            // attribute will be a resource configuration property on an agent resource - any changes to that attribute
            // will cause the server to write new scrape files for Prometheus to read. Those metrics will look like
            // they are coming from this agent because the metrics will be labeled with our agent's feed ID (hence
            // why we call it is a proxy).
            switch (meConfig.getProxyMode()) {
                case master: {
                    Runnable job = new Runnable() {
                        @Override
                        public void run() {
                            try {
                                File dataDir = new File(meConfig.getProxyDataDir());
                                File[] files = dataDir.listFiles();
                                if (files != null) {
                                    // each file has a name "host:port" - that's what we want to proxy
                                    Set<String> toBeProxied = new HashSet<>();
                                    for (File file : files) {
                                        Properties labels = new Properties();
                                        try (FileReader fr = new FileReader(file)) {
                                            labels.load(fr);
                                        }
                                        String endpointDescription = file.getName();
                                        if (labels.size() > 0) {
                                            endpointDescription += labels.toString(); // toString is {n1=v1,n2=v2,...}
                                        }
                                        toBeProxied.add(endpointDescription);
                                    }
                                    // if something was added or removed, force an inventory update
                                    if (!metricsExportersThatAreProxied.equals(toBeProxied)) {
                                        log.infof("Change in set of metrics exporters to be proxied: %s",
                                                toBeProxied);
                                        synchronized (metricsExportersThatAreProxied) {
                                            metricsExportersThatAreProxied.clear();
                                            metricsExportersThatAreProxied.addAll(toBeProxied);
                                        }
                                        getProtocolServices().discoverAll();
                                    }
                                }
                            } catch (Throwable t) {
                                // make sure we don't let exceptions bubble out - that would stop all future scans from executing
                                log.errorf(t, "Cannot proxy metrics exporters");
                            }
                        }
                    };

                    log.infof("Metrics exporter proxy mode 'master' - data dir: %s", meConfig.getProxyDataDir());
                    metricsExportersThatAreProxied.clear();
                    ThreadFactory threadFactory = ThreadFactoryGenerator.generateFactory(true,
                            "Hawkular-Agent-Metrics-Exporter-Discovery-Scan");
                    metricsExporterDiscoveryExecutor = Executors.newSingleThreadScheduledExecutor(threadFactory);
                    metricsExporterDiscoveryExecutor.scheduleAtFixedRate(job, 0, 30, TimeUnit.SECONDS);
                    break;
                }
                case slave: {
                    // create a file whose name is "host:port" whose content are name/value pairs that are the
                    // labels that we want attached to all of the slave metrics.
                    File dataDir = new File(meConfig.getProxyDataDir());
                    dataDir.mkdirs();
                    File dataFile = new File(dataDir, hostPort);
                    dataFile.delete(); // remove anything that might have existed before
                    try {
                        Properties labels = Util
                                .extractPropertiesFromExpression(meConfig.getProxyMetricLabelsExpression());
                        try (FileWriter fw = new FileWriter(dataFile)) {
                            labels.store(fw, null);
                        }
                        log.infof("Metrics exporter proxy mode 'slave' - data file [%s] with metric labels [%s]",
                                dataFile, labels);
                    } catch (Exception e) {
                        log.errorf(e, "Failed to write metrics exporter proxy data file: %s", dataFile);
                    }
                    break;
                }
                default: {
                    log.debugf("Metrics exporter proxy mode is disabled");
                    break;
                }
            }
        } else {
            log.infoMetricsExporterDisabled();
        }
    }

    private void stopMetricsExporter() throws Exception {
        if (metricsExporter != null) {
            log.infoStopMetricsExporter();
            metricsExporter.stop();
            metricsExporter = null;
        }
        if (metricsExporterDiscoveryExecutor != null) {
            log.debugf("Shutting down metric endpoint proxy discovery job");
            metricsExporterDiscoveryExecutor.shutdownNow();
            metricsExporterDiscoveryExecutor = null;
        }

    }

    // this method should not rely on agent fields (like this.configuration) because we want to make sure
    // this method doesn't use fields that aren't initialized yet. Pass everything through parameters.
    private void waitForHawkularServer(HttpClientBuilder hcb, StorageAdapterConfiguration storageAdapterConfig)
            throws Exception {
        waitForHawkularInventory(hcb, storageAdapterConfig);
    }

    // this method should not rely on agent fields (like this.configuration) because we want to make sure
    // this method doesn't use fields that aren't initialized yet. Pass everything through parameters.
    private void waitForHawkularInventory(HttpClientBuilder hcb, StorageAdapterConfiguration storageAdapterConfig)
            throws Exception {
        OkHttpClient httpclient = hcb.getHttpClient();
        String statusUrl = Util
                .getContextUrlString(storageAdapterConfig.getUrl(), storageAdapterConfig.getInventoryContext())
                .append("status").toString();
        Request request = hcb.buildJsonGetRequest(statusUrl, null);
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

    // this method should not rely on agent fields (like this.configuration) because we want to make sure
    // this method doesn't use fields that aren't initialized yet. Pass everything through parameters.
    private AgentCoreEngineConfiguration downloadAndOverlayConfiguration(
            HttpClientBuilder hcb,
            AgentCoreEngineConfiguration initialConfig)
            throws Exception {

        AgentCoreEngineConfiguration newOverlaidConfiguration = initialConfig;

        String typeVersionToDownload = initialConfig.getGlobalConfiguration().getTypeVersion();

        // If there is no type version declared, download and overlay nothing.
        // If we have no inventory metadata at all, and we are not in metrics only mode
        // then we are required to download the config successfully;
        // an exception is thrown if we cannot download and overlay the config in that case.
        // If we already have some inventory metadata already, then we will not abort with an exception
        // on download/overlay failure - we'll just continue with the old inventory metadata.
        boolean requireDownload = false;
        Exception error = null;

        if (typeVersionToDownload != null) {
            if (initialConfig.getDmrConfiguration().getTypeSets().isDisabledOrEmpty()
                    && initialConfig.getJmxConfiguration().getTypeSets().isDisabledOrEmpty()) {
                requireDownload = !isMetricsOnlyMode(initialConfig);
            }

            OkHttpClient httpclient = hcb.getHttpClient();
            String url = Util.getContextUrlString(
                    initialConfig.getStorageAdapter().getUrl(),
                    initialConfig.getStorageAdapter().getInventoryContext())
                    .append("get-inventory-config")
                    .append("/")
                    .append(typeVersionToDownload)
                    .toString();
            Request request = hcb.buildGetRequest(url, null);
            Response response = null;
            try {
                log.debugf("Downloading inventory configuration from server: %s", url);
                response = httpclient.newCall(request).execute();
                if (response.code() != 200) {
                    error = new Exception(String.format("Cannot download inventory configuration [%s]: %d/%s",
                            typeVersionToDownload, response.code(), response.message()));
                } else {
                    newOverlaidConfiguration = overlayConfiguration(response.body().byteStream());
                }
            } catch (Exception e) {
                error = new Exception(String.format("Failed to download and overlay inventory configuration [%s]",
                        typeVersionToDownload), e);
            } finally {
                if (response != null) {
                    response.body().close();
                }
            }
        } else {
            log.debugf("No inventory type version declared; no configuration will be downloaded. "
                    + "Original configuration will be used as-is.");
        }

        if (error != null) {
            if (requireDownload) {
                throw error;
            } else {
                log.errorf(error, "Will continue with the previous inventory configuration.");
            }
        }

        return newOverlaidConfiguration;
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
            log.debugf("Downloading jmx exporter configuration from server: %s", url);
            response = httpclient.newCall(request).execute();
            if (response.code() != 200) {
                log.errorf("Cannot download metrics exporter config file [%s]: %d/%s",
                        meConfig.getConfigFile(),
                        response.code(),
                        response.message());
            } else {
                String bodyString = response.body().string();
                configFileToWrite = expectedMetricsExporterFile();
                configFileToWrite.getParentFile().mkdirs();
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

    protected boolean isMetricsOnlyMode(AgentCoreEngineConfiguration config) {
        // if there are no managed servers defined, we are in metrics only mode
        return (config.getDmrConfiguration().getEndpoints().isEmpty()
                && config.getJmxConfiguration().getEndpoints().isEmpty());
    }

    /**
     * The Feed ID of the agent if the agent has started and the feed was generated. If this agent is not
     * registering inventory with the server (i.e. is in metrics-only mode) this will be null.
     *
     * @return feed ID or null if this agent is not associated with a feed
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
     * The set of all metrics exporters that this agent knows about and that should be proxied to look like they
     * come from ths agent. This is only useful when in metrics exporter proxy mode of "master".
     * @return a set of all known metric exporters that we want to proxy.
     */
    protected Set<String> getMetricsExportersThatAreProxied() {
        synchronized (metricsExportersThatAreProxied) {
            return new HashSet<>(metricsExportersThatAreProxied);
        }
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
     * This is called when the agent is starting up has obtained a configuration file
     * that needs to be overlaid on top of the current configuration.
     *
     * @param newConfig the stream containing the new overlay configuration file
     * @return the new runtime configuration to be used by the running agent
     */
    protected abstract AgentCoreEngineConfiguration overlayConfiguration(InputStream newConfig);

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
