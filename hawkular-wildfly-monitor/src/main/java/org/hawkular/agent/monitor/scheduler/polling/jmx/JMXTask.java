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

import javax.management.ObjectName;

import org.hawkular.agent.monitor.scheduler.config.Interval;
import org.hawkular.agent.monitor.scheduler.config.JMXEndpoint;
import org.hawkular.agent.monitor.scheduler.polling.Task;

/**
 * Represents a task that is to be executed on a JMX resource.
 */
public abstract class JMXTask implements Task {

    private final class JMXTaskKind implements Kind {
        private final String id;

        private JMXTaskKind(JMXTask us) {
            StringBuilder idBuilder = new StringBuilder();
            idBuilder.append(us.getClass().getName()).append(":");
            idBuilder.append(us.getType()).append(":");
            idBuilder.append(us.getEndpoint().getURL()).append(":");
            idBuilder.append(us.getEndpoint().getUsername());
            id = idBuilder.toString();
        }

        @Override
        public String getId() {
            return id;
        }
    }

    private final JMXEndpoint endpoint;
    private final Type type;
    private final ObjectName objectName;
    private final String attribute;
    private final String subref;
    private final Interval interval;

    public JMXTask(
            Type type,
            Interval interval,
            JMXEndpoint endpoint,
            ObjectName objectName,
            String attribute,
            String subref) {

        if (type == null) {
            throw new IllegalArgumentException("type cannot be null");
        }

        if (interval == null) {
            throw new IllegalArgumentException("interval cannot be null");
        }

        if (endpoint == null) {
            throw new IllegalArgumentException("endpoint cannot be null");
        }

        if (objectName == null) {
            throw new IllegalArgumentException("object name cannot be null");
        }

        this.type = type;
        this.interval = interval;
        this.endpoint = endpoint;
        this.objectName = objectName;
        this.attribute = attribute;
        this.subref = subref;
    }

    @Override
    public Type getType() {
        return type;
    }

    @Override
    public Interval getInterval() {
        return interval;
    }

    @Override
    public Kind getKind() {
        return new JMXTaskKind(this);
    }

    public JMXEndpoint getEndpoint() {
        return endpoint;
    }

    public ObjectName getObjectName() {
        return objectName;
    }

    public String getAttribute() {
        return attribute;
    }

    public String getSubref() {
        return subref;
    }

    @Override
    public String toString() {
        StringBuilder str = new StringBuilder("JMXTask: ");
        str.append("endpoint=[").append(endpoint).append("]");
        str.append(", type=[").append(type).append("]");
        str.append(", interval=[").append(interval).append("]");
        str.append(", objectName=[").append(objectName).append("]");
        str.append(", attribute=[").append(attribute).append("]");
        str.append(", subref=[").append(subref).append("]");
        str.append(", kind=[").append(getKind().getId()).append("]");
        return str.toString();
    }
}
