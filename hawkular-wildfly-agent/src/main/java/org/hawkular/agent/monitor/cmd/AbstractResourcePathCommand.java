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
package org.hawkular.agent.monitor.cmd;

import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.List;

import org.hawkular.agent.monitor.inventory.InventoryIdUtil;
import org.hawkular.agent.monitor.inventory.InventoryIdUtil.ResourceIdParts;
import org.hawkular.agent.monitor.inventory.MonitoredEndpoint;
import org.hawkular.agent.monitor.log.AgentLoggers;
import org.hawkular.agent.monitor.log.MsgLogger;
import org.hawkular.agent.monitor.protocol.EndpointService;
import org.hawkular.agent.monitor.protocol.dmr.DMRNodeLocation;
import org.hawkular.agent.monitor.protocol.dmr.DMRSession;
import org.hawkular.bus.common.BasicMessageWithExtraData;
import org.hawkular.bus.common.BinaryData;
import org.hawkular.cmdgw.api.MessageUtils;
import org.hawkular.cmdgw.api.ResourcePathRequest;
import org.hawkular.cmdgw.api.ResourcePathResponse;
import org.hawkular.cmdgw.api.ResponseStatus;
import org.hawkular.inventory.api.model.CanonicalPath;
import org.jboss.as.controller.client.ModelControllerClient;
import org.jboss.dmr.ModelNode;
import org.jboss.dmr.Property;

/**
 * A base for {@link Command}s initiated by subclasses of {@link ResourcePathRequest}.
 *
 * @author <a href="https://github.com/ppalaga">Peter Palaga</a>
 */
public abstract class AbstractResourcePathCommand<REQ extends ResourcePathRequest, //
RESP extends ResourcePathResponse> implements Command<REQ, RESP> {
    private static final MsgLogger log = AgentLoggers.getLogger(AbstractResourcePathCommand.class);

    /**
     * A natural language name of entity the present command is creating, removing or updating. {@link #entityType} is
     * supposed to be used in log and exception messages primarily. This field should have values like
     * {@code "Datasource"}, {@code "JDBC Driver"} or similar.
     */
    protected final String entityType;

    /**
     * A natural language name of the operation the present command is performing, such as {@code Updade}, {@code Add}
     * or {@code Remove}. {@link #operationName} is supposed to be used in log and exception messages primarily.
     */
    protected final String operationName;

    public AbstractResourcePathCommand(String operationName, String entityType) {
        super();
        this.operationName = operationName;
        this.entityType = entityType;
    }

    protected String getOperationName(BasicMessageWithExtraData<REQ> envelope) {
        return this.operationName;
    }

    @Override
    public BasicMessageWithExtraData<RESP> execute(BasicMessageWithExtraData<REQ> envelope, CommandContext context)
            throws Exception {

        REQ request = envelope.getBasicMessage();
        String rawResourcePath = request.getResourcePath();
        log.infof("Received request to perform [%s] on a [%s] given by inventory path [%s]",
                this.getOperationName(envelope), entityType, rawResourcePath);

        RESP response = createResponse();
        MessageUtils.prepareResourcePathResponse(request, response);
        BinaryData binaryData = null;
        ModelControllerClient controllerClient = null;
        long timestampBeforeExecution = System.currentTimeMillis();
        try {
            validate(envelope);

            // Based on the resource ID we need to know which inventory manager is handling it.
            // From the inventory manager, we can get the actual resource.
            CanonicalPath canonicalPath = CanonicalPath.fromString(rawResourcePath);

            String resourceId = canonicalPath.ids().getResourcePath().getSegment().getElementId();
            ResourceIdParts idParts = InventoryIdUtil.parseResourceId(resourceId);
            String modelNodePath = idParts.getIdPart();
            validate(modelNodePath, envelope);

            String managedServerName = idParts.getManagedServerName();
            EndpointService<DMRNodeLocation, DMRSession> //
            endpointService =
                    context.getDiscoveryService().getProtocolServices().getDmrProtocolService().getEndpointServices()
                            .get(managedServerName);
            if (endpointService == null) {
                throw new IllegalArgumentException(String.format(
                        "Cannot perform [%s] on a [%s] given by inventory path [%s]: unknown managed server [%s]",
                        this.getOperationName(envelope), entityType, managedServerName));
            }

            validate(envelope, endpointService.getEndpoint());

            DMRSession session = endpointService.openSession();

            controllerClient = session.getClient();

            binaryData =
                    execute(controllerClient, endpointService, modelNodePath, envelope, response, context, session);
            success(envelope, response);

        } catch (Exception e) {
            response.setStatus(ResponseStatus.ERROR);
            String formattedTimestamp = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mmX").withZone(ZoneOffset.UTC)
                    .format(Instant.ofEpochMilli(timestampBeforeExecution));

            String msg = String.format(
                    "Could not perform [%s] on a [%s] given by inventory path [%s] requested on [%s]: %s",
                    this.getOperationName(envelope), entityType, rawResourcePath, formattedTimestamp, e.getMessage());
            response.setMessage(msg);
            log.debug(msg, e);
        } finally {
            if (controllerClient != null) {
                try {
                    controllerClient.close();
                } catch (Exception e) {
                    log.errorf(e, "Could not close a [%s]", controllerClient.getClass().getName());
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
            EndpointService<DMRNodeLocation, DMRSession>//
            endpointService,
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
     * Checks if the {@code request} has {@code resourcePath} field set. Subclasses may want to add more {@code request}
     * validations in their overrides.
     *
     * @param envelope the request to validate
     */
    protected void validate(BasicMessageWithExtraData<REQ> envelope) {
        if (envelope.getBasicMessage().getResourcePath() == null) {
            throw new IllegalArgumentException(
                    String.format("resourcePath of a [%s] cannot be null", envelope.getClass().getName()));
        }
    }

    /**
     * Validation for subclasses.
     *
     * @param envelope a DMR path to check
     * @param dmrEndpoint the request the {@code modelNodePath} comes from
     */
    protected abstract void validate(BasicMessageWithExtraData<REQ> envelope, MonitoredEndpoint endpoint);

    /**
     * @return a new instance of the appropriate {@link ResourcePathResponse} subclass
     */
    protected abstract RESP createResponse();

    protected void success(BasicMessageWithExtraData<REQ> envelope, RESP response) {
        response.setStatus(ResponseStatus.OK);
        String msg = String.format("Performed [%s] on a [%s] given by Inventory path [%s]",
                this.getOperationName(envelope), entityType, envelope.getBasicMessage().getResourcePath());
        response.setMessage(msg);

    }

    protected void assertLocalServer(MonitoredEndpoint endpoint) {
        if (!endpoint.isLocal()) {
            throw new IllegalStateException(String.format(
                    "Cannot perform [%s] on a [%s] on a non local instance of [%s].", operationName,
                    entityType, endpoint.getClass().getName()));
        }
    }

    protected void assertNotRename(ModelNode adr, String newName) {
        List<Property> adrProps = adr.asPropertyList();
        String nameFromPath = adrProps.get(adrProps.size() - 1).getValue().asString();
        if (!nameFromPath.equals(newName)) {
            String msg = String.format("Renaming a [%s] is not supported. Old name: [%s], new name: [%s]", entityType,
                    nameFromPath, newName);
            throw new IllegalArgumentException(msg);
        }
    }
}
