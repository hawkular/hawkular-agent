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
import java.util.List;
import java.util.Optional;

import org.hawkular.agent.monitor.inventory.InventoryIdUtil;
import org.hawkular.agent.monitor.inventory.InventoryIdUtil.ResourceIdParts;
import org.hawkular.agent.monitor.log.AgentLoggers;
import org.hawkular.agent.monitor.log.MsgLogger;
import org.hawkular.agent.monitor.protocol.EndpointService;
import org.hawkular.agent.monitor.protocol.dmr.DMRNodeLocation;
import org.hawkular.agent.monitor.protocol.dmr.DMRSession;
import org.hawkular.bus.common.BasicMessageWithExtraData;
import org.hawkular.bus.common.BinaryData;
import org.hawkular.cmdgw.api.MessageUtils;
import org.hawkular.cmdgw.api.ResourceRequest;
import org.hawkular.cmdgw.api.ResourceResponse;
import org.hawkular.cmdgw.api.ResponseStatus;
import org.hawkular.cmdgw.api.ServerRefreshIndicator;
import org.hawkular.dmr.api.OperationBuilder.OperationResult;
import org.jboss.as.controller.client.ModelControllerClient;
import org.jboss.dmr.ModelNode;
import org.jboss.dmr.Property;

/**
 * A base for {@link Command}s initiated by subclasses of {@link ResourcePathRequest}.
 *
 * Note that this assumes the resource path refers to DMR resource! Thus commands that extend
 * this class will assume to need to access a DMR endpoint and will perform DMR-related operations.
 *
 * @author <a href="https://github.com/ppalaga">Peter Palaga</a>
 */
public abstract class AbstractDMRResourcePathCommand //
<REQ extends ResourceRequest, RESP extends ResourceResponse>
        extends AbstractResourceCommand<REQ, RESP> {

    private static final MsgLogger log = AgentLoggers.getLogger(AbstractDMRResourcePathCommand.class);

    public AbstractDMRResourcePathCommand(String operationName, String entityType) {
        super(operationName, entityType);
    }

    @Override
    public BasicMessageWithExtraData<RESP> execute(BasicMessageWithExtraData<REQ> envelope, CommandContext context)
            throws Exception {

        REQ request = envelope.getBasicMessage();
        String rawResourcePath = request.getResourceId();
        log.infoReceivedResourcePathCommand(this.getOperationName(envelope), this.getEntityType(envelope),
                rawResourcePath);

        RESP response = createResponse();
        MessageUtils.prepareResourceResponse(request, response);
        BinaryData binaryData = null;
        ModelControllerClient controllerClient = null;
        long timestampBeforeExecution = System.currentTimeMillis();
        try {
            validate(envelope);

            String resourceId = rawResourcePath;
            ResourceIdParts idParts = InventoryIdUtil.parseResourceId(resourceId);
            String modelNodePath = idParts.getIdPart();
            validate(modelNodePath, envelope);

            String managedServerName = idParts.getManagedServerName();
            EndpointService<DMRNodeLocation, DMRSession> endpointService = context.getAgentCoreEngine()
                    .getProtocolServices()
                    .getDmrProtocolService()
                    .getEndpointServices()
                    .get(managedServerName);
            if (endpointService == null) {
                throw new IllegalArgumentException(String.format(
                        "Cannot perform [%s] on a [%s] given by inventory path [%s]: unknown managed server [%s]",
                        this.getOperationName(envelope), this.getEntityType(envelope), resourceId, managedServerName));
            }

            validate(envelope, endpointService.getMonitoredEndpoint());

            if (modifiesResource()) {
                if (context.getAgentCoreEngine().isImmutable()) {
                    throw new IllegalStateException("Command not allowed because the agent is immutable");
                }
            }

            DMRSession session = endpointService.openSession();

            controllerClient = session.getClient();

            binaryData = execute(controllerClient, endpointService, modelNodePath, envelope, response, context,
                    session);
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
        } finally {
            if (controllerClient != null) {
                try {
                    controllerClient.close();
                } catch (Exception e) {
                    log.errorCannotClose(e, controllerClient.getClass().getName());
                }
            }
        }

        return new BasicMessageWithExtraData<>(response, binaryData);

    }

    /**
     * Do whatever with the already validated parameters.
     *
     * @param controllerClient a plain {@link ModelControllerClient}
     * @param endpointService an {@link EndpointService} belonging to the resource
     * @param modelNodePath a DMR path acquired from {@link ResourcePathRequest#getResourcePath()}
     * @param envelope the request
     * @param response the response
     * @param context the {@link CommandContext}
     * @param dmrContext a {@link DMRSession}
     * @return a {@link BinaryData} with binary content if this command returns any
     * @throws Exception if anything goes wrong
     */
    protected abstract BinaryData execute(ModelControllerClient controllerClient,
            EndpointService<DMRNodeLocation, DMRSession> endpointService,
            String modelNodePath, BasicMessageWithExtraData<REQ> envelope, RESP response, CommandContext context,
            DMRSession dmrContext)
            throws Exception;

    /**
     * {@code modelNodePath} validation for subclasses.
     *
     * @param modelNodePath a DMR path to check
     * @param envelope the request the {@code modelNodePath} comes from
     */
    protected abstract void validate(String modelNodePath, BasicMessageWithExtraData<REQ> envelope);

    /**
     * Given the results of an operation, this will set the {@link ServerRefreshIndicator}
     * found in those results in the given response.
     *
     * @param opResults contains the DMR results
     * @param response the response message
     */
    protected void setServerRefreshIndicator(OperationResult<?> opResults, RESP response) {
        Optional<String> processState = opResults.getOptionalProcessState();
        if (processState.isPresent()) {
            try {
                response.setServerRefreshIndicator(ServerRefreshIndicator.fromValue(processState.get().toUpperCase()));
            } catch (Exception e) {
                log.warnf("Cannot set server refresh indicator - process state is invalid", e);
            }
        }
    }

    protected void assertNotRename(ModelNode adr, String newName) {
        List<Property> adrProps = adr.asPropertyList();
        String nameFromPath = adrProps.get(adrProps.size() - 1).getValue().asString();
        if (!nameFromPath.equals(newName)) {
            String msg = String.format("Renaming a [%s] is not supported. Old name: [%s], new name: [%s]",
                    this.getEntityType(null), nameFromPath, newName);
            throw new IllegalArgumentException(msg);
        }
    }
}
