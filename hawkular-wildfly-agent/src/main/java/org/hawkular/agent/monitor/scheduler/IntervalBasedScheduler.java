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
 * Schedules metric collections and avail checks and will invoke completion handlers to store that data.
 *
 * @author John Mazzitelli
 * @author <a href="https://github.com/ppalaga">Peter Palaga</a>
 *
 * @param <T> the sublclass of {@link MeasurementType} to handle (such as metric types or avail types)
 * @param <D> the {@link DataPoint} type (such as metric data or avail data)
 */
public abstract class IntervalBasedScheduler<T extends MeasurementType<Object>, D extends DataPoint> {

    /**
     * Defines a job that collects metric data from a particular monitored endpoint.
     *
     * @param <L> defines the class that the endpoint needs to locate the metric attributes
     */
    private static class MetricsJob<L> implements Runnable {
        private final SamplingService<L> endpointService;
        private final Collection<MeasurementInstance<L, MetricType<L>>> instances;
        private final Consumer<MetricDataPoint> completionHandler;

        /**
         * Creates a job that is used to collect metrics (as defined by the given measurement instances)
         * from the given monitored endpoint. When a metric is collected (or if the metric collection failed
         * for some reason) the given <code>completionHandler</code> will be notified. The completion
         * handler should store the metric data appropriately.
         *
         * @param endpointService where the resource is whose metrics are being collected
         * @param instances the metrics that are to be collected
         * @param completionHandler when the metric values are found (or if an error occurs) this object is notified
         */
        public MetricsJob(SamplingService<L> endpointService,
                Collection<MeasurementInstance<L, MetricType<L>>> instances,
                Consumer<MetricDataPoint> completionHandler) {
            super();
            this.endpointService = endpointService;
            this.instances = instances;
            this.completionHandler = completionHandler;
        }

        /**
         * This actually collects the metrics and notifies the handler when complete.
         */
        @Override
        public void run() {
            try {
                endpointService.measureMetrics(instances, new Consumer<MetricDataPoint>() {
                    @Override
                    public void accept(MetricDataPoint dataPoint) {
                        completionHandler.accept(dataPoint);
                    }

                    @Override
                    public void report(Throwable e) {
                        log.errorFailedToStoreMetrics(endpointService.toString(), e);
                        completionHandler.report(e);
                    }
                });
            } catch (IllegalStateException ise) {
                log.debugf("Cannot collect metrics for endpoint [%s] - not ready yet: %s", this.endpointService, ise);
            } catch (Throwable t) {
                log.warnf(t, "Unexpected error caught in MetricsJob for endpoint [%s]", this.endpointService);
            }
        }
    }

    /**
     * Defines a job that performs availability checks for resources at a particular monitored endpoint.
     *
     * @param <L> defines the class that the endpoint needs to locate the avail attributes
     */
    private static class AvailsJob<L> implements Runnable {
        private final SamplingService<L> endpointService;
        private final Collection<MeasurementInstance<L, AvailType<L>>> instances;
        private final Consumer<AvailDataPoint> completionHandler;

        /**
         * Creates a job that is used to perform avail checks (as defined by the given measurement instances)
         * from the given monitored endpoint. When an avail check is performed (or if it failed
         * for some reason) the given <code>completionHandler</code> will be notified. The completion
         * handler should store the results of the availability check appropriately.
         *
         * @param endpointService where the resource is whose avail checks are being performed
         * @param instances the availability checks that are to be performed
         * @param completionHandler when the avail check results are in (or if an error occurs) this object is notified
         */
        public AvailsJob(SamplingService<L> endpointService,
                Collection<MeasurementInstance<L, AvailType<L>>> instances,
                Consumer<AvailDataPoint> completionHandler) {
            super();
            this.endpointService = endpointService;
            this.instances = instances;
            this.completionHandler = completionHandler;
        }

        /**
         * This actually performs the availability checks and notifies the handler when complete.
         */
        @Override
        public void run() {
            try {
                endpointService.measureAvails(instances, new Consumer<AvailDataPoint>() {
                    @Override
                    public void accept(AvailDataPoint dataPoint) {
                        completionHandler.accept(dataPoint);
                    }

                    @Override
                    public void report(Throwable e) {
                        log.errorFailedToStoreAvails(endpointService.toString(), e);
                        completionHandler.report(e);
                    }
                });
            } catch (IllegalStateException ise) {
                log.debugf("Cannot check avails for endpoint [%s] - not ready yet: %s", this.endpointService, ise);
            } catch (Throwable t) {
                log.warnf(t, "Unexpected error caught in AvailsJob for endpoint [%s]", this.endpointService);
            }
        }
    }

    private static final MsgLogger log = AgentLoggers.getLogger(IntervalBasedScheduler.class);

    /**
     * Static method that builds a scheduler (along with its thread pool) for metric collection.
     *
     * @param name the name of the scheduler (used for things like naming the threads)
     * @param schedulerThreads number of core threads in the thread pool
     * @param completionHandler object that is notified of metric values when they are collected
     *
     * @return the new metric collection scheduler
     */
    public static IntervalBasedScheduler<MetricType<Object>, MetricDataPoint> forMetrics(
            String name,
            int schedulerThreads,
            Consumer<MetricDataPoint> completionHandler) {

        return new IntervalBasedScheduler<MetricType<Object>, MetricDataPoint>(name, schedulerThreads,
                completionHandler) {

            /**
             * @return a MetricsJob that can be scheduled which will collect the metrics defined by the parameters.
             */
            @Override
            protected <L, MT extends MeasurementType<L>> Runnable createJob(
                    SamplingService<L> endpointService,
                    Collection<MeasurementInstance<L, MT>> instances,
                    Consumer<MetricDataPoint> completionHandler) {
                @SuppressWarnings("unchecked")
                Collection<MeasurementInstance<L, MetricType<L>>> insts = //
                (Collection<MeasurementInstance<L, MetricType<L>>>) (Collection<?>) instances;
                return new MetricsJob<L>(endpointService, insts, completionHandler);
            }

            /**
             * @return all the defined metric instances for the given resource.
             */
            @SuppressWarnings("unchecked")
            @Override
            protected <L, MT extends MeasurementType<L>> Collection<MeasurementInstance<L, MT>> //
            getMeasurementInstances(Resource<L> resource) {
                return (Collection<MeasurementInstance<L, MT>>) (Collection<?>) resource.getMetrics();
            }
        };
    }

    /**
     * Static method that builds a scheduler (along with its thread pool) for availability checking.
     *
     * @param name the name of the scheduler (used for things like naming the threads)
     * @param schedulerThreads number of core threads in the thread pool
     * @param completionHandler object that is notified of availability results when they are checked
     *
     * @return the new availability checking scheduler
     */
    public static IntervalBasedScheduler<AvailType<Object>, AvailDataPoint> forAvails(
            String name,
            int schedulerThreads,
            Consumer<AvailDataPoint> completionHandler) {

        return new IntervalBasedScheduler<AvailType<Object>, AvailDataPoint>(name, schedulerThreads,
                completionHandler) {

            /**
             * @return a AvailsJob that can be scheduled which will peform the necessary availability checks
             * defined by the parameters.
             */
            @Override
            protected <L, MT extends MeasurementType<L>> Runnable createJob(
                    SamplingService<L> endpointService,
                    Collection<MeasurementInstance<L, MT>> instances,
                    Consumer<AvailDataPoint> completionHandler) {
                @SuppressWarnings("unchecked")
                Collection<MeasurementInstance<L, AvailType<L>>> insts = //
                (Collection<MeasurementInstance<L, AvailType<L>>>) (Collection<?>) instances;
                return new AvailsJob<L>(endpointService, insts, completionHandler);
            }

            /**
             * @return all the defined avail instances for the given resource.
             */
            @SuppressWarnings("unchecked")
            @Override
            protected <L, MT extends MeasurementType<L>> Collection<MeasurementInstance<L, MT>> //
            getMeasurementInstances(Resource<L> resource) {
                return (Collection<MeasurementInstance<L, MT>>) (Collection<?>) resource.getAvails();
            }

        };
    }

    /** the name of the scheduler */
    private final String name;

    /** thread pool used by the scheduler to execute the difference metrics/avails jobs. */
    private final ScheduledExecutorService executorService;

    /** jobs that are to be executed - note the jobs are grouped by monitored endpoint. */
    private final Map<MonitoredEndpoint, List<ScheduledFuture<?>>> jobs = new HashMap<>();

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
    private IntervalBasedScheduler(String name, int schedulerThreads, Consumer<D> completionHandler) {
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
    public <L, TT extends MeasurementType<L>> void rescheduleAll(
            SamplingService<L> endpointService,
            List<Resource<L>> resources) {

        status.assertRunning(getClass(), "rescheduleAll()");

        // FIXME: consider if we need to lock the jobs here and elsewhere

        MonitoredEndpoint endpoint = endpointService.getEndpoint();

        // if there are any jobs currently running for the given endpoint, cancel them now
        List<ScheduledFuture<?>> oldJobs = jobs.get(endpoint);
        if (oldJobs != null) {
            log.debugf("Scheduler [%s]: canceling [%d] jobs for endpoint [%s]",
                    this.name, oldJobs.size(), endpointService);

            for (ScheduledFuture<?> oldJob : oldJobs) {
                oldJob.cancel(false);
            }
        }

        List<ScheduledFuture<?>> endpointJobs = new ArrayList<>();
        Map<Interval, Collection<MeasurementInstance<L, TT>>> instancesByInterval = new HashMap<>();
        for (Resource<L> resource : resources) {
            Collection<MeasurementInstance<L, TT>> resourceInstances = getMeasurementInstances(resource);
            for (MeasurementInstance<L, TT> instance : resourceInstances) {
                Interval interval = instance.getType().getInterval();
                Collection<MeasurementInstance<L, TT>> instances = instancesByInterval.get(interval);
                if (instances == null) {
                    instances = new ArrayList<>();
                    instancesByInterval.put(interval, instances);
                }
                instances.add(instance);
            }
        }

        int measurementInstances = 0;
        for (Entry<Interval, Collection<MeasurementInstance<L, TT>>> en : instancesByInterval.entrySet()) {
            Interval interval = en.getKey();
            Collection<MeasurementInstance<L, TT>> instances = en.getValue();
            ScheduledFuture<?> future = executorService.scheduleWithFixedDelay(
                    createJob(endpointService, instances, completionHandler),
                    0,
                    interval.millis(),
                    java.util.concurrent.TimeUnit.MILLISECONDS);
            endpointJobs.add(future);
            measurementInstances += instances.size();
        }

        jobs.put(endpointService.getEndpoint(), endpointJobs);
        log.debugf("Scheduler [%s]: [%d] jobs ([%d] measurements) have been submitted for endpoint [%s]",
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
    public <L, TT extends MeasurementType<L>> void schedule(
            SamplingService<L> endpointService,
            List<Resource<L>> resources) {
        status.assertRunning(getClass(), "schedule()");
        // TODO add resources to scheduled ones
        log.warn("TODO: SCHEDULE() IS NOT IMPLEMENTED");
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
    public <L, TT extends MeasurementType<L>> void unschedule(
            SamplingService<L> endpointService,
            List<Resource<L>> resources) {
        status.assertRunning(getClass(), "unschedule()");
        // TODO remove resources from scheduled ones
        log.warn("TODO: UNSCHEDULE() IS NOT IMPLEMENTED");
    }

    protected abstract <L, MT extends MeasurementType<L>> Runnable createJob(
            SamplingService<L> endpointService,
            Collection<MeasurementInstance<L, MT>> instances,
            Consumer<D> completionHandler);

    protected abstract <L, MT extends MeasurementType<L>> Collection<MeasurementInstance<L, MT>> //
    getMeasurementInstances(Resource<L> resource);

    public void start() {
        status.assertInitialOrStopped(getClass(), "start()");
        status = ServiceStatus.STARTING;

        // nothing to do here

        status = ServiceStatus.RUNNING;

    }

    public void stop() {
        status.assertRunning(getClass(), "stop()");
        status = ServiceStatus.STOPPING;

        log.infof("Stopping scheduler [%s] and its [%d] jobs", this.name, jobs.size());

        try {
            for (List<ScheduledFuture<?>> perEndpointJobs : jobs.values()) {
                for (ScheduledFuture<?> job : perEndpointJobs) {
                    job.cancel(false);
                }
            }
            executorService.shutdown();
            executorService.awaitTermination(5, TimeUnit.SECONDS);
            log.infof("Scheduler [%s] stopped", this.name);

        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt(); // Preserve interrupt status
        } finally {
            status = ServiceStatus.STOPPED;
        }
    }

}
