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

import org.hawkular.agent.monitor.scheduler.polling.KeyGenerator;
import org.hawkular.agent.monitor.scheduler.polling.Task;

/**
 * Resolve data input attributes to final storage name.
 */
public abstract class JMXTaskKeyGenerator implements KeyGenerator {

    /**
     * This is used to generate keys for tasks that are not associated with any inventoried resource.
     * @param task the task whose key is to be generated
     * @return the generated key that uniquely identifies the data that is collected by the task
     */
    protected String generateDefaultKey(Task task) {
        JMXTask jmxTask = (JMXTask) task;
        StringBuilder key = new StringBuilder();
        String serverId = jmxTask.getEndpoint().getServerIdentifier();
        if (serverId == null) {
            throw new RuntimeException(String.format("Cannot generate key for task [%s]", task));
        }
        key.append(serverId);
        key.append(".").append(jmxTask.getObjectName().getCanonicalName());
        key.append(".").append(jmxTask.getAttribute());
        if (jmxTask.getSubref() != null && !jmxTask.getSubref().isEmpty()) {
            key.append(".").append(jmxTask.getSubref());
        }
        return key.toString();
    }
}
