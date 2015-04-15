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
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.hawkular.agent.monitor.scheduler.config.Interval;
import org.hawkular.agent.monitor.scheduler.polling.Task.Type;

/**
 * Creates task groups based on task intervals. Groups are ensured
 * to only have the same types and same kinds.
 */
public class IntervalGrouping {

    public List<TaskGroup> separateIntoGroups(final List<Task> allTasks) {

        if (allTasks == null || allTasks.isEmpty()) {
            return Collections.emptyList();
        }

        List<TaskGroup> allGroups = new ArrayList<>();

        Map<String, List<Task>> tasksByKind = separateByTaskKind(allTasks);
        for (List<Task> tasksOfSingleKind : tasksByKind.values()) {
            Map<Type, List<Task>> tasksByType = separateByTaskType(tasksOfSingleKind);
            for (List<Task> tasksOfSingleType : tasksByType.values()) {
                List<TaskGroup> groupsOfSingleType = groupSimilarTasks(tasksOfSingleType);
                allGroups.addAll(groupsOfSingleType);
            }
        }

        return allGroups;
    }

    private Map<String, List<Task>> separateByTaskKind(List<Task> allTasks) {
        Map<String, List<Task>> tasksMap = new HashMap<>();

        for (Task singleTask : allTasks) {
            List<Task> tasksOfSingleKind = tasksMap.get(singleTask.getKind().getId());
            if (tasksOfSingleKind == null) {
                tasksOfSingleKind = new ArrayList<Task>();
                tasksMap.put(singleTask.getKind().getId(), tasksOfSingleKind);
            }
            tasksOfSingleKind.add(singleTask);
        }

        return tasksMap;
    }

    private Map<Type, List<Task>> separateByTaskType(List<Task> allTasks) {
        Map<Type, List<Task>> tasksMap = new HashMap<>();

        for (Task singleTask : allTasks) {
            List<Task> tasksOfSingleType = tasksMap.get(singleTask.getType());
            if (tasksOfSingleType == null) {
                tasksOfSingleType = new ArrayList<Task>();
                tasksMap.put(singleTask.getType(), tasksOfSingleType);
            }
            tasksOfSingleType.add(singleTask);
        }

        return tasksMap;
    }

    private List<TaskGroup> groupSimilarTasks(final List<Task> tasks) {

        // sort the tasks in order of their intervals
        Collections.sort(tasks, new Comparator<Task>() {
            @Override
            public int compare(Task t1, Task t2) {
                return new Long(t1.getInterval().millis()).compareTo(t2.getInterval().millis());
            }
        });

        // build task groups - one group per interval
        List<TaskGroup> groups = new ArrayList<>();
        Interval interval = tasks.get(0).getInterval();
        TaskGroup taskGroup = new TaskGroup(interval);
        groups.add(taskGroup);

        for (Task task : tasks) {
            if (!task.getInterval().equals(interval)) {
                // new group
                interval = task.getInterval();
                groups.add(new TaskGroup(task.getInterval()));
            }

            groups.get(groups.size() - 1).addTask(task);
        }

        return groups;
    }
}
