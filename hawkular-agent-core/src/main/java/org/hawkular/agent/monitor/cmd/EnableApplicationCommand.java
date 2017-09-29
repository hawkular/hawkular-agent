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
import org.hawkular.cmdgw.api.EnableApplicationRequest;
import org.hawkular.cmdgw.api.EnableApplicationResponse;
import org.hawkular.cmdgw.api.MessageUtils;
import org.hawkular.dmrclient.DeploymentJBossASClient;
import org.jboss.as.controller.client.ModelControllerClient;

/**
 * Enables a deployed application for a server resource.
 */
public class EnableApplicationCommand
        extends AbstractDMRResourcePathCommand<EnableApplicationRequest, EnableApplicationResponse> {

    private static final MsgLogger log = AgentLoggers.getLogger(EnableApplicationCommand.class);

    public static final Class<EnableApplicationRequest> REQUEST_CLASS = EnableApplicationRequest.class;
    public static final String NAME = "Enable Deployment";

    public EnableApplicationCommand() {
        super(NAME, "Application");
    }

    @Override
    protected BinaryData execute(
            ModelControllerClient controllerClient,
            EndpointService<DMRNodeLocation, DMRSession> endpointService,
            String modelNodePath,
            BasicMessageWithExtraData<EnableApplicationRequest> envelope,
            EnableApplicationResponse response,
            CommandContext context,
            DMRSession dmrContext)
                    throws Exception {

        EnableApplicationRequest request = envelope.getBasicMessage();

        final String resourcePath = request.getResourceId();
        final String destFileName = request.getDestinationFileName();
        final Set<String> serverGroups = convertCsvToSet(request.getServerGroups());

        String resourceId = resourcePath;

        ResourceManager<DMRNodeLocation> resourceManager = endpointService.getResourceManager();
        Resource<DMRNodeLocation> resource = resourceManager.getResource(new ID(resourceId));
        if (resource == null) {
            throw new IllegalArgumentException(
                    String.format("Cannot enable application: unknown resource [%s]", resourcePath));
        }

        Collection<Operation<DMRNodeLocation>> ops = resource.getResourceType().getOperations();
        boolean canPerform = false;
        log.tracef("Searching for [%s] operation among operations [%s] for resource [%s].", NAME, ops,
                resource.getID());
        for (Operation<DMRNodeLocation> op : ops) {
            if (NAME.equals(op.getName().getNameString())) {
                canPerform = true;
                break;
            }
        }

        if (!canPerform) {
            throw new IllegalArgumentException(
                    String.format("Cannot [%s] from [%s]. The operation is undefined.", NAME, resource));
        }

        MessageUtils.prepareResourceResponse(request, response);
        response.setDestinationFileName(request.getDestinationFileName());

        // don't close this wrapper client
        DeploymentJBossASClient client = new DeploymentJBossASClient(dmrContext.getClient());
        client.enableDeployment(destFileName, serverGroups);

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
    protected void validate(String modelNodePath, BasicMessageWithExtraData<EnableApplicationRequest> envelope) {
    }

    @Override
    protected void validate(BasicMessageWithExtraData<EnableApplicationRequest> envelope,
            MonitoredEndpoint<? extends AbstractEndpointConfiguration> endpoint) {
    }

    @Override
    protected EnableApplicationResponse createResponse() {
        return new EnableApplicationResponse();
    }
}
