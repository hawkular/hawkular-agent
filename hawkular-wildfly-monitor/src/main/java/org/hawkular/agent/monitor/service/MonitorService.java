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

import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;

import org.hawkular.agent.monitor.api.HawkularMonitorContext;
import org.hawkular.agent.monitor.api.HawkularMonitorContextImpl;
import org.hawkular.agent.monitor.extension.MonitorServiceConfiguration;
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
import org.hawkular.agent.monitor.scheduler.config.SchedulerConfiguration.StorageReportTo;
import org.hawkular.agent.monitor.storage.AvailStorageProxy;
import org.hawkular.agent.monitor.storage.MetricStorageProxy;
import org.hawkular.dmrclient.Address;
import org.jboss.as.controller.ControlledProcessState;
import org.jboss.as.controller.ControlledProcessStateService;
import org.jboss.as.controller.ModelController;
import org.jboss.as.controller.client.ModelControllerClient;
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

public class MonitorService implements Service<MonitorService> {

    private static final Logger LOG = Logger.getLogger(MonitorService.class);

    private final InjectedValue<ModelController> modelControllerValue = new InjectedValue<>();
    private final InjectedValue<ServerEnvironment> serverEnvironmentValue = new InjectedValue<>();
    private final InjectedValue<ControlledProcessStateService> processStateValue = new InjectedValue<>();

    private boolean started = false;

    private PropertyChangeListener serverStateListener;
    private ExecutorService managementClientExecutor;

    private MonitorServiceConfiguration configuration;

    private SchedulerConfiguration schedulerConfig;
    private SchedulerService schedulerService;

    private final MetricStorageProxy metricStorageProxy = new MetricStorageProxy();
    private final AvailStorageProxy availStorageProxy = new AvailStorageProxy();

    @Override
    public MonitorService getValue() {
        return this;
    }

    /**
     * @return the context that can be used by others for storing ad-hoc monitoring data
     */
    public HawkularMonitorContext getHawkularMonitorContext() {
        return new HawkularMonitorContextImpl(metricStorageProxy, availStorageProxy);
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
        prepareSchedulerConfig();
        startScheduler();
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

        // cleanup the state listener
        if (serverStateListener != null) {
            processStateValue.getValue().removePropertyChangeListener(serverStateListener);
            serverStateListener = null;
        }

        started = false;
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

    private void startScheduler() {
        ModelControllerClientFactory mccFactory = createLocalClientFactory();
        LocalDMREndpoint localDMREndpoint = new LocalDMREndpoint("_self", mccFactory);
        ServerIdentifiers id = localDMREndpoint.getServerIdentifiers();
        schedulerService = new SchedulerService(schedulerConfig, id, metricStorageProxy, availStorageProxy,
                createLocalClientFactory());
        schedulerService.start();
    }

    private void prepareSchedulerConfig() {
        this.schedulerConfig = new SchedulerConfiguration();
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
                    addDMRResources(managedServer, dmrEndpoint, resourceTypeSets);
                } else if (managedServer instanceof LocalDMRManagedServer) {
                    LocalDMRManagedServer dmrServer = (LocalDMRManagedServer) managedServer;
                    LocalDMREndpoint dmrEndpoint = new LocalDMREndpoint(dmrServer.getName().toString(),
                            createLocalClientFactory());
                    addDMRResources(managedServer, dmrEndpoint, resourceTypeSets);
                } else {
                    throw new IllegalArgumentException("An invalid managed server type was found. ["
                            + managedServer
                            + "] Please report this bug.");
                }
            }
        }
    }

    private void addDMRResources(ManagedServer managedServer, DMREndpoint dmrEndpoint,
            Collection<Name> dmrResourceTypeSets) {

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
        im = new DMRInventoryManager(rtm, mtm, atm, resourceManager, managedServer, dmrEndpoint, factory);
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

            // if we are participating in a full hawkular environment, add resource and its metadata to inventory
            if (this.configuration.storageAdapter.type == StorageReportTo.HAWKULAR) {
                DMRResourceType resourceType = resource.getResourceType();
                Collection<DMRMetricType> dmrMetricSets = new HashSet<>();
                Collection<DMRAvailType> dmrAvailSets = new HashSet<>();
                im.retrieveMetricAndAvailTypesForResourceType(resource.getResourceType(), dmrMetricSets, dmrAvailSets);
                LOG.errorf("Inventorying resource type [%s], metricTypes [%s], availTypes=[%s], "
                        + "resource [%s], metrics [%s], avails [%s]", resourceType, dmrMetricSets, dmrAvailSets,
                        resource, resource.getMetrics(), resource.getAvails());
            }

            // schedule collections
            Collection<DMRMetricInstance> metricsToBeCollected = resource.getMetrics();
            for (DMRMetricInstance metricToBeCollected : metricsToBeCollected) {
                schedulerConfig.addMetricToBeCollected(resource.getEndpoint(), metricToBeCollected.getProperty());
            }

            Collection<DMRAvailInstance> availsToBeCollected = resource.getAvails();
            for (DMRAvailInstance availToBeCollected : availsToBeCollected) {
                schedulerConfig.addAvailToBeChecked(resource.getEndpoint(), availToBeCollected.getProperty());
            }
        }
    }

    private void addDMRMetricsAndAvails(DMRResource resource, DMRInventoryManager im) {

        Collection<DMRMetricType> dmrMetricTypes = new HashSet<>();
        Collection<DMRAvailType> dmrAvailTypes = new HashSet<>();
        im.retrieveMetricAndAvailTypesForResourceType(resource.getResourceType(), dmrMetricTypes, dmrAvailTypes);

        for (DMRMetricType metricType : dmrMetricTypes) {
            Interval interval = new Interval(metricType.getInterval(), metricType.getTimeUnits());
            Address relativeAddress = Address.parse(metricType.getPath());
            Address fullAddress = getFullAddressOfChild(resource, relativeAddress);
            if (fullAddress != null) {
                DMRPropertyReference prop = new DMRPropertyReference(fullAddress, metricType.getAttribute(), interval);
                DMRMetricInstance metricInstance = new DMRMetricInstance(String.format("%s:M:%s", resource.getName(),
                        metricType.getName()), metricType, prop);
                resource.getMetrics().add(metricInstance);
            }
        }

        for (DMRAvailType availType : dmrAvailTypes) {
            Interval interval = new Interval(availType.getInterval(), availType.getTimeUnits());
            Address relativeAddress = Address.parse(availType.getPath());
            Address fullAddress = getFullAddressOfChild(resource, relativeAddress);
            if (fullAddress != null) {
                AvailDMRPropertyReference prop = new AvailDMRPropertyReference(fullAddress, availType.getAttribute(),
                        interval, availType.getUpRegex());
                DMRAvailInstance availInstance = new DMRAvailInstance(String.format("%s:A:%s", resource.getName(),
                        availType.getName()), availType, prop);
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
}
