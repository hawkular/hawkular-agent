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
package org.hawkular.agent.monitor.scheduler;

import java.util.Collection;
import java.util.List;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;

import org.hawkular.agent.monitor.api.InventoryEvent;
import org.hawkular.agent.monitor.api.InventoryListener;
import org.hawkular.agent.monitor.api.SamplingService;
import org.hawkular.agent.monitor.diagnostics.Diagnostics;
import org.hawkular.agent.monitor.inventory.AvailType;
import org.hawkular.agent.monitor.inventory.MetricType;
import org.hawkular.agent.monitor.inventory.Resource;
import org.hawkular.agent.monitor.log.AgentLoggers;
import org.hawkular.agent.monitor.log.MsgLogger;
import org.hawkular.agent.monitor.service.ServiceStatus;
import org.hawkular.agent.monitor.storage.AvailBufferedStorageDispatcher;
import org.hawkular.agent.monitor.storage.AvailDataPoint;
import org.hawkular.agent.monitor.storage.MetricBufferedStorageDispatcher;
import org.hawkular.agent.monitor.storage.MetricDataPoint;
import org.hawkular.agent.monitor.storage.PingStorageDispatcher;
import org.hawkular.agent.monitor.storage.StorageAdapter;
import org.hawkular.agent.monitor.util.ThreadFactoryGenerator;

/**
 * The core service that schedules tasks and stores the data resulting from those tasks to its storage adapter.
 *
 * @author John Mazzitelli
 */
public class SchedulerService implements InventoryListener {
    private static final MsgLogger log = AgentLoggers.getLogger(SchedulerService.class);
    private final Diagnostics diagnostics;
    private final MeasurementScheduler<Object, MetricType<Object>, MetricDataPoint> metricScheduler;
    private final MeasurementScheduler<Object, AvailType<Object>, AvailDataPoint> availScheduler;
    private final ScheduledThreadPoolExecutor pingScheduler;
    private final MetricBufferedStorageDispatcher metricStorage;
    private final AvailBufferedStorageDispatcher availStorage;
    private final PingStorageDispatcher pingStorage;

    private ScheduledFuture<?> pingJob;

    protected volatile ServiceStatus status = ServiceStatus.INITIAL;

    public SchedulerService(
            SchedulerConfiguration configuration,
            Diagnostics diagnostics,
            StorageAdapter storageAdapter) {

        // metrics for our own internals
        this.diagnostics = diagnostics;

        // create the schedulers - we use three: one for metric collections, one for avail checks and one for feed pings
        this.metricStorage = new MetricBufferedStorageDispatcher(configuration, storageAdapter, diagnostics);
        this.metricScheduler = MeasurementScheduler.forMetrics("Hawkular-WildFly-Agent-Scheduler-Metrics",
                metricStorage);

        this.availStorage = new AvailBufferedStorageDispatcher(configuration, storageAdapter, diagnostics);
        this.availScheduler = MeasurementScheduler.forAvails("Hawkular-WildFly-Agent-Scheduler-Avail",
                availStorage);

        this.pingStorage = new PingStorageDispatcher(configuration, storageAdapter, diagnostics);
        ThreadFactory threadFactory = ThreadFactoryGenerator.generateFactory(true, "Hawkular-WildFly-Scheduler-Ping");
        this.pingScheduler = new ScheduledThreadPoolExecutor(1, threadFactory);
    }

    public void start() {
        status.assertInitialOrStopped(getClass(), "start()");
        status = ServiceStatus.STARTING;

        log.infoStartingScheduler();

        // start showing the agent as running
        int pingPeriod = this.pingStorage.getConfig().getPingDispatcherPeriodSeconds();
        if (pingPeriod > 0) {
            this.pingJob = this.pingScheduler.scheduleAtFixedRate(this.pingStorage, 0L, pingPeriod, TimeUnit.SECONDS);
        }

        // start the collections
        this.metricStorage.start();
        this.metricScheduler.start();

        this.availStorage.start();
        this.availScheduler.start();

        status = ServiceStatus.RUNNING;
    }

    public void stop() {
        status.assertRunning(getClass(), "stop()");
        status = ServiceStatus.STOPPING;

        log.infoStoppingScheduler();

        // stop completion handlers
        this.metricStorage.shutdown();
        this.availStorage.shutdown();

        // stop the schedulers
        this.metricScheduler.stop();
        this.availScheduler.stop();

        // stop the agent availability ping
        if (null != this.pingJob) {
            this.pingJob.cancel(true);
        }

        status = ServiceStatus.STOPPED;
    }

    @Override
    public <L> void resourcesAdded(InventoryEvent<L> event) {
        List<Resource<L>> resources = event.getPayload();
        SamplingService<L> service = event.getSamplingService();

        log.debugf("Scheduling jobs for [%d] new resources for endpoint [%s]",
                resources.size(), service.getMonitoredEndpoint());

        ((MeasurementScheduler) metricScheduler).schedule(service, resources);
        ((MeasurementScheduler) availScheduler).schedule(service, resources);
    }

    @Override
    public <L> void resourcesRemoved(InventoryEvent<L> event) {
        List<Resource<L>> resources = event.getPayload();
        SamplingService<L> service = event.getSamplingService();

        log.debugf("Unscheduling jobs for [%d] obsolete resources for endpoint [%s]",
                resources.size(), service.getMonitoredEndpoint());

        unschedule(service, resources);
    }

    public <L> void unschedule(SamplingService<L> service, Collection<Resource<L>> resources) {
        ((MeasurementScheduler) metricScheduler).unschedule(service, resources);
        ((MeasurementScheduler) availScheduler).unschedule(service, resources);
    }
}
