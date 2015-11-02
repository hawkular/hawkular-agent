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

import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.Collection;

import org.hawkular.agent.monitor.extension.MonitorServiceConfiguration;
import org.hawkular.agent.monitor.inventory.ID;
import org.hawkular.agent.monitor.inventory.InventoryIdUtil;
import org.hawkular.agent.monitor.inventory.ManagedServer;
import org.hawkular.agent.monitor.inventory.Name;
import org.hawkular.agent.monitor.inventory.ResourceManager;
import org.hawkular.agent.monitor.inventory.dmr.DMRInventoryManager;
import org.hawkular.agent.monitor.inventory.dmr.DMROperation;
import org.hawkular.agent.monitor.inventory.dmr.DMRResource;
import org.hawkular.agent.monitor.inventory.dmr.LocalDMRManagedServer;
import org.hawkular.agent.monitor.inventory.dmr.RemoteDMRManagedServer;
import org.hawkular.agent.monitor.log.AgentLoggers;
import org.hawkular.agent.monitor.log.MsgLogger;
import org.hawkular.bus.common.BasicMessageWithExtraData;
import org.hawkular.bus.common.BinaryData;
import org.hawkular.cmdgw.api.ExportJdrRequest;
import org.hawkular.cmdgw.api.ExportJdrResponse;
import org.hawkular.cmdgw.api.MessageUtils;
import org.hawkular.cmdgw.api.ResponseStatus;
import org.hawkular.dmrclient.Address;
import org.hawkular.dmrclient.CoreJBossASClient;
import org.hawkular.dmrclient.JBossASClient;
import org.hawkular.inventory.api.model.CanonicalPath;
import org.jboss.as.controller.client.ModelControllerClient;
import org.jboss.dmr.ModelNode;

/**
 * @author Juraci Paixão Kröhling
 */
public class ExportJdrCommand implements Command<ExportJdrRequest, ExportJdrResponse> {
    private static final MsgLogger log = AgentLoggers.getLogger(ExportJdrCommand.class);
    public static final Class<ExportJdrRequest> REQUEST_CLASS = ExportJdrRequest.class;

    @Override
    public BasicMessageWithExtraData<ExportJdrResponse> execute(BasicMessageWithExtraData<ExportJdrRequest> envelope,
            CommandContext context) throws Exception {
        ExportJdrRequest request = envelope.getBasicMessage();
        log.infof("Received request to generate JDR on resource [%s]", request.getResourcePath());

        FeedCommProcessor processor = context.getFeedCommProcessor();
        MonitorServiceConfiguration config = context.getMonitorServiceConfiguration();

        // Based on the resource ID we need to know which inventory manager is handling it.
        // From the inventory manager, we can get the actual resource.
        CanonicalPath canonicalPath = CanonicalPath.fromString(request.getResourcePath());
        String resourceId = canonicalPath.ids().getResourcePath().getSegment().getElementId();
        InventoryIdUtil.ResourceIdParts idParts = InventoryIdUtil.parseResourceId(resourceId);
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

    private BasicMessageWithExtraData<ExportJdrResponse> executeOperationDMR(String resourceId,
            ExportJdrRequest request, CommandContext context, ManagedServer managedServer) throws Exception {

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

        String requestedOpName = "JDR";
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
            throw new IllegalArgumentException(String.format(
                    "Cannot execute operation: unknown operation [%s] for resource [%s]", requestedOpName, resource));
        }

        ExportJdrResponse response = new ExportJdrResponse();
        MessageUtils.prepareResourcePathResponse(request, response);

        BinaryData binaryData = null;
        long timestampBeforeExecution = System.currentTimeMillis();

        try (ModelControllerClient mcc = inventoryManager.getModelControllerClientFactory().createClient()) {
            ModelNode opReq = JBossASClient.createRequest(actualOperationName, opAddress);

            CoreJBossASClient client = new CoreJBossASClient(mcc);
            ModelNode opResp = client.execute(opReq);

            if (!JBossASClient.isSuccess(opResp)) {
                response.setStatus(ResponseStatus.ERROR);
                String formattedTimestamp = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mmX").withZone(ZoneOffset.UTC)
                        .format(Instant.ofEpochMilli(timestampBeforeExecution));

                String msg = String.format("Could not export JDR on resource [%s] requested on [%s]: %s", resourceId,
                        formattedTimestamp, JBossASClient.getFailureDescription(opResp));
                response.setMessage(msg);
            } else {
                String reportLocation = opResp.get("result").get("report-location").asString();

                File reportFile = new File(reportLocation);
                InputStream reportInputStream = new FileInputStream(reportFile);
                binaryData = new BinaryData(null, reportInputStream);

                response.setStatus(ResponseStatus.OK);
                response.setFileName(reportFile.getName());
                response.setMessage(JBossASClient.getResults(opResp).asString());
            }
        } catch (Exception e) {
            response.setStatus(ResponseStatus.ERROR);
            String formattedTimestamp = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mmX").withZone(ZoneOffset.UTC)
                    .format(Instant.ofEpochMilli(timestampBeforeExecution));

            String msg = String.format("Exception while generating JDR on resource [%s] requested on [%s]: %s",
                    resourceId, formattedTimestamp, e.toString());
            response.setMessage(msg);
        }

        return new BasicMessageWithExtraData<>(response, binaryData);
    }
}
