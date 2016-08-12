/*
 * Copyright 2015-2016 Red Hat, Inc. and/or its affiliates
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

import org.hawkular.agent.monitor.protocol.EndpointService;
import org.hawkular.agent.monitor.protocol.ProtocolService;
import org.hawkular.agent.monitor.protocol.ProtocolServices;
import org.hawkular.agent.monitor.protocol.platform.PlatformNodeLocation;
import org.hawkular.agent.monitor.protocol.platform.PlatformSession;
import org.hawkular.agent.monitor.service.MonitorService;
import org.hawkular.agent.monitor.util.Util;
import org.jboss.as.controller.OperationContext;
import org.jboss.as.controller.OperationFailedException;
import org.jboss.dmr.ModelNode;

public class PlatformAdd extends MonitorServiceAddStepHandler {

    public static final PlatformAdd INSTANCE = new PlatformAdd();

    private PlatformAdd() {
        super(PlatformAttributes.ATTRIBUTES);
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

        MonitorServiceConfiguration config = Util.getMonitorServiceConfiguration(context);

        // create a new endpoint service
        ProtocolServices newServices = monitorService.createProtocolServicesBuilder()
                .platformProtocolService(config.getPlatformConfiguration()).build();
        EndpointService<PlatformNodeLocation, PlatformSession> endpointService = newServices
                .getPlatformProtocolService().getEndpointServices().get(context.getCurrentAddressValue());

        // put the new endpoint service in the original protocol services container
        ProtocolService<PlatformNodeLocation, PlatformSession> platformService = monitorService.getProtocolServices()
                .getPlatformProtocolService();
        platformService.add(endpointService);

        // in case any types were added due to new endpoint being added, we need to register the new types
        monitorService.registerAllResourceTypes();
    }
}
