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

import java.util.Collection;
import java.util.Date;
import java.util.HashSet;
import java.util.Set;

import org.hawkular.agent.monitor.inventory.AvailType;
import org.hawkular.agent.monitor.inventory.MeasurementInstance;
import org.hawkular.agent.monitor.inventory.MeasurementType;
import org.hawkular.agent.monitor.inventory.MetricType;
import org.hawkular.agent.monitor.inventory.Resource;

/**
 * Determines when the next collection should occur for a given measurement instance for a given resource.
 */
public class ScheduledMeasurementInstance<L, T extends MeasurementType<L>>
        implements Comparable<ScheduledMeasurementInstance<L, T>> {

    /**
     * This will create scheduled metric instances for all metrics associated with the given resource.
     * Each will be scheduled for in the future based on the metric type interval.
     *
     * @param resource whose metric schedules are to be created
     * @return the scheduled metrics for the resource
     */
    public static <LL> Set<ScheduledMeasurementInstance<LL, MetricType<LL>>> createMetrics(Resource<LL> resource) {

        long now = System.currentTimeMillis(); // use the same time for all collection time calcs for better grouping
        Set<ScheduledMeasurementInstance<LL, MetricType<LL>>> set = new HashSet<>(resource.getMetrics().size());
        Collection<MeasurementInstance<LL, MetricType<LL>>> metrics = resource.getMetrics();
        for (MeasurementInstance<LL, MetricType<LL>> metric : metrics) {
            if (metric.getType().isDisabled()) {
                continue;
            }
            ScheduledMeasurementInstance<LL, MetricType<LL>> meas;
            meas = new ScheduledMeasurementInstance<LL, MetricType<LL>>(resource, metric);
            meas.setNextCollectionTime(metric.getType().getInterval().millis() + now);
            set.add(meas);
        }
        return set;
    }

    /**
     * This will create scheduled avail check instances for all availabilities associated with the given resource.
     * Each will be scheduled for in the future based on the avail type interval.
     *
     * @param resource whose avail check schedules are to be created
     * @return the scheduled avail checks for the resource
     */
    public static <LL> Set<ScheduledMeasurementInstance<LL, AvailType<LL>>> createAvails(Resource<LL> resource) {

        long now = System.currentTimeMillis(); // use the same time for all collection time calcs for better grouping
        Set<ScheduledMeasurementInstance<LL, AvailType<LL>>> set = new HashSet<>(resource.getAvails().size());
        Collection<MeasurementInstance<LL, AvailType<LL>>> avails = resource.getAvails();
        for (MeasurementInstance<LL, AvailType<LL>> avail : avails) {
            if (avail.getType().isDisabled()) {
                continue;
            }
            ScheduledMeasurementInstance<LL, AvailType<LL>> meas;
            meas = new ScheduledMeasurementInstance<LL, AvailType<LL>>(resource, avail);
            meas.setNextCollectionTime(avail.getType().getInterval().millis() + now);
            set.add(meas);
        }

        return set;
    }

    private final MeasurementInstance<L, T> measurementInstance;
    private final Resource<L> resource;
    private long nextCollectionTime;

    public ScheduledMeasurementInstance(Resource<L> resource, MeasurementInstance<L, T> measurementInstance) {

        if (resource == null) {
            throw new IllegalArgumentException("resource is null");
        }
        if (measurementInstance == null) {
            throw new IllegalArgumentException("measurementInstance is null");
        }

        this.resource = resource;
        this.measurementInstance = measurementInstance;
        this.nextCollectionTime = measurementInstance.getType().getInterval().millis() + System.currentTimeMillis();
    }

    public Resource<?> getResource() {
        return resource;
    }

    public MeasurementInstance<L, T> getMeasurementInstance() {
        return measurementInstance;
    }

    public long getNextCollectionTime() {
        return nextCollectionTime;
    }

    /**
     * This sets the next collection time to the given collection time.
     * If you just want to set the next collection time based on the collection interval,
     * use {@link #setNextCollectionTime()} instead.
     *
     * @param nextCollectionTime the new collection time when this measurement will be scheduled
     */
    public void setNextCollectionTime(long nextCollectionTime) {
        // round to the nearest second - this helps group schedule sets better
        nextCollectionTime = ((nextCollectionTime + 999) / 1000) * 1000;
        this.nextCollectionTime = nextCollectionTime;
    }

    /**
     * This will set the next collection time based on the measurement's collection interval
     * and the current time.
     */
    public void setNextCollectionTime() {
        long interval = getMeasurementInstance().getType().getInterval().millis();
        setNextCollectionTime(System.currentTimeMillis() + interval);
    }

    @Override
    public String toString() {
        return String.format("%s: resource=[%s], measurement=[%s], nextCollectionTime=[%s]",
                this.getClass().getName(), resource, measurementInstance, new Date(nextCollectionTime));
    }

    @Override
    public int compareTo(ScheduledMeasurementInstance<L, T> smi) {
        int n = (this.nextCollectionTime < smi.nextCollectionTime) ? -1
                : ((this.nextCollectionTime == smi.nextCollectionTime) ? 0 : 1);
        if (n != 0) {
            return n;
        }

        n = this.resource.getID().getIDString().compareTo(smi.resource.getID().getIDString());
        if (n != 0) {
            return n;
        }

        return this.measurementInstance.getID().getIDString().compareTo(smi.measurementInstance.getID().getIDString());
    }

    @Override
    public int hashCode() {
        final int prime = 31;
        int result = 1;
        result = prime * result + resource.hashCode();
        result = prime * result + measurementInstance.hashCode();
        result = prime * result + (int) (nextCollectionTime ^ (nextCollectionTime >>> 32));
        return result;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (!(obj instanceof ScheduledMeasurementInstance)) {
            return false;
        }

        @SuppressWarnings("unchecked")
        ScheduledMeasurementInstance<L, T> other = (ScheduledMeasurementInstance<L, T>) obj;

        if (!this.resource.equals(other.resource)) {
            return false;
        }

        if (!this.measurementInstance.equals(other.measurementInstance)) {
            return false;
        }

        if (this.nextCollectionTime != other.nextCollectionTime) {
            return false;
        }

        return true;
    }

}
