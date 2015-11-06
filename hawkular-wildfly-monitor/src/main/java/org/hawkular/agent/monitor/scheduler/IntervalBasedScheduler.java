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
 * @author John Mazzitelli
 * @author <a href="https://github.com/ppalaga">Peter Palaga</a>
 *
 * @param <T> the sublclass of {@link MeasurementType} to handle
 * @param <D> the {@link DataPoint} type
 */
public abstract class IntervalBasedScheduler<T extends MeasurementType<Object>, //
D extends DataPoint> {

    private static class MetricsJob<L, E extends MonitoredEndpoint> implements Runnable {
        private final SamplingService<L, E> endpointService;
        private final Collection<MeasurementInstance<L, MetricType<L>>> instances;
        private final Consumer<MetricDataPoint> completionHandler;

        public MetricsJob(SamplingService<L, E> endpointService,
                Collection<MeasurementInstance<L, MetricType<L>>> instances,
                Consumer<MetricDataPoint> completionHandler) {
            super();
            this.endpointService = endpointService;
            this.instances = instances;
            this.completionHandler = completionHandler;
        }

        /** @see java.lang.Runnable#run() */
        @Override
        public void run() {

            endpointService.measureMetrics(instances, new Consumer<MetricDataPoint>() {
                @Override
                public void accept(MetricDataPoint dataPoint) {
                    completionHandler.accept(dataPoint);
                }

                @Override
                public void report(Throwable e) {
                    log.errorCouldNotAccess(endpointService.getEndpoint(), e);
                }
            });

        }

    }

    private static class AvailsJob<L, E extends MonitoredEndpoint> implements Runnable {
        private final SamplingService<L, E> endpointService;
        private final Collection<MeasurementInstance<L, AvailType<L>>> instances;
        private final Consumer<AvailDataPoint> completionHandler;

        public AvailsJob(SamplingService<L, E> endpointService,
                Collection<MeasurementInstance<L, AvailType<L>>> instances,
                Consumer<AvailDataPoint> completionHandler) {
            super();
            this.endpointService = endpointService;
            this.instances = instances;
            this.completionHandler = completionHandler;
        }

        /** @see java.lang.Runnable#run() */
        @Override
        public void run() {
            endpointService.measureAvails(instances, new Consumer<AvailDataPoint>() {
                @Override
                public void accept(AvailDataPoint dataPoint) {
                    completionHandler.accept(dataPoint);
                }

                @Override
                public void report(Throwable e) {
                    log.errorCouldNotAccess(endpointService.getEndpoint(), e);
                }
            });

        }

    }

    private static final MsgLogger log = AgentLoggers.getLogger(IntervalBasedScheduler.class);

    public static IntervalBasedScheduler<MetricType<Object>, MetricDataPoint> forMetrics(
            String name, int schedulerThreads, Consumer<MetricDataPoint> completionHandler) {
        return new IntervalBasedScheduler<MetricType<Object>, MetricDataPoint>(name, schedulerThreads,
                completionHandler) {
            @Override
            protected <L, E extends MonitoredEndpoint, TT extends MeasurementType<L>> Runnable createJob(
                    SamplingService<L, E> endpointService, Collection<MeasurementInstance<L, TT>> instances,
                    Consumer<MetricDataPoint> completionHandler) {
                @SuppressWarnings("unchecked")
                Collection<MeasurementInstance<L, MetricType<L>>> insts =
                        (Collection<MeasurementInstance<L, MetricType<L>>>) (Collection<?>) instances;
                return new MetricsJob<L, E>(endpointService, insts, completionHandler);
            }

            @SuppressWarnings("unchecked")
            @Override
            protected <L, TT extends MeasurementType<L>> Collection<MeasurementInstance<L, TT>>
                    getMeasurementInstances(Resource<L> resource) {
                return (Collection<MeasurementInstance<L, TT>>) (Collection<?>) resource.getMetrics();
            }
        };
    }

    public static IntervalBasedScheduler<AvailType<Object>, AvailDataPoint>
            forAvails(
                    String name, int schedulerThreads, Consumer<AvailDataPoint> completionHandler) {
        return new IntervalBasedScheduler<AvailType<Object>, AvailDataPoint>(name, schedulerThreads,
                completionHandler) {
            @Override
            protected <L, E extends MonitoredEndpoint, TT extends MeasurementType<L>> Runnable createJob(
                    SamplingService<L, E> endpointService, Collection<MeasurementInstance<L, TT>> instances,
                    Consumer<AvailDataPoint> completionHandler) {
                @SuppressWarnings("unchecked")
                Collection<MeasurementInstance<L, AvailType<L>>> insts =
                        (Collection<MeasurementInstance<L, AvailType<L>>>) (Collection<?>) instances;
                return new AvailsJob<L, E>(endpointService, insts, completionHandler);
            }

            @SuppressWarnings("unchecked")
            @Override
            protected <L, TT extends MeasurementType<L>> Collection<MeasurementInstance<L, TT>>
                    getMeasurementInstances(Resource<L> resource) {
                return (Collection<MeasurementInstance<L, TT>>) (Collection<?>) resource.getAvails();
            }

        };
    }

    private final ScheduledExecutorService executorService;
    private final Map<MonitoredEndpoint, List<ScheduledFuture<?>>> jobs = new HashMap<>();
    private final Consumer<D> completionHandler;

    protected volatile ServiceStatus status = ServiceStatus.INITIAL;

    private IntervalBasedScheduler(String name, int schedulerThreads, Consumer<D> completionHandler) {
        this.completionHandler = completionHandler;
        ThreadFactory threadFactory = ThreadFactoryGenerator.generateFactory(true, name);
        this.executorService = Executors.newScheduledThreadPool(schedulerThreads, threadFactory);
    }

    public <L, E extends MonitoredEndpoint, TT extends MeasurementType<L>> void
            rescheduleAll(SamplingService<L, E> endpointService, List<Resource<L>> resources) {
        status.assertRunning(getClass(), "rescheduleAll()");

        // FIXME: consider if we need to lock the jobs here and elsewhere

        E endpoint = endpointService.getEndpoint();

        /* kill the old jobs first */
        List<ScheduledFuture<?>> oldJobs = jobs.get(endpoint);
        if (oldJobs != null) {
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

        for (Entry<Interval, Collection<MeasurementInstance<L, TT>>> en : instancesByInterval.entrySet()) {
            Interval interval = en.getKey();
            Collection<MeasurementInstance<L, TT>> instances = en.getValue();
            ScheduledFuture<?> future = executorService.scheduleWithFixedDelay(
                    createJob(endpointService, instances, completionHandler),
                    0,
                    interval.millis(),
                    java.util.concurrent.TimeUnit.MILLISECONDS);
            endpointJobs.add(future);

        }

        jobs.put(endpointService.getEndpoint(), endpointJobs);
    }

    public <L, E extends MonitoredEndpoint, TT extends MeasurementType<L>> void
            schedule(SamplingService<L, E> endpointService, List<Resource<L>> resources) {
        status.assertRunning(getClass(), "schedule()");
        // TODO add resources to scheduled ones
    }

    public <L, E extends MonitoredEndpoint, TT extends MeasurementType<L>> void
            unschedule(SamplingService<L, E> endpointService, List<Resource<L>> resources) {
        status.assertRunning(getClass(), "unschedule()");
        // TODO remove resources from scheduled ones
    }

    protected abstract <L, E extends MonitoredEndpoint, TT extends MeasurementType<L>> Runnable createJob(
            SamplingService<L, E> endpointService,
            Collection<MeasurementInstance<L, TT>> instances, Consumer<D> completionHandler);

    protected abstract <L, TT extends MeasurementType<L>> Collection<MeasurementInstance<L, TT>>
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

        try {
            for (List<ScheduledFuture<?>> perEndpointJobs : jobs.values()) {
                for (ScheduledFuture<?> job : perEndpointJobs) {
                    job.cancel(false);
                }
            }
            executorService.shutdown();
            executorService.awaitTermination(5, TimeUnit.SECONDS);

        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt(); // Preserve interrupt status
        } finally {
            status = ServiceStatus.STOPPED;
        }
    }

}
