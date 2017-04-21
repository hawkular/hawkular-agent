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
package org.hawkular.agent.monitor.extension;

import org.hawkular.agent.monitor.config.AgentCoreEngineConfiguration;
import org.hawkular.agent.monitor.config.AgentCoreEngineConfiguration.EndpointConfiguration;
import org.hawkular.agent.monitor.protocol.EndpointService;
import org.hawkular.agent.monitor.protocol.ProtocolService;
import org.hawkular.agent.monitor.protocol.ProtocolServices;
import org.hawkular.agent.monitor.protocol.dmr.DMRNodeLocation;
import org.hawkular.agent.monitor.protocol.dmr.DMRSession;
import org.hawkular.agent.monitor.service.MonitorService;
import org.hawkular.agent.wildfly.util.Util;
import org.hawkular.agent.wildfly.util.WildflyCompatibilityUtils;
import org.jboss.as.controller.OperationContext;
import org.jboss.as.controller.OperationFailedException;
import org.jboss.dmr.ModelNode;

public class RemoteDMRAdd extends MonitorServiceAddStepHandler {

    public static final RemoteDMRAdd INSTANCE = new RemoteDMRAdd();

    private RemoteDMRAdd() {
        super(RemoteDMRAttributes.ATTRIBUTES);
    }

    @Override
    protected void performRuntime(OperationContext context, ModelNode operation, ModelNode model)
            throws OperationFailedException {
        if (context.isBooting()) {
            return;
        }

        MonitorService monitorService = getMonitorService(context);
        if (monitorService == null) {
            return; // the agent wasn't enabled, nothing to do
        }

        AgentCoreEngineConfiguration config = Util.getMonitorServiceConfiguration(context);
        String newEndpointName = WildflyCompatibilityUtils.getCurrentAddressValue(context, operation);

        // Register the feed under the tenant of the new managed server.
        // If endpoint has a null tenant then there is nothing to do since it will just reuse the agent's tenant ID
        EndpointConfiguration endpointConfig = config.getDmrConfiguration().getEndpoints().get(newEndpointName);
        if (endpointConfig.isEnabled()) {
            // create a new endpoint service
            ProtocolServices newServices = monitorService.createProtocolServicesBuilder()
                    .dmrProtocolService(null, config.getDmrConfiguration()).build();
            EndpointService<DMRNodeLocation, DMRSession> endpointService = newServices.getDmrProtocolService()
                    .getEndpointServices().get(newEndpointName);

            // put the new endpoint service in the original protocol services container
            ProtocolService<DMRNodeLocation, DMRSession> dmrService = monitorService.getProtocolServices()
                    .getDmrProtocolService();
            dmrService.add(endpointService);
        }
    }
}
