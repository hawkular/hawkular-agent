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
package org.hawkular.agent.monitor.scheduler.storage;

import org.hawkular.agent.monitor.scheduler.polling.Task;
import org.hawkular.agent.monitor.service.ServerIdentifiers;

/**
 * Resolve data input attributes to final metric (storage) names.
 */
public class KeyResolution {

    public String resolve(Task task) {

        // build the key to be of the format "[host.]server.address[.subref]"
        StringBuilder key = new StringBuilder();

        ServerIdentifiers taskId = new ServerIdentifiers(task.getHost(), task.getServer(), null);
        key.append(taskId.getFullIdentifier());

        key.append(".").append(task.getAddress().toAddressPathString());

        if (task.getSubref() != null && !task.getSubref().isEmpty()) {
            key.append(".").append(task.getSubref());
        }

        return key.toString();
    }
}
