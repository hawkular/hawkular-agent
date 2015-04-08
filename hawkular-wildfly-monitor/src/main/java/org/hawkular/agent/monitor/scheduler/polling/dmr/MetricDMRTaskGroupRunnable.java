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
package org.hawkular.agent.monitor.scheduler.polling.dmr;

import java.util.List;
import java.util.concurrent.TimeUnit;

import org.hawkular.agent.monitor.diagnostics.Diagnostics;
import org.hawkular.agent.monitor.log.MsgLogger;
import org.hawkular.agent.monitor.scheduler.ModelControllerClientFactory;
import org.hawkular.agent.monitor.scheduler.polling.MetricCompletionHandler;
import org.hawkular.agent.monitor.scheduler.polling.TaskGroup;
import org.hawkular.agent.monitor.storage.DataPoint;
import org.hawkular.dmrclient.JBossASClient;
import org.jboss.dmr.ModelNode;
import org.jboss.dmr.Property;

import com.codahale.metrics.Timer;

public class MetricDMRTaskGroupRunnable implements Runnable {

    private final TaskGroup group;
    private final MetricCompletionHandler completionHandler;
    private final Diagnostics diagnostics;
    private final ModelControllerClientFactory mccFactory;
    private final ModelNode operation;

    public MetricDMRTaskGroupRunnable(TaskGroup group, MetricCompletionHandler completionHandler,
            Diagnostics diagnostics, ModelControllerClientFactory mccFactory) {
        this.group = group;
        this.completionHandler = completionHandler;
        this.diagnostics = diagnostics;
        this.mccFactory = mccFactory;

        // for the lifetime of this runnable, the operation is immutable and can be re-used
        this.operation = new ReadAttributeOperationBuilder().createOperation(group);
    }

    @Override
    public void run() {
        try (JBossASClient client = new JBossASClient(mccFactory.createClient())) {

            // execute request
            Timer.Context requestContext = diagnostics.getDMRRequestTimer().time();
            ModelNode response = client.execute(operation);
            long durationNanos = requestContext.stop();
            long durationMs = TimeUnit.MILLISECONDS.convert(durationNanos, TimeUnit.NANOSECONDS);

            if (JBossASClient.isSuccess(response)) {

                if (durationMs > group.getInterval().millis()) {
                    diagnostics.getDMRDelayedRate().mark(1);
                }

                List<Property> stepResults = JBossASClient.getResults(response).asPropertyList();

                if (stepResults.size() != group.size()) {
                    MsgLogger.LOG.warnBatchResultsDoNotMatchRequests(group.size(), stepResults.size());
                }

                int i = 0;
                for (Property step : stepResults) {
                    DMRTask task = (DMRTask) group.getTask(i);

                    // deconstruct model node
                    ModelNode data = step.getValue();
                    ModelNode dataResult = JBossASClient.getResults(data);
                    Double value = null;
                    if (task.getSubref() != null) {
                        value = dataResult.get(task.getSubref()).asDouble();
                    }
                    else {
                        value = dataResult.asDouble();
                    }

                    completionHandler.onCompleted(new DataPoint(task, value));
                    i++;
                }

            } else {
                this.diagnostics.getDMRErrorRate().mark(1);
                completionHandler.onFailed(new RuntimeException(JBossASClient.getFailureDescription(response)));
            }

        } catch (Throwable e) {
            this.diagnostics.getDMRErrorRate().mark(1);
            completionHandler.onFailed(e);
        }
    }
}