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
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;

import org.hawkular.agent.monitor.api.SamplingService;
import org.hawkular.agent.monitor.inventory.AvailType;
import org.hawkular.agent.monitor.inventory.Interval;
import org.hawkular.agent.monitor.inventory.MeasurementInstance;
import org.hawkular.agent.monitor.inventory.MeasurementType;
import org.hawkular.agent.monitor.inventory.MetricType;
import org.hawkular.agent.monitor.inventory.MonitoredEndpoint;
import org.hawkular.agent.monitor.inventory.Resource;
import org.hawkular.agent.monitor.log.AgentLoggers;
import org.hawkular.agent.monitor.log.MsgLogger;
import org.hawkular.agent.monitor.service.ServiceStatus;
import org.hawkular.agent.monitor.storage.AvailDataPoint;
import org.hawkular.agent.monitor.storage.DataPoint;
import org.hawkular.agent.monitor.storage.MetricDataPoint;
import org.hawkular.agent.monitor.util.Consumer;
import org.hawkular.agent.monitor.util.ThreadFactoryGenerator;

/**
 * A scheduler that can be used to collect metrics or availability. To create a scheduler,
 * use one of {@link #forMetrics(String, int, Consumer)} or {@link #forAvails(String, int, Consumer)}.
 *
 * @param <T> the sublclass of {@link MeasurementType} to handle (such as metric types or avail types)
 * @param <D> the {@link DataPoint} type (such as metric data or avail data)
 */
public abstract class MeasurementScheduler<L, T extends MeasurementType<L>, D extends DataPoint> {

    private static final MsgLogger LOG = AgentLoggers.getLogger(MeasurementScheduler.class);

    /**
     * Static method that builds a scheduler for metric collection.
     *
     * @param name the name of the scheduler (used for things like naming the threads)
     * @param schedulerThreads number of core threads in the thread pool
     * @param completionHandler object that is notified of metric values when they are collected
     *
     * @return the new metric collection scheduler
     */
    public static <LL> MeasurementScheduler<LL, MetricType<LL>, MetricDataPoint> forMetrics(
            String name, int schedulerThreads, Consumer<MetricDataPoint> completionHandler) {

        return new MeasurementScheduler<LL, MetricType<LL>, MetricDataPoint>(name, schedulerThreads,
                completionHandler) {

            /**
             * @return the collector that will be used to get metrics for resources at the given endpoint.
             */
            @Override
            protected Runnable createCollector(SamplingService<LL> endpointService,
                    Consumer<MetricDataPoint> completionHandler) {
                return new MetricsCollector<LL>(endpointService, createOrGetScheduledCollectionsQueue(endpointService),
                        completionHandler);
            }

            /**
             * @return all the defined metric instances for the given resource.
             */
            @Override
            protected Collection<ScheduledMeasurementInstance<LL, MetricType<LL>>> getScheduledMeasurementInstances(
                    Resource<LL> resource) {
                return ScheduledMeasurementInstance.createMetrics(resource);
            }
        };
    }

    /**
     * Static method that builds a scheduler for availability checking.
     *
     * @param name the name of the scheduler (used for things like naming the threads)
     * @param schedulerThreads number of core threads in the thread pool
     * @param completionHandler object that is notified of availability results when they are checked
     *
     * @return the new availability checking scheduler
     */
    public static <LL> MeasurementScheduler<LL, AvailType<LL>, AvailDataPoint> forAvails(
            String name, int schedulerThreads, Consumer<AvailDataPoint> completionHandler) {

        return new MeasurementScheduler<LL, AvailType<LL>, AvailDataPoint>(name, schedulerThreads,
                completionHandler) {

            /**
             * @return the collector that will be used to check availabilities for resources at the given endpoint.
             */
            @Override
            protected Runnable createCollector(SamplingService<LL> endpointService,
                    Consumer<AvailDataPoint> completionHandler) {
                return new AvailsCollector<LL>(endpointService, createOrGetScheduledCollectionsQueue(endpointService),
                        completionHandler);
            }

            /**
             * @return all the defined avail instances for the given resource.
             */
            @Override
            protected Collection<ScheduledMeasurementInstance<LL, AvailType<LL>>> getScheduledMeasurementInstances(
                    Resource<LL> resource) {
                return ScheduledMeasurementInstance.createAvails(resource);
            }
        };
    }

    /** the name of the scheduler */
    private final String name;

    /** thread pool used by the scheduler to execute the difference metrics/avails jobs. */
    private final ScheduledExecutorService executorService;

    /** prioritized queue for each endpoint that indicates what metrics are next to be collected */
    private final Map<SamplingService<L>, ScheduledCollectionsQueue<L, T>> queues = new HashMap<>();

    /** jobs that are to be executed - note the jobs are grouped by monitored endpoint. */
    private final Map<MonitoredEndpoint, List<ScheduledFuture<?>>> DO_I_NEED_jobs = new HashMap<>();

    /** object that will be notified when metric data or avail results have been collected and ready to be stored */
    private final Consumer<D> completionHandler;

    /** lifecycle status of the scheduler itself */
    protected volatile ServiceStatus status = ServiceStatus.INITIAL;

    /**
     * The actual scheduler constructor. To build schedulers, call {@link #forMetrics(String, int, Consumer)}
     * or {@link #forAvails(String, int, Consumer)}.
     *
     * @param name name of scheduler
     * @param schedulerThreads number of threads in thread pool
     * @param completionHandler object notified when a job is done and its data needs to be stored
     */
    private MeasurementScheduler(String name, int schedulerThreads, Consumer<D> completionHandler) {
        this.name = name;
        this.completionHandler = completionHandler;
        ThreadFactory threadFactory = ThreadFactoryGenerator.generateFactory(true, name);
        this.executorService = Executors.newScheduledThreadPool(schedulerThreads, threadFactory);
    }

    /**
     * This will reschedule all metric collection and avail checking jobs for the given endpoint.
     * Any jobs running currently for that endpoint will be canceled, and the given resources will
     * have their metric/avail jobs recreated and rescheduled.
     *
     * @param endpointService defines where the resources are
     * @param resources the resources whose metric collections/avail checks are to be rescheduled
     */
    public void rescheduleAll(SamplingService<L> endpointService, List<Resource<L>> resources) {

        status.assertRunning(getClass(), "rescheduleAll()");

        // FIXME: consider if we need to lock the jobs here and elsewhere

        MonitoredEndpoint endpoint = endpointService.getEndpoint();

        // if there are any jobs currently running for the given endpoint, cancel them now
        List<ScheduledFuture<?>> oldJobs = DO_I_NEED_jobs.get(endpoint);
        if (oldJobs != null) {
            LOG.debugf("Scheduler [%s]: canceling [%d] jobs for endpoint [%s]",
                    this.name, oldJobs.size(), endpointService);

            for (ScheduledFuture<?> oldJob : oldJobs) {
                oldJob.cancel(false);
            }
        }

        List<ScheduledFuture<?>> endpointJobs = new ArrayList<>();
        Map<Interval, Collection<MeasurementInstance<L, T>>> instancesByInterval = new HashMap<>();
        for (Resource<L> resource : resources) {
            Collection<MeasurementInstance<L, T>> resourceInstances = getMeasurementInstances(resource);
            for (MeasurementInstance<L, T> instance : resourceInstances) {
                Interval interval = instance.getType().getInterval();
                Collection<MeasurementInstance<L, T>> instances = instancesByInterval.get(interval);
                if (instances == null) {
                    instances = new ArrayList<>();
                    instancesByInterval.put(interval, instances);
                }
                instances.add(instance);
            }
        }

        int measurementInstances = 0;
        for (Entry<Interval, Collection<MeasurementInstance<L, T>>> en : instancesByInterval.entrySet()) {
            Interval interval = en.getKey();
            Collection<MeasurementInstance<L, T>> instances = en.getValue();
            ScheduledFuture<?> future = executorService.scheduleWithFixedDelay(
                    createJob(endpointService, instances, completionHandler),
                    0,
                    interval.millis(),
                    java.util.concurrent.TimeUnit.MILLISECONDS);
            endpointJobs.add(future);
            measurementInstances += instances.size();
        }

        DO_I_NEED_jobs.put(endpointService.getEndpoint(), endpointJobs);
        LOG.debugf("Scheduler [%s]: [%d] jobs ([%d] measurements) have been submitted for endpoint [%s]",
                this.name, endpointJobs.size(), measurementInstances, endpointService);
    }

    /**
     * Schedules metric collections and avail checks for the given resources.
     *
     * Unliked {@link #rescheduleAll(SamplingService, List)}, this method keeps currently scheduled jobs
     * in place.
     *
     * @param endpointService defines where the resources are
     * @param resources the resources whose metric collections/avail checks are to be added to the scheduler
     */
    public void schedule(SamplingService<L> endpointService, List<Resource<L>> resources) {
        status.assertRunning(getClass(), "schedule()");

        List<ScheduledMeasurementInstance<L, T>> schedules = new ArrayList<>();
        resources.forEach(new java.util.function.Consumer<Resource<L>>() {
            @Override
            public void accept(Resource<L> res) {
                schedules.addAll(getScheduledMeasurementInstances(res));
            }
        });

        ScheduledCollectionsQueue<L, T> queue = createOrGetScheduledCollectionsQueue(endpointService);
        queue.schedule(schedules);
    }

    /**
     * Removes any existing metric collections and avail checks that are scheduled for the given resources.
     *
     * Unliked {@link #rescheduleAll(SamplingService, List)}, this method keeps other currently scheduled jobs
     * in place.
     *
     * @param endpointService defines where the resources are
     * @param resources the resources whose metric collections/avail checks are to be removed from the scheduler
     */
    public void unschedule(SamplingService<L> endpointService, List<Resource<L>> resources) {
        status.assertRunning(getClass(), "unschedule()");
        // TODO remove resources from scheduled ones
        LOG.warn("TODO: UNSCHEDULE() IS NOT IMPLEMENTED");
    }

    /**
     * Call this to get the scheduled collections queue for the given endpoint. If it doesn't yet exist
     * one will be created.
     *
     * @param endpointService the endpoint service whose queue is to be retrieved (and created if necessary)
     * @return the queue assigned to the given endpoint service
     */
    protected ScheduledCollectionsQueue<L, T> createOrGetScheduledCollectionsQueue(
            SamplingService<L> endpointService) {

        synchronized (this.queues) {
            ScheduledCollectionsQueue<L, T> q = this.queues.get(endpointService);
            if (q == null) {
                q = new ScheduledCollectionsQueue<L, T>();
                this.queues.put(endpointService, q);
            }
            return q;
        }
    }

    /**
     * Returns the scheduled collections queue associated with the given endpoint service. If one has not been
     * created yet an exception is thrown.
     *
     * @param endpointService
     * @return the queue assigned to the given endpoint service
     *
     * @throws IllegalStateException if the endpoint service does not have a priority queue 
     */
    private ScheduledCollectionsQueue<L, T> getScheduledCollectionsQueue(SamplingService<L> endpointService) {
        synchronized (this.queues) {
            ScheduledCollectionsQueue<L, T> q = this.queues.get(endpointService);
            if (q == null) {
                throw new IllegalStateException("There is no priority queue for endpoint: " + endpointService);
            }
            return q;
        }
    }

    public void start() {
        status.assertInitialOrStopped(getClass(), "start()");
        status = ServiceStatus.STARTING;
        // nothing to do here
        status = ServiceStatus.RUNNING;

    }

    public void stop() {
        status.assertRunning(getClass(), "stop()");
        status = ServiceStatus.STOPPING;

        LOG.infof("Stopping scheduler [%s] and its [%d] jobs", this.name, DO_I_NEED_jobs.size());

        try {
            for (List<ScheduledFuture<?>> perEndpointJobs : DO_I_NEED_jobs.values()) {
                for (ScheduledFuture<?> job : perEndpointJobs) {
                    job.cancel(false);
                }
            }
            executorService.shutdown();
            executorService.awaitTermination(5, TimeUnit.SECONDS);
            LOG.infof("Scheduler [%s] stopped", this.name);

        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt(); // Preserve interrupt status
        } finally {
            status = ServiceStatus.STOPPED;
        }
    }

    /**
     * Creates the object that will be responsible for collecting the data;
     *
     * @param endpointService
     * @param completionHandler
     * @return the collector object
     */
    protected abstract Runnable createCollector(SamplingService<L> endpointService, Consumer<D> completionHandler);

    /**
     * Given a resource, this returns the measurement instances that this scheduler should collect for it.
     * This does NOT return the currently scheduled measurements - this returns a set of collections
     * that can be used to add to the scheduler in order to begin collecting them.
     *
     * @param resource the resource whose measurement instances are to be returned
     * @return the measurement instances that need to be collected for this resource
     */
    protected abstract Collection<ScheduledMeasurementInstance<L, T>> getScheduledMeasurementInstances(
            Resource<L> resource);
}
