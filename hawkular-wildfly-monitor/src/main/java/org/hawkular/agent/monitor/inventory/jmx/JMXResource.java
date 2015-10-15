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
package org.hawkular.agent.monitor.inventory.jmx;

import javax.management.ObjectName;

import org.hawkular.agent.monitor.inventory.ID;
import org.hawkular.agent.monitor.inventory.Name;
import org.hawkular.agent.monitor.inventory.Resource;
import org.hawkular.agent.monitor.scheduler.config.JMXEndpoint;

public class JMXResource extends Resource
        <JMXResourceType,
        JMXEndpoint,
        JMXMetricInstance,
        JMXAvailInstance,
        JMXResourceConfigurationPropertyInstance> {

    private final ObjectName objectName;

    public JMXResource(ID id, Name name, JMXEndpoint endpoint, JMXResourceType type, JMXResource parent,
            ObjectName objectName) {
        super(id, name, endpoint, type, parent);
        this.objectName = objectName;
    }

    public ObjectName getObjectName() {
        return objectName;
    }

    @Override
    public String toString() {
        return String.format("%s[objectName=%s]", super.toString(), this.objectName);
    }
}
