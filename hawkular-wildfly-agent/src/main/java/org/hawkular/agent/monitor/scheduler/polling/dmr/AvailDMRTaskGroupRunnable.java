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

import org.hawkular.agent.monitor.api.Avail;
import org.hawkular.agent.monitor.diagnostics.Diagnostics;
import org.hawkular.agent.monitor.scheduler.ModelControllerClientFactory;
import org.hawkular.agent.monitor.scheduler.polling.AvailCompletionHandler;
import org.hawkular.agent.monitor.scheduler.polling.Task;
import org.hawkular.agent.monitor.scheduler.polling.TaskGroup;
import org.hawkular.agent.monitor.storage.AvailDataPoint;
import org.hawkular.dmrclient.JBossASClient;
import org.jboss.dmr.ModelNode;
import org.jboss.dmr.ModelType;
import org.jboss.logging.Logger;

import com.codahale.metrics.Timer;

public class AvailDMRTaskGroupRunnable implements Runnable {
    private static final Logger LOG = Logger.getLogger(MetricDMRTaskGroupRunnable.class);

    private final TaskGroup group;
    private final AvailCompletionHandler completionHandler;
    private final Diagnostics diagnostics;
    private final ModelControllerClientFactory mccFactory;
    private final ModelNode[] operations;

    public AvailDMRTaskGroupRunnable(TaskGroup group, AvailCompletionHandler completionHandler,
            Diagnostics diagnostics, ModelControllerClientFactory mccFactory) {
        this.group = group;
        this.completionHandler = completionHandler;
        this.diagnostics = diagnostics;
        this.mccFactory = mccFactory;

        // for the lifetime of this runnable, the operation is immutable and can be re-used
        this.operations = new ReadAttributeOrResourceOperationBuilder().createOperations(group);
    }

    @Override
    public void run() {
        int operationIndex = -1;

        try (JBossASClient client = new JBossASClient(mccFactory.createClient())) {

            for (ModelNode operation : this.operations) {
                operationIndex++; // move to the one we are working on - this is important in the catch block

                // execute request
                final Timer.Context requestContext = diagnostics.getDMRRequestTimer().time();
                final ModelNode response = client.execute(operation);
                final long durationNanos = requestContext.stop();
                final long durationMs = TimeUnit.MILLISECONDS.convert(durationNanos, TimeUnit.NANOSECONDS);

                final AvailDMRTask task = (AvailDMRTask) group.getTask(operationIndex);

                if (JBossASClient.isSuccess(response)) {

                    if (durationMs > group.getInterval().millis()) {
                        diagnostics.getDMRDelayedRate().mark(1);
                    }

                    // deconstruct model node
                    Avail avail;
                    if (task.getAttribute() == null) {
                        // step operation didn't read any attribute, it just read the resource to see if it exists
                        avail = Avail.UP;
                    } else {
                        // operation read attribute; need to see what avail that attrib value corresponds to
                        final ModelNode result = JBossASClient.getResults(response);
                        if (result.getType() != ModelType.UNDEFINED) {
                            if (result.getType() == ModelType.LIST) {
                                // a avail request that asked to aggregate avail across potentially multiple resources
                                LOG.tracef("Task [%s] resulted in aggregated avail: %s", task, result);
                                Avail aggregate = null;
                                List<ModelNode> listNodes = result.asList();
                                for (ModelNode listNode : listNodes) {
                                    if (JBossASClient.isSuccess(listNode)) {
                                        avail = getAvailFromResponse(listNode, task);
                                        // If we don't know the avail yet, set it to the first avail result we get.
                                        // Otherwise, if the aggregate is down, it stays down (we don't have the
                                        // concept of MIXED). The aggregate stays as it was unless the new avail
                                        // is down in which case aggregate goes down.
                                        if (aggregate == null) {
                                            aggregate = avail;
                                        } else {
                                            aggregate = (avail == Avail.DOWN) ? Avail.DOWN : aggregate;
                                        }
                                    } else {
                                        // a resource failed to report avail but keep going and aggregate the others
                                        this.diagnostics.getDMRErrorRate().mark(1);
                                        LOG.debugf("Failed to fully aggregate avail for task [%s]: %s ", task,
                                                listNode);
                                    }
                                }
                                completionHandler.onCompleted(new AvailDataPoint(task, aggregate));
                            } else {
                                avail = getAvailFromResponse(response, task);
                                completionHandler.onCompleted(new AvailDataPoint(task, avail));
                            }
                        }
                    }


                } else {
                    if (task.getAttribute() == null) {
                        // operation didn't read any attribute, it just read the resource to see if it exists
                        completionHandler.onCompleted(new AvailDataPoint(task, Avail.DOWN));
                    } else {
                        this.diagnostics.getDMRErrorRate().mark(1);
                        String err = JBossASClient.getFailureDescription(response);
                        completionHandler.onFailed(new RuntimeException(err));

                        // we are going to artifically mark the availabilities UNKNOWN since we really don't know
                        completionHandler.onCompleted(new AvailDataPoint(task, Avail.UNKNOWN));
                    }
                }
            }
        } catch (Throwable e) {
            this.diagnostics.getDMRErrorRate().mark(1);
            completionHandler.onFailed(e);
            // we are going to artifically mark the availabilities UNKNOWN since we really don't know
            // only mark the ones we didn't get to yet and the one we are currently worked on
            for (int i = operationIndex; i < this.group.size(); i++) {
                Task task = this.group.getTask(i);
                completionHandler.onCompleted(new AvailDataPoint(task, Avail.UNKNOWN));
            }
        }
    }

    private Avail getAvailFromResponse(final ModelNode response, final AvailDMRTask task) {
        Avail avail;
        final ModelNode result = JBossASClient.getResults(response);
        final ModelNode valueNode = (task.getSubref() == null) ? result : result.get(task.getSubref());
        String value = null;
        if (valueNode.getType() != ModelType.UNDEFINED) {
            value = valueNode.asString();
        }
        if (value == null) {
            value = "";
        }

        String upRegex = task.getUpRegex();
        if (upRegex == null) {
            try {
                Integer valueAsNumber = new Integer(value);
                avail = (valueAsNumber.intValue() == 0) ? Avail.DOWN : Avail.UP;
            } catch (Exception e) {
                avail = (value.matches("(?i)(UP|OK)")) ? Avail.UP : Avail.DOWN;
            }
        } else {
            avail = (value.matches(upRegex)) ? Avail.UP : Avail.DOWN;
        }
        return avail;
    }
}