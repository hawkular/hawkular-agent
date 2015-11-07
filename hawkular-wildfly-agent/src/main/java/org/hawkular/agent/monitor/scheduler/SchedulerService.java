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
package org.hawkular.agent.monitor.scheduler;

import java.util.List;

import org.hawkular.agent.monitor.api.InventoryEvent;
import org.hawkular.agent.monitor.api.InventoryListener;
import org.hawkular.agent.monitor.api.SamplingService;
import org.hawkular.agent.monitor.diagnostics.Diagnostics;
import org.hawkular.agent.monitor.inventory.AvailType;
import org.hawkular.agent.monitor.inventory.MetricType;
import org.hawkular.agent.monitor.inventory.MonitoredEndpoint;
import org.hawkular.agent.monitor.inventory.Resource;
import org.hawkular.agent.monitor.log.AgentLoggers;
import org.hawkular.agent.monitor.log.MsgLogger;
import org.hawkular.agent.monitor.service.ServiceStatus;
import org.hawkular.agent.monitor.storage.AvailBufferedStorageDispatcher;
import org.hawkular.agent.monitor.storage.AvailDataPoint;
import org.hawkular.agent.monitor.storage.MetricBufferedStorageDispatcher;
import org.hawkular.agent.monitor.storage.MetricDataPoint;
import org.hawkular.agent.monitor.storage.StorageAdapter;

/**
 * The core service that schedules tasks and stores the data resulting from those tasks to its storage adapter.
 *
 * @author John Mazzitelli
 */
public class SchedulerService implements InventoryListener {
    private static final MsgLogger log = AgentLoggers.getLogger(SchedulerService.class);
    private final Diagnostics diagnostics;
    private final IntervalBasedScheduler<MetricType<Object>, MetricDataPoint> metricScheduler;
    private final IntervalBasedScheduler<AvailType<Object>, AvailDataPoint> availScheduler;
    private final MetricBufferedStorageDispatcher metricCompletionHandler;
    private final AvailBufferedStorageDispatcher availCompletionHandler;

    protected volatile ServiceStatus status = ServiceStatus.INITIAL;

    public SchedulerService(
            SchedulerConfiguration configuration,
            Diagnostics diagnostics,
            StorageAdapter storageAdapter) {

        // metrics for our own internals
        this.diagnostics = diagnostics;

        // create the schedulers - we use two: one for metric collections and one for avail checks
        this.metricCompletionHandler = new MetricBufferedStorageDispatcher(configuration, storageAdapter,
                diagnostics);
        this.metricScheduler = IntervalBasedScheduler
                .forMetrics("Hawkular-Monitor-Scheduler-Metrics",
                        configuration.getMetricSchedulerThreads(), metricCompletionHandler);

        this.availCompletionHandler = new AvailBufferedStorageDispatcher(configuration, storageAdapter,
                diagnostics);
        this.availScheduler = IntervalBasedScheduler.forAvails("Hawkular-Monitor-Scheduler-Avail",
                configuration.getAvailSchedulerThreads(), availCompletionHandler);
    }

    public void start() {
        status.assertInitialOrStopped(getClass(), "start()");
        status = ServiceStatus.STARTING;

        log.infoStartingScheduler();

        // start the collections
        this.metricCompletionHandler.start();
        this.metricScheduler.start();

        this.availCompletionHandler.start();
        this.availScheduler.start();

        status = ServiceStatus.RUNNING;
    }

    public void stop() {
        status.assertInitialOrStopped(getClass(), "stop()");
        status = ServiceStatus.STOPPING;

        log.infoStoppingScheduler();

        // stop completion handlers
        this.metricCompletionHandler.shutdown();
        this.availCompletionHandler.shutdown();

        // stop the schedulers
        this.metricScheduler.stop();
        this.availScheduler.stop();

        status = ServiceStatus.STOPPED;
    }

    @Override
    public <L, E extends MonitoredEndpoint> void discoverAllFinished(InventoryEvent<L, E> event) {
        List<Resource<L>> resources = event.getPayload();
        SamplingService<L, E> service = event.getSamplingService();
        metricScheduler.rescheduleAll(service, resources);
        availScheduler.rescheduleAll(service, resources);
    }

    @Override
    public <L, E extends MonitoredEndpoint> void resourcesAdded(InventoryEvent<L, E> event) {
        List<Resource<L>> resources = event.getPayload();
        SamplingService<L, E> service = event.getSamplingService();
        metricScheduler.schedule(service, resources);
        availScheduler.schedule(service, resources);
    }

    @Override
    public <L, E extends MonitoredEndpoint> void resourceRemoved(InventoryEvent<L, E> event) {
        List<Resource<L>> resources = event.getPayload();
        SamplingService<L, E> service = event.getSamplingService();
        metricScheduler.unschedule(service, resources);
        availScheduler.unschedule(service, resources);
    }

}
