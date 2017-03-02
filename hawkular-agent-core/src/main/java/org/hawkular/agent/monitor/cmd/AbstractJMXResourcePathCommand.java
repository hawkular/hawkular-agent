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

import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;

import org.hawkular.agent.monitor.inventory.InventoryIdUtil;
import org.hawkular.agent.monitor.inventory.InventoryIdUtil.ResourceIdParts;
import org.hawkular.agent.monitor.log.AgentLoggers;
import org.hawkular.agent.monitor.log.MsgLogger;
import org.hawkular.agent.monitor.protocol.EndpointService;
import org.hawkular.agent.monitor.protocol.jmx.JMXNodeLocation;
import org.hawkular.agent.monitor.protocol.jmx.JMXSession;
import org.hawkular.bus.common.BasicMessageWithExtraData;
import org.hawkular.bus.common.BinaryData;
import org.hawkular.cmdgw.api.MessageUtils;
import org.hawkular.cmdgw.api.ResourcePathRequest;
import org.hawkular.cmdgw.api.ResourcePathResponse;
import org.hawkular.cmdgw.api.ResponseStatus;
import org.hawkular.inventory.paths.CanonicalPath;

/**
 * A base for {@link Command}s initiated by subclasses of {@link ResourcePathRequest}.
 *
 * Note that this assumes the resource path refers to a JMX resource! Thus commands that extend
 * this class will assume to need to access a JMX endpoint and will perform JMX-related operations.
 */
public abstract class AbstractJMXResourcePathCommand //
<REQ extends ResourcePathRequest, RESP extends ResourcePathResponse>
        extends AbstractResourcePathCommand<REQ, RESP> {

    private static final MsgLogger log = AgentLoggers.getLogger(AbstractJMXResourcePathCommand.class);

    public AbstractJMXResourcePathCommand(String operationName, String entityType) {
        super(operationName, entityType);
    }

    @Override
    public BasicMessageWithExtraData<RESP> execute(BasicMessageWithExtraData<REQ> envelope, CommandContext context)
            throws Exception {

        REQ request = envelope.getBasicMessage();
        String rawResourcePath = request.getResourcePath();
        log.infoReceivedResourcePathCommand(this.getOperationName(envelope), this.getEntityType(envelope),
                rawResourcePath);

        RESP response = createResponse();
        MessageUtils.prepareResourcePathResponse(request, response);
        BinaryData binaryData = null;
        long timestampBeforeExecution = System.currentTimeMillis();

        try {
            validate(envelope);

            // Based on the resource ID we need to know which inventory manager is handling it.
            // From the inventory manager, we can get the actual resource.
            CanonicalPath canonicalPath = CanonicalPath.fromString(rawResourcePath);

            String resourceId;
            try {
                resourceId = canonicalPath.ids().getResourcePath().getSegment().getElementId();
            } catch (Exception e) {
                throw new IllegalArgumentException("Bad resource path specified in command: " + rawResourcePath);
            }
            ResourceIdParts idParts = InventoryIdUtil.parseResourceId(resourceId);
            String managedServerName = idParts.getManagedServerName();
            EndpointService<JMXNodeLocation, JMXSession> endpointService = context.getAgentCoreEngine()
                    .getProtocolServices()
                    .getJmxProtocolService()
                    .getEndpointServices()
                    .get(managedServerName);
            if (endpointService == null) {
                throw new IllegalArgumentException(String.format(
                        "Cannot perform [%s] on a [%s] given by inventory path [%s]: unknown managed server [%s]",
                        this.getOperationName(envelope), this.getEntityType(envelope), resourceId, managedServerName));
            }

            if (modifiesResource()) {
                if (context.getAgentCoreEngine().isImmutable()) {
                    throw new IllegalStateException("Command not allowed because the agent is immutable");
                }
            }

            binaryData = execute(endpointService, resourceId, envelope, response, context);
            success(envelope, response);

        } catch (Throwable t) {
            response.setStatus(ResponseStatus.ERROR);
            String formattedTimestamp = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mmX").withZone(ZoneOffset.UTC)
                    .format(Instant.ofEpochMilli(timestampBeforeExecution));

            String msg = String.format(
                    "Could not perform [%s] on a [%s] given by inventory path [%s] requested on [%s]: %s",
                    this.getOperationName(envelope), this.getEntityType(envelope), rawResourcePath, formattedTimestamp,
                    t.toString());
            response.setMessage(msg);
            log.debug(msg, t);
        }

        return new BasicMessageWithExtraData<>(response, binaryData);

    }

    /**
     * Execute the command.
     *
     * @param endpointService an {@link EndpointService} belonging to the resource
     * @param resourceId the resource ID of the target of the command - usually this is a JMX ObjectName
     * @param envelope the request
     * @param response the response
     * @param context the {@link CommandContext}
     * @return a {@link BinaryData} with binary content if this command returns any
     * @throws Exception if anything goes wrong
     */
    protected abstract BinaryData execute(
            EndpointService<JMXNodeLocation, JMXSession> endpointService,
            String resourceId,
            BasicMessageWithExtraData<REQ> envelope,
            RESP response,
            CommandContext context) throws Exception;
}
