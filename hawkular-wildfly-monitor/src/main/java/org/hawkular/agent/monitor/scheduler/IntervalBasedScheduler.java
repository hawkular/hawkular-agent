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

import org.hawkular.agent.monitor.inventory.AvailType;
import org.hawkular.agent.monitor.inventory.Interval;
import org.hawkular.agent.monitor.inventory.MeasurementInstance;
import org.hawkular.agent.monitor.inventory.MeasurementType;
import org.hawkular.agent.monitor.inventory.MetricType;
import org.hawkular.agent.monitor.inventory.MonitoredEndpoint;
import org.hawkular.agent.monitor.inventory.NodeLocation;
import org.hawkular.agent.monitor.inventory.Resource;
import org.hawkular.agent.monitor.log.AgentLoggers;
import org.hawkular.agent.monitor.log.MsgLogger;
import org.hawkular.agent.monitor.protocol.EndpointService;
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
 * @param <L> the type of the protocol specific location, typically a subclass of {@link NodeLocation}
 * @param <T> the sublclass of {@link MeasurementType} to handle
 * @param <D> the {@link DataPoint} type
 */
public abstract class IntervalBasedScheduler<L, T extends MeasurementType<L>, //
    D extends DataPoint> {

    private static class MetricsJob<L> implements Runnable {
        private final EndpointService<L, ?, ?> endpointService;
        private final Collection<MeasurementInstance<L, MetricType<L>>> instances;
        private final Consumer<MetricDataPoint> completionHandler;

        public MetricsJob(EndpointService<L, ?, ?> endpointService,
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

    private static class AvailsJob<L> implements Runnable {
        private final EndpointService<L, ?, ?> endpointService;
        private final Collection<MeasurementInstance<L, AvailType<L>>> instances;
        private final Consumer<AvailDataPoint> completionHandler;

        public AvailsJob(EndpointService<L, ?, ?> endpointService,
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

    public static <L> IntervalBasedScheduler<L, MetricType<L>, MetricDataPoint> forMetrics(
            String name, int schedulerThreads, Consumer<MetricDataPoint> completionHandler) {
        return new IntervalBasedScheduler<L, MetricType<L>, MetricDataPoint>(name, schedulerThreads,
                completionHandler) {
            @Override
            protected Runnable createJob(EndpointService<L, ?, ?> endpointService,
                    Collection<MeasurementInstance<L, MetricType<L>>> instances,
                    Consumer<MetricDataPoint> completionHandler) {
                return new MetricsJob<L>(endpointService, instances, completionHandler);
            }

            @Override
            protected Collection<MeasurementInstance<L, MetricType<L>>> getMeasurementInstances(
                    Resource<L> resource) {
                return resource.getMetrics();
            }
        };
    }

    public static <L> IntervalBasedScheduler<L, AvailType<L>, AvailDataPoint> forAvails(
            String name, int schedulerThreads, Consumer<AvailDataPoint> completionHandler) {
        return new IntervalBasedScheduler<L, AvailType<L>, AvailDataPoint>(name, schedulerThreads,
                completionHandler) {
            @Override
            protected Runnable createJob(EndpointService<L, ?, ?> endpointService,
                    Collection<MeasurementInstance<L, AvailType<L>>> instances,
                    Consumer<AvailDataPoint> completionHandler) {
                return new AvailsJob<L>(endpointService, instances, completionHandler);
            }

            @Override
            protected Collection<MeasurementInstance<L, AvailType<L>>> getMeasurementInstances(
                    Resource<L> resource) {
                return resource.getAvails();
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

    public void rescheduleAll(EndpointService<L, ?, ?> endpointService, List<Resource<L>> resources) {
        status.assertRunning(getClass(), "rescheduleAll()");

        // FIXME: consider if we need to lock the jobs here and elsewhere

        MonitoredEndpoint endpoint = endpointService.getEndpoint();

        /* kill the old jobs first */
        List<ScheduledFuture<?>> oldJobs = jobs.get(endpoint);
        if (oldJobs != null) {
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

        for (Entry<Interval, Collection<MeasurementInstance<L, T>>> en : instancesByInterval.entrySet()) {
            Interval interval = en.getKey();
            Collection<MeasurementInstance<L, T>> instances = en.getValue();
            ScheduledFuture<?> future = executorService.scheduleWithFixedDelay(
                    createJob(endpointService, instances, completionHandler),
                    0,
                    interval.millis(),
                    java.util.concurrent.TimeUnit.MILLISECONDS);
            endpointJobs.add(future);

        }

        jobs.put(endpointService.getEndpoint(), endpointJobs);
    }

    public void schedule(EndpointService<?, ?, ?> endpointService, List<Resource<L>> resources) {
        status.assertRunning(getClass(), "schedule()");
        // TODO add resources to scheduled ones
    }

    public void unschedule(EndpointService<?, ?, ?> endpointService, List<Resource<L>> resources) {
        status.assertRunning(getClass(), "unschedule()");
        // TODO remove resources from scheduled ones
    }

    protected abstract Runnable createJob(EndpointService<L, ?, ?> endpointService,
            Collection<MeasurementInstance<L, T>> instances, Consumer<D> completionHandler);

    protected abstract Collection<MeasurementInstance<L, T>> getMeasurementInstances(Resource<L> resource);

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
