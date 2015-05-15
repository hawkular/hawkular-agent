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
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;

import org.hawkular.agent.monitor.api.HawkularMonitorContext;
import org.hawkular.agent.monitor.api.HawkularMonitorContextImpl;
import org.hawkular.agent.monitor.extension.MonitorServiceConfiguration;
import org.hawkular.agent.monitor.extension.MonitorServiceConfiguration.AvailDMR;
import org.hawkular.agent.monitor.extension.MonitorServiceConfiguration.AvailSetDMR;
import org.hawkular.agent.monitor.extension.MonitorServiceConfiguration.LocalDMRManagedServer;
import org.hawkular.agent.monitor.extension.MonitorServiceConfiguration.ManagedServer;
import org.hawkular.agent.monitor.extension.MonitorServiceConfiguration.MetricDMR;
import org.hawkular.agent.monitor.extension.MonitorServiceConfiguration.MetricSetDMR;
import org.hawkular.agent.monitor.extension.MonitorServiceConfiguration.RemoteDMRManagedServer;
import org.hawkular.agent.monitor.extension.MonitorServiceConfiguration.ResourceTypeDMR;
import org.hawkular.agent.monitor.extension.MonitorServiceConfiguration.ResourceTypeSetDMR;
import org.hawkular.agent.monitor.log.MsgLogger;
import org.hawkular.agent.monitor.scheduler.ModelControllerClientFactory;
import org.hawkular.agent.monitor.scheduler.SchedulerService;
import org.hawkular.agent.monitor.scheduler.config.AvailDMRPropertyReference;
import org.hawkular.agent.monitor.scheduler.config.DMREndpoint;
import org.hawkular.agent.monitor.scheduler.config.DMRPropertyReference;
import org.hawkular.agent.monitor.scheduler.config.Interval;
import org.hawkular.agent.monitor.scheduler.config.LocalDMREndpoint;
import org.hawkular.agent.monitor.scheduler.config.SchedulerConfiguration;
import org.hawkular.agent.monitor.storage.AvailStorageProxy;
import org.hawkular.agent.monitor.storage.MetricStorageProxy;
import org.jboss.as.controller.ControlledProcessState;
import org.jboss.as.controller.ControlledProcessStateService;
import org.jboss.as.controller.ModelController;
import org.jboss.as.controller.client.ModelControllerClient;
import org.jboss.as.server.ServerEnvironment;
import org.jboss.as.server.ServerEnvironmentService;
import org.jboss.as.server.Services;
import org.jboss.msc.service.Service;
import org.jboss.msc.service.ServiceBuilder;
import org.jboss.msc.service.StartContext;
import org.jboss.msc.service.StartException;
import org.jboss.msc.service.StopContext;
import org.jboss.msc.value.InjectedValue;

public class MonitorService implements Service<MonitorService> {

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

    private void prepareSchedulerConfig() {
        this.schedulerConfig = new SchedulerConfiguration();
        schedulerConfig.setDiagnosticsConfig(this.configuration.diagnostics);
        schedulerConfig.setStorageAdapterConfig(this.configuration.storageAdapter);
        schedulerConfig.setMetricSchedulerThreads(this.configuration.numMetricSchedulerThreads);
        schedulerConfig.setAvailSchedulerThreads(this.configuration.numAvailSchedulerThreads);
        schedulerConfig.setManagedServers(this.configuration.managedServersMap);

        // for each managed server, add their metrics and avails
        for (ManagedServer managedServer : this.configuration.managedServersMap.values()) {
            if (!managedServer.enabled) {
                MsgLogger.LOG.infoManagedServerDisabled(managedServer.name);
            } else {
                List<String> resourceTypeSets = managedServer.resourceTypeSets;

                if (managedServer instanceof RemoteDMRManagedServer) {
                    RemoteDMRManagedServer dmrServer = (RemoteDMRManagedServer) managedServer;
                    DMREndpoint dmrEndpoint = new DMREndpoint(dmrServer.name, dmrServer.host, dmrServer.port,
                            dmrServer.username, dmrServer.password);
                    addDMRResources(managedServer, dmrEndpoint, resourceTypeSets);
                } else if (managedServer instanceof LocalDMRManagedServer) {
                    LocalDMRManagedServer dmrServer = (LocalDMRManagedServer) managedServer;
                    LocalDMREndpoint dmrEndpoint = new LocalDMREndpoint(dmrServer.name, createLocalClientFactory());
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
            List<String> dmrResourceTypeSets) {
        for (String resourceTypeSetName : dmrResourceTypeSets) {
            ResourceTypeSetDMR resourceTypeSet = this.configuration.resourceTypeSetDmrMap.get(resourceTypeSetName);
            if (resourceTypeSet != null) {
                if (resourceTypeSet.enabled) {
                    for (ResourceTypeDMR resourceType : resourceTypeSet.resourceTypeDmrMap.values()) {
                        //String a = resourceType.path;
                    }
                }
            } else {
                MsgLogger.LOG.warnResourceTypeSetDoesNotExist(managedServer.name, resourceTypeSetName);
            }
        }
    }

    private void addDMRMetricsAndAvails(ManagedServer managedServer, DMREndpoint dmrEndpoint,
            List<String> dmrMetricSets, List<String> dmrAvailSets) {

        for (String metricSetName : dmrMetricSets) {
            MetricSetDMR metricSet = this.configuration.metricSetDmrMap.get(metricSetName);
            if (metricSet != null) {
                if (metricSet.enabled) {
                    for (MetricDMR metric : metricSet.metricDmrMap.values()) {
                        Interval interval = new Interval(metric.interval, metric.timeUnits);
                        DMRPropertyReference ref = new DMRPropertyReference(metric.path, metric.attribute,
                                interval);
                        schedulerConfig.addMetricToBeCollected(dmrEndpoint, ref);
                    }
                }
            } else {
                MsgLogger.LOG.warnMetricSetDoesNotExist(managedServer.name, metricSetName);
            }
        }

        for (String availSetName : dmrAvailSets) {
            AvailSetDMR availSet = this.configuration.availSetDmrMap.get(availSetName);
            if (availSet != null) {
                if (availSet.enabled) {
                    for (AvailDMR avail : availSet.availDmrMap.values()) {
                        Interval interval = new Interval(avail.interval, avail.timeUnits);
                        AvailDMRPropertyReference ref = new AvailDMRPropertyReference(avail.path,
                                avail.attribute, interval, avail.upRegex);
                        schedulerConfig.addAvailToBeChecked(dmrEndpoint, ref);
                    }
                }
            } else {
                MsgLogger.LOG.warnAvailSetDoesNotExist(managedServer.name, availSetName);
            }
        }
    }

    private void startScheduler() {
        ModelControllerClientFactory mccFactory = createLocalClientFactory();
        LocalDMREndpoint localDMREndpoint = new LocalDMREndpoint("_self", mccFactory);
        ServerIdentifiers id = localDMREndpoint.getServerIdentifiers();
        schedulerService = new SchedulerService(schedulerConfig, id, metricStorageProxy, availStorageProxy,
                createLocalClientFactory());
        schedulerService.start();
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
}
