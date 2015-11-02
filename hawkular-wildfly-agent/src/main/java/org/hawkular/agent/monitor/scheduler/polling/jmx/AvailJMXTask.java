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

import org.hawkular.agent.monitor.inventory.jmx.JMXAvailInstance;
import org.hawkular.agent.monitor.scheduler.config.Interval;
import org.hawkular.agent.monitor.scheduler.config.JMXEndpoint;
import org.hawkular.agent.monitor.scheduler.polling.KeyGenerator;

/**
 * Represents a JMX task that is to be used to check availability.
 */
public class AvailJMXTask extends JMXTask {

    private final JMXAvailInstance availInstance;
    private final String upRegex;

    public AvailJMXTask(
            Interval interval,
            JMXEndpoint endpoint,
            ObjectName objectName,
            String attribute,
            String subref,
            JMXAvailInstance availInstance,
            String upRegex) {

        super(Type.AVAIL, interval, endpoint, objectName, attribute, subref);
        this.availInstance = availInstance;
        this.upRegex = upRegex;
    }

    /**
     * If this task is checking an avail for an inventoried resource,
     * this will be the avail instance of that resource.
     * If there is no inventoried resource behind this availability check, this will be null.
     *
     * @return the avail instance or null if no inventoried resource backs this avail check
     */
    public JMXAvailInstance getAvailInstance() {
        return availInstance;
    }

    public String getUpRegex() {
        return upRegex;
    }

    @Override
    public KeyGenerator getKeyGenerator() {
        return new AvailJMXTaskKeyGenerator();
    }

    @Override
    public String toString() {
        StringBuilder str = new StringBuilder("AvailJMXTask: ");
        str.append("jmxTask=[").append(super.toString()).append("]");
        str.append(", upRegex=[").append(upRegex).append("]");
        return str.toString();
    }
}
