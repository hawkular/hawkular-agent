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
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;

import org.apache.http.HttpResponse;
import org.apache.http.StatusLine;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.ContentType;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.HttpClientBuilder;
import org.hawkular.agent.monitor.api.HawkularMonitorContext;
import org.hawkular.agent.monitor.api.HawkularMonitorContextImpl;
import org.hawkular.agent.monitor.diagnostics.Diagnostics;
import org.hawkular.agent.monitor.diagnostics.DiagnosticsImpl;
import org.hawkular.agent.monitor.diagnostics.JBossLoggingReporter;
import org.hawkular.agent.monitor.diagnostics.JBossLoggingReporter.LoggingLevel;
import org.hawkular.agent.monitor.diagnostics.StorageReporter;
import org.hawkular.agent.monitor.extension.MonitorServiceConfiguration;
import org.hawkular.agent.monitor.extension.MonitorServiceConfiguration.StorageReportTo;
import org.hawkular.agent.monitor.inventory.AvailTypeManager;
import org.hawkular.agent.monitor.inventory.ID;
import org.hawkular.agent.monitor.inventory.ManagedServer;
import org.hawkular.agent.monitor.inventory.MetricTypeManager;
import org.hawkular.agent.monitor.inventory.Name;
import org.hawkular.agent.monitor.inventory.ResourceManager;
import org.hawkular.agent.monitor.inventory.ResourceTypeManager;
import org.hawkular.agent.monitor.inventory.dmr.DMRAvailInstance;
import org.hawkular.agent.monitor.inventory.dmr.DMRAvailType;
import org.hawkular.agent.monitor.inventory.dmr.DMRAvailTypeSet;
import org.hawkular.agent.monitor.inventory.dmr.DMRInventoryManager;
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
import org.hawkular.agent.monitor.scheduler.config.AvailDMRPropertyReference;
import org.hawkular.agent.monitor.scheduler.config.DMREndpoint;
import org.hawkular.agent.monitor.scheduler.config.DMRPropertyReference;
import org.hawkular.agent.monitor.scheduler.config.Interval;
import org.hawkular.agent.monitor.scheduler.config.LocalDMREndpoint;
import org.hawkular.agent.monitor.scheduler.config.SchedulerConfiguration;
import org.hawkular.agent.monitor.storage.AvailStorageProxy;
import org.hawkular.agent.monitor.storage.HawkularStorageAdapter;
import org.hawkular.agent.monitor.storage.InventoryStorageProxy;
import org.hawkular.agent.monitor.storage.MetricStorageProxy;
import org.hawkular.agent.monitor.storage.MetricsOnlyStorageAdapter;
import org.hawkular.agent.monitor.storage.StorageAdapter;
import org.hawkular.dmrclient.Address;
import org.hawkular.inventory.api.model.Feed;
import org.hawkular.inventory.api.model.Tenant;
import org.jboss.as.controller.ControlledProcessState;
import org.jboss.as.controller.ControlledProcessStateService;
import org.jboss.as.controller.ModelController;
import org.jboss.as.controller.client.ModelControllerClient;
import org.jboss.as.network.OutboundSocketBinding;
import org.jboss.as.network.SocketBinding;
import org.jboss.as.server.ServerEnvironment;
import org.jboss.as.server.ServerEnvironmentService;
import org.jboss.as.server.Services;
import org.jboss.dmr.ModelNode;
import org.jboss.dmr.ModelType;
import org.jboss.logging.Logger;
import org.jboss.msc.service.Service;
import org.jboss.msc.service.ServiceBuilder;
import org.jboss.msc.service.StartContext;
import org.jboss.msc.service.StartException;
import org.jboss.msc.service.StopContext;
import org.jboss.msc.value.InjectedValue;
import org.jgrapht.graph.DefaultEdge;
import org.jgrapht.traverse.BreadthFirstIterator;

import com.codahale.metrics.MetricRegistry;
import com.codahale.metrics.ScheduledReporter;

public class MonitorService implements Service<MonitorService> {

    private static final Logger LOG = Logger.getLogger(MonitorService.class);

    private final InjectedValue<ModelController> modelControllerValue = new InjectedValue<>();
    private final InjectedValue<ServerEnvironment> serverEnvironmentValue = new InjectedValue<>();
    private final InjectedValue<ControlledProcessStateService> processStateValue = new InjectedValue<>();
    private final InjectedValue<SocketBinding> httpSocketBindingValue = new InjectedValue<>();
    private final InjectedValue<OutboundSocketBinding> serverOutboundSocketBindingValue = new InjectedValue<>();

    private boolean started = false;

    private PropertyChangeListener serverStateListener;
    private ExecutorService managementClientExecutor;

    private MonitorServiceConfiguration configuration;

    // this is used to identify us to the Hawkular environment as a particular feed
    private ServerIdentifiers selfId;
    private String feedId;

    // used to report our own internal metrics
    private Diagnostics diagnostics;
    private ScheduledReporter diagnosticsReporter;

    // used to send monitored data for storage
    private StorageAdapter storageAdapter;

    // scheduled metric and avail collections
    private SchedulerService schedulerService;

    // proxies that are exposed via JNDI so external apps can emit their own inventory, metrics, and avail checks
    private final MetricStorageProxy metricStorageProxy = new MetricStorageProxy();
    private final AvailStorageProxy availStorageProxy = new AvailStorageProxy();
    private final InventoryStorageProxy inventoryStorageProxy = new InventoryStorageProxy();

    // our internal inventories for each monitored server
    private final Map<ManagedServer, DMRInventoryManager> dmrServerInventories = new HashMap<>();

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
        selfId = localDMREndpoint.getServerIdentifiers();

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
                    SocketBinding httpSocketBinding = httpSocketBindingValue.getValue();
                    address = httpSocketBinding.getAddress().getHostAddress();
                    if (address.equals("0.0.0.0") || address.equals("::/128")) {
                        address = InetAddress.getLocalHost().getCanonicalHostName();
                    }
                    port = httpSocketBinding.getAbsolutePort();
                } else {
                    OutboundSocketBinding serverBinding = serverOutboundSocketBindingValue.getValue();
                    address = serverBinding.getResolvedDestinationAddress().getHostAddress();
                    port = serverBinding.getDestinationPort();
                }
                // TODO: should we use https?
                this.configuration.storageAdapter.url = String.format("http://%s:%d", address, port);
            } catch (UnknownHostException uhe) {
                throw new IllegalArgumentException("Cannot determine Hawkular server host", uhe);
            }
        }
        MsgLogger.LOG.infoUsingServerSideUrl(this.configuration.storageAdapter.url);

        // if we are participating in a full Hawkular environment, register our feed ID
        if (configuration.storageAdapter.type == StorageReportTo.HAWKULAR) {
            try {
                determineTenantId();
                registerFeed();
            } catch (Exception e) {
                MsgLogger.LOG.errorCannotDoAnythingWithoutFeed(e);
                return;
            }
        } else {
            if (configuration.storageAdapter.tenantId == null) {
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

        // start the scheduler - this will begin metric collection
        try {
            startScheduler();
        } catch (Exception e) {
            MsgLogger.LOG.errorCannotInitializeScheduler(e);
            return;
        }

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
        if (schedulerService != null) {
            schedulerService.stop();
            schedulerService = null;
        }

        // stop diagnostic reporting and spit out a final diagnostics report
        if (diagnosticsReporter != null) {
            this.diagnosticsReporter.stop();
            if (this.configuration.diagnostics.enabled) {
                this.diagnosticsReporter.report();
            }
        }

        // cleanup the state listener
        if (serverStateListener != null) {
            processStateValue.getValue().removePropertyChangeListener(serverStateListener);
            serverStateListener = null;
        }

        started = false;
    }

    private File getDataDirectory() {
        File dataDir = new File(this.serverEnvironmentValue.getValue().getServerDataDir(), "hawkular-agent");
        dataDir.mkdirs();
        return dataDir;
    }

    private String slurpDataFile(String filename) throws FileNotFoundException {
        File dataFile = new File(getDataDirectory(), filename);
        FileInputStream dataFileInputStream = new FileInputStream(dataFile);
        String fileContents = Util.slurpStream(dataFileInputStream);
        return fileContents;
    }

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

    private void startStorageAdapter() throws Exception {
        // build the diagnostics object that will be used to track our own performance
        final MetricRegistry metricRegistry = new MetricRegistry();
        this.diagnostics = new DiagnosticsImpl(configuration.diagnostics, metricRegistry, selfId);

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

        this.storageAdapter.setStorageAdapterConfiguration(configuration.storageAdapter);
        this.storageAdapter.setDiagnostics(diagnostics);
        this.storageAdapter.setSelfIdentifiers(selfId);

        // provide our storage adapter to the proxies - allows external apps to use them to store its own data
        metricStorageProxy.setStorageAdapter(storageAdapter);
        availStorageProxy.setStorageAdapter(storageAdapter);
        inventoryStorageProxy.setStorageAdapter(storageAdapter);

        // determine where we are to store our own diagnostic reports
        switch (configuration.diagnostics.reportTo) {
            case LOG: {
                this.diagnosticsReporter = JBossLoggingReporter.forRegistry(metricRegistry)
                        .convertRatesTo(TimeUnit.SECONDS)
                        .convertDurationsTo(MILLISECONDS)
                        .outputTo(Logger.getLogger(getClass()))
                        .withLoggingLevel(LoggingLevel.DEBUG)
                        .build();
                break;
            }
            case STORAGE: {
                this.diagnosticsReporter = StorageReporter
                        .forRegistry(metricRegistry, configuration.diagnostics, storageAdapter, selfId)
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

    private void startScheduler() throws Exception {
        SchedulerConfiguration schedulerConfig = prepareSchedulerConfig();
        schedulerService = new SchedulerService(schedulerConfig, selfId, diagnostics, storageAdapter,
                createLocalClientFactory());

        // if we are participating in a full hawkular environment, add resource and its metadata to inventory now
        if (this.configuration.storageAdapter.type == MonitorServiceConfiguration.StorageReportTo.HAWKULAR) {

            ResourceManager<DMRResource> resourceManager;
            BreadthFirstIterator<DMRResource, DefaultEdge> bIter;

            try {
                for (DMRInventoryManager im : this.dmrServerInventories.values()) {
                    resourceManager = im.getResourceManager();
                    bIter = resourceManager.getBreadthFirstIterator();
                    while (bIter.hasNext()) {
                        // TODO store resource and resource type data to inventory
                        DMRResource resource = bIter.next();
                        inventoryStorageProxy.storeResourceType(resource.getResourceType());
                        inventoryStorageProxy.storeResource(resource);
                    }
                }
            } catch (Throwable t) {
                // TODO for now, just stop what we were doing and whatever we have in inventory is "good enough"
                // for prototyping, this is good enough, but we'll need better handling later
                LOG.errorf(t, "Failed to completely add our inventory - but we will keep going with partial inventory");
            }
        }

        // now we can begin collecting metrics
        schedulerService.start();
    }

    private SchedulerConfiguration prepareSchedulerConfig() {
        SchedulerConfiguration schedulerConfig = new SchedulerConfiguration();
        schedulerConfig.setDiagnosticsConfig(this.configuration.diagnostics);
        schedulerConfig.setStorageAdapterConfig(this.configuration.storageAdapter);
        schedulerConfig.setMetricSchedulerThreads(this.configuration.numMetricSchedulerThreads);
        schedulerConfig.setAvailSchedulerThreads(this.configuration.numAvailSchedulerThreads);
        schedulerConfig.setMetricDispatcherBufferSize(this.configuration.metricDispatcherBufferSize);
        schedulerConfig.setMetricDispatcherMaxBatchSize(this.configuration.metricDispatcherMaxBatchSize);
        schedulerConfig.setAvailDispatcherBufferSize(this.configuration.availDispatcherBufferSize);
        schedulerConfig.setAvailDispatcherMaxBatchSize(this.configuration.availDispatcherMaxBatchSize);

        // process each managed server
        for (ManagedServer managedServer : this.configuration.managedServersMap.values()) {
            if (!managedServer.isEnabled()) {
                MsgLogger.LOG.infoManagedServerDisabled(managedServer.getName().toString());
            } else {
                Collection<Name> resourceTypeSets = managedServer.getResourceTypeSets();

                if (managedServer instanceof RemoteDMRManagedServer) {
                    RemoteDMRManagedServer dmrServer = (RemoteDMRManagedServer) managedServer;
                    DMREndpoint dmrEndpoint = new DMREndpoint(dmrServer.getName().toString(), dmrServer.getHost(),
                            dmrServer.getPort(), dmrServer.getUsername(), dmrServer.getPassword());
                    addDMRResources(schedulerConfig, managedServer, dmrEndpoint, resourceTypeSets);
                } else if (managedServer instanceof LocalDMRManagedServer) {
                    LocalDMRManagedServer dmrServer = (LocalDMRManagedServer) managedServer;
                    LocalDMREndpoint dmrEndpoint = new LocalDMREndpoint(dmrServer.getName().toString(),
                            createLocalClientFactory());
                    addDMRResources(schedulerConfig, managedServer, dmrEndpoint, resourceTypeSets);
                } else {
                    throw new IllegalArgumentException("An invalid managed server type was found. ["
                            + managedServer
                            + "] Please report this bug.");
                }
            }
        }

        return schedulerConfig;
    }

    private void addDMRResources(SchedulerConfiguration schedulerConfig, ManagedServer managedServer,
            DMREndpoint dmrEndpoint, Collection<Name> dmrResourceTypeSets) {

        // determine the client to use to connect to the managed server
        ModelControllerClientFactory factory;
        if (dmrEndpoint instanceof LocalDMREndpoint) {
            factory = createLocalClientFactory();
        } else {
            factory = new ModelControllerClientFactoryImpl(dmrEndpoint);
        }

        // Build our inventory manager
        // First build our resource type manager.
        ResourceTypeManager<DMRResourceType, DMRResourceTypeSet> rtm;
        rtm = new ResourceTypeManager<>(this.configuration.dmrResourceTypeSetMap, dmrResourceTypeSets);

        // Now tell metric/avail managers what metric and avail types we need to use for the resource types
        MetricTypeManager<DMRMetricType, DMRMetricTypeSet> mtm = new MetricTypeManager<>();
        AvailTypeManager<DMRAvailType, DMRAvailTypeSet> atm = new AvailTypeManager<>();
        BreadthFirstIterator<DMRResourceType, DefaultEdge> resourceTypeIter = rtm.getBreadthFirstIterator();
        while (resourceTypeIter.hasNext()) {
            DMRResourceType type = resourceTypeIter.next();
            Collection<Name> metricSetsToUse = type.getMetricSets();
            Collection<Name> availSetsToUse = type.getAvailSets();
            mtm.addMetricTypes(this.configuration.dmrMetricTypeSetMap, metricSetsToUse);
            atm.addAvailTypes(this.configuration.dmrAvailTypeSetMap, availSetsToUse);
        }

        // Create our empty resource manager - this will be filled in during discovery with our resources
        ResourceManager<DMRResource> resourceManager = new ResourceManager<>();

        // Now we can build our inventory manager and discover our resources
        DMRInventoryManager im;
        im = new DMRInventoryManager(this.feedId, rtm, mtm, atm, resourceManager, managedServer, dmrEndpoint, factory);
        this.dmrServerInventories.put(managedServer, im);
        im.discoverResources();

        // now that we have our resources discovered, we need to do the following:
        // - finish fleshing our the resource by adding their metrics and avail checks
        // - add the resource and its metadata to inventory if applicable
        // - schedule the metric collections and avail checks
        BreadthFirstIterator<DMRResource, DefaultEdge> bIter = resourceManager.getBreadthFirstIterator();
        while (bIter.hasNext()) {
            DMRResource resource = bIter.next();

            // flesh out the resource by adding its metrics and avails
            addDMRMetricsAndAvails(resource, im);

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

    private void addDMRMetricsAndAvails(DMRResource resource, DMRInventoryManager im) {

        im.populateMetricAndAvailTypesForResourceType(resource.getResourceType());

        for (DMRMetricType metricType : resource.getResourceType().getMetricTypes()) {
            Interval interval = new Interval(metricType.getInterval(), metricType.getTimeUnits());
            Address relativeAddress = Address.parse(metricType.getPath());
            Address fullAddress = getFullAddressOfChild(resource, relativeAddress);
            if (fullAddress != null) {
                DMRPropertyReference prop = new DMRPropertyReference(fullAddress, metricType.getAttribute(), interval);
                ID id = new ID(String.format("MI~R~%s~MT~%s", resource.getID(), metricType.getName()));
                Name name = metricType.getName();
                DMRMetricInstance metricInstance = new DMRMetricInstance(id, name, resource, metricType, prop);
                resource.getMetrics().add(metricInstance);
            }
        }

        for (DMRAvailType availType : resource.getResourceType().getAvailTypes()) {
            Interval interval = new Interval(availType.getInterval(), availType.getTimeUnits());
            Address relativeAddress = Address.parse(availType.getPath());
            Address fullAddress = getFullAddressOfChild(resource, relativeAddress);
            if (fullAddress != null) {
                AvailDMRPropertyReference prop = new AvailDMRPropertyReference(fullAddress, availType.getAttribute(),
                        interval, availType.getUpRegex());
                ID id = new ID(String.format("AI~R~%s~AT~%s", resource.getID(), availType.getName()));
                Name name = availType.getName();
                DMRAvailInstance availInstance = new DMRAvailInstance(id, name, resource, availType, prop);
                resource.getAvails().add(availInstance);
            }
        }
    }

    private Address getFullAddressOfChild(DMRResource parentResource, Address childRelativePath) {
        // Some metrics/avails are collected from child resources. But sometimes resources
        // don't have those child resources (e.g. ear deployments don't have an undertow subsystem).
        // This means those metrics/avails cannot be collected (i.e. they are optional).
        // We don't want to fail with errors in this case; we just want to ignore those metrics/avails
        // since they don't exist.
        // If the child does exist (by examining the parent resource's model), then this method
        // will return the full address to that child resource.

        Address fullAddress = null;
        if (childRelativePath.isRoot()) {
            fullAddress = parentResource.getAddress(); // there really is no child; it is the resource itself
        } else {
            boolean childResourceExists = false;
            String[] addressParts = childRelativePath.toAddressParts();
            if (addressParts.length > 2) {
                // we didn't query the parent's model for recursive data - so we only know direct children.
                // if a metric/avail gets data from grandchildren or deeper, we don't know if it exists,
                // so just assume it does.
                childResourceExists = true;
                LOG.tracef("Cannot test long child path [%s] under resource [%s] "
                        + "for existence so it will be assumed to exist", childRelativePath, parentResource);
            } else {
                ModelNode haystackNode = parentResource.getModelNode().get(addressParts[0]);
                if (haystackNode.getType() != ModelType.UNDEFINED) {
                    final List<ModelNode> haystackList = haystackNode.asList();
                    for (ModelNode needleNode : haystackList) {
                        if (needleNode.has(addressParts[1])) {
                            childResourceExists = true;
                            break;
                        }
                    }
                }
            }
            if (childResourceExists) {
                fullAddress = parentResource.getAddress().clone().add(childRelativePath);
            }
        }
        return fullAddress;
    }

    private String determineTenantId() {
        if (configuration.storageAdapter.tenantId != null) {
            return configuration.storageAdapter.tenantId;
        }

        HttpGet request = null;

        try {
            StringBuilder url = Util.getContextUrlString(configuration.storageAdapter.url,
                    configuration.storageAdapter.inventoryContext);
            url.append("tenant");
            HttpClient httpclient = HttpClientBuilder.create().build();
            request = new HttpGet(url.toString());

            // make sure we are authenticated
            // http://en.wikipedia.org/wiki/Basic_access_authentication#Client_side
            String base64Encode = Util.base64Encode(configuration.storageAdapter.username + ":"
                    + configuration.storageAdapter.password);
            request.setHeader("Authorization", "Basic " + base64Encode);

            HttpResponse httpResponse = httpclient.execute(request);
            StatusLine statusLine = httpResponse.getStatusLine();

            if (statusLine.getStatusCode() != 200) {
                throw new Exception("status-code=[" + statusLine.getStatusCode() + "], reason=["
                        + statusLine.getReasonPhrase() + "], url=[" + request.getURI() + "]");
            }

            final String fromServer = Util.slurpStream(httpResponse.getEntity().getContent());
            final Tenant tenant = Util.fromJson(fromServer, Tenant.class);
            LOG.infof("Tenant ID [%s]", tenant.getId());

            configuration.storageAdapter.tenantId = tenant.getId();
            return configuration.storageAdapter.tenantId;
        } catch (Throwable t) {
            throw new RuntimeException("Cannot get tenant ID", t);
        } finally {
            if (request != null) {
                request.releaseConnection();
            }
        }
    }

    private void registerFeed() throws Exception {
        HttpPost request = null;
        String desiredFeedId = this.selfId.getFullIdentifier();
        this.feedId = desiredFeedId; // assume we will get what we want

        try {
            File feedFile = new File(getDataDirectory(), "feedId.txt");
            try {
                String feedIdFromDataFile = slurpDataFile(feedFile.getName());
                feedIdFromDataFile = feedIdFromDataFile.trim();
                if (!desiredFeedId.equals(feedIdFromDataFile)) {
                    LOG.warnf("Will use feed ID [%s] found in [%s];"
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

            // the REST URL requires environment ID next in the path
            url.append(environmentId);

            // rest of the URL says we want the feeds API
            url.append("/feeds");

            // now send the REST request
            HttpClient httpclient = HttpClientBuilder.create().build();
            request = new HttpPost(url.toString());
            request.setEntity(new StringEntity(jsonPayload, ContentType.APPLICATION_JSON));

            // make sure we are authenticated
            // http://en.wikipedia.org/wiki/Basic_access_authentication#Client_side
            String base64Encode = Util.base64Encode(configuration.storageAdapter.username + ":"
                    + configuration.storageAdapter.password);
            request.setHeader("Authorization", "Basic " + base64Encode);
            request.setHeader("Accept", "application/json");

            HttpResponse httpResponse = httpclient.execute(request);
            StatusLine statusLine = httpResponse.getStatusLine();

            // HTTP status of 201 means success; 409 means we already created the feed, anything else is an error
            if (statusLine.getStatusCode() != 201 && statusLine.getStatusCode() != 409) {
                throw new Exception("status-code=[" + statusLine.getStatusCode() + "], reason=["
                        + statusLine.getReasonPhrase() + "], url=[" + request.getURI() + "]");
            }

            // success - store our feed ID so we remember it the next time
            final String feedObjectFromServer = Util.slurpStream(httpResponse.getEntity().getContent());
            final Feed feed = Util.fromJson(feedObjectFromServer, Feed.class);
            if (desiredFeedId.equals(feed.getId())) {
                LOG.infof("Feed ID registered [%s]", feed.getId());
            } else {
                LOG.errorf("Server gave us a feed ID [%s] but we wanted [%s]", feed.getId(), desiredFeedId);
                // should we throw an error here or just use the feed ID we were given?
                LOG.errorf("Using feed ID [%s]; make sure the agent doesn't lose its data file", feed.getId());
            }

            this.feedId = feed.getId();
            writeDataFile(feedFile.getName(), feedId);
        } catch (Throwable t) {
            throw new Exception(String.format("Cannot create feed [%s]", desiredFeedId), t);
        } finally {
            if (request != null) {
                request.releaseConnection();
            }
        }
    }
}
