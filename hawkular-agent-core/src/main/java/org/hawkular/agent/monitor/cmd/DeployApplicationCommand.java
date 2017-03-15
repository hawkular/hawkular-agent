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
import org.hawkular.cmdgw.api.DeployApplicationRequest;
import org.hawkular.cmdgw.api.DeployApplicationResponse;
import org.hawkular.cmdgw.api.MessageUtils;
import org.hawkular.dmrclient.DeploymentJBossASClient;
import org.hawkular.inventory.paths.CanonicalPath;
import org.jboss.as.controller.client.ModelControllerClient;

/**
 * Deploys an application on a resource.
 */
public class DeployApplicationCommand
        extends AbstractDMRResourcePathCommand<DeployApplicationRequest, DeployApplicationResponse> {

    private static final MsgLogger log = AgentLoggers.getLogger(DeployApplicationCommand.class);
    public static final Class<DeployApplicationRequest> REQUEST_CLASS = DeployApplicationRequest.class;

    public DeployApplicationCommand() {
        super("Deploy", "Application");
    }

    @Override
    protected BinaryData execute(
            ModelControllerClient controllerClient,
            EndpointService<DMRNodeLocation, DMRSession> endpointService,
            String modelNodePath,
            BasicMessageWithExtraData<DeployApplicationRequest> envelope,
            DeployApplicationResponse response,
            CommandContext context,
            DMRSession dmrContext)
            throws Exception {

        DeployApplicationRequest request = envelope.getBasicMessage();

        final String resourcePath = request.getResourcePath();
        final String destFileName = request.getDestinationFileName();
        final boolean enabled = (request.getEnabled() == null) ? true : request.getEnabled().booleanValue();
        final boolean forceDeploy = (request.getForceDeploy() == null) ? true
                : request.getForceDeploy().booleanValue();
        final Set<String> serverGroups = convertCsvToSet(request.getServerGroups());

        CanonicalPath canonicalPath = CanonicalPath.fromString(request.getResourcePath());
        String resourceId = canonicalPath.ids().getResourcePath().getSegment().getElementId();

        ResourceManager<DMRNodeLocation> resourceManager = endpointService.getResourceManager();
        Resource<DMRNodeLocation> resource = resourceManager.getResource(new ID(resourceId));
        if (resource == null) {
            throw new IllegalArgumentException(
                    String.format("Cannot deploy application: unknown resource [%s]", resourcePath));
        }

        // find the operation we need to execute - make sure it exists
        Collection<Operation<DMRNodeLocation>> ops = resource.getResourceType().getOperations();
        boolean canDeploy = false;
        log.tracef("Searching for Deploy operation among operations [%s] for resource [%s].", ops, resource.getID());
        for (Operation<DMRNodeLocation> op : ops) {
            if ("Deploy".equals(op.getName().getNameString())) {
                canDeploy = true;
                break;
            }
        }

        if (!canDeploy) {
            throw new IllegalArgumentException(
                    String.format("Cannot deploy application to [%s]. That feature is disabled.", resource));
        }

        MessageUtils.prepareResourcePathResponse(request, response);
        response.setDestinationFileName(request.getDestinationFileName());

        DeploymentJBossASClient client = new DeploymentJBossASClient(dmrContext.getClient());
        client.deploy(destFileName, envelope.getBinaryData(), enabled, serverGroups, forceDeploy);

        // run discovery now so we can quickly get the new app in inventory
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
    protected void validate(String modelNodePath, BasicMessageWithExtraData<DeployApplicationRequest> envelope) {
    }

    @Override
    protected void validate(BasicMessageWithExtraData<DeployApplicationRequest> envelope,
            MonitoredEndpoint<? extends AbstractEndpointConfiguration> endpoint) {
    }

    @Override
    protected DeployApplicationResponse createResponse() {
        return new DeployApplicationResponse();
    }
}
