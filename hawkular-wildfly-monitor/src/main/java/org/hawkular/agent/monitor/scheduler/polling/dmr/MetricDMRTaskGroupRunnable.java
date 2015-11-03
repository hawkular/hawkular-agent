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
import org.hawkular.agent.monitor.scheduler.ModelControllerClientFactory;
import org.hawkular.agent.monitor.scheduler.polling.MetricCompletionHandler;
import org.hawkular.agent.monitor.scheduler.polling.TaskGroup;
import org.hawkular.agent.monitor.storage.MetricDataPoint;
import org.hawkular.dmrclient.JBossASClient;
import org.hawkular.metrics.client.common.MetricType;
import org.jboss.dmr.ModelNode;
import org.jboss.dmr.ModelType;
import org.jboss.logging.Logger;

import com.codahale.metrics.Timer;

public class MetricDMRTaskGroupRunnable implements Runnable {
    private static final Logger LOG = Logger.getLogger(MetricDMRTaskGroupRunnable.class);

    private final TaskGroup group;
    private final MetricCompletionHandler completionHandler;
    private final Diagnostics diagnostics;
    private final ModelControllerClientFactory mccFactory;
    private final ModelNode[] operations;

    public MetricDMRTaskGroupRunnable(TaskGroup group, MetricCompletionHandler completionHandler,
            Diagnostics diagnostics, ModelControllerClientFactory mccFactory) {
        this.group = group;
        this.completionHandler = completionHandler;
        this.diagnostics = diagnostics;
        this.mccFactory = mccFactory;

        // for the lifetime of this runnable, the operation is immutable and can be re-used
        this.operations = new ReadAttributeOperationBuilder().createOperations(group);
    }

    @Override
    public void run() {
        try (final JBossASClient client = new JBossASClient(mccFactory.createClient())) {

            int i = 0;
            for (ModelNode operation : this.operations) {

                // execute request
                final Timer.Context requestContext = diagnostics.getDMRRequestTimer().time();
                final ModelNode response = client.execute(operation);
                final long durationNanos = requestContext.stop();
                final long durationMs = TimeUnit.MILLISECONDS.convert(durationNanos, TimeUnit.NANOSECONDS);

                if (JBossASClient.isSuccess(response)) {

                    if (durationMs > group.getInterval().millis()) {
                        diagnostics.getDMRDelayedRate().mark(1);
                    }

                    final MetricDMRTask task = (MetricDMRTask) group.getTask(i++);
                    final MetricType metricType = task.getMetricInstance().getMetricType().getMetricType();

                    // deconstruct model node
                    final ModelNode result = JBossASClient.getResults(response);
                    if (result.getType() != ModelType.UNDEFINED) {
                        if (result.getType() == ModelType.LIST) {
                            // a metric request that asked to aggregate a metric across potentially multiple resources
                            LOG.tracef("Task [%s] resulted in aggregated metric: %s", task, result);
                            double aggregate = 0.0;
                            List<ModelNode> listNodes = result.asList();
                            for (ModelNode listNode : listNodes) {
                                if (JBossASClient.isSuccess(listNode)) {
                                    final ModelNode listNodeResult = JBossASClient.getResults(listNode);
                                    final ModelNode listNodeValueNode =
                                            (task.getSubref() == null) ? listNodeResult : listNodeResult.get(task
                                                    .getSubref());
                                    if (listNode.getType() != ModelType.UNDEFINED) {
                                        aggregate += listNodeValueNode.asDouble();
                                    }
                                } else {
                                    // a resources failed to report metric but keep going and aggregate the others
                                    this.diagnostics.getDMRErrorRate().mark(1);
                                    LOG.debugf("Failed to fully aggregate metric for task [%s]: %s ", task, listNode);
                                }
                            }
                            completionHandler.onCompleted(new MetricDataPoint(task, aggregate, metricType));
                        } else {
                            // a metric was requested from a single resource
                            final ModelNode valueNode =
                                    (task.getSubref() == null) ? result : result.get(task.getSubref());
                            final Double value = valueNode.asDouble();
                            completionHandler.onCompleted(new MetricDataPoint(task, value, metricType));
                        }
                    }

                } else {
                    this.diagnostics.getDMRErrorRate().mark(1);
                    completionHandler.onFailed(new RuntimeException(JBossASClient.getFailureDescription(response)));
                }
            }

        } catch (Throwable e) {
            this.diagnostics.getDMRErrorRate().mark(1);
            completionHandler.onFailed(e);
        }
    }
}