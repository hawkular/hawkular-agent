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

import java.util.Set;

import org.hawkular.agent.monitor.api.SamplingService;
import org.hawkular.agent.monitor.inventory.MeasurementInstance;
import org.hawkular.agent.monitor.inventory.MetricType;
import org.hawkular.agent.monitor.log.AgentLoggers;
import org.hawkular.agent.monitor.log.MsgLogger;
import org.hawkular.agent.monitor.storage.MetricDataPoint;
import org.hawkular.agent.monitor.util.Consumer;

/**
 * Defines a job that collects metric data from a particular monitored endpoint.
 *
 * @param <L> defines the class that the endpoint needs to locate the metric attributes
 */
class MetricsCollector<L> extends MeasurementCollector<L, MetricType<L>, MetricDataPoint> implements Runnable {
    static final MsgLogger LOG = AgentLoggers.getLogger(MetricsCollector.class);

    public MetricsCollector(SamplingService<L> endpointService,
            ScheduledCollectionsQueue<L, MetricType<L>> priorityQueue,
            Consumer<MetricDataPoint> completionHandler) {
        super(endpointService, priorityQueue, completionHandler);
    }

    /**
     * This actually collects the metrics and notifies the handler when complete.
     */
    @Override
    public void run() {
        while (!Thread.interrupted()) {
            try {
                ScheduledCollectionsQueue<L, MetricType<L>> queue = getScheduledCollectionsQueue();
                long next = queue.getNextExpectedCollectionTime();

                if (next == Long.MIN_VALUE) {
                    Thread.sleep(10_000); // nothing scheduled; sleep for a bit and see if we get something later
                } else {
                    long delay = next - System.currentTimeMillis();
                    if (delay <= 0) {
                        // we're late, we're late, for a very important date - collect now
                        Set<MeasurementInstance<L, MetricType<L>>> instances = queue.popNextScheduledSet();
                        getEndpointService().measureMetrics(instances, new Consumer<MetricDataPoint>() {
                            @Override
                            public void accept(MetricDataPoint dataPoint) {
                                getCompletionHandler().accept(dataPoint);
                            }

                            @Override
                            public void report(Throwable e) {
                                LOG.errorFailedToStoreMetrics(getEndpointService().toString(), e);
                                getCompletionHandler().report(e);
                            }
                        });
                    } else {
                        // wait for the amount of time before the next collection is scheduled
                        Thread.sleep(delay);
                    }
                }
            } catch (InterruptedException ie) {
                return;
            } catch (IllegalStateException ise) {
                LOG.debugf("Cannot collect metrics for endpoint [%s] - not ready yet: %s", getEndpointService(), ise);
            } catch (Throwable t) {
                LOG.warnf(t, "Unexpected error caught in MetricsCollector for endpoint [%s]", getEndpointService());
            }
        }
    }
}