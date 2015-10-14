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
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

import org.hawkular.agent.monitor.api.Avail;
import org.hawkular.agent.monitor.diagnostics.Diagnostics;
import org.hawkular.agent.monitor.scheduler.JmxClientFactory;
import org.hawkular.agent.monitor.scheduler.polling.AvailCompletionHandler;
import org.hawkular.agent.monitor.scheduler.polling.Task;
import org.hawkular.agent.monitor.scheduler.polling.TaskGroup;
import org.hawkular.agent.monitor.storage.AvailDataPoint;
import org.hawkular.metrics.client.common.MetricType;
import org.jboss.logging.Logger;
import org.jolokia.client.J4pClient;
import org.jolokia.client.request.J4pReadRequest;
import org.jolokia.client.request.J4pResponse;

import com.codahale.metrics.Timer;

public class AvailJMXTaskGroupRunnable implements Runnable {
    private static final Logger LOG = Logger.getLogger(MetricJMXTaskGroupRunnable.class);

    private final TaskGroup group;
    private final AvailCompletionHandler completionHandler;
    private final Diagnostics diagnostics;
    private final JmxClientFactory jmxClientFactory;

    public AvailJMXTaskGroupRunnable(TaskGroup group, AvailCompletionHandler completionHandler,
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
                    if (jmxTask.getSubref() != null) {
                        jmxAttrib = jmxAttrib + "/" + jmxTask.getSubref();
                    }
                    J4pReadRequest req = new J4pReadRequest(jmxTask.getObjectName(), jmxAttrib);
                    reqs.add(req);
                }
            });

            // execute the JMX request
            final Timer.Context requestContext = diagnostics.getJMXRequestTimer().time();
            List<J4pResponse<J4pReadRequest>> responses = client.execute(reqs);
            final long durationNanos = requestContext.stop();
            final long durationMs = TimeUnit.MILLISECONDS.convert(durationNanos, TimeUnit.NANOSECONDS);
            if (durationMs > group.getInterval().millis()) {
                diagnostics.getJMXDelayedRate().mark(1);
            }

            // process the responses
            int i = 0;
            for (J4pResponse<J4pReadRequest> response : responses) {
                final MetricJMXTask task = (MetricJMXTask) group.getTask(i++);
                final MetricType metricType = task.getMetricInstance().getMetricType().getMetricType();

                // TODO get value from results - might need to aggregate
                Avail avail = Avail.UP;
                completionHandler.onCompleted(new AvailDataPoint(task, avail));

            }
        } catch (Throwable e) {
            this.diagnostics.getJMXErrorRate().mark(1);
            completionHandler.onFailed(e);
            // we are going to mark the availabilities UNKNOWN since we really don't know
            this.group.forEach(new Consumer<Task>() {
                @Override
                public void accept(Task task) {
                    completionHandler.onCompleted(new AvailDataPoint(task, Avail.UNKNOWN));
                }
            });
        }
    }

    private Avail getAvailFromResponse(String response, AvailJMXTask task) {
        Avail avail;
        if (response == null) {
            response = "";
        }

        String upRegex = task.getUpRegex();
        if (upRegex == null) {
            try {
                Integer valueAsNumber = new Integer(response);
                avail = (valueAsNumber.intValue() == 0) ? Avail.DOWN : Avail.UP;
            } catch (Exception e) {
                avail = (response.matches("(?i)(UP|OK)")) ? Avail.UP : Avail.DOWN;
            }
        } else {
            avail = (response.matches(upRegex)) ? Avail.UP : Avail.DOWN;
        }
        return avail;
    }
}