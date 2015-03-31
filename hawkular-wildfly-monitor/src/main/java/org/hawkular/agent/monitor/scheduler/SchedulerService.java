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

import static java.util.concurrent.TimeUnit.MILLISECONDS;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

import org.hawkular.agent.monitor.log.MsgLogger;
import org.hawkular.agent.monitor.scheduler.config.ResourceRef;
import org.hawkular.agent.monitor.scheduler.config.SchedulerConfiguration;
import org.hawkular.agent.monitor.scheduler.diagnostics.Diagnostics;
import org.hawkular.agent.monitor.scheduler.diagnostics.DiagnosticsImpl;
import org.hawkular.agent.monitor.scheduler.diagnostics.JBossLoggingReporter;
import org.hawkular.agent.monitor.scheduler.diagnostics.JBossLoggingReporter.LoggingLevel;
import org.hawkular.agent.monitor.scheduler.diagnostics.StorageReporter;
import org.hawkular.agent.monitor.scheduler.polling.IntervalBasedScheduler;
import org.hawkular.agent.monitor.scheduler.polling.Scheduler;
import org.hawkular.agent.monitor.scheduler.polling.Task;
import org.hawkular.agent.monitor.scheduler.storage.BufferedStorageDispatcher;
import org.hawkular.agent.monitor.scheduler.storage.HawkularMetricsStorageAdapter;
import org.hawkular.agent.monitor.scheduler.storage.HawkularStorageAdapter;
import org.hawkular.agent.monitor.scheduler.storage.StorageAdapter;
import org.hawkular.agent.monitor.service.SelfIdentifiers;
import org.hawkular.dmrclient.Address;
import org.jboss.logging.Logger;

import com.codahale.metrics.MetricRegistry;
import com.codahale.metrics.ScheduledReporter;

/**
 * The core service that schedules metric collections and stores the data to its storage adapter.
 */
public class SchedulerService {

    private final StorageAdapter storageAdapter;
    private final SchedulerConfiguration schedulerConfig;
    private final Scheduler scheduler;
    private final Diagnostics diagnostics;
    private final ScheduledReporter diagnosticsReporter;
    private final BufferedStorageDispatcher completionHandler;
    private final SelfIdentifiers selfId;
    private boolean started = false;

    public SchedulerService(SchedulerConfiguration configuration, ModelControllerClientFactory clientFactory,
            SelfIdentifiers selfId) {

        this.selfId = selfId;
        final MetricRegistry metricRegistry = new MetricRegistry();
        this.diagnostics = createDiagnostics(metricRegistry);

        switch (configuration.getStorageAdapterConfig().type) {
            case HAWKULAR: {
                this.storageAdapter = new HawkularStorageAdapter();
                break;
            }
            case METRICS: {
                this.storageAdapter = new HawkularMetricsStorageAdapter();
                break;
            }
            default: {
                throw new IllegalArgumentException("Invalid storage adapter: "
                        + configuration.getStorageAdapterConfig());
            }
        }

        this.storageAdapter.setSchedulerConfiguration(configuration);
        this.storageAdapter.setDiagnostics(diagnostics);

        switch (configuration.getDiagnosticsConfig().reportTo) {
            case LOG: {
                this.diagnosticsReporter = JBossLoggingReporter.forRegistry(metricRegistry)
                        .convertRatesTo(TimeUnit.SECONDS)
                        .convertDurationsTo(MILLISECONDS)
                        .outputTo(Logger.getLogger(getClass()))
                        .withLoggingLevel(LoggingLevel.INFO)
                        .build();
                break;
            }
            case STORAGE: {
                this.diagnosticsReporter = StorageReporter.forRegistry(metricRegistry, storageAdapter, selfId)
                        .convertRatesTo(TimeUnit.SECONDS)
                        .convertDurationsTo(MILLISECONDS)
                        .build();
                break;
            }
            default: {
                throw new IllegalArgumentException("Invalid diagnostics type: "
                        + configuration.getDiagnosticsConfig().reportTo);
            }
        }

        this.completionHandler = new BufferedStorageDispatcher(configuration, storageAdapter, diagnostics);
        this.scheduler = new IntervalBasedScheduler(clientFactory, diagnostics, configuration.getSchedulerThreads());
        this.schedulerConfig = configuration;
    }

    private Diagnostics createDiagnostics(final MetricRegistry metrics) {
        return new DiagnosticsImpl(metrics);
    }

    public void start() {
        if (started) {
            return; // already started
        }

        MsgLogger.LOG.infoStartingScheduler();

        // turn ResourceRef into Tasks
        List<Task> tasks = createTasks(schedulerConfig.getResourceRefs());
        this.completionHandler.start();
        this.scheduler.schedule(tasks, completionHandler);

        if (this.schedulerConfig.getDiagnosticsConfig().enabled) {
            diagnosticsReporter.start(this.schedulerConfig.getDiagnosticsConfig().interval,
                    this.schedulerConfig.getDiagnosticsConfig().timeUnits);
        }

        started = true;
    }

    public void stop() {
        if (!started) {
            return; // already started
        }

        MsgLogger.LOG.infoStoppingScheduler();

        this.completionHandler.shutdown();
        this.scheduler.shutdown();
        this.diagnosticsReporter.stop();

        if (this.schedulerConfig.getDiagnosticsConfig().enabled) {
            this.diagnosticsReporter.report();
        }

        started = false;
    }

    private List<Task> createTasks(List<ResourceRef> resourceRefs) {
        List<Task> tasks = new ArrayList<>();
        for (ResourceRef ref : resourceRefs) {

            // parse sub references (complex attribute support)
            String attribute = ref.getAttribute();
            String subref = null;
            int i = attribute.indexOf("#");
            if (i > 0) {
                subref = attribute.substring(i + 1, attribute.length());
                attribute = attribute.substring(0, i);
            }

            String host = this.selfId.getLocalHost();
            String server = this.selfId.getLocalServer();

            tasks.add(new Task(host, server, Address.parse(ref.getAddress()), attribute, subref, ref.getInterval()));
        }
        return tasks;
    }
}
