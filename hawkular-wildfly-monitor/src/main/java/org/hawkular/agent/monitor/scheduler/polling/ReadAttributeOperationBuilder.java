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

import org.hawkular.dmrclient.JBossASClient;
import org.jboss.dmr.ModelNode;

/**
 * Given a task group of metric collection tasks, creates a batch operation
 * to read the attributes.
 */
public class ReadAttributeOperationBuilder {
    public ModelNode createOperation(final TaskGroup group) {
        if (group.isEmpty()) {
            throw new IllegalArgumentException("Empty groups are not allowed");
        }

        ModelNode[] readOps = new ModelNode[group.size()];
        int i = 0;
        for (Task task : group) {
            readOps[i++] = JBossASClient.createReadAttributeRequest(task.getAttribute(), task.getAddress());
        }

        return JBossASClient.createBatchRequest(readOps);
    }
}
