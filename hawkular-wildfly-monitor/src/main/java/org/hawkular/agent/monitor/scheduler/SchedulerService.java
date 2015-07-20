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

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.hawkular.agent.monitor.diagnostics.Diagnostics;
import org.hawkular.agent.monitor.inventory.dmr.DMRAvailInstance;
import org.hawkular.agent.monitor.inventory.dmr.DMRMetricInstance;
import org.hawkular.agent.monitor.log.MsgLogger;
import org.hawkular.agent.monitor.scheduler.config.AvailDMRPropertyReference;
import org.hawkular.agent.monitor.scheduler.config.DMREndpoint;
import org.hawkular.agent.monitor.scheduler.config.DMRPropertyReference;
import org.hawkular.agent.monitor.scheduler.config.LocalDMREndpoint;
import org.hawkular.agent.monitor.scheduler.config.SchedulerConfiguration;
import org.hawkular.agent.monitor.scheduler.polling.IntervalBasedScheduler;
import org.hawkular.agent.monitor.scheduler.polling.Scheduler;
import org.hawkular.agent.monitor.scheduler.polling.Task;
import org.hawkular.agent.monitor.scheduler.polling.TaskGroup;
import org.hawkular.agent.monitor.scheduler.polling.dmr.AvailDMRTask;
import org.hawkular.agent.monitor.scheduler.polling.dmr.AvailDMRTaskGroupRunnable;
import org.hawkular.agent.monitor.scheduler.polling.dmr.DMRTask;
import org.hawkular.agent.monitor.scheduler.polling.dmr.MetricDMRTask;
import org.hawkular.agent.monitor.scheduler.polling.dmr.MetricDMRTaskGroupRunnable;
import org.hawkular.agent.monitor.service.ServerIdentifiers;
import org.hawkular.agent.monitor.storage.AvailBufferedStorageDispatcher;
import org.hawkular.agent.monitor.storage.HttpClientBuilder;
import org.hawkular.agent.monitor.storage.MetricBufferedStorageDispatcher;
import org.hawkular.agent.monitor.storage.StorageAdapter;

/**
 * The core service that schedules tasks and stores the data resulting from those tasks to its storage adapter.
 */
public class SchedulerService {

    private final SchedulerConfiguration schedulerConfig;
    private final ServerIdentifiers selfId;
    private final ModelControllerClientFactory localDMRClientFactory;
    private final Diagnostics diagnostics;
    private final Scheduler metricScheduler;
    private final Scheduler availScheduler;
    private final MetricBufferedStorageDispatcher metricCompletionHandler;
    private final AvailBufferedStorageDispatcher availCompletionHandler;
    private final HttpClientBuilder httpClientBuilder;

    private boolean started = false;

    public SchedulerService(
            SchedulerConfiguration configuration,
            ServerIdentifiers selfId,
            Diagnostics diagnostics,
            StorageAdapter storageAdapter,
            ModelControllerClientFactory localDMRClientFactory,
            HttpClientBuilder httpClientBuilder) {

        this.schedulerConfig = configuration;

        // for those tasks that require a DMR client to our own WildFly server, this factory can provide those clients
        this.localDMRClientFactory = localDMRClientFactory;

        // this helps identify where we are running
        this.selfId = selfId;

        // metrics for our own internals
        this.diagnostics = diagnostics;

        // used to send requests to the server
        this.httpClientBuilder = httpClientBuilder;

        // create the schedulers - we use two: one for metric collections and one for avail checks
        this.metricCompletionHandler = new MetricBufferedStorageDispatcher(configuration, storageAdapter,
                diagnostics);
        this.metricScheduler = new IntervalBasedScheduler(this, "Hawkular-Monitor-Scheduler-Metrics",
                configuration.getMetricSchedulerThreads());

        this.availCompletionHandler = new AvailBufferedStorageDispatcher(configuration, storageAdapter,
                diagnostics);
        this.availScheduler = new IntervalBasedScheduler(this, "Hawkular-Monitor-Scheduler-Avail",
                configuration.getAvailSchedulerThreads());
    }

    public ServerIdentifiers getSelfIdentifiers() {
        return this.selfId;
    }

    public Diagnostics getDiagnostics() {
        return this.diagnostics;
    }

    public void start() {
        if (started) {
            return; // already started
        }

        MsgLogger.LOG.infoStartingScheduler();

        // turn metric DMR refs into Tasks and schedule them now
        List<Task> metricTasks = createMetricDMRTasks(schedulerConfig.getDMRMetricsToBeCollected());
        this.metricCompletionHandler.start();
        this.metricScheduler.schedule(metricTasks);

        // turn avail DMR refs into Tasks and schedule them now
        List<Task> availTasks = createAvailDMRTasks(schedulerConfig.getDMRAvailsToBeChecked());
        this.availCompletionHandler.start();
        this.availScheduler.schedule(availTasks);

        started = true;
    }

    public void stop() {
        if (!started) {
            return; // already stopped
        }

        MsgLogger.LOG.infoStoppingScheduler();

        // stop completion handlers
        this.metricCompletionHandler.shutdown();
        this.availCompletionHandler.shutdown();

        // stop the schedulers
        this.metricScheduler.shutdown();
        this.availScheduler.shutdown();

        started = false;
    }

    public Runnable getTaskGroupRunnable(TaskGroup group) {
        switch (group.getType()) {
            case METRIC: {
                // we are guaranteed the first task is the same kind as all the rest
                Task firstTask = group.getTask(0);
                if (DMRTask.class.isInstance(firstTask)) {
                    // we are guaranteed that all tasks in a group refer to the same endpoint
                    DMREndpoint endpoint = ((DMRTask) firstTask).getEndpoint();
                    ModelControllerClientFactory factory;
                    if (endpoint instanceof LocalDMREndpoint) {
                        factory = this.localDMRClientFactory;
                    } else {
                        factory = new ModelControllerClientFactoryImpl(endpoint);
                    }
                    return new MetricDMRTaskGroupRunnable(group, metricCompletionHandler, getDiagnostics(), factory);
                } else {
                    throw new UnsupportedOperationException("Unsupported metric group: " + group);
                }
            }

            case AVAIL: {
                // we are guaranteed the first task is the same kind as all the rest
                Task firstTask = group.getTask(0);
                if (DMRTask.class.isInstance(firstTask)) {
                    // we are guaranteed that all tasks in a group refer to the same endpoint
                    DMREndpoint endpoint = ((DMRTask) firstTask).getEndpoint();
                    ModelControllerClientFactory factory;
                    if (endpoint instanceof LocalDMREndpoint) {
                        factory = this.localDMRClientFactory;
                    } else {
                        factory = new ModelControllerClientFactoryImpl(endpoint);
                    }
                    return new AvailDMRTaskGroupRunnable(group, availCompletionHandler, getDiagnostics(), factory);
                } else {
                    throw new UnsupportedOperationException("Unsupported avail group: " + group);
                }
            }

            default: {
                throw new IllegalArgumentException("Bad group [" + group + "]. Please report this bug.");
            }
        }
    }

    private List<Task> createMetricDMRTasks(Map<DMREndpoint, List<DMRMetricInstance>> map) {
        List<Task> tasks = new ArrayList<>();

        for (Map.Entry<DMREndpoint, List<DMRMetricInstance>> entry : map.entrySet()) {
            DMREndpoint dmrEndpoint = entry.getKey();
            for (DMRMetricInstance instance : entry.getValue()) {
                // parse sub references (complex attribute support)
                DMRPropertyReference propRef = instance.getProperty();
                String attribute = propRef.getAttribute();
                String subref = null;

                if (attribute != null) {
                    int i = attribute.indexOf("#");
                    if (i > 0) {
                        subref = attribute.substring(i + 1, attribute.length());
                        attribute = attribute.substring(0, i);
                    }
                }

                tasks.add(new MetricDMRTask(propRef.getInterval(), dmrEndpoint, propRef.getAddress(), attribute,
                        subref, instance));
            }
        }

        return tasks;
    }

    private List<Task> createAvailDMRTasks(Map<DMREndpoint, List<DMRAvailInstance>> map) {
        List<Task> tasks = new ArrayList<>();

        for (Map.Entry<DMREndpoint, List<DMRAvailInstance>> entry : map.entrySet()) {
            DMREndpoint dmrEndpoint = entry.getKey();
            for (DMRAvailInstance instance : entry.getValue()) {
                // parse sub references (complex attribute support)
                AvailDMRPropertyReference propRef = instance.getProperty();
                String attribute = propRef.getAttribute();
                String subref = null;

                if (attribute != null) {
                    int i = attribute.indexOf("#");
                    if (i > 0) {
                        subref = attribute.substring(i + 1, attribute.length());
                        attribute = attribute.substring(0, i);
                    }
                }

                tasks.add(new AvailDMRTask(propRef.getInterval(), dmrEndpoint, propRef.getAddress(), attribute,
                        subref, instance, propRef.getUpRegex()));
            }
        }

        return tasks;
    }
}
