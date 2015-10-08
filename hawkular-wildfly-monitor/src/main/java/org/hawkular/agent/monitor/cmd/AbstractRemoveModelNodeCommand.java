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

import org.hawkular.agent.monitor.inventory.ManagedServer;
import org.hawkular.agent.monitor.inventory.dmr.DMRInventoryManager;
import org.hawkular.bus.common.BasicMessageWithExtraData;
import org.hawkular.cmdgw.api.ResourcePathRequest;
import org.hawkular.cmdgw.api.ResourcePathResponse;
import org.hawkular.cmdgw.api.ResponseStatus;
import org.hawkular.dmrclient.Address;
import org.hawkular.dmrclient.JBossASClient;

/**
 * A base for {@link Command}s removing nodes from the DMR tree.
 *
 * @author <a href="https://github.com/ppalaga">Peter Palaga</a>
 */
public abstract class AbstractRemoveModelNodeCommand<REQ extends ResourcePathRequest, RESP extends ResourcePathResponse>
        extends AbstractResourcePathCommand<REQ, RESP, JBossASClient> {

    public AbstractRemoveModelNodeCommand(String entityType) {
        super("Remove", entityType);
    }

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

    /**
     * @param context
     * @param managedServer
     * @return
     */
    @Override
    protected JBossASClient createControllerClient(ManagedServer managedServer, CommandContext context) {
        DMRInventoryManager inventoryManager = context.getDiscoveryService().getDmrServerInventories()
                .get(managedServer);
        return new JBossASClient(inventoryManager.getModelControllerClientFactory().createClient());
    }

    @Override
    protected void execute(JBossASClient controllerClient, ManagedServer managedServer, String modelNodePath,
            BasicMessageWithExtraData<REQ> envelope, RESP response, CommandContext context) throws Exception {
        Address addr = Address.parse(modelNodePath);
        controllerClient.remove(addr);

        afterModelNodeRemoved(envelope, response);
    }

    /**
     * Sets the {@link ResponseStatus#OK} state together with a message informing about the successful removal to
     * {@code response}. Subclasses may want to perform more modifications on {@code response}.
     *
     * @param request the request the present command is processing
     * @param response the response that will be sent back to the client
     */
    protected void afterModelNodeRemoved(BasicMessageWithExtraData<REQ> envelope, RESP response) {
        response.setStatus(ResponseStatus.OK);
        String msg = String.format("Removed [%s] given by Inventory path [%s]", entityType,
                envelope.getBasicMessage().getResourcePath());
        response.setMessage(msg);
    }

}
