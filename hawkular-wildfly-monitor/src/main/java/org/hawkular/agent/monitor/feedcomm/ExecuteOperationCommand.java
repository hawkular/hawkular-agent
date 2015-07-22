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

import org.hawkular.agent.monitor.inventory.InventoryIdUtil;
import org.hawkular.agent.monitor.inventory.InventoryIdUtil.ResourceIdParts;
import org.hawkular.agent.monitor.inventory.ManagedServer;
import org.hawkular.agent.monitor.inventory.dmr.DMRInventoryManager;
import org.hawkular.agent.monitor.log.MsgLogger;
import org.hawkular.feedcomm.api.ExecuteOperationRequest;
import org.hawkular.feedcomm.api.GenericSuccessResponse;

/**
 * Execute an operation on a resource.
 */
public class ExecuteOperationCommand implements Command<ExecuteOperationRequest, GenericSuccessResponse> {
    public static final Class<ExecuteOperationRequest> REQUEST_CLASS = ExecuteOperationRequest.class;

    @Override
    public GenericSuccessResponse execute(ExecuteOperationRequest request, CommandContext context) throws Exception {
        MsgLogger.LOG.infof("Received request to execute operation [%s] on resource [%s]",
                request.getOperationName(), request.getResourceId());

        ResourceIdParts idParts = InventoryIdUtil.parseResourceId(request.getResourceId());
        ManagedServer managedServer = context.getFeedCommProcessor().getMonitorServiceConfiguration().managedServersMap
                .get(idParts.managedServerName);
        DMRInventoryManager inventoryManager = context.getFeedCommProcessor().getDmrServerInventories()
                .get(managedServer);

        return null;
    }
}
