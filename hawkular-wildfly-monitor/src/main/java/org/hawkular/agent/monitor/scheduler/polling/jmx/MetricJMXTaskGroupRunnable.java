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
package org.hawkular.agent.monitor.scheduler.polling.jmx;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

import javax.management.ObjectName;

import org.hawkular.agent.monitor.diagnostics.Diagnostics;
import org.hawkular.agent.monitor.scheduler.JmxClientFactory;
import org.hawkular.agent.monitor.scheduler.polling.MetricCompletionHandler;
import org.hawkular.agent.monitor.scheduler.polling.Task;
import org.hawkular.agent.monitor.scheduler.polling.TaskGroup;
import org.hawkular.agent.monitor.storage.MetricDataPoint;
import org.hawkular.metrics.client.common.MetricType;
import org.jboss.logging.Logger;
import org.jolokia.client.J4pClient;
import org.jolokia.client.exception.J4pBulkRemoteException;
import org.jolokia.client.request.J4pReadRequest;
import org.jolokia.client.request.J4pReadResponse;

import com.codahale.metrics.Timer;

public class MetricJMXTaskGroupRunnable implements Runnable {
    private static final Logger LOG = Logger.getLogger(MetricJMXTaskGroupRunnable.class);

    private final TaskGroup group;
    private final MetricCompletionHandler completionHandler;
    private final Diagnostics diagnostics;
    private final JmxClientFactory jmxClientFactory;

    public MetricJMXTaskGroupRunnable(TaskGroup group, MetricCompletionHandler completionHandler,
            Diagnostics diagnostics, JmxClientFactory jmxClientFactory) {
        this.group = group;
        this.completionHandler = completionHandler;
        this.diagnostics = diagnostics;
        this.jmxClientFactory = jmxClientFactory;
    }

    @Override
    public void run() {
        try {
            final J4pClient client = jmxClientFactory.createClient();

            ArrayList<J4pReadRequest> reqs = new ArrayList<>(this.group.size());

            this.group.forEach(new Consumer<Task>() {
                @Override
                public void accept(Task t) {
                    MetricJMXTask jmxTask = (MetricJMXTask) t;
                    String jmxAttrib = jmxTask.getAttribute();
                    J4pReadRequest req = new J4pReadRequest(jmxTask.getObjectName(), jmxAttrib);
                    if (jmxTask.getSubref() != null) {
                        req.setPath(jmxTask.getSubref());
                    }
                    reqs.add(req);
                }
            });

            // execute the JMX request
            final Timer.Context requestContext = diagnostics.getJMXRequestTimer().time();
            List<J4pReadResponse> responses;
            try {
                responses = client.execute(reqs);
            } catch (J4pBulkRemoteException bulkError) {
                responses = bulkError.getResponses();
                if (responses == null || responses.isEmpty()) {
                    throw new Exception("Failed to execute bulk JMX request", bulkError);
                } else {
                    // some, but not all, failed - we will just process the successful ones
                    // but at least we mark an error in our diagnostics
                    this.diagnostics.getJMXErrorRate().mark(1);
                }
            }
            final long durationNanos = requestContext.stop();
            final long durationMs = TimeUnit.MILLISECONDS.convert(durationNanos, TimeUnit.NANOSECONDS);
            if (durationMs > group.getInterval().millis()) {
                diagnostics.getJMXDelayedRate().mark(1);
            }

            // process the responses
            int i = 0;
            for (J4pReadResponse response : responses) {
                final MetricJMXTask task = (MetricJMXTask) group.getTask(i++);
                final MetricType metricType = task.getMetricInstance().getMetricType().getMetricType();

                double value;

                Collection<ObjectName> responseObjectNames = response.getObjectNames();
                if (responseObjectNames.size() > 1) {
                    // we need to aggregate them
                    value = 0.0;
                    for (ObjectName responseObjectName : responseObjectNames) {
                        Object valueObject = response.getValue(responseObjectName, task.getAttribute());
                        value += getDoubleValue(valueObject);
                    }
                } else {
                    Object valueObject = response.getValue();
                    value = getDoubleValue(valueObject);
                }

                completionHandler.onCompleted(new MetricDataPoint(task, value, metricType));

            }
        } catch (Throwable e) {
            this.diagnostics.getJMXErrorRate().mark(1);
            completionHandler.onFailed(e);
        }
    }

    private double getDoubleValue(Object valueObject) {
        double value;
        if (valueObject == null) {
            value = Double.NaN;
        } else if (valueObject instanceof Number) {
            value = ((Number) valueObject).doubleValue();
        } else {
            value = Double.valueOf(valueObject.toString());
        }
        return value;
    }
}