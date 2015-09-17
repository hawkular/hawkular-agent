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
package org.hawkular.agent.monitor.service;

import static java.util.concurrent.TimeUnit.MILLISECONDS;

import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.hawkular.agent.monitor.api.HawkularMonitorContext;
import org.hawkular.agent.monitor.api.HawkularMonitorContextImpl;
import org.hawkular.agent.monitor.diagnostics.Diagnostics;
import org.hawkular.agent.monitor.diagnostics.DiagnosticsImpl;
import org.hawkular.agent.monitor.diagnostics.JBossLoggingReporter;
import org.hawkular.agent.monitor.diagnostics.JBossLoggingReporter.LoggingLevel;
import org.hawkular.agent.monitor.diagnostics.StorageReporter;
import org.hawkular.agent.monitor.extension.MonitorServiceConfiguration;
import org.hawkular.agent.monitor.extension.MonitorServiceConfiguration.StorageReportTo;
import org.hawkular.agent.monitor.feedcomm.FeedCommProcessor;
import org.hawkular.agent.monitor.inventory.AvailTypeManager;
import org.hawkular.agent.monitor.inventory.ManagedServer;
import org.hawkular.agent.monitor.inventory.MetricTypeManager;
import org.hawkular.agent.monitor.inventory.Name;
import org.hawkular.agent.monitor.inventory.ResourceManager;
import org.hawkular.agent.monitor.inventory.ResourceTypeManager;
import org.hawkular.agent.monitor.inventory.dmr.DMRAvailInstance;
import org.hawkular.agent.monitor.inventory.dmr.DMRAvailType;
import org.hawkular.agent.monitor.inventory.dmr.DMRAvailTypeSet;
import org.hawkular.agent.monitor.inventory.dmr.DMRInventoryManager;
import org.hawkular.agent.monitor.inventory.dmr.DMRMetadataManager;
import org.hawkular.agent.monitor.inventory.dmr.DMRMetricInstance;
import org.hawkular.agent.monitor.inventory.dmr.DMRMetricType;
import org.hawkular.agent.monitor.inventory.dmr.DMRMetricTypeSet;
import org.hawkular.agent.monitor.inventory.dmr.DMRResource;
import org.hawkular.agent.monitor.inventory.dmr.DMRResourceType;
import org.hawkular.agent.monitor.inventory.dmr.DMRResourceTypeSet;
import org.hawkular.agent.monitor.inventory.dmr.LocalDMRManagedServer;
import org.hawkular.agent.monitor.inventory.dmr.RemoteDMRManagedServer;
import org.hawkular.agent.monitor.log.MsgLogger;
import org.hawkular.agent.monitor.scheduler.ModelControllerClientFactory;
import org.hawkular.agent.monitor.scheduler.ModelControllerClientFactoryImpl;
import org.hawkular.agent.monitor.scheduler.SchedulerService;
import org.hawkular.agent.monitor.scheduler.config.DMREndpoint;
import org.hawkular.agent.monitor.scheduler.config.LocalDMREndpoint;
import org.hawkular.agent.monitor.scheduler.config.SchedulerConfiguration;
import org.hawkular.agent.monitor.storage.AvailStorageProxy;
import org.hawkular.agent.monitor.storage.HawkularStorageAdapter;
import org.hawkular.agent.monitor.storage.HttpClientBuilder;
import org.hawkular.agent.monitor.storage.InventoryStorageProxy;
import org.hawkular.agent.monitor.storage.MetricStorageProxy;
import org.hawkular.agent.monitor.storage.MetricsOnlyStorageAdapter;
import org.hawkular.agent.monitor.storage.StorageAdapter;
import org.hawkular.inventory.api.model.Feed;
import org.jboss.as.controller.ControlledProcessState;
import org.jboss.as.controller.ControlledProcessStateService;
import org.jboss.as.controller.ModelController;
import org.jboss.as.controller.client.ModelControllerClient;
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
import org.jgrapht.event.GraphVertexChangeEvent;
import org.jgrapht.event.VertexSetListener;
import org.jgrapht.graph.DefaultEdge;
import org.jgrapht.traverse.BreadthFirstIterator;

import com.codahale.metrics.InstrumentedExecutorService;
import com.codahale.metrics.MetricRegistry;
import com.codahale.metrics.ScheduledReporter;
import com.squareup.okhttp.OkHttpClient;
import com.squareup.okhttp.Request;
import com.squareup.okhttp.Response;

public class MonitorService implements Service<MonitorService>, DiscoveryService {

    private final InjectedValue<ModelController> modelControllerValue = new InjectedValue<>();
    private final InjectedValue<ServerEnvironment> serverEnvironmentValue = new InjectedValue<>();
    private final InjectedValue<ControlledProcessStateService> processStateValue = new InjectedValue<>();
    private final InjectedValue<SocketBinding> httpSocketBindingValue = new InjectedValue<>();
    private final InjectedValue<SocketBinding> httpsSocketBindingValue = new InjectedValue<>();
    private final InjectedValue<OutboundSocketBinding> serverOutboundSocketBindingValue = new InjectedValue<>();

    private boolean started = false;

    private PropertyChangeListener serverStateListener;
    private ExecutorService managementClientExecutor;

    // the full configuration as declared in standalone.xml
    private MonitorServiceConfiguration configuration;

    // this is used to identify us to the Hawkular environment as a particular feed
    private ServerIdentifiers selfId;
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

    // our internal inventories for each monitored server
    private final Map<ManagedServer, DMRInventoryManager> dmrServerInventories = new HashMap<>();

    // this is a thread pool that requests newly discovered resources to be stored in inventory
    private ExecutorService discoveredResourcesStorageExecutor;

    @Override
    public MonitorService getValue() {
        return this;
    }

    /**
     * @return the context that can be used by others for storing ad-hoc monitoring data
     */
    public HawkularMonitorContext getHawkularMonitorContext() {
        return new HawkularMonitorContextImpl(metricStorageProxy, availStorageProxy, inventoryStorageProxy);
    }

    /**
     * Configures this service and its internals.
     *
     * @param config the configuration with all settings needed to start monitoring metrics
     */
    public void configure(MonitorServiceConfiguration config) {
        if (isMonitorServiceStarted()) {
            throw new IllegalStateException(
                    "Service is already started and cannot be reconfigured. Shut it down first.");
        }

        this.configuration = config;

        // the config has everything we need to build the http clients
        this.httpClientBuilder = new HttpClientBuilder(config);
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
        if (this.configuration.storageAdapter.serverOutboundSocketBindingRef != null) {
            bldr.addDependency(OutboundSocketBinding.OUTBOUND_SOCKET_BINDING_BASE_SERVICE_NAME
                    .append(this.configuration.storageAdapter.serverOutboundSocketBindingRef),
                    OutboundSocketBinding.class, serverOutboundSocketBindingValue);
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

        MsgLogger.LOG.infoStarting();

        // get our self identifiers
        ModelControllerClientFactory mccFactory = createLocalClientFactory();
        LocalDMREndpoint localDMREndpoint = new LocalDMREndpoint("_self", mccFactory);
        this.selfId = localDMREndpoint.getServerIdentifiers();

        // build the diagnostics object that will be used to track our own performance
        final MetricRegistry metricRegistry = new MetricRegistry();
        this.diagnostics = new DiagnosticsImpl(configuration.diagnostics, metricRegistry, selfId);

        // determine where our Hawkular server is
        // If the user gave us a URL explicitly, that overrides everything and we use it.
        // If no URL is configured, but we are given a server outbound socket binding name,
        // we use that to determine the remote Hawkular URL.
        // If neither URL nor output socket binding name is provided, we assume we are running
        // co-located with the Hawkular server and we use local bindings.
        if (this.configuration.storageAdapter.url == null) {
            try {
                String address;
                int port;

                if (this.configuration.storageAdapter.serverOutboundSocketBindingRef == null) {
                    // no URL or output socket binding - assume we are running co-located with server
                    SocketBinding socketBinding;
                    if (this.configuration.storageAdapter.useSSL) {
                        socketBinding = this.httpsSocketBindingValue.getValue();
                    } else {
                        socketBinding = this.httpSocketBindingValue.getValue();
                    }
                    address = socketBinding.getAddress().getHostAddress();
                    if (address.equals("0.0.0.0") || address.equals("::/128")) {
                        address = InetAddress.getLocalHost().getCanonicalHostName();
                    }
                    port = socketBinding.getAbsolutePort();
                } else {
                    OutboundSocketBinding serverBinding = this.serverOutboundSocketBindingValue.getValue();
                    address = serverBinding.getResolvedDestinationAddress().getHostAddress();
                    port = serverBinding.getDestinationPort();
                }
                String protocol = (this.configuration.storageAdapter.useSSL) ? "https" : "http";
                this.configuration.storageAdapter.url = String.format("%s://%s:%d", protocol, address, port);
            } catch (UnknownHostException uhe) {
                throw new IllegalArgumentException("Cannot determine Hawkular server host", uhe);
            }
        }

        MsgLogger.LOG.infoUsingServerSideUrl(this.configuration.storageAdapter.url);

        // if we are participating in a full Hawkular environment, we need to do some additional things:
        // 1. determine our tenant ID dynamically
        // 2. register our feed ID
        // 3. connect to the server's feed comm channel
        // 4. prepare the thread pool that will store discovered resources into inventory
        if (this.configuration.storageAdapter.type == StorageReportTo.HAWKULAR) {
            try {
                determineTenantId();
                registerFeed();
            } catch (Exception e) {
                MsgLogger.LOG.errorCannotDoAnythingWithoutFeed(e);
                return;
            }

            // try to connect to the server via command-gateway channel; if it fails, just log an error but keep going
            try {
                connectToCommandGatewayCommChannel();
            } catch (Exception e) {
                MsgLogger.LOG.errorCannotEstablishFeedComm(e);
            }

            // build the thread pool that will run jobs that store inventory
            final ThreadFactory factoryGenerator = ThreadFactoryGenerator.generateFactory(true,
                    "Hawkular-Monitor-Discovered-Resources-Storage");
            final ExecutorService threadPool = Executors.newFixedThreadPool(1, factoryGenerator);
            final String metricNamePrefix = DiagnosticsImpl.name(selfId, "inventory");
            this.discoveredResourcesStorageExecutor = new InstrumentedExecutorService(threadPool, metricRegistry,
                    metricNamePrefix);
        } else {
            if (this.configuration.storageAdapter.tenantId == null) {
                MsgLogger.LOG.errorMustHaveTenantIdConfigured();
                return;
            }
        }

        // start the storage adapter
        try {
            startStorageAdapter();
        } catch (Exception e) {
            MsgLogger.LOG.errorCannotStartStorageAdapter(e);
            return;
        }

        // build our inventory managers and find all the resources we need to monitor
        discoverAllResourcesForAllManagedServers();

        started = true;
    }

    /**
     * Stops this service. If the service is already stopped, this method is a no-op.
     */
    public void stopMonitorService() {
        if (!isMonitorServiceStarted()) {
            return; // we are already stopped
        }

        MsgLogger.LOG.infoStopping();

        // shutdown scheduler
        stopScheduler();

        // disconnect from the feed comm channel
        if (feedComm != null) {
            feedComm.disconnect();
            feedComm = null;
        }

        // stop storing things to inventory
        if (discoveredResourcesStorageExecutor != null) {
            discoveredResourcesStorageExecutor.shutdownNow();
            discoveredResourcesStorageExecutor = null;
        }

        // remove our inventories
        dmrServerInventories.clear();

        // stop diagnostic reporting and spit out a final diagnostics report
        if (diagnosticsReporter != null) {
            diagnosticsReporter.stop();
            if (configuration.diagnostics.enabled) {
                diagnosticsReporter.report();
            }
        }

        // cleanup the state listener
        if (serverStateListener != null) {
            processStateValue.getValue().removePropertyChangeListener(serverStateListener);
            serverStateListener = null;
        }

        started = false;
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
     * Reads in a data file into memory and returns its contents. Do NOT call this for very large files.
     *
     * @param filename the name of the file to read - its location is assumed to be under
     *                 the {@link #getDataDirectory() data directory}.
     * @return the full contents of the file
     * @throws FileNotFoundException if the file does not exist
     */
    private String slurpDataFile(String filename) throws FileNotFoundException {
        File dataFile = new File(getDataDirectory(), filename);
        FileInputStream dataFileInputStream = new FileInputStream(dataFile);
        String fileContents = Util.slurpStream(dataFileInputStream);
        return fileContents;
    }

    /**
     * Writes a data file to the {@link #getDataDirectory() data directory}.
     *
     * @param filename the name of the file - this is relative to under the data directory
     * @param fileContents what the contents of the file should be
     * @return the file that was written
     * @throws FileNotFoundException if the file could not be created
     */
    private File writeDataFile(String filename, String fileContents) throws FileNotFoundException {
        File dataFile = new File(getDataDirectory(), filename);
        FileOutputStream dataFileOutputStream = new FileOutputStream(dataFile);
        ByteArrayInputStream bais = new ByteArrayInputStream(fileContents.getBytes());
        Util.copyStream(bais, dataFileOutputStream, true);
        return dataFile;
    }

    /**
     * Create a factory that will create ModelControllerClient objects that talk
     * to the WildFly server we are running in.
     *
     * @return factory to create intra-VM clients
     */
    private ModelControllerClientFactory createLocalClientFactory() {
        ModelControllerClientFactory mccFactory = new ModelControllerClientFactory() {
            @Override
            public ModelControllerClient createClient() {
                return getManagementControllerClient();
            }
        };
        return mccFactory;
    }

    /**
     * Returns a client that can be used to talk to the management interface of the app server this
     * service is running in.
     *
     * Make sure you close this when you are done with it.
     *
     * @return client
     */
    private ModelControllerClient getManagementControllerClient() {
        ExecutorService executor = getManagementClientExecutor();
        return this.modelControllerValue.getValue().createClient(executor);
    }

    /**
     * Returns the thread pool to be used by the management clients (see {@link #getManagementControllerClient()}).
     *
     * @return thread pool
     */
    private ExecutorService getManagementClientExecutor() {
        if (managementClientExecutor == null) {
            final int numThreadsInPool = this.configuration.numDmrSchedulerThreads;
            final ThreadFactory threadFactory = ThreadFactoryGenerator.generateFactory(true,
                    "Hawkular-Monitor-MgmtClient");
            managementClientExecutor = Executors.newFixedThreadPool(numThreadsInPool, threadFactory);
        }

        return managementClientExecutor;
    }

    /**
     * Creates and starts the storage adapter that will be used to store our inventory data and monitoring data.
     *
     * @throws Exception if failed to start the storage adapter
     */
    private void startStorageAdapter() throws Exception {
        // determine what our backend storage should be and create its associated adapter
        switch (configuration.storageAdapter.type) {
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
                        + configuration.storageAdapter);
            }
        }

        this.storageAdapter.initialize(configuration.storageAdapter, diagnostics, selfId, httpClientBuilder);

        // provide our storage adapter to the proxies - allows external apps to use them to store its own data
        metricStorageProxy.setStorageAdapter(storageAdapter);
        availStorageProxy.setStorageAdapter(storageAdapter);
        inventoryStorageProxy.setStorageAdapter(storageAdapter);

        // determine where we are to store our own diagnostic reports
        switch (configuration.diagnostics.reportTo) {
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
                        .forRegistry(this.diagnostics.getMetricRegistry(), configuration.diagnostics, storageAdapter,
                                selfId)
                        .convertRatesTo(TimeUnit.SECONDS)
                        .convertDurationsTo(MILLISECONDS)
                        .build();
                break;
            }
            default: {
                throw new Exception("Invalid diagnostics type: " + configuration.diagnostics.reportTo);
            }
        }

        if (this.configuration.diagnostics.enabled) {
            diagnosticsReporter.start(this.configuration.diagnostics.interval,
                    this.configuration.diagnostics.timeUnits);
        }
    }

    /**
     * Builds the scheduler's configuraton and starts the scheduler.
     * Do NOT call this until you know all resources have been discovered
     * and the inventories have been built.
     *
     * @throws Exception
     */
    private void startScheduler() throws Exception {
        if (this.schedulerService != null) {
            return; // it has already been started
        }

        SchedulerConfiguration schedulerConfig = new SchedulerConfiguration();
        schedulerConfig.setDiagnosticsConfig(this.configuration.diagnostics);
        schedulerConfig.setStorageAdapterConfig(this.configuration.storageAdapter);
        schedulerConfig.setMetricSchedulerThreads(this.configuration.numMetricSchedulerThreads);
        schedulerConfig.setAvailSchedulerThreads(this.configuration.numAvailSchedulerThreads);
        schedulerConfig.setMetricDispatcherBufferSize(this.configuration.metricDispatcherBufferSize);
        schedulerConfig.setMetricDispatcherMaxBatchSize(this.configuration.metricDispatcherMaxBatchSize);
        schedulerConfig.setAvailDispatcherBufferSize(this.configuration.availDispatcherBufferSize);
        schedulerConfig.setAvailDispatcherMaxBatchSize(this.configuration.availDispatcherMaxBatchSize);

        // for all the resources we have in inventory, schedule their metric and avail collections
        for (DMRInventoryManager im : this.dmrServerInventories.values()) {
            scheduleDMRMetricAvailCollections(schedulerConfig, im);
        }

        this.schedulerService = new SchedulerService(
                schedulerConfig,
                this.selfId,
                this.diagnostics,
                this.storageAdapter,
                createLocalClientFactory(),
                this.httpClientBuilder);
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
     * Convienence method that stops the scheduler if its already running, and then starts it up.
     * @throws Exception if cannot start the scheduler
     */
    private void restartScheduler() throws Exception {
        stopScheduler();
        startScheduler();
    }

    /**
     * This prepares the given scheduler config with all the schedules needed to monitor all the resources
     * in the given inventory manager.
     * @param schedulerConfig scheduler configuration
     * @param im inventory manager
     */
    private void scheduleDMRMetricAvailCollections(SchedulerConfiguration schedulerConfig, DMRInventoryManager im) {
        BreadthFirstIterator<DMRResource, DefaultEdge> bIter = im.getResourceManager().getBreadthFirstIterator();
        while (bIter.hasNext()) {
            DMRResource resource = bIter.next();

            // schedule collections
            Collection<DMRMetricInstance> metricsToBeCollected = resource.getMetrics();
            for (DMRMetricInstance metricToBeCollected : metricsToBeCollected) {
                schedulerConfig.addMetricToBeCollected(resource.getEndpoint(), metricToBeCollected);
            }

            Collection<DMRAvailInstance> availsToBeCollected = resource.getAvails();
            for (DMRAvailInstance availToBeCollected : availsToBeCollected) {
                schedulerConfig.addAvailToBeChecked(resource.getEndpoint(), availToBeCollected);
            }
        }
    }

    @Override
    public Map<ManagedServer, DMRInventoryManager> getDmrServerInventories() {
        return Collections.unmodifiableMap(this.dmrServerInventories);
    }

    @Override
    public void discoverAllResourcesForAllManagedServers() {
        int resourcesDiscovered = 0;

        // there may be some old managed servers that we don't manage anymore - remove them now
        this.dmrServerInventories.keySet().retainAll(this.configuration.managedServersMap.values());

        // go through each configured managed server and discovery all resources in them
        for (ManagedServer managedServer : this.configuration.managedServersMap.values()) {
            if (!managedServer.isEnabled()) {
                MsgLogger.LOG.infoManagedServerDisabled(managedServer.getName().toString());
            } else {
                if (managedServer instanceof RemoteDMRManagedServer) {
                    RemoteDMRManagedServer dmrServer = (RemoteDMRManagedServer) managedServer;
                    DMREndpoint dmrEndpoint = new DMREndpoint(dmrServer.getName().toString(), dmrServer.getHost(),
                            dmrServer.getPort(), dmrServer.getUsername(), dmrServer.getPassword());
                    discoverResourcesForDMRManagedServer(managedServer, dmrEndpoint);
                } else if (managedServer instanceof LocalDMRManagedServer) {
                    DMREndpoint dmrEndpoint = new LocalDMREndpoint(managedServer.getName().toString(),
                            createLocalClientFactory());
                    discoverResourcesForDMRManagedServer(managedServer, dmrEndpoint);
                } else {
                    throw new IllegalArgumentException("An invalid managed server type was found. ["
                            + managedServer + "] Please report this bug.");
                }
                resourcesDiscovered += this.dmrServerInventories.get(managedServer).getResourceManager()
                        .getAllResources().size();
            }
        }

        MsgLogger.LOG.debugf("Full discovery scan found [%d] resources", resourcesDiscovered);

        // restart the scheduler - this will begin metric collections for our new inventory
        try {
            restartScheduler();
        } catch (Exception e) {
            MsgLogger.LOG.errorCannotInitializeScheduler(e);
        }

        return;
    }

    private VertexSetListener<DMRResource> getDMRListenerForChangedInventory(final DMRInventoryManager imOriginal) {
        VertexSetListener<DMRResource> dmrListener = null;

        // if we are participating in a full hawkular environment,
        // new resources and their metadata will be added to inventory
        if (MonitorService.this.configuration.storageAdapter.type == StorageReportTo.HAWKULAR) {
            dmrListener = new VertexSetListener<DMRResource>() {
                @Override
                public void vertexRemoved(GraphVertexChangeEvent<DMRResource> e) {
                }

                @Override
                public void vertexAdded(GraphVertexChangeEvent<DMRResource> e) {
                    final DMRResource resource = e.getVertex();
                    final DMRResourceType resourceType = resource.getResourceType();

                    if (imOriginal != null) {
                        DMRResource oldResource = imOriginal.getResourceManager().getResource(resource.getID());
                        if (oldResource != null) {
                            // we discovered a resource we had before. For now do nothing other than
                            // set its persisted flag since we assume it has already been or will be persisted
                            resource.setPersisted(true);
                            resourceType.setPersisted(true);
                            return;
                        }
                    }

                    discoveredResourcesStorageExecutor.execute(new Runnable() {
                        @Override
                        public void run() {
                            try {
                                MonitorService.this.inventoryStorageProxy.storeResourceType(resourceType);
                                MonitorService.this.inventoryStorageProxy.storeResource(resource);
                            } catch (Throwable t) {
                                MsgLogger.LOG.errorf(t, "Failed to store resource [%s]", resource);
                            }
                        }
                    });
                }
            };
        }

        return dmrListener;
    }

    private void discoverResourcesForDMRManagedServer(ManagedServer managedServer, DMREndpoint dmrEndpoint) {
        Collection<Name> resourceTypeSets = managedServer.getResourceTypeSets();
        DMRInventoryManager im = buildDMRInventoryManager(managedServer, dmrEndpoint, resourceTypeSets, this.feedId,
                this.configuration);
        DMRInventoryManager imOriginal = this.dmrServerInventories.put(managedServer, im);
        im.discoverResources(getDMRListenerForChangedInventory(imOriginal));
    }

    /**
     * Builds an DMR inventory manager with all metadata but with an empty set of resources.
     *
     * @param managedServer the managed server whose inventory will be stored in the returned manager
     * @param dmrEndpoint the endpoint used to connect to the managed server
     * @param dmrResourceTypeSets only resources of these types will be managed by the inventory manager
     * @param feedId our feed ID
     * @param monitorServiceConfig our full configuration
     *
     * @return the DMR inventory manager that was created
     */
    private DMRInventoryManager buildDMRInventoryManager(ManagedServer managedServer, DMREndpoint dmrEndpoint,
            Collection<Name> dmrResourceTypeSets, String feedId, MonitorServiceConfiguration monitorServiceConfig) {

        DMRMetadataManager metadataMgr = buildDMRMetadataManager(dmrResourceTypeSets, monitorServiceConfig);

        // Create our empty resource manager - this will be filled in during discovery with our resources
        ResourceManager<DMRResource> resourceManager = new ResourceManager<>();

        // determine the client to use to connect to the managed server
        ModelControllerClientFactory factory;
        if (dmrEndpoint instanceof LocalDMREndpoint) {
            factory = createLocalClientFactory();
        } else {
            factory = new ModelControllerClientFactoryImpl(dmrEndpoint);
        }

        DMRInventoryManager im;
        im = new DMRInventoryManager(feedId, metadataMgr, resourceManager, managedServer, dmrEndpoint, factory);
        return im;
    }

    /**
     * Given a collection of resource type set names for DMR resources, this will build a metadata manager
     * for all things (resource types, metric types, avail types) associated with those named type sets.
     *
     * @param dmrResourceTypeSets names of resource types to be used
     * @param monitorServiceConfig configuration that contains all types known to our subsystem service
     * @return metadata manager containing all metadata for the types in the named sets
     */
    private DMRMetadataManager buildDMRMetadataManager(Collection<Name> dmrResourceTypeSets,
            MonitorServiceConfiguration monitorServiceConfig) {

        // First build our metadata manager and its resource type manager, metric type manager, and avail type manager
        ResourceTypeManager<DMRResourceType, DMRResourceTypeSet> rtm;
        rtm = new ResourceTypeManager<>(monitorServiceConfig.dmrResourceTypeSetMap, dmrResourceTypeSets);

        // tell metric/avail managers what metric and avail types we need to use for the resource types
        MetricTypeManager<DMRMetricType, DMRMetricTypeSet> mtm = new MetricTypeManager<>();
        AvailTypeManager<DMRAvailType, DMRAvailTypeSet> atm = new AvailTypeManager<>();
        BreadthFirstIterator<DMRResourceType, DefaultEdge> resourceTypeIter = rtm.getBreadthFirstIterator();
        while (resourceTypeIter.hasNext()) {
            DMRResourceType type = resourceTypeIter.next();
            Collection<Name> metricSetsToUse = type.getMetricSets();
            Collection<Name> availSetsToUse = type.getAvailSets();
            mtm.addMetricTypes(monitorServiceConfig.dmrMetricTypeSetMap, metricSetsToUse);
            atm.addAvailTypes(monitorServiceConfig.dmrAvailTypeSetMap, availSetsToUse);
        }
        DMRMetadataManager mm = new DMRMetadataManager(rtm, mtm, atm);
        mm.populateMetricAndAvailTypesForAllResourceTypes();
        return mm;
    }

    /**
     * @return Determines what Hawkular tenant ID should be used and returns it.
     */
    private String determineTenantId() {
        if (configuration.storageAdapter.tenantId != null) {
            return configuration.storageAdapter.tenantId;
        }

        try {
            StringBuilder url = Util.getContextUrlString(configuration.storageAdapter.url,
                configuration.storageAdapter.accountsContext);
            url.append("personas/current");

            OkHttpClient httpclient = this.httpClientBuilder.getHttpClient();

            // TODO: next three lines are only temporary and should be deleted when inventory no longer needs this.
            // make the call to the inventory to pre-create the test environment and other assumed entities
            String tenantUrl = Util.getContextUrlString(configuration.storageAdapter.url,
                configuration.storageAdapter.inventoryContext).append("tenant").toString();
            httpclient.newCall(this.httpClientBuilder.buildJsonGetRequest(tenantUrl, null)).execute();

            Request request = this.httpClientBuilder.buildJsonGetRequest(url.toString(), null);
            Response httpResponse = httpclient.newCall(request).execute();

            if (!httpResponse.isSuccessful()) {
                throw new Exception("status-code=[" + httpResponse.code() + "], reason=["
                        + httpResponse.message() + "], url=[" + url + "]");
            }

            final String fromServer = Util.slurpStream(httpResponse.body().byteStream());
            // depending on accounts is probably overkill because of 1 REST call, so let's process the JSON via regex
            Matcher matcher = Pattern.compile("\"id\":\"(.*?)\"").matcher(fromServer);
            if (matcher.find()) {
                configuration.storageAdapter.tenantId = matcher.group(1);
            }
            MsgLogger.LOG.debugf("Tenant ID [%s]", configuration.storageAdapter.tenantId == null ? "unknown" :
                    configuration.storageAdapter.tenantId);
            return configuration.storageAdapter.tenantId;
        } catch (Throwable t) {
            throw new RuntimeException("Cannot get tenant ID", t);
        }
    }

    /**
     * Registers our feed with the Hawkular system.
     *
     * @throws Exception if failed to register feed
     */
    private void registerFeed() throws Exception {
        String desiredFeedId = this.selfId.getFullIdentifier();
        this.feedId = desiredFeedId; // assume we will get what we want

        try {
            File feedFile = new File(getDataDirectory(), "feedId.txt");
            try {
                String feedIdFromDataFile = slurpDataFile(feedFile.getName());
                feedIdFromDataFile = feedIdFromDataFile.trim();
                if (!desiredFeedId.equals(feedIdFromDataFile)) {
                    MsgLogger.LOG.warnf("Will use feed ID [%s] found in [%s];"
                            + " note that it is different than our desired feed ID [%s].",
                            feedIdFromDataFile, feedFile, desiredFeedId);
                    feedId = feedIdFromDataFile;
                }
                return; // we already have a feed ID - we can return now since there is nothing else to do
            } catch (FileNotFoundException e) {
                // probably just haven't been registered yet, keep going
            }

            // get the payload in JSON format
            String environmentId = "test";
            Feed.Blueprint feedPojo = new Feed.Blueprint(desiredFeedId, null);
            String jsonPayload = Util.toJson(feedPojo);

            // build the REST URL...
            // start with the protocol, host, and port, plus context
            StringBuilder url = Util.getContextUrlString(configuration.storageAdapter.url,
                    configuration.storageAdapter.inventoryContext);
            url = Util.convertToNonSecureUrl(url.toString());

            // the REST URL requires environment ID next in the path
            url.append(environmentId);

            // rest of the URL says we want the feeds API
            url.append("/feeds");

            // now send the REST request
            OkHttpClient httpclient = this.httpClientBuilder.getHttpClient();
            Request request = this.httpClientBuilder.buildJsonPostRequest(url.toString(), null, jsonPayload);
            Response httpResponse = httpclient.newCall(request).execute();

            // HTTP status of 201 means success; 409 means it already exists, anything else is an error
            if (httpResponse.code() != 201 && httpResponse.code() != 409) {
                throw new Exception("status-code=[" + httpResponse.code() + "], reason=["
                        + httpResponse.message() + "], url=[" + request.urlString() + "]");
            }

            // success - store our feed ID so we remember it the next time
            final String feedObjectFromServer = Util.slurpStream(httpResponse.body().byteStream());
            final Feed feed = Util.fromJson(feedObjectFromServer, Feed.class);
            if (desiredFeedId.equals(feed.getId())) {
                MsgLogger.LOG.infof("Feed ID registered [%s]", feed.getId());
            } else {
                MsgLogger.LOG.errorf("Server gave us a feed ID [%s] but we wanted [%s]", feed.getId(), desiredFeedId);
                // should we throw an error here or just use the feed ID we were given?
                MsgLogger.LOG.errorf("Using feed ID [%s]; make sure the agent doesn't lose its data file",
                        feed.getId());
            }

            this.feedId = feed.getId();
            writeDataFile(feedFile.getName(), feedId);
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
}
