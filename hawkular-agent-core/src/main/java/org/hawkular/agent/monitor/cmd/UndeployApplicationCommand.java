/*
 * Copyright 2015-2017 Red Hat, Inc. and/or its affiliates
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

import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

import org.hawkular.agent.monitor.config.AgentCoreEngineConfiguration.AbstractEndpointConfiguration;
import org.hawkular.agent.monitor.inventory.ID;
import org.hawkular.agent.monitor.inventory.MonitoredEndpoint;
import org.hawkular.agent.monitor.inventory.Operation;
import org.hawkular.agent.monitor.inventory.Resource;
import org.hawkular.agent.monitor.inventory.ResourceManager;
import org.hawkular.agent.monitor.log.AgentLoggers;
import org.hawkular.agent.monitor.log.MsgLogger;
import org.hawkular.agent.monitor.protocol.EndpointService;
import org.hawkular.agent.monitor.protocol.dmr.DMRNodeLocation;
import org.hawkular.agent.monitor.protocol.dmr.DMRSession;
import org.hawkular.bus.common.BasicMessageWithExtraData;
import org.hawkular.bus.common.BinaryData;
import org.hawkular.cmdgw.api.MessageUtils;
import org.hawkular.cmdgw.api.UndeployApplicationRequest;
import org.hawkular.cmdgw.api.UndeployApplicationResponse;
import org.hawkular.dmrclient.DeploymentJBossASClient;
import org.jboss.as.controller.client.ModelControllerClient;

/**
 * Removes a deployed application from a resource.
 */
public class UndeployApplicationCommand
        extends AbstractDMRResourcePathCommand<UndeployApplicationRequest, UndeployApplicationResponse> {

    private static final MsgLogger log = AgentLoggers.getLogger(UndeployApplicationCommand.class);
    public static final Class<UndeployApplicationRequest> REQUEST_CLASS = UndeployApplicationRequest.class;

    public UndeployApplicationCommand() {
        super("Undeploy", "Application");
    }

    @Override
    protected BinaryData execute(
            ModelControllerClient controllerClient,
            EndpointService<DMRNodeLocation, DMRSession> endpointService,
            String modelNodePath,
            BasicMessageWithExtraData<UndeployApplicationRequest> envelope,
            UndeployApplicationResponse response,
            CommandContext context,
            DMRSession dmrContext)
            throws Exception {

        UndeployApplicationRequest request = envelope.getBasicMessage();

        final String destFileName = request.getDestinationFileName();
        final boolean removeContent = (request.getRemoveContent() == null) ? true
                : request.getRemoveContent().booleanValue();

        final Set<String> serverGroups = convertCsvToSet(request.getServerGroups());

        final String resourceId = request.getResourceId();

        ResourceManager<DMRNodeLocation> resourceManager = endpointService.getResourceManager();
        Resource<DMRNodeLocation> resource = resourceManager.getResource(new ID(resourceId));
        if (resource == null) {
            throw new IllegalArgumentException(
                    String.format("Cannot undeploy application: unknown resource [%s]", resourceId));
        }

        // find the operation we need to execute - make sure it exists
        Collection<Operation<DMRNodeLocation>> ops = resource.getResourceType().getOperations();
        boolean canUndeploy = false;
        log.tracef("Searching for Undeploy operation among operations [%s] for resource [%s].", ops, resource.getID());
        for (Operation<DMRNodeLocation> op : ops) {
            if ("Undeploy".equals(op.getName().getNameString())) {
                canUndeploy = true;
                break;
            }
        }

        if (!canUndeploy) {
            throw new IllegalArgumentException(
                    String.format("Cannot undeploy application from [%s]. That feature is disabled.", resource));
        }

        MessageUtils.prepareResourceResponse(request, response);
        response.setDestinationFileName(request.getDestinationFileName());

        DeploymentJBossASClient client = new DeploymentJBossASClient(dmrContext.getClient());
        client.undeploy(destFileName, serverGroups, removeContent);

        // run discovery now so we can quickly show the app has been removed
        endpointService.discoverAll();
        return null;
    }

    private Set<String> convertCsvToSet(String serverGroups) {
        if (serverGroups == null || serverGroups.isEmpty()) {
            return Collections.emptySet();
        }
        return new HashSet<>(Arrays.asList(serverGroups.split(",")));
    }

    @Override
    protected void validate(String modelNodePath, BasicMessageWithExtraData<UndeployApplicationRequest> envelope) {
    }

    @Override
    protected void validate(BasicMessageWithExtraData<UndeployApplicationRequest> envelope,
            MonitoredEndpoint<? extends AbstractEndpointConfiguration> endpoint) {
    }

    @Override
    protected UndeployApplicationResponse createResponse() {
        return new UndeployApplicationResponse();
    }
}
