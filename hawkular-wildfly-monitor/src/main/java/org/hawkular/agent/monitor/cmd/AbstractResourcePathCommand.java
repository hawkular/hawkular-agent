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

import java.util.List;

import org.hawkular.agent.monitor.extension.MonitorServiceConfiguration;
import org.hawkular.agent.monitor.inventory.InventoryIdUtil;
import org.hawkular.agent.monitor.inventory.InventoryIdUtil.ResourceIdParts;
import org.hawkular.agent.monitor.inventory.ManagedServer;
import org.hawkular.agent.monitor.inventory.Name;
import org.hawkular.agent.monitor.inventory.dmr.DMRInventoryManager;
import org.hawkular.agent.monitor.inventory.dmr.LocalDMRManagedServer;
import org.hawkular.agent.monitor.inventory.dmr.RemoteDMRManagedServer;
import org.hawkular.agent.monitor.log.AgentLoggers;
import org.hawkular.agent.monitor.log.MsgLogger;
import org.hawkular.bus.common.BasicMessageWithExtraData;
import org.hawkular.cmdgw.api.MessageUtils;
import org.hawkular.cmdgw.api.ResourcePathRequest;
import org.hawkular.cmdgw.api.ResourcePathResponse;
import org.hawkular.cmdgw.api.ResponseStatus;
import org.hawkular.dmrclient.JBossASClient;
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

        ModelControllerClient controllerClient = null;
        try {
            validate(envelope);

            // Based on the resource ID we need to know which inventory manager is handling it.
            // From the inventory manager, we can get the actual resource.
            CanonicalPath canonicalPath = CanonicalPath.fromString(rawResourcePath);

            String resourceId = canonicalPath.ids().getResourcePath().getSegment().getElementId();
            ResourceIdParts idParts = InventoryIdUtil.parseResourceId(resourceId);
            String modelNodePath = idParts.getIdPart();
            validate(modelNodePath, envelope);

            MonitorServiceConfiguration config = context.getMonitorServiceConfiguration();

            String managedServerName = idParts.getManagedServerName();
            ManagedServer managedServer = config.managedServersMap.get(new Name(managedServerName));
            validate(envelope, managedServerName, managedServer);

            controllerClient = createControllerClient(managedServer, context);

            execute(controllerClient, managedServer, modelNodePath, envelope, response, context);
            success(envelope, response);

        } catch (Exception e) {
            response.setStatus(ResponseStatus.ERROR);
            String msg = String.format("Could not perform [%s] on a [%s] given by inventory path [%s]: %s",
                    this.getOperationName(envelope), entityType, rawResourcePath, e.getMessage());
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

        return new BasicMessageWithExtraData<>(response, null);

    }

    /**
     * Do whatever with the already validated parameters.
     *
     * @param controllerClient a subclass of {@link JBossASClient} returned by
     *        {@link #createControllerClient(ManagedServer, CommandContext)}
     * @param managedServer a {@link ManagedServer} instance acquired from {@link ResourcePathRequest#getResourcePath()}
     * @param modelNodePath a DMR path acquired from {@link ResourcePathRequest#getResourcePath()}
     * @param envelope the request
     * @param response the response
     * @param context the {@link CommandContext}
     * @throws Exception if anything goes wrong
     */
    protected abstract void execute(ModelControllerClient controllerClient, ManagedServer managedServer,
            String modelNodePath, BasicMessageWithExtraData<REQ> envelope, RESP response, CommandContext context)
                    throws Exception;

    /**
     * Returns a freshly opened {@link ModelControllerClient}.
     *
     * @param context the {@link CommandContext}
     * @param managedServer a {@link ManagedServer} instance acquired from {@link ResourcePathRequest#getResourcePath()}
     * @return a freshly opened {@link ModelControllerClient}
     */
    protected ModelControllerClient createControllerClient(ManagedServer managedServer, CommandContext context) {
        DMRInventoryManager inventoryManager = context.getDiscoveryService().getDmrServerInventories()
                .get(managedServer);
        return inventoryManager.getModelControllerClientFactory().createClient();
    }

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
     * Throws an {@link IllegalArgumentException} if {@code managedServer} is {@code null}. Subclasses can add more
     * checks.
     *
     * @param managedServerName the name of the {@code managedServer}
     * @param managedServer the managed server to validate
     */
    protected void validate(BasicMessageWithExtraData<REQ> envelope, String managedServerName,
            ManagedServer managedServer) {
        if (managedServer == null) {
            throw new IllegalArgumentException(String.format(
                    "Cannot perform [%s] on a [%s] given by inventory path [%s]: unknown managed server [%s]",
                    this.getOperationName(envelope), entityType, managedServerName));
        }
    }

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

    protected void assertLocalOrRemoteServer(ManagedServer managedServer) {
        if (!(managedServer instanceof LocalDMRManagedServer) && !(managedServer instanceof RemoteDMRManagedServer)) {
            throw new IllegalStateException(String.format(
                    "Cannot perform [%s] on a [%s] on a instance of [%s]. Only [%s] and [%s] is supported",
                    operationName, entityType, managedServer.getClass().getName(),
                    LocalDMRManagedServer.class.getName(), RemoteDMRManagedServer.class.getName()));
        }
    }

    protected void assertLocalServer(ManagedServer managedServer) {
        if (!(managedServer instanceof LocalDMRManagedServer)) {
            throw new IllegalStateException(String.format(
                    "Cannot perform [%s] on a [%s] on a instance of [%s]. Only [%s] is supported", operationName,
                    entityType, managedServer.getClass().getName(), LocalDMRManagedServer.class.getName()));
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
