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

import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.hawkular.agent.monitor.diagnostics.Diagnostics;
import org.hawkular.agent.monitor.inventory.ID;
import org.hawkular.agent.monitor.inventory.Name;
import org.hawkular.agent.monitor.inventory.platform.Constants;
import org.hawkular.agent.monitor.inventory.platform.PlatformMetricInstance;
import org.hawkular.agent.monitor.scheduler.polling.MetricCompletionHandler;
import org.hawkular.agent.monitor.scheduler.polling.Task;
import org.hawkular.agent.monitor.scheduler.polling.TaskGroup;
import org.hawkular.agent.monitor.storage.MetricDataPoint;
import org.hawkular.metrics.client.common.MetricType;
import org.jboss.logging.Logger;

import oshi.SystemInfo;

public class MetricPlatformTaskGroupRunnable implements Runnable {
    private static final Logger LOG = Logger.getLogger(MetricPlatformTaskGroupRunnable.class);

    private static final Pattern BRACKETED_NAME_PATTERN = Pattern.compile(".*\\[(.*)\\].*");

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
            SystemInfo sysInfo = new SystemInfo();

            for (Task groupTask : group) {
                final MetricPlatformTask platformTask = (MetricPlatformTask) groupTask;
                final PlatformMetricInstance metricInstance = platformTask.getMetricInstance();
                final MetricType metricType = metricInstance.getMetricType().getMetricType();

                String itemName = parseBracketedNameValue(metricInstance.getResource().getID());
                Name typeName = metricInstance.getResource().getResourceType().getName();
                String metricToCollect = metricInstance.getMeasurementType().getID().getIDString();

                if (typeName.equals(Constants.PlatformResourceType.FILE_STORE.getName())) {
                    LOG.warnf("=======~~~~~~~~~~ COLLECT FILE_STORE %s", itemName);

                } else if (typeName.equals(Constants.PlatformResourceType.MEMORY.getName())) {
                    LOG.warnf("=======~~~~~~~~~~ COLLECT MEMORY %s", itemName);

                } else if (typeName.equals(Constants.PlatformResourceType.PROCESSOR.getName())) {
                    LOG.warnf("=======~~~~~~~~~~ COLLECT PROCESSOR %s", itemName);

                } else if (typeName.equals(Constants.PlatformResourceType.POWER_SOURCE.getName())) {
                    LOG.warnf("=======~~~~~~~~~~ COLLECT POWER SOURCE %s", itemName);

                } else {
                    LOG.errorf("Invalid platform type [%s]; cannot collect metric: [%s]", typeName, metricInstance);
                }

                double value = 0.0; // TODO
                completionHandler.onCompleted(new MetricDataPoint(platformTask, value, metricType));
            }
        } catch (Throwable e) {
            completionHandler.onFailed(e);
        }
    }

    private String parseBracketedNameValue(ID id) {
        // most have named like "File Store [/opt]" or "Processor [1]"
        Matcher m = BRACKETED_NAME_PATTERN.matcher(id.getIDString());
        if (m.matches()) {
            return m.group(1);
        } else {
            return id.getIDString(); // Memory doesn't have a bracketed name
        }
    }
}