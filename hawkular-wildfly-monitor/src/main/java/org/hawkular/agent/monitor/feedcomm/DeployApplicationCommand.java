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

import org.hawkular.agent.monitor.extension.MonitorServiceConfiguration;
import org.hawkular.agent.monitor.inventory.ID;
import org.hawkular.agent.monitor.inventory.InventoryIdUtil;
import org.hawkular.agent.monitor.inventory.InventoryIdUtil.ResourceIdParts;
import org.hawkular.agent.monitor.inventory.ManagedServer;
import org.hawkular.agent.monitor.inventory.Name;
import org.hawkular.agent.monitor.inventory.ResourceManager;
import org.hawkular.agent.monitor.inventory.dmr.DMRInventoryManager;
import org.hawkular.agent.monitor.inventory.dmr.DMRResource;
import org.hawkular.agent.monitor.inventory.dmr.LocalDMRManagedServer;
import org.hawkular.agent.monitor.inventory.dmr.RemoteDMRManagedServer;
import org.hawkular.agent.monitor.log.MsgLogger;
import org.hawkular.bus.common.BinaryData;
import org.hawkular.cmdgw.api.DeployApplicationRequest;
import org.hawkular.cmdgw.api.DeployApplicationResponse;
import org.hawkular.dmrclient.Address;
import org.hawkular.dmrclient.DeploymentJBossASClient;
import org.hawkular.inventory.api.model.CanonicalPath;
import org.jboss.as.controller.client.ModelControllerClient;

/**
 * Deploys an application on a resource.
 */
public class DeployApplicationCommand implements Command<DeployApplicationRequest, DeployApplicationResponse> {
    public static final Class<DeployApplicationRequest> REQUEST_CLASS = DeployApplicationRequest.class;

    @Override
    public DeployApplicationResponse execute(DeployApplicationRequest request, BinaryData applicationContent,
            CommandContext context) throws Exception {

        MsgLogger.LOG.infof("Received request to deploy application [%s] on resource [%s]",
                request.getDestinationFileName(), request.getResourcePath());

        FeedCommProcessor processor = context.getFeedCommProcessor();
        MonitorServiceConfiguration config = processor.getMonitorServiceConfiguration();

        // Based on the resource ID we need to know which inventory manager is handling it.
        // From the inventory manager, we can get the actual resource.
        CanonicalPath canonicalPath = CanonicalPath.fromString(request.getResourcePath());
        String resourceId = canonicalPath.ids().getResourcePath().getSegment().getElementId();
        ResourceIdParts idParts = InventoryIdUtil.parseResourceId(resourceId);
        ManagedServer managedServer = config.managedServersMap.get(new Name(idParts.managedServerName));
        if (managedServer == null) {
            throw new IllegalArgumentException(
                    String.format("Cannot deploy application: unknown managed server [%s]", idParts.managedServerName));
        }

        if (managedServer instanceof LocalDMRManagedServer || managedServer instanceof RemoteDMRManagedServer) {
            return deployApplicationDMR(resourceId, request, applicationContent, processor, managedServer);
        } else {
            throw new IllegalStateException("Cannot deploy application: report this bug: " + managedServer.getClass());
        }
    }

    private DeployApplicationResponse deployApplicationDMR(String resourceId, DeployApplicationRequest request,
            BinaryData applicationContent, FeedCommProcessor processor, ManagedServer managedServer) throws Exception {

        DMRInventoryManager inventoryManager = processor.getDmrServerInventories().get(managedServer);
        if (inventoryManager == null) {
            throw new IllegalArgumentException(
                    String.format("Cannot deploy application: missing inventory manager [%s]", managedServer));
        }

        ResourceManager<DMRResource> resourceManager = inventoryManager.getResourceManager();
        DMRResource resource = resourceManager.getResource(new ID(resourceId));
        if (resource == null) {
            throw new IllegalArgumentException(
                    String.format("Cannot deploy application: unknown resource [%s]", request.getResourcePath()));
        }

        // find the operation we need to execute - make sure it exists and get the address for the resource to invoke
        Address opAddress = resource.getAddress();

        DeployApplicationResponse response = new DeployApplicationResponse();
        response.setResourcePath(request.getResourcePath());

        try (ModelControllerClient mcc = inventoryManager.getModelControllerClientFactory().createClient()) {
            DeploymentJBossASClient client = new DeploymentJBossASClient(mcc);
            client.deployStandalone(request.getDestinationFileName(), applicationContent);
            response.setStatus("OK");
            response.setMessage(String.format("Deployed application: %s", request.getDestinationFileName()));
        } catch (Exception e) {
            response.setStatus("ERROR");
            response.setMessage(e.toString());
        }

        return response;
    }
}
