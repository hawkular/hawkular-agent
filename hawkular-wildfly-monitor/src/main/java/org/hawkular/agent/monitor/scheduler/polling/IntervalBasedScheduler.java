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
package org.hawkular.agent.monitor.scheduler.polling;

import static java.util.concurrent.TimeUnit.MILLISECONDS;

import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;

import org.hawkular.agent.monitor.log.MsgLogger;
import org.hawkular.agent.monitor.scheduler.ModelControllerClientFactory;
import org.hawkular.agent.monitor.scheduler.diagnostics.Diagnostics;
import org.hawkular.agent.monitor.scheduler.storage.DataPoint;
import org.hawkular.agent.monitor.service.ThreadFactoryGenerator;
import org.hawkular.dmrclient.JBossASClient;
import org.jboss.dmr.ModelNode;
import org.jboss.dmr.Property;
import org.jboss.logging.Logger;

import com.codahale.metrics.Timer;

public class IntervalBasedScheduler implements Scheduler {

    private static final Logger LOGGER = Logger.getLogger(IntervalBasedScheduler.class);

    private final ScheduledExecutorService executorService;
    private final List<ScheduledFuture<?>> jobs;
    private final ModelControllerClientFactory clientFactory;
    private final Diagnostics diagnostics;

    private boolean started = false;

    public IntervalBasedScheduler(ModelControllerClientFactory clientFactory, Diagnostics diagnostics, int poolSize) {
        this.clientFactory = clientFactory;
        this.diagnostics = diagnostics;

        ThreadFactory threadFactory = ThreadFactoryGenerator.generateFactory(true, "Hawkular-Monitor-Scheduler");
        this.executorService = Executors.newScheduledThreadPool(poolSize, threadFactory);

        this.jobs = new LinkedList<>();

    }

    @Override
    public void schedule(List<Task> tasks, final CompletionHandler completionHandler) {
        if (this.started) {
            return; // already running
        }

        // optimize task groups
        List<TaskGroup> groups = new IntervalGrouping().apply(tasks);

        LOGGER.debugf("Scheduling [%d] tasks in [%d] task groups", tasks.size(), groups.size());

        // schedule IO
        for (TaskGroup group : groups) {
            jobs.add(executorService.scheduleWithFixedDelay(new IO(group, completionHandler), group.getOffsetMillis(),
                    group.getInterval().millis(), MILLISECONDS));
        }

        this.started = true;
    }

    @Override
    public void shutdown() {
        if (!this.started) {
            return; // already shutdown
        }

        try {
            for (ScheduledFuture<?> job : jobs) {
                job.cancel(false);
            }
            executorService.shutdown();
            executorService.awaitTermination(5, TimeUnit.SECONDS);

        } catch (InterruptedException ie) {
            // Preserve interrupt status
            Thread.currentThread().interrupt();
        } finally {
            this.started = false;
        }
    }

    private class IO implements Runnable {

        private final TaskGroup group;
        private final CompletionHandler completionHandler;
        private final ModelNode operation;

        private IO(TaskGroup group, CompletionHandler completionHandler) {
            this.group = group;
            this.completionHandler = completionHandler;

            // for the IO lifetime the operation is immutable and can be re-used
            this.operation = new ReadAttributeOperationBuilder().createOperation(group);
        }

        @Override
        public void run() {
            try (JBossASClient client = new JBossASClient(clientFactory.createClient())) {

                // execute request
                Timer.Context requestContext = diagnostics.getRequestTimer().time();
                ModelNode response = client.execute(operation);
                long durationMs = requestContext.stop() / 1000000;

                if (JBossASClient.isSuccess(response)) {

                    if (durationMs > group.getInterval().millis()) {
                        diagnostics.getDelayedRate().mark(1);
                    }

                    List<Property> stepResults = JBossASClient.getResults(response).asPropertyList();

                    if (stepResults.size() != group.size()) {
                        MsgLogger.LOG.warnBatchResultsDoNotMatchRequests(group.size(), stepResults.size());
                    }

                    int i = 0;
                    for (Property step : stepResults) {
                        Task task = group.getTask(i);

                        // deconstruct model node
                        ModelNode data = step.getValue();
                        ModelNode dataResult = JBossASClient.getResults(data);
                        Double value = null;
                        if (task.getSubref() != null) {
                            value = dataResult.get(task.getSubref()).asDouble();
                        }
                        else {
                            value = dataResult.asDouble();
                        }

                        completionHandler.onCompleted(new DataPoint(task, value));
                        i++;
                    }

                } else {
                    diagnostics.getErrorRate().mark(1);
                    completionHandler.onFailed(new RuntimeException(JBossASClient.getFailureDescription(response)));
                }

            } catch (Throwable e) {
                diagnostics.getErrorRate().mark(1);
                completionHandler.onFailed(e);
            }
        }
    }
}
