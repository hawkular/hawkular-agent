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

import org.hawkular.agent.monitor.protocol.ProtocolService;
import org.hawkular.agent.monitor.scheduler.SchedulerService;
import org.hawkular.agent.monitor.service.MonitorService;
import org.hawkular.agent.monitor.util.WildflyCompatibilityUtils;
import org.jboss.as.controller.OperationContext;
import org.jboss.as.controller.OperationFailedException;
import org.jboss.dmr.ModelNode;

public class PlatformRemove extends MonitorServiceRemoveStepHandler {

    public static final PlatformRemove INSTANCE = new PlatformRemove();

    private PlatformRemove() {
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

        SchedulerService schedulerService = monitorService.getSchedulerService();
        ProtocolService<?, ?> platformService = monitorService.getProtocolServices().getPlatformProtocolService();
        String doomedEndpointName = WildflyCompatibilityUtils.getCurrentAddressValue(context, operation);
        platformService.remove(doomedEndpointName, schedulerService);
    }
}
