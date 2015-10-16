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

import java.util.Collection;
import java.util.Map;

import org.hawkular.agent.monitor.inventory.ID;
import org.hawkular.agent.monitor.inventory.ManagedServer;
import org.hawkular.agent.monitor.inventory.ResourceManager;
import org.hawkular.agent.monitor.inventory.dmr.DMRInventoryManager;
import org.hawkular.agent.monitor.inventory.dmr.DMROperation;
import org.hawkular.agent.monitor.inventory.dmr.DMRResource;
import org.hawkular.agent.monitor.log.AgentLoggers;
import org.hawkular.agent.monitor.log.MsgLogger;
import org.hawkular.bus.common.BasicMessageWithExtraData;
import org.hawkular.cmdgw.api.ExecuteOperationRequest;
import org.hawkular.cmdgw.api.ExecuteOperationResponse;
import org.hawkular.dmr.api.OperationBuilder;
import org.hawkular.dmrclient.Address;
import org.hawkular.inventory.api.model.CanonicalPath;
import org.jboss.as.controller.client.ModelControllerClient;

/**
 * Execute an operation on a resource.
 */
public class ExecuteOperationCommand extends
        AbstractResourcePathCommand<ExecuteOperationRequest, ExecuteOperationResponse> {
    private static final MsgLogger log = AgentLoggers.getLogger(ExecuteOperationCommand.class);
    public static final Class<ExecuteOperationRequest> REQUEST_CLASS = ExecuteOperationRequest.class;

    public ExecuteOperationCommand() {
        super("Execute Operation", "DMR Node");
    }

    /** @see org.hawkular.agent.monitor.cmd.AbstractResourcePathCommand#createResponse() */
    @Override
    protected ExecuteOperationResponse createResponse() {
        return new ExecuteOperationResponse();
    }

    @Override
    protected String getOperationName(BasicMessageWithExtraData<ExecuteOperationRequest> envelope) {
        return envelope.getBasicMessage().getOperationName();
    }

    /**
     * @see org.hawkular.agent.monitor.cmd.AbstractResourcePathCommand#validate(java.lang.String,
     *      org.hawkular.cmdgw.api.ResourcePathRequest)
     */
    @Override
    protected void validate(String modelNodePath, BasicMessageWithExtraData<ExecuteOperationRequest> envelope) {
    }

    /**
     * @see org.hawkular.agent.monitor.cmd.AbstractResourcePathCommand#execute(org.hawkular.dmrclient.JBossASClient,
     *      org.hawkular.agent.monitor.inventory.ManagedServer, java.lang.String,
     *      org.hawkular.cmdgw.api.ResourcePathRequest, org.hawkular.cmdgw.api.ResourcePathResponse,
     *      org.hawkular.agent.monitor.cmd.CommandContext)
     */
    @Override
    protected void execute(ModelControllerClient controllerClient, ManagedServer managedServer, String modelNodePath,
            BasicMessageWithExtraData<ExecuteOperationRequest> envelope, ExecuteOperationResponse response,
            CommandContext context) throws Exception {
        ExecuteOperationRequest request = envelope.getBasicMessage();
        CanonicalPath canonicalPath = CanonicalPath.fromString(request.getResourcePath());
        String resourceId = canonicalPath.ids().getResourcePath().getSegment().getElementId();

        DMRInventoryManager inventoryManager = context.getDiscoveryService().getDmrServerInventories()
                .get(managedServer);
        if (inventoryManager == null) {
            throw new IllegalArgumentException(
                    String.format("Cannot execute operation: missing inventory manager [%s]", managedServer));
        }

        ResourceManager<DMRResource> resourceManager = inventoryManager.getResourceManager();
        DMRResource resource = resourceManager.getResource(new ID(resourceId));
        if (resource == null) {
            throw new IllegalArgumentException(
                    String.format("Cannot execute operation: unknown resource [%s]", request.getResourcePath()));
        }

        // find the operation we need to execute - make sure it exists and get the address for the resource to invoke
        Address opAddress = null;
        String actualOperationName = null;

        String requestedOpName = request.getOperationName();
        Collection<DMROperation> ops = resource.getResourceType().getOperations();
        log.tracef("Searching for operation [%s] among operations [%s] for resource [%s].", requestedOpName, ops,
                resource.getID());
        for (DMROperation op : ops) {
            if (requestedOpName.equals(op.getID().getIDString())) {
                opAddress = resource.getAddress().clone().add(Address.parse(op.getPath()));
                actualOperationName = op.getOperationName();
                break;
            }
        }

        if (opAddress == null) {
            throw new IllegalArgumentException(
                    String.format("Cannot execute operation: unknown operation [%s] for resource [%s]",
                            request.getOperationName(), resource));
        }

        response.setOperationName(request.getOperationName());

        final OperationBuilder.ByNameOperationBuilder<?> operation;
        operation = OperationBuilder.byName(actualOperationName) //
                .address(opAddress.getAddressNode());

        Map<String, String> params = request.getParameters();
        if (params != null) {
            for (Map.Entry<String, String> param : params.entrySet()) {
                operation.attribute(param.getKey(), param.getValue());
            }
        }

        operation.execute(controllerClient).assertSuccess();
    }

}
