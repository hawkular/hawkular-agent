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
package org.hawkular.agent.monitor.scheduler.polling;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

import org.hawkular.agent.monitor.scheduler.config.DMREndpoint;
import org.hawkular.agent.monitor.scheduler.config.Interval;
import org.hawkular.agent.monitor.scheduler.config.LocalDMREndpoint;
import org.hawkular.agent.monitor.scheduler.polling.Task.Type;
import org.hawkular.agent.monitor.scheduler.polling.dmr.AvailDMRTask;
import org.hawkular.agent.monitor.scheduler.polling.dmr.DMRTask;
import org.hawkular.agent.monitor.scheduler.polling.dmr.MetricDMRTask;
import org.hawkular.agent.monitor.service.ServerIdentifiers;
import org.hawkular.dmrclient.Address;
import org.junit.Assert;
import org.junit.Test;

public class TaskGroupTest {

    @Test
    public void testAddTask() {
        TaskGroup group = new TaskGroup(interval(1));
        Assert.assertEquals(0, group.size());
        Assert.assertNull(group.getType());
        Assert.assertNull(group.getKind());

        group.addTask(new TestTask(Type.METRIC, 1));
        Assert.assertEquals(1, group.size());
        Assert.assertEquals(Type.METRIC, group.getType());
        Assert.assertNotNull(group.getKind());

        try {
            group.addTask(new TestTask(Type.AVAIL, 1));
            Assert.fail("Should not have been able to add task of a different type");
        } catch (Exception ok) {
        }

        try {
            group.addTask(new TestTask(Type.AVAIL, 2));
            Assert.fail("Should not have been able to add task of a different interval");
        } catch (Exception ok) {
        }

        try {
            group.addTask(createDMRTask(Type.METRIC, 1));
            Assert.fail("Should not have been able to add task of a different class kind");
        } catch (Exception ok) {
        }
    }

    @Test
    public void testGrouping() {
        List<Task> allTasks;
        List<TaskGroup> groups;

        allTasks = new ArrayList<Task>();
        allTasks.add(new TestTask(Type.METRIC, 1));
        allTasks.add(new TestTask(Type.METRIC, 1));
        groups = new IntervalGrouping().separateIntoGroups(allTasks);
        Assert.assertEquals(1, groups.size());

        allTasks = new ArrayList<Task>();
        allTasks.add(new TestTask(Type.METRIC, 1));
        allTasks.add(new TestTask(Type.AVAIL, 1));
        groups = new IntervalGrouping().separateIntoGroups(allTasks);
        Assert.assertEquals(2, groups.size());

        allTasks = new ArrayList<Task>();
        allTasks.add(new TestTask(Type.METRIC, 1));
        allTasks.add(new TestTask(Type.METRIC, 2));
        groups = new IntervalGrouping().separateIntoGroups(allTasks);
        Assert.assertEquals(2, groups.size());

        allTasks = new ArrayList<Task>();
        allTasks.add(new TestTask(Type.METRIC, 1));
        allTasks.add(new TestTask(Type.AVAIL, 2));
        groups = new IntervalGrouping().separateIntoGroups(allTasks);
        Assert.assertEquals(2, groups.size());
    }

    @Test
    public void testBigGrouping() {
        List<Task> allTasks = new ArrayList<Task>();
        allTasks.add(new TestTask(Type.METRIC, 1));
        allTasks.add(new TestTask(Type.METRIC, 2));
        allTasks.add(new TestTask(Type.METRIC, 2));
        allTasks.add(new TestTask(Type.AVAIL, 1));
        allTasks.add(new TestTask(Type.AVAIL, 2));
        allTasks.add(new TestTask(Type.AVAIL, 2));
        allTasks.add(createDMRTask(Type.METRIC, 1));
        allTasks.add(createDMRTask(Type.METRIC, 2));
        allTasks.add(createDMRTask(Type.AVAIL, 1));
        allTasks.add(createDMRTask(Type.AVAIL, 2));

        // There should be 8 groups:
        // type=METRIC, classKind=TestTask, interval=1 (number of tasks=1)
        // type=METRIC, classKind=TestTask, interval=2 (number of tasks=2)
        // type=AVAIL, classKind=TestTask, interval=1 (number of tasks=1)
        // type=AVAIL, classKind=TestTask, interval=2 (number of tasks=2)
        // type=METRIC, classKind=DMRTask, interval=1 (number of tasks=1)
        // type=METRIC, classKind=DMRTask, interval=2 (number of tasks=1)
        // type=AVAIL, classKind=DMRTask, interval=1 (number of tasks=1)
        // type=AVAIL, classKind=DMRTask, interval=2 (number of tasks=1)

        List<TaskGroup> groups = new IntervalGrouping().separateIntoGroups(allTasks);
        Assert.assertEquals(8, groups.size());
    }

    @Test
    public void testDMRGroupWithDifferentKinds() {
        List<Task> allTasks = new ArrayList<Task>();
        List<TaskGroup> groups;

        // the task "kind" is the same - same task class, type, and endpoint - so only one group
        allTasks.clear();
        allTasks.add(createDMRTask(Type.METRIC, 1, new DMREndpoint("n", "h", 1, "u", "p")));
        allTasks.add(createDMRTask(Type.METRIC, 1, new DMREndpoint("n", "h", 1, "u", "p")));
        groups = new IntervalGrouping().separateIntoGroups(allTasks);
        Assert.assertEquals(1, groups.size());
        Assert.assertEquals(2, groups.get(0).size());

        // the task "kind" is the same (name of endpoint and password doesn't matter even if different) - so 1 group
        allTasks.clear();
        allTasks.add(createDMRTask(Type.METRIC, 1, new DMREndpoint("nONE", "h", 1, "u", "pONE")));
        allTasks.add(createDMRTask(Type.METRIC, 1, new DMREndpoint("nTWO", "h", 1, "u", "pTWO")));
        groups = new IntervalGrouping().separateIntoGroups(allTasks);
        Assert.assertEquals(1, groups.size());
        Assert.assertEquals(2, groups.get(0).size());

        // the task "kind" is the same, but interval is different, so two groups
        allTasks.clear();
        allTasks.add(createDMRTask(Type.METRIC, 1, new DMREndpoint("n", "h", 1, "u", "p")));
        allTasks.add(createDMRTask(Type.METRIC, 2, new DMREndpoint("n", "h", 1, "u", "p")));
        groups = new IntervalGrouping().separateIntoGroups(allTasks);
        Assert.assertEquals(2, groups.size());
        Assert.assertEquals(1, groups.get(0).size());
        Assert.assertEquals(1, groups.get(1).size());

        // the task "kind" is different (type is different), so two groups
        allTasks.clear();
        allTasks.add(createDMRTask(Type.METRIC, 1, new DMREndpoint("n", "h", 1, "u", "p")));
        allTasks.add(createDMRTask(Type.AVAIL, 1, new DMREndpoint("n", "h", 1, "u", "p")));
        groups = new IntervalGrouping().separateIntoGroups(allTasks);
        Assert.assertEquals(2, groups.size());
        Assert.assertEquals(1, groups.get(0).size());
        Assert.assertEquals(1, groups.get(1).size());

        // the task "kind" is different (endpoint host is different), so two groups
        allTasks.clear();
        allTasks.add(createDMRTask(Type.METRIC, 1, new DMREndpoint("n", "hONE", 1, "u", "p")));
        allTasks.add(createDMRTask(Type.METRIC, 1, new DMREndpoint("n", "hTWO", 1, "u", "p")));
        groups = new IntervalGrouping().separateIntoGroups(allTasks);
        Assert.assertEquals(2, groups.size());
        Assert.assertEquals(1, groups.get(0).size());
        Assert.assertEquals(1, groups.get(1).size());

        // the task "kind" is different (endpoint port is different), so two groups
        allTasks.clear();
        allTasks.add(createDMRTask(Type.METRIC, 1, new DMREndpoint("n", "h", 1, "u", "p")));
        allTasks.add(createDMRTask(Type.METRIC, 1, new DMREndpoint("n", "h", 2, "u", "p")));
        groups = new IntervalGrouping().separateIntoGroups(allTasks);
        Assert.assertEquals(2, groups.size());
        Assert.assertEquals(1, groups.get(0).size());
        Assert.assertEquals(1, groups.get(1).size());

        // the task "kind" is different (endpoint username is different), so two groups
        allTasks.clear();
        allTasks.add(createDMRTask(Type.METRIC, 1, new DMREndpoint("n", "h", 1, "uONE", "p")));
        allTasks.add(createDMRTask(Type.METRIC, 1, new DMREndpoint("n", "h", 1, "uTWO", "p")));
        groups = new IntervalGrouping().separateIntoGroups(allTasks);
        Assert.assertEquals(2, groups.size());
        Assert.assertEquals(1, groups.get(0).size());
        Assert.assertEquals(1, groups.get(1).size());

        // just make sure we can handle groups with more than 1 task in them
        allTasks.clear();
        allTasks.add(createDMRTask(Type.METRIC, 1, new DMREndpoint("n", "hONE", 1, "u", "p")));
        allTasks.add(createDMRTask(Type.METRIC, 1, new DMREndpoint("n", "hONE", 1, "u", "p")));
        allTasks.add(createDMRTask(Type.METRIC, 1, new DMREndpoint("n", "hTWO", 1, "u", "p")));
        allTasks.add(createDMRTask(Type.METRIC, 1, new DMREndpoint("n", "hTWO", 1, "u", "p")));
        groups = new IntervalGrouping().separateIntoGroups(allTasks);
        Assert.assertEquals(2, groups.size());
        Assert.assertEquals(2, groups.get(0).size());
        Assert.assertEquals(2, groups.get(1).size());

    }

    private static Interval interval(int duration) {
        return new Interval(duration, TimeUnit.SECONDS);
    }

    private static DMRTask createDMRTask(Type type, int duration) {
        DMREndpoint endpoint = new LocalDMREndpoint("_self", new ServerIdentifiers("x", "y", "z", null));
        return createDMRTask(type, duration, endpoint);
    }

    private static DMRTask createDMRTask(Type type, int duration, DMREndpoint endpoint) {
        if (type == Type.METRIC) {
            return new MetricDMRTask(interval(duration), endpoint, Address.root(), "c", null, null);
        } else {
            return new AvailDMRTask(interval(duration), endpoint, Address.root(), "c", null, null, null);
        }
    }

    private class TestTask implements Task {
        private Interval interval;
        private Type type;

        TestTask(Type type, int duration) {
            this.type = type;
            this.interval = interval(duration);
        }

        @Override
        public Type getType() {
            return type;
        }

        @Override
        public Interval getInterval() {
            return interval;
        }

        @Override
        public KeyGenerator getKeyGenerator() {
            return null;
        }

        @Override
        public Kind getKind() {
            return new Kind() {
                @Override
                public String getId() {
                    return TestTask.class.getName();
                }
            };
        }
    }
}
