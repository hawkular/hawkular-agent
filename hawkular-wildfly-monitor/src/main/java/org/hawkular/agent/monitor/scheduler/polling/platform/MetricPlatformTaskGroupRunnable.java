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
package org.hawkular.agent.monitor.scheduler.polling.platform;

import org.hawkular.agent.monitor.diagnostics.Diagnostics;
import org.hawkular.agent.monitor.scheduler.polling.MetricCompletionHandler;
import org.hawkular.agent.monitor.scheduler.polling.Task;
import org.hawkular.agent.monitor.scheduler.polling.TaskGroup;
import org.hawkular.agent.monitor.storage.MetricDataPoint;
import org.hawkular.metrics.client.common.MetricType;
import org.jboss.logging.Logger;

public class MetricPlatformTaskGroupRunnable implements Runnable {
    private static final Logger LOG = Logger.getLogger(MetricPlatformTaskGroupRunnable.class);

    private final TaskGroup group;
    private final MetricCompletionHandler completionHandler;

    public MetricPlatformTaskGroupRunnable(TaskGroup group, MetricCompletionHandler completionHandler,
            Diagnostics diagnostics) {
        this.group = group;
        this.completionHandler = completionHandler;
    }

    @Override
    public void run() {
        try {
            for (Task groupTask : group) {
                final MetricPlatformTask platformTask = (MetricPlatformTask) groupTask;
                final MetricType metricType = platformTask.getMetricInstance().getMetricType().getMetricType();
                double value = 0.0; // TODO
                completionHandler.onCompleted(new MetricDataPoint(platformTask, value, metricType));
            }
        } catch (Throwable e) {
            completionHandler.onFailed(e);
        }
    }
}