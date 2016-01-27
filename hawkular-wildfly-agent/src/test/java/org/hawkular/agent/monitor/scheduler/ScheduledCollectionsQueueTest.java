/*
 * Copyright 2015-2016 Red Hat, Inc. and/or its affiliates
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

import java.util.Arrays;
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
    public void testInvalidInterval() throws InterruptedException {
        createMetricType("goodInterval", 1000);

        try {
            createMetricType("badInterval", 500);
            Assert.fail("Should have thrown exception due to the interval being too small");
        } catch (IllegalArgumentException expected) {
        }
    }

    @Test
    public void testUnschedule() throws InterruptedException {
        ScheduledCollectionsQueue<DMRNodeLocation, MetricType<DMRNodeLocation>> q = new ScheduledCollectionsQueue<>();
        Assert.assertEquals("Nothing scheduled!", Long.MIN_VALUE, q.getNextExpectedCollectionTime());
        Assert.assertTrue("Nothing scheduled!", q.popNextScheduledSet().isEmpty());

        int collInterval = 1000;
        MeasurementInstance<DMRNodeLocation, MetricType<DMRNodeLocation>> measInstance;
        measInstance = createMeasurementInstance("metricName", createMetricType("metricTypeName", collInterval));
        Resource<DMRNodeLocation> resource = createResource("root", measInstance);

        Collection<ScheduledMeasurementInstance<DMRNodeLocation, MetricType<DMRNodeLocation>>> schedules;
        ScheduledMeasurementInstance<DMRNodeLocation, MetricType<DMRNodeLocation>> schedule;
        schedule = new ScheduledMeasurementInstance<>(resource, measInstance);
        q.schedule(Collections.singleton(schedule));

        // now that something is scheduled, let's see that the queue gives us the right answers
        Assert.assertTrue(q.getNextExpectedCollectionTime() > System.currentTimeMillis());

        // unschedule and see that the queue goes empty
        q.unschedule(Collections.singleton(resource));
        Assert.assertEquals("Nothing scheduled!", Long.MIN_VALUE, q.getNextExpectedCollectionTime());
        Assert.assertTrue("Nothing scheduled!", q.popNextScheduledSet().isEmpty());
    }

    @Test
    public void testSimpleSchedule() throws InterruptedException {
        ScheduledCollectionsQueue<DMRNodeLocation, MetricType<DMRNodeLocation>> q = new ScheduledCollectionsQueue<>();
        Assert.assertEquals("Nothing scheduled!", Long.MIN_VALUE, q.getNextExpectedCollectionTime());
        Assert.assertTrue("Nothing scheduled!", q.popNextScheduledSet().isEmpty());

        int collInterval = 1500;
        MeasurementInstance<DMRNodeLocation, MetricType<DMRNodeLocation>> measInstance;
        measInstance = createMeasurementInstance("metricName", createMetricType("metricTypeName", collInterval));
        Resource<DMRNodeLocation> resource = createResource("root", measInstance);

        Collection<ScheduledMeasurementInstance<DMRNodeLocation, MetricType<DMRNodeLocation>>> schedules;
        ScheduledMeasurementInstance<DMRNodeLocation, MetricType<DMRNodeLocation>> schedule;
        schedule = new ScheduledMeasurementInstance<>(resource, measInstance);
        q.schedule(Collections.singleton(schedule));
        Assert.assertTrue("The scheduled collection time isn't here yet", q.popNextScheduledSet().isEmpty());

        // now that something is scheduled, let's see that the queue gives us the right answers
        long nextExpectedCollectionTime = q.getNextExpectedCollectionTime();
        Assert.assertTrue(nextExpectedCollectionTime > System.currentTimeMillis());
        Thread.sleep(collInterval); // wait for the collection time to pass
        Set<MeasurementInstance<DMRNodeLocation, MetricType<DMRNodeLocation>>> scheduledSet = q.popNextScheduledSet();
        Assert.assertEquals(1, scheduledSet.size());
        Assert.assertTrue(scheduledSet.contains(schedule.getMeasurementInstance()));

        // now see that the reschedule works
        Assert.assertTrue(nextExpectedCollectionTime + collInterval <= q.getNextExpectedCollectionTime());
    }

    @Test
    public void testSimpleMultipleSchedules() throws InterruptedException {
        ScheduledCollectionsQueue<DMRNodeLocation, MetricType<DMRNodeLocation>> q = new ScheduledCollectionsQueue<>();

        int collInterval = 1000;
        MetricType<DMRNodeLocation> metricType1 = createMetricType("metricTypeName1", collInterval);
        MetricType<DMRNodeLocation> metricType2 = createMetricType("metricTypeName2", collInterval);
        MeasurementInstance<DMRNodeLocation, MetricType<DMRNodeLocation>> measInstance1;
        MeasurementInstance<DMRNodeLocation, MetricType<DMRNodeLocation>> measInstance2;
        measInstance1 = createMeasurementInstance("metricName1", metricType1);
        measInstance2 = createMeasurementInstance("metricName2", metricType2);
        Resource<DMRNodeLocation> resource = createResource("root", measInstance1, measInstance2);

        // schedule two collections on the same resource with the same collection interval
        ScheduledMeasurementInstance<DMRNodeLocation, MetricType<DMRNodeLocation>> schedule1;
        ScheduledMeasurementInstance<DMRNodeLocation, MetricType<DMRNodeLocation>> schedule2;
        schedule1 = new ScheduledMeasurementInstance<>(resource, measInstance1);
        schedule2 = new ScheduledMeasurementInstance<>(resource, measInstance2);
        q.schedule(Arrays.asList(schedule1, schedule2));

        // let's see that the queue gives us the right answers
        long nextExpectedCollectionTime = q.getNextExpectedCollectionTime();
        Assert.assertTrue(nextExpectedCollectionTime > System.currentTimeMillis());
        Thread.sleep(collInterval + 100); // wait for the collection time to pass
        Set<MeasurementInstance<DMRNodeLocation, MetricType<DMRNodeLocation>>> scheduledSet = q.popNextScheduledSet();
        Assert.assertEquals(2, scheduledSet.size());
        Assert.assertTrue(scheduledSet.contains(schedule1.getMeasurementInstance()));
        Assert.assertTrue(scheduledSet.contains(schedule2.getMeasurementInstance()));

        // now see that the reschedule works
        Assert.assertTrue(nextExpectedCollectionTime + collInterval <= q.getNextExpectedCollectionTime());
    }

    @Test
    public void testMultipleSchedulesWithDifferentIntervals() throws InterruptedException {
        ScheduledCollectionsQueue<DMRNodeLocation, MetricType<DMRNodeLocation>> q = new ScheduledCollectionsQueue<>();

        int collInterval1 = 1000;
        int collInterval2 = 1500;
        MetricType<DMRNodeLocation> metricType1 = createMetricType("metricTypeName1", collInterval1);
        MetricType<DMRNodeLocation> metricType2 = createMetricType("metricTypeName2", collInterval1);
        MetricType<DMRNodeLocation> metricType3 = createMetricType("metricTypeName3", collInterval2);
        MeasurementInstance<DMRNodeLocation, MetricType<DMRNodeLocation>> measInstance1;
        MeasurementInstance<DMRNodeLocation, MetricType<DMRNodeLocation>> measInstance2;
        MeasurementInstance<DMRNodeLocation, MetricType<DMRNodeLocation>> measInstance3;
        measInstance1 = createMeasurementInstance("metricName1", metricType1);
        measInstance2 = createMeasurementInstance("metricName2", metricType2);
        measInstance3 = createMeasurementInstance("metricName3", metricType3);
        Resource<DMRNodeLocation> resource = createResource("root", measInstance1, measInstance2, measInstance3);

        // schedule three collections on the same resource but the third has a different collection interval
        ScheduledMeasurementInstance<DMRNodeLocation, MetricType<DMRNodeLocation>> schedule1;
        ScheduledMeasurementInstance<DMRNodeLocation, MetricType<DMRNodeLocation>> schedule2;
        ScheduledMeasurementInstance<DMRNodeLocation, MetricType<DMRNodeLocation>> schedule3;
        schedule1 = new ScheduledMeasurementInstance<>(resource, measInstance1);
        schedule2 = new ScheduledMeasurementInstance<>(resource, measInstance2);
        schedule3 = new ScheduledMeasurementInstance<>(resource, measInstance3);
        q.schedule(Arrays.asList(schedule1, schedule2, schedule3));

        // let's see that the queue gives us the right answers (remember popNextScheduledSet reschedules)
        long firstCollectionTime = q.getNextExpectedCollectionTime();
        Assert.assertTrue(firstCollectionTime > System.currentTimeMillis());
        Thread.sleep(collInterval1); // wait for the first collection time to pass
        Set<MeasurementInstance<DMRNodeLocation, MetricType<DMRNodeLocation>>> scheduledSet = q.popNextScheduledSet();
        Assert.assertEquals("The first set should only have 2 of the 3 schedules", 2, scheduledSet.size());
        Assert.assertTrue(scheduledSet.contains(schedule1.getMeasurementInstance()));
        Assert.assertTrue(scheduledSet.contains(schedule2.getMeasurementInstance()));
        Assert.assertFalse(scheduledSet.contains(schedule3.getMeasurementInstance()));

        long secondCollectionTime = q.getNextExpectedCollectionTime();
        Assert.assertTrue("Next collection should be after the first", secondCollectionTime > firstCollectionTime);
        Assert.assertTrue(q.getNextExpectedCollectionTime() > System.currentTimeMillis());
        Thread.sleep(collInterval2); // wait for the second collection time to pass
        scheduledSet = q.popNextScheduledSet();
        Assert.assertEquals("The second set should only have 1 of the 3 schedules", 1, scheduledSet.size());
        Assert.assertFalse(scheduledSet.contains(schedule1.getMeasurementInstance()));
        Assert.assertFalse(scheduledSet.contains(schedule2.getMeasurementInstance()));
        Assert.assertTrue(scheduledSet.contains(schedule3.getMeasurementInstance()));
    }

    @Test
    public void testMultipleResources() throws InterruptedException {
        ScheduledCollectionsQueue<DMRNodeLocation, MetricType<DMRNodeLocation>> q = new ScheduledCollectionsQueue<>();

        int collInterval = 1000;
        MetricType<DMRNodeLocation> metricType1 = createMetricType("metricTypeName1", collInterval);
        MetricType<DMRNodeLocation> metricType2 = createMetricType("metricTypeName2", collInterval);
        MetricType<DMRNodeLocation> metricType3 = createMetricType("metricTypeName3", collInterval);
        MeasurementInstance<DMRNodeLocation, MetricType<DMRNodeLocation>> measInstance1;
        MeasurementInstance<DMRNodeLocation, MetricType<DMRNodeLocation>> measInstance2;
        MeasurementInstance<DMRNodeLocation, MetricType<DMRNodeLocation>> measInstance3;
        measInstance1 = createMeasurementInstance("metricName1", metricType1);
        measInstance2 = createMeasurementInstance("metricName2", metricType2);
        measInstance3 = createMeasurementInstance("metricName3", metricType3);
        Resource<DMRNodeLocation> resource1 = createResource("root1", measInstance1, measInstance2);
        Resource<DMRNodeLocation> resource2 = createResource("root2", measInstance3);

        // schedule three collections on the same resource but the third has a different collection interval
        ScheduledMeasurementInstance<DMRNodeLocation, MetricType<DMRNodeLocation>> schedule1;
        ScheduledMeasurementInstance<DMRNodeLocation, MetricType<DMRNodeLocation>> schedule2;
        ScheduledMeasurementInstance<DMRNodeLocation, MetricType<DMRNodeLocation>> schedule3;
        schedule1 = new ScheduledMeasurementInstance<>(resource1, measInstance1);
        schedule2 = new ScheduledMeasurementInstance<>(resource1, measInstance2);
        schedule3 = new ScheduledMeasurementInstance<>(resource2, measInstance3);
        q.schedule(Arrays.asList(schedule1, schedule2, schedule3));

        // let's see that the queue gives us the right answers
        Thread.sleep(collInterval);
        Set<MeasurementInstance<DMRNodeLocation, MetricType<DMRNodeLocation>>> scheduledSet = q.popNextScheduledSet();
        Assert.assertEquals("Should have 3 schedules even those they are for 2 resources", 3, scheduledSet.size());
        Assert.assertTrue(scheduledSet.contains(schedule1.getMeasurementInstance()));
        Assert.assertTrue(scheduledSet.contains(schedule2.getMeasurementInstance()));
        Assert.assertTrue(scheduledSet.contains(schedule3.getMeasurementInstance()));
    }

    @Test
    public void testUnscheduleResource() throws InterruptedException {
        ScheduledCollectionsQueue<DMRNodeLocation, MetricType<DMRNodeLocation>> q = new ScheduledCollectionsQueue<>();

        int collInterval1 = 1000;
        int collInterval2 = 1250;
        int collInterval3 = 1500;
        MetricType<DMRNodeLocation> metricType1 = createMetricType("metricTypeName1", collInterval1);
        MetricType<DMRNodeLocation> metricType2 = createMetricType("metricTypeName2", collInterval2);
        MetricType<DMRNodeLocation> metricType3 = createMetricType("metricTypeName3", collInterval3);
        MeasurementInstance<DMRNodeLocation, MetricType<DMRNodeLocation>> measInstance1;
        MeasurementInstance<DMRNodeLocation, MetricType<DMRNodeLocation>> measInstance2;
        MeasurementInstance<DMRNodeLocation, MetricType<DMRNodeLocation>> measInstance3;
        measInstance1 = createMeasurementInstance("metricName1", metricType1);
        measInstance2 = createMeasurementInstance("metricName2", metricType2);
        measInstance3 = createMeasurementInstance("metricName3", metricType3);
        Resource<DMRNodeLocation> resource1 = createResource("root1", measInstance1, measInstance3);
        Resource<DMRNodeLocation> resource2 = createResource("root2", measInstance2);

        // schedule three collections - first is for resource1, second for resource2, third for resource1 again
        ScheduledMeasurementInstance<DMRNodeLocation, MetricType<DMRNodeLocation>> schedule1;
        ScheduledMeasurementInstance<DMRNodeLocation, MetricType<DMRNodeLocation>> schedule2;
        ScheduledMeasurementInstance<DMRNodeLocation, MetricType<DMRNodeLocation>> schedule3;
        schedule1 = new ScheduledMeasurementInstance<>(resource1, measInstance1);
        schedule2 = new ScheduledMeasurementInstance<>(resource2, measInstance2);
        schedule3 = new ScheduledMeasurementInstance<>(resource1, measInstance3);
        q.schedule(Arrays.asList(schedule1, schedule2, schedule3));

        // now unschedule for resource1 and see the first and third schedule go away
        q.unschedule(Collections.singletonList(resource1));
        Thread.sleep(collInterval3);
        Set<MeasurementInstance<DMRNodeLocation, MetricType<DMRNodeLocation>>> scheduledSet = q.popNextScheduledSet();
        Assert.assertEquals("Should have 1 schedule left for resource2 only", 1, scheduledSet.size());
        Assert.assertFalse(scheduledSet.contains(schedule1.getMeasurementInstance()));
        Assert.assertTrue(scheduledSet.contains(schedule2.getMeasurementInstance()));
        Assert.assertFalse(scheduledSet.contains(schedule3.getMeasurementInstance()));
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

    private MetricType<DMRNodeLocation> createMetricType(String typeName, int defaultIntervalMillis) {
        ID id = new ID(typeName);
        Name name = new Name(typeName);
        AttributeLocation<DMRNodeLocation> location = new AttributeLocation<DMRNodeLocation>(DMRNodeLocation.empty(),
                "foo");
        Interval interval = new Interval(defaultIntervalMillis, TimeUnit.MILLISECONDS);
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
