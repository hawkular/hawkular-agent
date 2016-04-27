/*
 * Copyright 2015-2016 Red Hat, Inc. and/or its affiliates
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
package org.hawkular.agent.monitor.protocol.dmr;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.hawkular.agent.monitor.diagnostics.ProtocolDiagnostics;
import org.hawkular.agent.monitor.extension.MonitorServiceConfiguration.EndpointConfiguration;
import org.hawkular.agent.monitor.inventory.AttributeLocation;
import org.hawkular.agent.monitor.inventory.MonitoredEndpoint;
import org.hawkular.agent.monitor.log.AgentLoggers;
import org.hawkular.agent.monitor.log.MsgLogger;
import org.hawkular.agent.monitor.protocol.Driver;
import org.hawkular.agent.monitor.protocol.ProtocolException;
import org.hawkular.dmr.api.OperationBuilder;
import org.hawkular.dmr.api.OperationBuilder.OperationResult;
import org.hawkular.dmr.api.OperationBuilder.ReadAttributeOperationBuilder;
import org.hawkular.dmr.api.OperationBuilder.ReadResourceOperationBuilder;
import org.jboss.as.controller.client.ModelControllerClient;
import org.jboss.dmr.ModelNode;
import org.jboss.dmr.ModelType;

import com.codahale.metrics.Timer.Context;

/**
 * @author <a href="https://github.com/ppalaga">Peter Palaga</a>
 * @see Driver
 */
public class DMRDriver implements Driver<DMRNodeLocation> {
    private static final MsgLogger log = AgentLoggers.getLogger(DMRDriver.class);

    private static Object toObject(ModelNode value) throws ProtocolException {
        switch (value.getType()) {
            case BIG_DECIMAL:
                return value.asBigDecimal();
            case BIG_INTEGER:
                return value.asBigInteger();
            case BOOLEAN:
                return value.asBoolean();
            case BYTES:
                return value.asBytes();
            case DOUBLE:
                return value.asDouble();
            case INT:
                return value.asInt();
            case LONG:
                return value.asLong();
            case OBJECT:
                return value.asObject();
            case PROPERTY:
                return value.asProperty();
            case STRING:
                return value.asString();
            case UNDEFINED:
                return null;
            case LIST:
                return toObjectList(value.asList());
            default:
                throw new ProtocolException("cannot handle an attribute of type [" + value.getType() + "]");
        }
    }

    /**
     * Returns a {@link List} of objects extracted from the given {@code nodeList}
     *
     * @param nodeList the source list to extract the result values from
     * @return a {@link List} of objects extracted from the given {@code nodeList}
     * @throws ProtocolException
     */
    private static List<Object> toObjectList(List<ModelNode> nodeList) throws ProtocolException {
        if (nodeList.isEmpty()) {
            return Collections.emptyList();
        } else {
            ArrayList<Object> result = new ArrayList<>(nodeList.size());
            for (ModelNode node : nodeList) {
                if (node.hasDefined("result")) {
                    result.add(toObject(node.get("result")));
                } else {
                    throw new IllegalStateException("No 'result' in a nodeList item [" + node + "]");
                }
            }
            return Collections.unmodifiableList(result);
        }
    }

    private final ModelControllerClient client;
    private final MonitoredEndpoint<EndpointConfiguration> endpoint;
    private final ProtocolDiagnostics diagnostics;

    public DMRDriver(ModelControllerClient client, MonitoredEndpoint<EndpointConfiguration> endpoint,
            ProtocolDiagnostics diagnostics) {
        super();
        this.client = client;
        this.endpoint = endpoint;
        this.diagnostics = diagnostics;
    }

    @Override
    public boolean attributeExists(AttributeLocation<DMRNodeLocation> location) {
        Optional<ModelNode> result = OperationBuilder.readResource()
                .address(location.getLocation().getPathAddress())
                .includeRuntime().execute(client).getOptionalResultNode();
        return result.isPresent();
    }

    @Override
    public Object fetchAttribute(AttributeLocation<DMRNodeLocation> location) throws ProtocolException {
        String[] attribute = location.getAttribute().split("#");
        String useAttribute = attribute[0];
        ReadAttributeOperationBuilder<?> opBuilder = OperationBuilder
                .readAttribute()
                .address(location.getLocation().getPathAddress())
                .resolveExpressions()
                .name(useAttribute);

        // time the execute separately - we want to time ONLY the execute call
        OperationResult<?> opResult;
        try (Context timerContext = diagnostics.getRequestTimer().time()) {
            opResult = opBuilder.execute(client);
        } catch (Exception e) {
            diagnostics.getErrorRate().mark(1);
            throw new ProtocolException("Error fetching DMR attribute [" + useAttribute + "]", e);
        }

        // we got a response - so the underlying comm execution worked; see if we got a valid attribute value
        ModelNode value;
        try {
            value = opResult.assertSuccess().getResultNode();
        } catch (Exception e) {
            diagnostics.getErrorRate().mark(1);
            throw new ProtocolException("Unsuccessful fetching DMR attribute [" + useAttribute + "]", e);
        }

        if (attribute.length > 1 && value != null && value.isDefined()) {
            useAttribute = attribute[1];
            value = value.get(useAttribute);
        }

        if (value == null || !value.isDefined()) {
            return null;
        }

        return postProcessAttribute(useAttribute, toObject(value));
    }

    @Override
    public Map<DMRNodeLocation, Object> fetchAttributeAsMap(AttributeLocation<DMRNodeLocation> location)
            throws ProtocolException {

        // short-circuit if its only one location
        if (!new DMRLocationResolver().isMultiTarget(location.getLocation())) {
            Object o = fetchAttribute(location);
            return Collections.singletonMap(location.getLocation(), o);
        }

        String[] attribute = location.getAttribute().split("#");

        Map<DMRNodeLocation, ModelNode> nodes = fetchNodes(location.getLocation());
        Map<DMRNodeLocation, Object> attribsMap = new HashMap<>(nodes.size());
        for (Map.Entry<DMRNodeLocation, ModelNode> entry : nodes.entrySet()) {
            ModelNode attribNode = entry.getValue().get(attribute[0]);

            if (attribute.length > 1 && attribNode != null && attribNode.isDefined()) {
                attribNode = attribNode.get(attribute[1]);
            }

            Object attribObjectValue;
            if (attribNode == null || !attribNode.isDefined()) {
                attribObjectValue = null;
            } else {
                attribObjectValue = postProcessAttribute(attribute[attribute.length == 1 ? 0 : 1],
                        toObject(attribNode));
            }

            attribsMap.put(entry.getKey(), attribObjectValue);
        }

        return Collections.unmodifiableMap(attribsMap);
    }

    @SuppressWarnings("unchecked")
    @Override
    public Map<DMRNodeLocation, ModelNode> fetchNodes(DMRNodeLocation query) throws ProtocolException {
        log.tracef("About to fetch DMR nodes [%s]", query);
        ReadResourceOperationBuilder<?> opBuilder = OperationBuilder
                .readResource()//
                .address(query.getPathAddress()) //
                .includeRuntime();

        // time the execute separately - we want to time ONLY the execute call
        OperationResult<?> opResult;
        try (Context timerContext = diagnostics.getRequestTimer().time()) {
            opResult = opBuilder.execute(client);
        } catch (Exception e) {
            diagnostics.getErrorRate().mark(1);
            throw new ProtocolException("Error fetching nodes for query [" + query + "]", e);
        }

        Optional<ModelNode> resultNode = opResult.getOptionalResultNode();
        if (resultNode.isPresent()) {
            ModelNode n = resultNode.get();
            if (n.getType() == ModelType.OBJECT) {
                return Collections.singletonMap(query, n);
            } else if (n.getType() == ModelType.LIST) {
                Map<DMRNodeLocation, ModelNode> result = new HashMap<>();
                List<ModelNode> list = n.asList();
                for (ModelNode item : list) {
                    ModelNode pathAddress = item.get("address");
                    result.put(DMRNodeLocation.of(pathAddress),
                            item.hasDefined("result") ? item.get("result") : new ModelNode());
                }
                return Collections.unmodifiableMap(result);
            } else {
                throw new IllegalStateException("Invalid type - please report this bug: " + n.getType()
                        + " [[" + n.toString() + "]]");
            }

        } else {
            return Collections.emptyMap();
        }
    }

    public ModelControllerClient getClient() {
        return client;
    }

    protected Object postProcessAttribute(String attribute, Object oldValue) {
        if ("bound-address".equals(attribute)) {
            if (oldValue != null) {
                try {
                    // Replaces 0.0.0.0 server address with the list of addresses received from
                    // InetAddress.getByName(String) where the argument of getByName(String) is the host the agent
                    // uses to query the AS'es DMR.
                    InetAddress dmrAddr = InetAddress.getByName((String) oldValue);
                    if (dmrAddr.isAnyLocalAddress() && endpoint.getConnectionData() != null
                            && endpoint.getConnectionData().getUri() != null) {
                        String host = endpoint.getConnectionData().getUri().getHost();
                        InetAddress[] resolvedAddresses = InetAddress.getAllByName(host);
                        String newValue = Stream.of(resolvedAddresses).map(a -> a.getHostAddress())
                                .collect(Collectors.joining(", "));
                        if (!oldValue.equals(newValue)) {
                            return newValue;
                        }
                    }
                } catch (UnknownHostException e) {
                    log.warnf(e, "Could not parse IP address [%s]", oldValue);
                }
            }
        }

        return oldValue;
    }
}
