/*
 * Copyright 2015-2017 Red Hat, Inc. and/or its affiliates
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
import org.hawkular.agent.monitor.inventory.AvailType;
import org.hawkular.agent.monitor.inventory.MeasurementInstance;
import org.hawkular.agent.monitor.log.AgentLoggers;
import org.hawkular.agent.monitor.log.MsgLogger;
import org.hawkular.agent.monitor.storage.AvailDataPoint;
import org.hawkular.agent.monitor.util.Consumer;

/**
 * Defines a job that performs availability checking for a particular monitored endpoint.
 *
 * @param <L> defines the class that the endpoint needs to determine what availability to check
 */
class AvailsCollector<L> extends MeasurementCollector<L, AvailType<L>, AvailDataPoint> implements Runnable {
    static final MsgLogger LOG = AgentLoggers.getLogger(AvailsCollector.class);

    public AvailsCollector(SamplingService<L> endpointService,
            ScheduledCollectionsQueue<L, AvailType<L>> priorityQueue,
            Consumer<AvailDataPoint> completionHandler) {
        super(endpointService, priorityQueue, completionHandler);
    }

    /**
     * This actually checks availability and notifies the handler when complete.
     */
    @Override
    public void run() {
        while (!Thread.interrupted()) {
            try {
                ScheduledCollectionsQueue<L, AvailType<L>> queue = getScheduledCollectionsQueue();
                long next = queue.getNextExpectedCollectionTime();

                if (next == Long.MIN_VALUE) {
                    Thread.sleep(10_000); // nothing scheduled; sleep for a bit and see if we get something later
                } else {
                    long delay = next - System.currentTimeMillis();
                    if (delay <= 0) {
                        // we're late, we're late, for a very important date - collect now
                        Set<MeasurementInstance<L, AvailType<L>>> instances = queue.popNextScheduledSet();
                        getEndpointService().measureAvails(instances, new Consumer<AvailDataPoint>() {
                            @Override
                            public void accept(AvailDataPoint dataPoint) {
                                getCompletionHandler().accept(dataPoint);
                            }

                            @Override
                            public void report(Throwable e) {
                                LOG.errorFailedToStoreAvails(getEndpointService().toString(), e);
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
                LOG.debugf("Cannot check avails for endpoint [%s] - not ready yet: %s", getEndpointService(), ise);
            } catch (Throwable t) {
                LOG.warnf(t, "Unexpected error caught in AvailsCollector for endpoint [%s]", getEndpointService());
            }
        }
    }
}