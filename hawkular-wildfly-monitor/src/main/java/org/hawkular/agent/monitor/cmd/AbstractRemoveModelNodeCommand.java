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
import org.hawkular.agent.monitor.inventory.dmr.DMRInventoryManager;
import org.hawkular.agent.monitor.log.AgentLoggers;
import org.hawkular.agent.monitor.log.MsgLogger;
import org.hawkular.bus.common.BasicMessageWithExtraData;
import org.hawkular.bus.common.BinaryData;
import org.hawkular.cmdgw.api.MessageUtils;
import org.hawkular.cmdgw.api.ResourcePathRequest;
import org.hawkular.cmdgw.api.ResourcePathResponse;
import org.hawkular.cmdgw.api.ResponseStatus;
import org.hawkular.dmrclient.Address;
import org.hawkular.dmrclient.JBossASClient;
import org.hawkular.inventory.api.model.CanonicalPath;

/**
 * A base for {@link Command}s removing nodes from the DMR tree.
 *
 * @author <a href="https://github.com/ppalaga">Peter Palaga</a>
 */
public abstract class AbstractRemoveModelNodeCommand<REQ extends ResourcePathRequest, RESP extends ResourcePathResponse>
        implements Command<REQ, RESP> {
    private static final MsgLogger log = AgentLoggers.getLogger(AbstractRemoveModelNodeCommand.class);

    protected final String entityType;

    public AbstractRemoveModelNodeCommand(String entityType) {
        super();
        this.entityType = entityType;
    }

    @Override
    public BasicMessageWithExtraData<RESP> execute(REQ request, BinaryData ignored, CommandContext context)
            throws Exception {

        String rawResourcePath = request.getResourcePath();
        log.infof("Received request to remove [%s] given by inventory path [%s]", entityType, rawResourcePath);

        MonitorServiceConfiguration config = context.getMonitorServiceConfiguration();

        // Based on the resource ID we need to know which inventory manager is handling it.
        // From the inventory manager, we can get the actual resource.
        CanonicalPath canonicalPath = CanonicalPath.fromString(rawResourcePath);

        String resourceId = canonicalPath.ids().getResourcePath().getSegment().getElementId();
        ResourceIdParts idParts = InventoryIdUtil.parseResourceId(resourceId);
        String modelNodePath = idParts.getIdPart();

        RESP response = createResponse();
        MessageUtils.prepareResourcePathResponse(request, response);

        JBossASClient dsc = null;
        try {
            String managedServerName = idParts.getManagedServerName();
            ManagedServer managedServer = config.managedServersMap.get(new Name(managedServerName));
            validate(request, managedServerName, managedServer);

            validate(modelNodePath, request);
            DMRInventoryManager inventoryManager = context.getDiscoveryService().getDmrServerInventories()
                    .get(managedServer);
            dsc = new JBossASClient(inventoryManager.getModelControllerClientFactory().createClient());

            Address addr = Address.parse(modelNodePath);
            dsc.remove(addr);

            afterModelNodeRemoved(request, response);

        } catch (Exception e) {
            response.setStatus(ResponseStatus.ERROR);
            String msg = String.format("Could not remove [%s] [%s]: %s", entityType, rawResourcePath, e.getMessage());
            response.setMessage(msg);
        } finally {
            if (dsc != null) {
                try {
                    dsc.close();
                } catch (Exception e) {
                    log.errorf(e, "Could not close a DatasourceJBossASClient");
                }
            }
        }

        return new BasicMessageWithExtraData<>(response, null);

    }

    /**
     * Subclasses can put some {@code request} validation here.
     * <p>
     * Does nothing in this implementation.
     *
     * @param modelNodePath
     * @param request
     */
    protected void validate(String modelNodePath, REQ request) {
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
            throw new IllegalArgumentException(
                    String.format("Cannot add [%s]: unknown managed server [%s]", entityType, managedServerName));
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
