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

import org.hawkular.agent.monitor.scheduler.SchedulerService;
import org.hawkular.agent.monitor.service.ThreadFactoryGenerator;
import org.jboss.logging.Logger;

public class IntervalBasedScheduler implements Scheduler {

    private static final Logger LOGGER = Logger.getLogger(IntervalBasedScheduler.class);

    private final SchedulerService schedulerService;
    private final ScheduledExecutorService executorService;
    private final List<ScheduledFuture<?>> jobs;

    private boolean started = false;

    public IntervalBasedScheduler(SchedulerService schedulerService, String name) {
        this.schedulerService = schedulerService;

        ThreadFactory threadFactory = ThreadFactoryGenerator.generateFactory(true, name);
        this.executorService = Executors.newScheduledThreadPool(schedulerService.getSchedulerConfiguration()
                .getSchedulerThreads(), threadFactory);

        this.jobs = new LinkedList<>();

    }

    @Override
    public void schedule(List<Task> tasks) {
        if (this.started) {
            return; // already running
        }

        // optimize task groups
        List<TaskGroup> groups = new IntervalGrouping().separateIntoGroups(tasks);

        LOGGER.debugf("Scheduling [%d] tasks in [%d] task groups", tasks.size(), groups.size());

        // schedule
        for (TaskGroup group : groups) {
            jobs.add(executorService.scheduleWithFixedDelay(
                    schedulerService.getTaskGroupRunnable(group),
                    group.getOffsetMillis(),
                    group.getInterval().millis(),
                    MILLISECONDS));
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
            Thread.currentThread().interrupt(); // Preserve interrupt status
        } finally {
            this.started = false;
        }
    }
}
