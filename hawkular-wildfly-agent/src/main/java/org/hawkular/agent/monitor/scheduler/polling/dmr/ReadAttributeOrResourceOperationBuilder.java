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

import org.hawkular.agent.monitor.scheduler.polling.Task;
import org.hawkular.agent.monitor.scheduler.polling.TaskGroup;
import org.hawkular.dmrclient.JBossASClient;
import org.jboss.dmr.ModelNode;

/**
 * Given a task group of DMR tasks, creates a batch operations to read the attributes for those
 * tasks that have an attribute defined and to read a resource for those tasks that have no
 * attribute defined (the latter is just a way to confirm the resource exists).
 */
public class ReadAttributeOrResourceOperationBuilder {
    // Returns a batch operation that obtains all the data in one request.
    public ModelNode createBatchOperation(final TaskGroup group) {
        return JBossASClient.createBatchRequest(createOperations(group));
    }

    // Returns one request operation per group item
    public ModelNode[] createOperations(final TaskGroup group) {
        if (group.isEmpty()) {
            throw new IllegalArgumentException("Empty groups are not allowed");
        }

        int i = 0;
        ModelNode[] readOps = new ModelNode[group.size()];
        for (Task task : group) {
            DMRTask dmrTask = (DMRTask) task;

            if (dmrTask.getAttribute() != null) {
                readOps[i++] = JBossASClient.createReadAttributeRequest(dmrTask.getAttribute(), dmrTask.getAddress());
            } else {
                readOps[i++] = JBossASClient.createRequest(JBossASClient.READ_RESOURCE, dmrTask.getAddress());
            }
        }

        return readOps;
    }
}
