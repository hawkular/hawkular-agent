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

import org.hawkular.agent.monitor.extension.MonitorServiceConfiguration;
import org.hawkular.agent.monitor.inventory.InventoryIdUtil;
import org.hawkular.agent.monitor.inventory.InventoryIdUtil.ResourceIdParts;
import org.hawkular.agent.monitor.inventory.ManagedServer;
import org.hawkular.agent.monitor.inventory.Name;
import org.hawkular.agent.monitor.log.AgentLoggers;
import org.hawkular.agent.monitor.log.MsgLogger;
import org.hawkular.bus.common.BasicMessageWithExtraData;
import org.hawkular.bus.common.BinaryData;
import org.hawkular.cmdgw.api.MessageUtils;
import org.hawkular.cmdgw.api.ResourcePathRequest;
import org.hawkular.cmdgw.api.ResourcePathResponse;
import org.hawkular.cmdgw.api.ResponseStatus;
import org.hawkular.dmrclient.JBossASClient;
import org.hawkular.inventory.api.model.CanonicalPath;

/**
 * A base for {@link Command}s initiated by subclasses of {@link ResourcePathRequest}.
 *
 * @author <a href="https://github.com/ppalaga">Peter Palaga</a>
 */
public abstract class AbstractResourcePathCommand<REQ extends ResourcePathRequest, //
RESP extends ResourcePathResponse, C extends JBossASClient> implements Command<REQ, RESP> {
    private static final MsgLogger log = AgentLoggers.getLogger(AbstractResourcePathCommand.class);

    /**
     * A natural language name of entity the present command is creating, removing or updating. {@link #entityType} is
     * supposed to be used in log and exception messages primarily. This field should have values like
     * {@code "Datasource"}, {@code "JDBC Driver"} or similar.
     */
    protected final String entityType;

    /**
     * A natural language name of the operation the present command is performing, such as {@code Updade}, {@code Add}
     * or {@code Remove}
     */
    protected final String operationName;

    public AbstractResourcePathCommand(String operationName, String entityType) {
        super();
        this.operationName = operationName;
        this.entityType = entityType;
    }

    @Override
    public BasicMessageWithExtraData<RESP> execute(REQ request, BinaryData ignored, CommandContext context)
            throws Exception {

        String rawResourcePath = request.getResourcePath();
        log.infof("Received request to perform [%s] on a [%s] given by inventory path [%s]", operationName, entityType,
                rawResourcePath);

        RESP response = createResponse();
        MessageUtils.prepareResourcePathResponse(request, response);

        C controllerClient = null;
        try {
            validate(request);

            // Based on the resource ID we need to know which inventory manager is handling it.
            // From the inventory manager, we can get the actual resource.
            CanonicalPath canonicalPath = CanonicalPath.fromString(rawResourcePath);

            String resourceId = canonicalPath.ids().getResourcePath().getSegment().getElementId();
            ResourceIdParts idParts = InventoryIdUtil.parseResourceId(resourceId);
            String modelNodePath = idParts.getIdPart();
            validate(modelNodePath, request);

            MonitorServiceConfiguration config = context.getMonitorServiceConfiguration();

            String managedServerName = idParts.getManagedServerName();
            ManagedServer managedServer = config.managedServersMap.get(new Name(managedServerName));
            validate(request, managedServerName, managedServer);

            controllerClient = createControllerClient(managedServer, context);

            execute(controllerClient, managedServer, modelNodePath, request, response, context);

            afterModelNodeRemoved(request, response);

        } catch (Exception e) {
            response.setStatus(ResponseStatus.ERROR);
            String msg = String.format("Could not remove [%s] [%s]: %s", entityType, rawResourcePath, e.getMessage());
            response.setMessage(msg);
        } finally {
            if (controllerClient != null) {
                try {
                    controllerClient.close();
                } catch (Exception e) {
                    log.errorf(e, "Could not close a [%s]", controllerClient.getClass().getName());
                }
            }
        }

        return new BasicMessageWithExtraData<>(response, null);

    }

    /**
     * Do whatever with the already validated parameters.
     *
     * @param controllerClient a subclass of {@link JBossASClient} returned by
     *        {@link #createControllerClient(ManagedServer, CommandContext)}
     * @param managedServer a {@link ManagedServer} instance acquired from {@link ResourcePathRequest#getResourcePath()}
     * @param modelNodePath a DMR path acquired from {@link ResourcePathRequest#getResourcePath()}
     * @param request the request
     * @param response the response
     * @param context the {@link CommandContext}
     * @throws Exception if anything goes wrong
     */
    protected abstract void execute(C controllerClient, ManagedServer managedServer, String modelNodePath, REQ request,
            RESP response, CommandContext context) throws Exception;

    /**
     * Returns a new instance of the appropriate subclass of {@link JBossASClient}.
     *
     * @param context the {@link CommandContext}
     * @param managedServer a {@link ManagedServer} instance acquired from {@link ResourcePathRequest#getResourcePath()}
     * @return a new instance of the appropriate subclass of {@link JBossASClient}
     */
    protected abstract C createControllerClient(ManagedServer managedServer, CommandContext context);

    /**
     * {@code modelNodePath} validation for subclasses.
     *
     * @param modelNodePath a DMR path to check
     * @param request the request the {@code modelNodePath} comes from
     */
    protected abstract void validate(String modelNodePath, REQ request);

    /**
     * Checks if the {@code request} has {@code resourcePath} field set. Subclasses may want to add more {@code request}
     * validations in their overrides.
     *
     * @param request the request to validate
     */
    protected void validate(REQ request) {
        if (request.getResourcePath() == null) {
            throw new IllegalArgumentException(
                    String.format("resourcePath of a [%s] cannot be null", request.getClass().getName()));
        }
    }

    /**
     * Throws an {@link IllegalArgumentException} if {@code managedServer} is {@code null}. Subclasses can add more
     * checks.
     *
     * @param managedServerName the name of the {@code managedServer}
     * @param managedServer the managed server to validate
     */
    protected void validate(REQ request, String managedServerName, ManagedServer managedServer) {
        if (managedServer == null) {
            throw new IllegalArgumentException(String.format(
                    "Cannot perform [%s] on a [%s] given by inventory path [%s]: unknown managed server [%s]",
                    entityType, managedServerName));
        }
    }

    /**
     * @return a new instance of the appropriate {@link ResourcePathResponse} subclass
     */
    protected abstract RESP createResponse();

    /**
     * Sets the {@link ResponseStatus#OK} state together with a message informing about the successful removal to
     * {@code response}. Subclasses may want to perform more modifications on {@code response}.
     *
     * @param request the request the present command is processing
     * @param response the response that will be sent back to the client
     */
    protected void afterModelNodeRemoved(REQ request, RESP response) {
        response.setStatus(ResponseStatus.OK);
        String msg = String.format("Removed [%s] given by Inventory path [%s]", entityType, request.getResourcePath());
        response.setMessage(msg);
    }

}
