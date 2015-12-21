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
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;

import org.hawkular.agent.monitor.api.SamplingService;
import org.hawkular.agent.monitor.inventory.AvailType;
import org.hawkular.agent.monitor.inventory.MeasurementType;
import org.hawkular.agent.monitor.inventory.MetricType;
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
     * @param completionHandler object that is notified of metric values when they are collected
     *
     * @return the new metric collection scheduler
     */
    public static <LL> MeasurementScheduler<LL, MetricType<LL>, MetricDataPoint> forMetrics(
            String name, Consumer<MetricDataPoint> completionHandler) {

        return new MeasurementScheduler<LL, MetricType<LL>, MetricDataPoint>(name, completionHandler) {

            /**
             * @return the collector that will be used to get metrics for resources at the given endpoint.
             */
            @Override
            protected Runnable createCollector(SamplingService<LL> endpointService,
                    ScheduledCollectionsQueue<LL, MetricType<LL>> queue, Consumer<MetricDataPoint> completionHandler) {
                return new MetricsCollector<LL>(endpointService, queue, completionHandler);
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
     * @param completionHandler object that is notified of availability results when they are checked
     *
     * @return the new availability checking scheduler
     */
    public static <LL> MeasurementScheduler<LL, AvailType<LL>, AvailDataPoint> forAvails(
            String name, Consumer<AvailDataPoint> completionHandler) {

        return new MeasurementScheduler<LL, AvailType<LL>, AvailDataPoint>(name, completionHandler) {

            /**
             * @return the collector that will be used to check availabilities for resources at the given endpoint.
             */
            @Override
            protected Runnable createCollector(SamplingService<LL> endpointService,
                    ScheduledCollectionsQueue<LL, AvailType<LL>> queue, Consumer<AvailDataPoint> completionHandler) {
                return new AvailsCollector<LL>(endpointService, queue, completionHandler);
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

    /** thread pool used by the scheduler to execute the different metrics/avails jobs. */
    private final ExecutorService executorService;

    /** prioritized queue for each endpoint that indicates what metrics are next to be collected */
    private final Map<SamplingService<L>, ScheduledCollectionsQueue<L, T>> queues = new HashMap<>();

    /** object that will be notified when metric data or avail results have been collected and ready to be stored */
    private final Consumer<D> completionHandler;

    /** lifecycle status of the scheduler itself */
    protected volatile ServiceStatus status = ServiceStatus.INITIAL;

    /**
     * The actual scheduler constructor.
     * To build schedulers, call {@link #forMetrics(String, Consumer)} or {@link #forAvails(String, Consumer)}.
     *
     * @param name name of scheduler
     * @param completionHandler object notified when a job is done and its data needs to be stored
     */
    private MeasurementScheduler(String name, Consumer<D> completionHandler) {
        this.name = name;
        this.completionHandler = completionHandler;
        ThreadFactory threadFactory = ThreadFactoryGenerator.generateFactory(true, name);
        this.executorService = Executors.newCachedThreadPool(threadFactory);
    }

    /**
     * Schedules collections for all measurements for the given resources.
     *
     * @param endpointService defines where the resources are
     * @param resources the resources whose metric collections/avail checks are to be added to the scheduler
     */
    public void schedule(SamplingService<L> endpointService, List<Resource<L>> resources) {
        status.assertRunning(getClass(), "schedule()");

        List<ScheduledMeasurementInstance<L, T>> schedules = new ArrayList<>();
        resources.forEach(r -> schedules.addAll(getScheduledMeasurementInstances(r)));
        ScheduledCollectionsQueue<L, T> queue = createOrGetScheduledCollectionsQueue(endpointService);
        queue.schedule(schedules);

        LOG.debugf("Scheduler [%s]: [%d] measurements for [%d] resources have been scheduled for endpoint [%s]",
                this.name, schedules.size(), resources.size(), endpointService);
    }

    /**
     * Removes any existing collections that are scheduled for the given resources.
     *
     * @param endpointService defines where the resources are
     * @param resources the resources whose metric collections/avail checks are to be removed from the scheduler
     */
    public void unschedule(SamplingService<L> endpointService, List<Resource<L>> resources) {
        status.assertRunning(getClass(), "unschedule()");

        ScheduledCollectionsQueue<L, T> queue = getScheduledCollectionsQueue(endpointService);
        if (queue != null) {
            queue.unschedule(resources);
        }

        LOG.debugf("Scheduler [%s]: all measurements for [%d] resources have been unscheduled for endpoint [%s]",
                this.name, resources.size(), endpointService);
    }

    /**
     * Call this to get the scheduled collections queue for the given endpoint. If it doesn't yet exist
     * one will be created and its collector thread will be created.
     *
     * @param endpointService the endpoint service whose queue is to be retrieved (and created if necessary)
     * @return the queue assigned to the given endpoint service
     */
    private ScheduledCollectionsQueue<L, T> createOrGetScheduledCollectionsQueue(
            SamplingService<L> endpointService) {

        synchronized (this.queues) {
            ScheduledCollectionsQueue<L, T> q = this.queues.get(endpointService);
            if (q == null) {
                q = new ScheduledCollectionsQueue<L, T>();
                this.queues.put(endpointService, q);

                // create our collector thread to start processing the collections
                Runnable collector = createCollector(endpointService, q, completionHandler);
                this.executorService.submit(collector);
            }
            return q;
        }
    }

    /**
     * Returns the scheduled collections queue associated with the given endpoint service. If one has not been
     * created yet null is returned.
     *
     * @param endpointService
     * @return the queue assigned to the given endpoint service or null if the endpoint service
     *         does not have a priority queue
     *
     * @see #createOrGetScheduledCollectionsQueue(SamplingService)
     */
    private ScheduledCollectionsQueue<L, T> getScheduledCollectionsQueue(SamplingService<L> endpointService) {
        synchronized (this.queues) {
            ScheduledCollectionsQueue<L, T> q = this.queues.get(endpointService);
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

        LOG.debugf("Stopping scheduler [%s]", this.name);

        try {
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
     * @param endpointService the resources whose data is to be collected are managed by this endpoint
     * @param queue contains the scheduled measurements
     * @param completionHandler handler to process the measurement collection results
     * @return the collector object
     */
    protected abstract Runnable createCollector(SamplingService<L> endpointService,
            ScheduledCollectionsQueue<L, T> queue, Consumer<D> completionHandler);

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
