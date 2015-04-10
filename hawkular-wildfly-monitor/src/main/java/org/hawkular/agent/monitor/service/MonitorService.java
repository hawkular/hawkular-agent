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
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;

import org.hawkular.agent.monitor.api.HawkularMonitorContext;
import org.hawkular.agent.monitor.api.HawkularMonitorContextImpl;
import org.hawkular.agent.monitor.extension.MonitorServiceConfiguration;
import org.hawkular.agent.monitor.extension.MonitorServiceConfiguration.AvailDMR;
import org.hawkular.agent.monitor.extension.MonitorServiceConfiguration.AvailSetDMR;
import org.hawkular.agent.monitor.extension.MonitorServiceConfiguration.MetricDMR;
import org.hawkular.agent.monitor.extension.MonitorServiceConfiguration.MetricSetDMR;
import org.hawkular.agent.monitor.log.MsgLogger;
import org.hawkular.agent.monitor.scheduler.ModelControllerClientFactory;
import org.hawkular.agent.monitor.scheduler.SchedulerService;
import org.hawkular.agent.monitor.scheduler.config.AvailDMRPropertyReference;
import org.hawkular.agent.monitor.scheduler.config.DMRPropertyReference;
import org.hawkular.agent.monitor.scheduler.config.Interval;
import org.hawkular.agent.monitor.scheduler.config.SchedulerConfiguration;
import org.hawkular.agent.monitor.storage.AvailStorageProxy;
import org.hawkular.agent.monitor.storage.MetricStorageProxy;
import org.hawkular.dmrclient.Address;
import org.hawkular.dmrclient.CoreJBossASClient;
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

        this.schedulerConfig = new SchedulerConfiguration();
        schedulerConfig.setDiagnosticsConfig(config.diagnostics);
        schedulerConfig.setStorageAdapterConfig(config.storageAdapter);
        schedulerConfig.setSchedulerThreads(config.numSchedulerThreads);

        // get all the metrics to be collected from DMR resources
        for (MetricSetDMR metricSet : config.metricSetDmrMap.values()) {
            for (MetricDMR metric : metricSet.metricDmrMap.values()) {
                Interval interval = new Interval(metric.interval, metric.timeUnits);
                DMRPropertyReference ref = new DMRPropertyReference(metric.resource, metric.attribute, interval);
                schedulerConfig.addMetricToBeCollected(ref);
            }
        }

        // get all the availabilities that need to be checked from DMR resources
        for (AvailSetDMR availSet : config.availSetDmrMap.values()) {
            for (AvailDMR avail : availSet.availDmrMap.values()) {
                Interval interval = new Interval(avail.interval, avail.timeUnits);
                AvailDMRPropertyReference ref = new AvailDMRPropertyReference(avail.resource, avail.attribute,
                        interval, avail.upRegex);
                schedulerConfig.addAvailToBeChecked(ref);
            }
        }
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

    private void startScheduler() {
        // Get the server name from the runtime model
        boolean isDomainMode = getRootAttribute("launch-type").equalsIgnoreCase("domain");
        String hostName = (isDomainMode) ? getRootAttribute("host") : "";
        String serverName = getRootAttribute("name");
        String nodeName = System.getProperty("jboss.node.name");
        ServerIdentifiers id = new ServerIdentifiers(hostName, serverName, nodeName);

        ModelControllerClientFactory mccFactory = new ModelControllerClientFactory() {
            @Override
            public ModelControllerClient createClient() {
                return getManagementControllerClient();
            }
        };

        schedulerService = new SchedulerService(schedulerConfig, id, metricStorageProxy, mccFactory);
        schedulerService.start();
    }

    /**
     * Returns a client that can be used to talk to the management interface of the app server.
     * Make sure you close this when you are done with it.
     *
     * Use this with the DMR clients (JBossASClient and its subclasses) for more strongly-typed management API.
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
            final int numThreadsInPool = this.configuration.numSchedulerThreads; // same as scheduler
            final ThreadFactory threadFactory = ThreadFactoryGenerator.generateFactory(true,
                    "Hawkular-Monitor-MgmtClient");
            managementClientExecutor = Executors.newFixedThreadPool(numThreadsInPool, threadFactory);
        }

        return managementClientExecutor;
    }

    /**
     * Returns the value of an attribute of the main root resource of the app server.
     *
     * @param attributeName root resource's attribute whose value is to be returned
     *
     * @return root resource attribute value
     */
    private String getRootAttribute(String attributeName) {
        try (CoreJBossASClient client = new CoreJBossASClient(getManagementControllerClient())) {
            return client.getStringAttribute(attributeName, Address.root());
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
