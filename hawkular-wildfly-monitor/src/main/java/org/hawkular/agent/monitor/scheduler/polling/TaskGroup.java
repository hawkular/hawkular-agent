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

import java.util.Collection;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.UUID;

import org.hawkular.agent.monitor.scheduler.config.Interval;
import org.hawkular.agent.monitor.scheduler.polling.Task.Kind;
import org.hawkular.agent.monitor.scheduler.polling.Task.Type;

/**
 * A group of tasks that have the same interval.
 *
 * A group must have tasks of the same {@link Type} - in other words,
 * you can't have a group with some tasks that collect {@link Type#METRIC metrics}
 * and some that collect {@link Type#AVAIL availability}.
 *
 * You also can't have a task group that has different kinds of tasks (that is,
 * they all must extend from the same Task implementation class).
 */
public class TaskGroup implements Iterable<Task> {

    private final String id; // to uniquely reference this group
    private final Interval interval; // impacts thread scheduling
    private final long offsetMillis;
    private final LinkedList<Task> tasks;
    private Type type;
    private Kind kind;

    public TaskGroup(final Interval interval) {
        this.offsetMillis = 0; // don't wait to collect the first time
        this.id = UUID.randomUUID().toString();
        this.interval = interval;
        this.tasks = new LinkedList<>();
    }

    /**
     * @return the type of tasks in the group; will be <code>null</code> if group is empty
     */
    public Type getType() {
        return type;
    }

    /**
     * @return the kind of tasks in the group; will be <code>null</code> if group is empty
     */
    public Kind getKind() {
        return kind;
    }

    public void addTask(Task task) {
        verify(task);
        tasks.add(task);
    }

    public boolean addTasks(final Collection<Task> collection) {
        for (Task task : collection) {
            verify(task);
        }
        return tasks.addAll(collection);
    }

    public int size() {
        return tasks.size();
    }

    public boolean isEmpty() {
        return tasks.isEmpty();
    }

    @Override
    public Iterator<Task> iterator() {
        return tasks.iterator();
    }

    public String getId() {
        return id;
    }

    public Interval getInterval() {
        return interval;
    }

    public long getOffsetMillis() {
        return offsetMillis;
    }

    public Task getTask(int i) {
        return tasks.get(i);
    }

    @Override
    public String toString() {
        StringBuilder str = new StringBuilder("TaskGroup: ");
        str.append("id=[").append(id).append("]");
        str.append(", type=[").append(type).append("]");
        str.append(", interval=[").append(interval).append("]");
        str.append(", size=[").append(size()).append("]");
        return str.toString();
    }

    protected void verify(final Task task) {
        verifyInterval(task);
        verifyType(task);
        verifyClassKind(task);
    }

    private void verifyInterval(final Task task) {
        if (!task.getInterval().equals(interval)) {
            throw new IllegalArgumentException("Wrong interval: Expected [" + interval + "], but got ["
                    + task.getInterval() + "]");
        }
    }

    private void verifyType(final Task task) {
        if (this.type == null) {
            this.type = task.getType();
        } else if (task.getType() != type) {
            throw new IllegalArgumentException("Wrong type: Expected [" + this.type + "], but got ["
                    + task.getType() + "]");
        }
    }

    private void verifyClassKind(final Task task) {
        if (this.kind == null) {
            this.kind = task.getKind();
        } else if (!this.kind.isSameKind(task)) {
            throw new IllegalArgumentException("Wrong kind. Cannot add to group: " + task);
        }
    }
}
