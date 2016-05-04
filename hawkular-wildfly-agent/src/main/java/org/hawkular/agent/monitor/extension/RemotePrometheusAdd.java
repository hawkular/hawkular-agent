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

import org.hawkular.agent.monitor.dynamicprotocol.DynamicEndpointService;
import org.hawkular.agent.monitor.dynamicprotocol.DynamicProtocolService;
import org.hawkular.agent.monitor.dynamicprotocol.DynamicProtocolServices;
import org.hawkular.agent.monitor.log.AgentLoggers;
import org.hawkular.agent.monitor.log.MsgLogger;
import org.hawkular.agent.monitor.service.MonitorService;
import org.hawkular.agent.monitor.util.Util;
import org.jboss.as.controller.OperationContext;
import org.jboss.as.controller.OperationFailedException;
import org.jboss.dmr.ModelNode;

public class RemotePrometheusAdd extends MonitorServiceAddStepHandler {
    private static final MsgLogger log = AgentLoggers.getLogger(RemotePrometheusAdd.class);

    public static final RemotePrometheusAdd INSTANCE = new RemotePrometheusAdd();

    private RemotePrometheusAdd() {
        super(RemotePrometheusAttributes.ATTRIBUTES);
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
        DynamicProtocolServices newServices = monitorService.createDynamicProtocolServicesBuilder()
                .prometheusDynamicProtocolService(config.getPrometheusConfiguration(),
                        monitorService.getHawkularMonitorContext())
                .build();
        DynamicEndpointService endpointService = newServices.getPrometheusProtocolService()
                .getDynamicEndpointServices().get(context.getCurrentAddressValue());

        // put the new endpoint service in the original protocol services container
        DynamicProtocolService promService = monitorService.getDynamicProtocolServices()
                .getPrometheusProtocolService();
        promService.add(endpointService);
    }
}
