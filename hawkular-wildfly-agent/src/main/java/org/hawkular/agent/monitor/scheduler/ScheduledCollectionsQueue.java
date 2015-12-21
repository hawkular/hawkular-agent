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

import java.util.Collection;
import java.util.HashSet;
import java.util.PriorityQueue;
import java.util.Set;

import org.hawkular.agent.monitor.inventory.MeasurementInstance;
import org.hawkular.agent.monitor.inventory.MeasurementType;
import org.hawkular.agent.monitor.inventory.Resource;
import org.hawkular.agent.monitor.log.AgentLoggers;
import org.hawkular.agent.monitor.log.MsgLogger;

/**
 * A priority queue that organizes measurement collections such that the next collection that needs
 * to be made is at the head of the queue.
 *
 * @author John Mazzitelli
 */
public class ScheduledCollectionsQueue<L, T extends MeasurementType<L>> {
    private static final MsgLogger LOG = AgentLoggers.getLogger(ScheduledCollectionsQueue.class);

    //  WARNING: make sure you synchronize access to this queue!
    //           Right now code just uses "synchronized" blocks, but should introduce R/W locks in future
    private final PriorityQueue<ScheduledMeasurementInstance<L, T>> priorityQueue;

    public ScheduledCollectionsQueue() {
        this.priorityQueue = new PriorityQueue<>();
    }

    /**
     * Returns the time when the next collection is to be done.
     * If there are no scheduled collections at all, this returns Long#MIN_VALUE.
     * Nothing is popped off the queue by this method.
     *
     * @return the time when the next expected collection time is to be
     */
    public long getNextExpectedCollectionTime() {
        synchronized (priorityQueue) {
            ScheduledMeasurementInstance<L, T> nextScheduledMeasurement = priorityQueue.peek();
            if (nextScheduledMeasurement == null) {
                return Long.MIN_VALUE;
            } else {
                return nextScheduledMeasurement.getNextCollectionTime();
            }
        }
    }

    /**
     * Pops off of the queue the set of the next measurements to be collected.
     * The returned set will be those measurements to be collected at the same time
     * (see {@link #getNextExpectedCollectionTime()}) but could be across multiple resources.
     *
     * A new set of measurements will be rescheduled according to their intervals and pushed back on the queue.
     *
     * If the next scheduled set of collections is to occur in the future or there are no schedules at all
     * then this returns an empty set. In other words, this returns those collections that need to be performed now.
     *
     * @return the next set of collections that need to be made
     */
    public Set<MeasurementInstance<L, T>> popNextScheduledSet() {

        Set<MeasurementInstance<L, T>> nextScheduledSet = new HashSet<>();

        synchronized (priorityQueue) {
            ScheduledMeasurementInstance<L, T> first = priorityQueue.peek();
            if ((first == null) || (first.getNextCollectionTime() > System.currentTimeMillis())) {
                // nothing is scheduled at all, or the next schedule is in the future
                return nextScheduledSet;
            }

            // Start picking things off the queue. We gobble up all the metrics at the head of the queue
            // next scheduled to be collected, but only for the same next collection time.
            ScheduledMeasurementInstance<L, T> next = first;
            long firstCollectionTime = first.getNextCollectionTime();
            while ((next != null) && (next.getNextCollectionTime() == firstCollectionTime)) {
                ScheduledMeasurementInstance<L, T> queueItem = priorityQueue.poll();
                nextScheduledSet.add(queueItem.getMeasurementInstance());

                // reschedule it
                queueItem.setNextCollectionTime();
                priorityQueue.offer(queueItem);
                LOG.debugf("Popped measurement off queue and rescheduled: %s", queueItem);

                // peek ahead at the next scheduled collection
                next = priorityQueue.peek();
            }
        }

        return nextScheduledSet;
    }

    /**
     * Puts the given schedules in the queue to be prioritized for collection.
     *
     * @param schedules the new schedules to add
     */
    public void schedule(Collection<ScheduledMeasurementInstance<L, T>> schedules) {
        synchronized (priorityQueue) {
            priorityQueue.addAll(schedules);
        }
    }

    /**
     * Unschedules all measurement collections for all given resources.
     *
     * @param resources all measurements for all these resources will be unscheduled
     */
    public void unschedule(Collection<Resource<L>> resources) {
        synchronized (priorityQueue) {
            priorityQueue.removeIf(mi -> resources.contains(mi.getResource()));
        }
    }
}