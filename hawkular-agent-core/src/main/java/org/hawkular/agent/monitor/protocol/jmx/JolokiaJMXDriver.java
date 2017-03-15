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
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.management.ObjectName;

import org.hawkular.agent.monitor.diagnostics.ProtocolDiagnostics;
import org.hawkular.agent.monitor.inventory.AttributeLocation;
import org.hawkular.agent.monitor.protocol.Driver;
import org.hawkular.agent.monitor.protocol.ProtocolException;
import org.jolokia.client.J4pClient;
import org.jolokia.client.exception.J4pException;
import org.jolokia.client.request.J4pExecRequest;
import org.jolokia.client.request.J4pExecResponse;
import org.jolokia.client.request.J4pReadRequest;
import org.jolokia.client.request.J4pReadResponse;
import org.jolokia.client.request.J4pSearchRequest;
import org.jolokia.client.request.J4pSearchResponse;

import com.codahale.metrics.Timer.Context;

/**
 * The driver that will access a remote MBeanServer via the Jolokia REST API.
 *
 * @see Driver
 */
public class JolokiaJMXDriver extends JMXDriver {

    private final J4pClient client;

    /**
     * Creates the JMX driver.
     *
     * @param diagnostics
     * @param client the client used to connect to the JMX MBeanServer
     */
    public JolokiaJMXDriver(ProtocolDiagnostics diagnostics, J4pClient client) {
        super(diagnostics);
        this.client = client;
    }

    @SuppressWarnings("unchecked")
    @Override
    public Map<JMXNodeLocation, ObjectName> fetchNodes(JMXNodeLocation query) throws ProtocolException {

        try {
            J4pSearchRequest searchReq = new J4pSearchRequest(query.getObjectName().getCanonicalName());
            J4pSearchResponse searchResponse;
            try (Context timerContext = getDiagnostics().getRequestTimer().time()) {
                searchResponse = client.execute(searchReq);
            }

            Map<JMXNodeLocation, ObjectName> result = new HashMap<>();
            for (ObjectName objectName : searchResponse.getObjectNames()) {
                JMXNodeLocation location = new JMXNodeLocation(objectName);
                result.put(location, objectName);
            }
            return Collections.unmodifiableMap(result);
        } catch (J4pException e) {
            getDiagnostics().getErrorRate().mark(1);
            throw new ProtocolException(e);
        } catch (Exception e) {
            throw new ProtocolException(e);
        }
    }

    @Override
    public boolean attributeExists(AttributeLocation<JMXNodeLocation> location)
            throws ProtocolException {
        return true;
    }

    @Override
    public Object fetchAttribute(AttributeLocation<JMXNodeLocation> location) throws ProtocolException {

        try {
            String[] attribute = location.getAttribute().split("#", 2);
            J4pReadRequest request = new J4pReadRequest(location.getLocation().getObjectName(), attribute[0]);
            if (attribute.length > 1) {
                request.setPath(attribute[1]); // this is the sub-reference
            }

            J4pReadResponse response;
            try (Context timerContext = getDiagnostics().getRequestTimer().time()) {
                response = client.execute(request);
            }
            Collection<ObjectName> responseObjectNames = response.getObjectNames();
            switch (responseObjectNames.size()) {
                case 0:
                    return null;
                case 1:
                    return response.getValue();
                default:
                    List<Object> results = new ArrayList<>(responseObjectNames.size());
                    for (ObjectName responseObjectName : responseObjectNames) {
                        Object value = response.getValue(responseObjectName, location.getAttribute());
                        results.add(value);
                    }
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

    public J4pClient getClient() {
        return client;
    }

    @Override
    public Object executeOperation(ObjectName mbeanName, String operationName, Object[] params,
            Class<?>[] paramTypes) throws Exception {
        J4pExecRequest req = new J4pExecRequest(mbeanName, operationName, params);
        J4pExecResponse resp = this.getClient().execute(req);
        return resp.getValue();
    }

}
