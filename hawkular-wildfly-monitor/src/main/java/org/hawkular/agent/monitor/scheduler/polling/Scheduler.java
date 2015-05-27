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

/**
 * Performs the actual work collecting the data from the monitored resources.
 */
public interface Scheduler {
    /**
     * Submit tasks to the scheduler. The completion handler
     * will be called when the tasks are either successful or a failure occurred.
     *
     * @param tasks tasks to schedule
     */
    void schedule(List<Task> tasks);

    /**
     * Shuts down the scheduler. No more tasks will be executed after this returns.
     */
    void shutdown();
}
