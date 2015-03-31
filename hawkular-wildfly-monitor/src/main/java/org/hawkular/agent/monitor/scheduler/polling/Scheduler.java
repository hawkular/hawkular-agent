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

import java.util.List;

import org.hawkular.agent.monitor.scheduler.storage.DataPoint;

/**
 * Performs the actual work collecting the data from the monitored resources.
 */
public interface Scheduler {
    /**
     * Submit metric collection tasks to the scheduler. The completion handler
     * will be called when the metrics either were successfully collected
     * or they failed to be collected.
     *
     * @param operations metric collection tasks
     * @param completionHandler callback when metric collection attempt has completed
     */
    void schedule(List<Task> operations, CompletionHandler completionHandler);

    /**
     * Shuts down the scheduler. No more metrics will be collected after this returns.
     */
    void shutdown();

    /**
     * Callback for completed collection tasks.
     */
    interface CompletionHandler {
        void onCompleted(DataPoint sample);
        void onFailed(Throwable e);
    }
}
