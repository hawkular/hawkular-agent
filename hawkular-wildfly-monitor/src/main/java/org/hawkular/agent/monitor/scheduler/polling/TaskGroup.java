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

/**
 * A group of metric collection tasks that have the same interval.
 */
public class TaskGroup implements Iterable<Task> {

    private final String id; // to uniquely reference this group
    private final Interval interval; // impacts thread scheduling
    private final long offsetMillis;
    private final LinkedList<Task> tasks;

    public TaskGroup(final Interval interval) {
        this.offsetMillis = 0; // don't wait to collect the first time
        this.id = UUID.randomUUID().toString();
        this.interval = interval;
        this.tasks = new LinkedList<>();
    }

    public void addTask(Task task) {
        verifyInterval(task);
        tasks.add(task);
    }

    public boolean addTasks(final Collection<? extends Task> collection) {
        for (Task t : collection) {
            verifyInterval(t);
        }
        return tasks.addAll(collection);
    }

    private void verifyInterval(final Task task) {
        if (task.getInterval() != interval) {
            throw new IllegalArgumentException("Wrong interval: Expected [" + interval + "], but got ["
                    + task.getInterval() + "]");
        }
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
}
