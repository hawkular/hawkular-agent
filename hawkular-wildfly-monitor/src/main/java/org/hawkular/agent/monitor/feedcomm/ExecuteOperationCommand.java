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
package org.hawkular.agent.monitor.feedcomm;

import java.util.Collection;
import java.util.Map;

import org.hawkular.agent.monitor.extension.MonitorServiceConfiguration;
import org.hawkular.agent.monitor.inventory.ID;
import org.hawkular.agent.monitor.inventory.InventoryIdUtil;
import org.hawkular.agent.monitor.inventory.InventoryIdUtil.ResourceIdParts;
import org.hawkular.agent.monitor.inventory.ManagedServer;
import org.hawkular.agent.monitor.inventory.Name;
import org.hawkular.agent.monitor.inventory.ResourceManager;
import org.hawkular.agent.monitor.inventory.dmr.DMRInventoryManager;
import org.hawkular.agent.monitor.inventory.dmr.DMROperation;
import org.hawkular.agent.monitor.inventory.dmr.DMRResource;
import org.hawkular.agent.monitor.inventory.dmr.LocalDMRManagedServer;
import org.hawkular.agent.monitor.inventory.dmr.RemoteDMRManagedServer;
import org.hawkular.agent.monitor.log.MsgLogger;
import org.hawkular.bus.common.BasicMessageWithExtraData;
import org.hawkular.bus.common.BinaryData;
import org.hawkular.cmdgw.api.ExecuteOperationRequest;
import org.hawkular.cmdgw.api.ExecuteOperationResponse;
import org.hawkular.dmrclient.Address;
import org.hawkular.dmrclient.CoreJBossASClient;
import org.hawkular.dmrclient.JBossASClient;
import org.hawkular.inventory.api.model.CanonicalPath;
import org.jboss.as.controller.client.ModelControllerClient;
import org.jboss.dmr.ModelNode;

/**
 * Execute an operation on a resource.
 */
public class ExecuteOperationCommand implements Command<ExecuteOperationRequest, ExecuteOperationResponse> {
    public static final Class<ExecuteOperationRequest> REQUEST_CLASS = ExecuteOperationRequest.class;

    @Override
    public BasicMessageWithExtraData<ExecuteOperationResponse> execute(ExecuteOperationRequest request,
            BinaryData binaryData, CommandContext context) throws Exception {
        MsgLogger.LOG.infof("Received request to execute operation [%s] on resource [%s]",
                request.getOperationName(), request.getResourcePath());

        MonitorServiceConfiguration config = context.getMonitorServiceConfiguration();

        // Based on the resource ID we need to know which inventory manager is handling it.
        // From the inventory manager, we can get the actual resource.
        CanonicalPath canonicalPath = CanonicalPath.fromString(request.getResourcePath());
        String resourceId = canonicalPath.ids().getResourcePath().getSegment().getElementId();
        ResourceIdParts idParts = InventoryIdUtil.parseResourceId(resourceId);
        ManagedServer managedServer = config.managedServersMap.get(new Name(idParts.getManagedServerName()));
        if (managedServer == null) {
            throw new IllegalArgumentException(String.format("Cannot execute operation: unknown managed server [%s]",
                    idParts.getManagedServerName()));
        }

        if (managedServer instanceof LocalDMRManagedServer || managedServer instanceof RemoteDMRManagedServer) {
            return executeOperationDMR(resourceId, request, context, managedServer);
        } else {
            throw new IllegalStateException("Cannot execute operation: report this bug: " + managedServer.getClass());
        }
    }

    private BasicMessageWithExtraData<ExecuteOperationResponse> executeOperationDMR(String resourceId,
            ExecuteOperationRequest request,
            CommandContext context, ManagedServer managedServer) throws Exception {

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
        MsgLogger.LOG.tracef("Searching for operation [%s] among operations [%s] for resource [%s].",
                requestedOpName, ops, resource.getID());
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

        ExecuteOperationResponse response = new ExecuteOperationResponse();
        response.setResourcePath(request.getResourcePath());
        response.setOperationName(request.getOperationName());

        try (ModelControllerClient mcc = inventoryManager.getModelControllerClientFactory().createClient()) {
            ModelNode opReq = JBossASClient.createRequest(actualOperationName, opAddress);

            Map<String, String> params = request.getParameters();
            if (params != null) {
                for (Map.Entry<String, String> param : params.entrySet()) {
                    opReq.get(param.getKey()).set(param.getValue());
                }
            }

            CoreJBossASClient client = new CoreJBossASClient(mcc);
            ModelNode opResp = client.execute(opReq);
            if (!JBossASClient.isSuccess(opResp)) {
                response.setStatus("ERROR");
                response.setMessage(JBossASClient.getFailureDescription(opResp));
            } else {
                response.setStatus("OK");
                // FIXME HAWKULAR-604 The message field of ExecuteOperationResponse should not be "undefined"
                response.setMessage(JBossASClient.getResults(opResp).toString());
            }
        } catch (Exception e) {
            response.setStatus("ERROR");
            response.setMessage(e.toString());
        }

        return new BasicMessageWithExtraData<>(response, null);
    }
}
