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
import java.util.Collections;
import java.util.Set;
import java.util.concurrent.TimeUnit;

import org.hawkular.agent.monitor.inventory.AttributeLocation;
import org.hawkular.agent.monitor.inventory.ID;
import org.hawkular.agent.monitor.inventory.Interval;
import org.hawkular.agent.monitor.inventory.MeasurementInstance;
import org.hawkular.agent.monitor.inventory.MetricType;
import org.hawkular.agent.monitor.inventory.Name;
import org.hawkular.agent.monitor.inventory.Resource;
import org.hawkular.agent.monitor.inventory.Resource.Builder;
import org.hawkular.agent.monitor.inventory.ResourceType;
import org.hawkular.agent.monitor.protocol.dmr.DMRNodeLocation;
import org.jboss.as.controller.client.helpers.MeasurementUnit;
import org.junit.Assert;
import org.junit.Test;

public class ScheduledCollectionsQueueTest {

    @Test
    public void testSimpleSchedule() throws InterruptedException {
        ScheduledCollectionsQueue<DMRNodeLocation, MetricType<DMRNodeLocation>> q = new ScheduledCollectionsQueue<>();
        Assert.assertEquals("Nothing scheduled!", Long.MIN_VALUE, q.getNextExpectedCollectionTime());
        Assert.assertTrue("Nothing scheduled!", q.getNextScheduledSet().isEmpty());
        
        int collInterval = 2;
        MeasurementInstance<DMRNodeLocation, MetricType<DMRNodeLocation>> measInstance;
        measInstance = createMeasurementInstance("metricName", createMetricType("metricTypeName", collInterval));
        Resource<DMRNodeLocation> resource = createResource("root", measInstance);
        Collection<ScheduledMeasurementInstance<DMRNodeLocation, MetricType<DMRNodeLocation>>> schedules;
        ScheduledMeasurementInstance<DMRNodeLocation, MetricType<DMRNodeLocation>> schedule;
        schedule = new ScheduledMeasurementInstance<>(resource, measInstance);
        q.schedule(Collections.singleton(schedule));

        // now that something is scheduled, let's see that the queue gives us the right answers
        Assert.assertTrue(q.getNextExpectedCollectionTime() > System.currentTimeMillis());
        Thread.sleep(collInterval * 1000); // wait for the collection time to pass
        Set<MeasurementInstance<DMRNodeLocation, MetricType<DMRNodeLocation>>> scheduledSet = q.getNextScheduledSet();
        Assert.assertEquals(1, scheduledSet.size());
        Assert.assertTrue(scheduledSet.contains(schedule.getMeasurementInstance()));
        
        // now unschedule and see the queue go empty
        q.unschedule(Collections.singleton(resource));
        Assert.assertEquals("Nothing scheduled!", Long.MIN_VALUE, q.getNextExpectedCollectionTime());
        Assert.assertTrue("Nothing scheduled!", q.getNextScheduledSet().isEmpty());
    }

    private Resource<DMRNodeLocation> createResource(String name,
            MeasurementInstance<DMRNodeLocation, MetricType<DMRNodeLocation>>... metrics) {
        ResourceType<DMRNodeLocation> type = ResourceType.<DMRNodeLocation> builder()
                .id(new ID("resType"))
                .name(new Name("resTypeName"))
                .location(DMRNodeLocation.empty())
                .build();
        Builder<DMRNodeLocation> bldr = Resource.<DMRNodeLocation> builder()
                .id(new ID(name))
                .name(new Name(name))
                .location(DMRNodeLocation.empty())
                .type(type);

        if (metrics != null) {
            for (MeasurementInstance<DMRNodeLocation, MetricType<DMRNodeLocation>> m : metrics) {
                bldr.metric(m);
            }
        }
        return bldr.build();
    }

    private MetricType<DMRNodeLocation> createMetricType(String typeName, int defaultIntervalSecs) {
        ID id = new ID(typeName);
        Name name = new Name(typeName);
        AttributeLocation<DMRNodeLocation> location = new AttributeLocation<DMRNodeLocation>(DMRNodeLocation.empty(),
                "foo");
        Interval interval = new Interval(defaultIntervalSecs, TimeUnit.SECONDS);
        MeasurementUnit metricUnits = MeasurementUnit.MEGABYTES;
        org.hawkular.metrics.client.common.MetricType metricType = org.hawkular.metrics.client.common.MetricType.GAUGE;

        MetricType<DMRNodeLocation> type = new MetricType<DMRNodeLocation>(id, name, location, interval, metricUnits,
                metricType);
        return type;
    }

    private MeasurementInstance<DMRNodeLocation, MetricType<DMRNodeLocation>> createMeasurementInstance(
            String instanceName, MetricType<DMRNodeLocation> type) {
        MeasurementInstance<DMRNodeLocation, MetricType<DMRNodeLocation>> meas;
        ID id = new ID(instanceName);
        Name name = new Name(instanceName);
        AttributeLocation<DMRNodeLocation> attributeLocation = new AttributeLocation<DMRNodeLocation>(
                DMRNodeLocation.empty(), "attrib");

        meas = new MeasurementInstance<DMRNodeLocation, MetricType<DMRNodeLocation>>(id, name, attributeLocation,
                type);

        return meas;
    }
}
