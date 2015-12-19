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

import org.hawkular.agent.monitor.api.SamplingService;
import org.hawkular.agent.monitor.inventory.MeasurementType;
import org.hawkular.agent.monitor.log.AgentLoggers;
import org.hawkular.agent.monitor.log.MsgLogger;
import org.hawkular.agent.monitor.storage.DataPoint;
import org.hawkular.agent.monitor.util.Consumer;

/**
 * Superclass of measurement collector jobs.
 *
 * @param <L> defines the class that the endpoint needs to locate the metric attributes
 * @param <T> defines the class of the type of measurement to be collected (e.g. metric or availability)
 * @param <D> defines the class that is the data point that can contain the measurement data
 */
public abstract class MeasurementCollector<L, T extends MeasurementType<L>, D extends DataPoint> implements Runnable {
    static final MsgLogger LOG = AgentLoggers.getLogger(MeasurementCollector.class);

    private final SamplingService<L> endpointService;
    private final ScheduledCollectionsQueue<L, T> priorityQueue;
    private final Consumer<D> completionHandler;

    /**
     * Creates a job that is used to collect measurements provided by the priority queue
     * from the given monitored endpoint. When a measurement is collected (or if the collection failed
     * for some reason) the given <code>completionHandler</code> will be notified. The completion
     * handler should store the data appropriately.
     *
     * @param endpointService where the resource is whose data is to be collected
     * @param priorityQueue the queue that determines what is scheduled next for collection
     * @param completionHandler when the data are found (or if an error occurs) this object is notified
     */
    public MeasurementCollector(SamplingService<L> endpointService,
            ScheduledCollectionsQueue<L, T> priorityQueue,
            Consumer<D> completionHandler) {
        this.endpointService = endpointService;
        this.priorityQueue = priorityQueue;
        this.completionHandler = completionHandler;
    }

    protected SamplingService<L> getEndpointService() {
        return endpointService;
    }

    protected ScheduledCollectionsQueue<L, T> getPriorityQueue() {
        return priorityQueue;
    }

    protected Consumer<D> getCompletionHandler() {
        return completionHandler;
    }
}