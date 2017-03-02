/*
 * Copyright 2015-2017 Red Hat, Inc. and/or its affiliates
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

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.management.MBeanServerConnection;
import javax.management.ObjectName;
import javax.management.openmbean.CompositeData;

import org.hawkular.agent.monitor.diagnostics.ProtocolDiagnostics;
import org.hawkular.agent.monitor.inventory.AttributeLocation;
import org.hawkular.agent.monitor.protocol.Driver;
import org.hawkular.agent.monitor.protocol.ProtocolException;

import com.codahale.metrics.Timer.Context;

/**
 * The driver that will access a local MBeanServer via the JMX API.
 *
 * @see Driver
 */
public class MBeanServerConnectionJMXDriver extends JMXDriver {

    private final MBeanServerConnection mbs;

    /**
     * Creates the JMX driver.
     *
     * @param diagnostics
     * @param mbs the client used to connect to the JMX MBeanServer
     */
    public MBeanServerConnectionJMXDriver(ProtocolDiagnostics diagnostics, MBeanServerConnection mbs) {
        super(diagnostics);
        this.mbs = mbs;
    }

    @SuppressWarnings("unchecked")
    @Override
    public Map<JMXNodeLocation, ObjectName> fetchNodes(JMXNodeLocation query) throws ProtocolException {

        try {
            Set<ObjectName> searchResponse;
            try (Context timerContext = getDiagnostics().getRequestTimer().time()) {
                searchResponse = this.mbs.queryNames(query.getObjectName(), null);
            }

            Map<JMXNodeLocation, ObjectName> result = new HashMap<>();
            for (ObjectName objectName : searchResponse) {
                JMXNodeLocation location = new JMXNodeLocation(objectName);
                result.put(location, objectName);
            }
            return Collections.unmodifiableMap(result);
        } catch (Exception e) {
            getDiagnostics().getErrorRate().mark(1);
            throw new ProtocolException(e);
        }
    }

    @Override
    public boolean attributeExists(AttributeLocation<JMXNodeLocation> location)
            throws ProtocolException {
        return true; // assume it exists
    }

    @Override
    public Object fetchAttribute(AttributeLocation<JMXNodeLocation> location) throws ProtocolException {
        try {
            Map<JMXNodeLocation, ObjectName> all = fetchNodes(location.getLocation());
            if (all.isEmpty()) {
                return null;
            }

            String[] attributeArr = location.getAttribute().split("#", 2);
            String mainAttribute = attributeArr[0];
            String subAttribute = (attributeArr.length > 1) ? attributeArr[1] : null;

            List<Object> results = new ArrayList<>(all.size());

            for (Map.Entry<JMXNodeLocation, ObjectName> entry : all.entrySet()) {
                ObjectName objName = entry.getValue();
                Object value;
                try (Context timerContext = getDiagnostics().getRequestTimer().time()) {
                    value = this.mbs.getAttribute(objName, mainAttribute);
                }
                if (subAttribute == null) {
                    results.add(value); // found the attribute
                } else {
                    if (value instanceof CompositeData) {
                        CompositeData cd = (CompositeData) value;
                        results.add(cd.get(subAttribute));
                    } else {
                        throw new Exception("Not a composite attribute: " + location);
                    }
                }
            }

            if (results.size() == 1) {
                return results.get(0);
            } else {
                return Collections.unmodifiableList(results);
            }
        } catch (Exception e) {
            getDiagnostics().getErrorRate().mark(1);
            throw new ProtocolException(e);
        }
    }

    @Override
    public Map<JMXNodeLocation, Object> fetchAttributeAsMap(AttributeLocation<JMXNodeLocation> location)
            throws ProtocolException {

        // short-circuit if its only one location
        if (!new JMXLocationResolver().isMultiTarget(location.getLocation())) {
            Object o = fetchAttribute(location);
            return Collections.singletonMap(location.getLocation(), o);
        }

        Map<JMXNodeLocation, ObjectName> nodes = fetchNodes(location.getLocation());
        Map<JMXNodeLocation, Object> attribsMap = new HashMap<>(nodes.size());
        for (Map.Entry<JMXNodeLocation, ObjectName> entry : nodes.entrySet()) {
            Object o = fetchAttribute(new AttributeLocation<>(entry.getKey(), location.getAttribute()));
            attribsMap.put(entry.getKey(), o);
        }

        return Collections.unmodifiableMap(attribsMap);
    }

    @Override
    public Object executeOperation(ObjectName mbeanName, String operationName, Object[] args, Class<?>[] signature)
            throws Exception {
        String[] signatureAsStrings;
        if (signature == null) {
            signatureAsStrings = new String[0];
        } else {
            signatureAsStrings = new String[signature.length];
            for (int i = 0; i < signature.length; i++) {
                signatureAsStrings[i] = signature[i].getName();
            }
        }
        return this.mbs.invoke(mbeanName, operationName, args, signatureAsStrings);
    }
}
