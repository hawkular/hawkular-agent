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
package org.hawkular.agent.monitor.storage;

import java.util.Date;

import org.hawkular.agent.monitor.api.Avail;
import org.hawkular.agent.monitor.scheduler.polling.Task;

/**
 * Availability check data that was collected.
 */
public class AvailDataPoint {
    private Task task;
    private long timestamp;
    private Avail value;

    public AvailDataPoint(Task task, Avail value) {
        this.task = task;
        this.timestamp = System.currentTimeMillis();
        this.value = value;
    }

    /**
     * @return object that identifies the property that was used to check availability
     */
    public Task getTask() {
        return task;
    }

    /**
     * @return when the availability was checked
     */
    public long getTimestamp() {
        return timestamp;
    }

    /**
     * @return the actual availability status
     */
    public Avail getValue() {
        return value;
    }

    @Override
    public String toString() {
        return "AvailDataPoint: task=[" + task + "], timestamp=[" + new Date(timestamp) + "], value=[" + value + "]";
    }
}
