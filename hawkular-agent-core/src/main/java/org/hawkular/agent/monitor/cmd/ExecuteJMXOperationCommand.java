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
package org.hawkular.agent.monitor.cmd;

import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import javax.management.ObjectName;

import org.hawkular.agent.monitor.config.AgentCoreEngineConfiguration.AbstractEndpointConfiguration;
import org.hawkular.agent.monitor.inventory.ID;
import org.hawkular.agent.monitor.inventory.MonitoredEndpoint;
import org.hawkular.agent.monitor.inventory.Operation;
import org.hawkular.agent.monitor.inventory.OperationParam;
import org.hawkular.agent.monitor.inventory.Resource;
import org.hawkular.agent.monitor.inventory.ResourceManager;
import org.hawkular.agent.monitor.log.AgentLoggers;
import org.hawkular.agent.monitor.log.MsgLogger;
import org.hawkular.agent.monitor.protocol.EndpointService;
import org.hawkular.agent.monitor.protocol.jmx.JMXDriver;
import org.hawkular.agent.monitor.protocol.jmx.JMXNodeLocation;
import org.hawkular.agent.monitor.protocol.jmx.JMXSession;
import org.hawkular.bus.common.BasicMessageWithExtraData;
import org.hawkular.bus.common.BinaryData;
import org.hawkular.cmdgw.api.ExecuteOperationRequest;
import org.hawkular.cmdgw.api.ExecuteOperationResponse;

/**
 * Execute an operation on a JMX resource.
 */
public class ExecuteJMXOperationCommand extends
        AbstractJMXResourcePathCommand<ExecuteOperationRequest, ExecuteOperationResponse> {
    private static final MsgLogger log = AgentLoggers.getLogger(ExecuteJMXOperationCommand.class);

    public ExecuteJMXOperationCommand() {
        super("Execute Operation", "JMX MBean");
    }

    @Override
    protected ExecuteOperationResponse createResponse() {
        return new ExecuteOperationResponse();
    }

    @Override
    protected String getOperationName(BasicMessageWithExtraData<ExecuteOperationRequest> envelope) {
        return envelope.getBasicMessage().getOperationName();
    }

    @Override
    protected void validate(BasicMessageWithExtraData<ExecuteOperationRequest> envelope,
            MonitoredEndpoint<? extends AbstractEndpointConfiguration> endpoint) {
    }

    @Override
    protected boolean modifiesResource() {
        return false; // we don't know yet - assume it won't modify anything - we'll do the real check in execute
    }

    @Override
    protected BinaryData execute(
            EndpointService<JMXNodeLocation, JMXSession> endpointService,
            String resourceId,
            BasicMessageWithExtraData<ExecuteOperationRequest> envelope,
            ExecuteOperationResponse response,
            CommandContext context) throws Exception {

        ExecuteOperationRequest request = envelope.getBasicMessage();

        ResourceManager<JMXNodeLocation> resourceManager = endpointService.getResourceManager();
        Resource<JMXNodeLocation> resource = resourceManager.getResource(new ID(resourceId));
        if (resource == null) {
            throw new IllegalArgumentException(
                    String.format("Cannot execute operation: unknown resource [%s]", request.getResourceId()));
        }

        // find the operation we need to execute - make sure it exists and get the address for the resource to invoke
        JMXNodeLocation opLocation = null;
        Operation<JMXNodeLocation> theOperation = null;

        try (JMXSession session = endpointService.openSession()) {
            String requestedOpName = request.getOperationName();
            Collection<Operation<JMXNodeLocation>> ops = resource.getResourceType().getOperations();
            log.tracef("Searching for operation [%s] among operations [%s] for resource [%s].", requestedOpName, ops,
                    resource.getID());
            for (Operation<JMXNodeLocation> op : ops) {
                if (requestedOpName.equals(op.getName().getNameString())) {
                    opLocation = session.getLocationResolver().absolutize(resource.getLocation(), op.getLocation());
                    theOperation = op;
                    if (op.getModifies()) {
                        if (context.getAgentCoreEngine().isImmutable()) {
                            throw new IllegalStateException(
                                    "Operation [" + requestedOpName + "] not allowed because the agent is immutable");
                        }
                    }
                    break;
                }
            }

            if (opLocation == null) {
                throw new IllegalArgumentException(
                        String.format("Cannot execute operation: unknown operation [%s] for resource [%s]",
                                request.getOperationName(), resource));
            }

            response.setOperationName(request.getOperationName());

            Map<String, String> argsMap = request.getParameters();
            if (argsMap == null) {
                argsMap = Collections.emptyMap();
            }
            List<OperationParam> opSignature = theOperation.getParameters();
            Object[] args = new Object[opSignature == null ? 0 : opSignature.size()];
            Class<?>[] signature = new Class<?>[opSignature == null ? 0 : opSignature.size()];

            int i = 0;
            for (OperationParam opParam : opSignature) {
                guessJavaType(opParam, argsMap.get(opParam.getName()), i++, args, signature);
            }

            ObjectName targetMBean = opLocation.getObjectName();
            String internalOpName = theOperation.getInternalName();
            JMXDriver jmxDriver = (JMXDriver) session.getDriver();
            Object results = jmxDriver.executeOperation(targetMBean, internalOpName, args, signature);
            if (results != null) {
                response.setMessage(results.toString());
            }
        }

        // because we don't know if the effects of the operation will alter inventory,
        // let's request a full discovery scan just in case.
        endpointService.discoverAll();

        return null;
    }

    private void guessJavaType(
            OperationParam paramDef,
            String valueStr,
            int arrayIndex,
            Object[] argArray,
            Class<?>[] sigArray)
            throws Exception {

        String typeStr = paramDef.getType();
        if (valueStr == null) {
            valueStr = paramDef.getDefaultValue();
        }

        Class<?> realType = null;
        Object value = null;

        // Only supports primitive types (and their Object forms) and String.
        // If typeStr is null, we assume the type is a String.
        // If typeStr is capitalized, we assume the type is an Object.
        // If typeStr starts with a lowercase, we assume the type is a primitive.
        if (typeStr == null || typeStr.equalsIgnoreCase("string")) {
            realType = String.class;
            value = valueStr;
        } else {
            boolean isPrim = Character.isLowerCase(typeStr.charAt(0));
            if (typeStr.equalsIgnoreCase("int") || typeStr.equalsIgnoreCase("integer")) {
                realType = isPrim ? int.class : Integer.class;
                value = (valueStr == null) ? (isPrim ? Integer.valueOf(0) : null) : Integer.valueOf(valueStr);

            } else if (typeStr.equalsIgnoreCase("bool") || typeStr.equalsIgnoreCase("boolean")) {
                realType = isPrim ? boolean.class : Boolean.class;
                value = (valueStr == null) ? (isPrim ? Boolean.FALSE : null) : Boolean.valueOf(valueStr);

            } else if (typeStr.equalsIgnoreCase("long")) {
                realType = isPrim ? long.class : Long.class;
                value = (valueStr == null) ? (isPrim ? Long.valueOf(0L) : null) : Long.valueOf(valueStr);

            } else if (typeStr.equalsIgnoreCase("double")) {
                realType = isPrim ? double.class : Double.class;
                value = (valueStr == null) ? (isPrim ? Double.valueOf(0.0) : null) : Double.valueOf(valueStr);

            } else if (typeStr.equalsIgnoreCase("float")) {
                realType = isPrim ? float.class : Float.class;
                value = (valueStr == null) ? (isPrim ? Float.valueOf(0.0f) : null) : Float.valueOf(valueStr);

            } else if (typeStr.equalsIgnoreCase("short")) {
                realType = isPrim ? short.class : Short.class;
                value = (valueStr == null) ? (isPrim ? Short.valueOf((short) 0) : null) : Short.valueOf(valueStr);

            } else if (typeStr.equalsIgnoreCase("char") || typeStr.equalsIgnoreCase("character")) {
                realType = isPrim ? char.class : Character.class;
                value = (valueStr == null || valueStr.isEmpty()) ? (isPrim ? Character.valueOf((char) 0) : null)
                        : Character.valueOf(valueStr.charAt(0));

            } else if (typeStr.equalsIgnoreCase("byte")) {
                realType = isPrim ? byte.class : Byte.class;
                value = (valueStr == null) ? (isPrim ? Byte.valueOf((byte) 0) : null) : Byte.valueOf(valueStr);

            } else {
                throw new UnsupportedOperationException(
                        "Cannot support operation with param type of [" + typeStr + "]");
            }
        }
        argArray[arrayIndex] = value;
        sigArray[arrayIndex] = realType;
    }
}
