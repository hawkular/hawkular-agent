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
package org.hawkular.agent.monitor.protocol.jmx;

import java.util.Collections;
import java.util.Set;
import java.util.TreeSet;

import javax.management.MalformedObjectNameException;
import javax.management.ObjectName;

import org.hawkular.agent.monitor.inventory.NodeLocation;

/**
 * A {@link NodeLocation} based on JMX {@link ObjectName}.
 *
 * @author <a href="https://github.com/ppalaga">Peter Palaga</a>
 */
public class JMXNodeLocation implements NodeLocation {
    private final Set<String> canonicalKeys;
    private final ObjectName objectName;

    public JMXNodeLocation(ObjectName objectName) {
        super();
        if (objectName == null) {
            throw new IllegalArgumentException(
                    "Cannot create a new [" + getClass().getName() + "] with a null objectName");
        }
        this.objectName = objectName;
        this.canonicalKeys = Collections.unmodifiableSet(new TreeSet<>(objectName.getKeyPropertyList().keySet()));

    }

    public JMXNodeLocation(String objectName) throws MalformedObjectNameException {
        this(new ObjectName(objectName));
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj)
            return true;
        if (obj == null)
            return false;
        if (getClass() != obj.getClass())
            return false;
        JMXNodeLocation other = (JMXNodeLocation) obj;
        if (objectName == null) {
            if (other.objectName != null)
                return false;
        } else if (!objectName.equals(other.objectName))
            return false;
        return true;
    }

    public Set<String> getCanonicalKeys() {
        return canonicalKeys;
    }

    public ObjectName getObjectName() {
        return objectName;
    }

    @Override
    public int hashCode() {
        return objectName.hashCode();
    }

    @Override
    public String toString() {
        return objectName.getCanonicalName();
    }

}
