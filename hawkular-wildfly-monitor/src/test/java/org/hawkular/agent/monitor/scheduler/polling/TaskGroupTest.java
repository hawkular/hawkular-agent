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

import org.hawkular.agent.monitor.scheduler.config.Interval;
import org.hawkular.agent.monitor.scheduler.polling.Task.Type;
import org.hawkular.agent.monitor.scheduler.polling.dmr.DMRTask;
import org.hawkular.dmrclient.Address;
import org.junit.Assert;
import org.junit.Test;

public class TaskGroupTest {

    @Test
    public void testAddTask() {
        TaskGroup group = new TaskGroup(interval(1));
        Assert.assertEquals(0, group.size());
        Assert.assertNull(group.getType());
        Assert.assertNull(group.getClassKind());

        group.addTask(new TestTask(Type.METRIC, 1));
        Assert.assertEquals(1, group.size());
        Assert.assertEquals(Type.METRIC, group.getType());
        Assert.assertEquals(TestTask.class, group.getClassKind());

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

    private static Interval interval(int duration) {
        return new Interval(duration, TimeUnit.SECONDS);
    }

    private static DMRTask createDMRTask(Type type, int duration) {
        return new DMRTask(type, interval(duration), "a", "b", Address.root(), "c", null);
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
    }
}
