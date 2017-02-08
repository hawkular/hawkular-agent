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

import org.hawkular.agent.monitor.log.AgentLoggers;
import org.hawkular.agent.monitor.log.MsgLogger;
import org.hawkular.agent.monitor.service.MonitorService;
import org.hawkular.agent.monitor.util.WildflyCompatibilityUtils;
import org.jboss.as.controller.OperationContext;
import org.jboss.as.controller.OperationFailedException;
import org.jboss.as.controller.PathAddress;
import org.jboss.as.controller.ProcessType;
import org.jboss.as.controller.RunningMode;
import org.jboss.as.controller.registry.Resource;
import org.jboss.dmr.ModelNode;
import org.jboss.msc.service.ServiceBuilder;
import org.jboss.msc.service.ServiceController;
import org.jboss.msc.service.ServiceTarget;

public class SubsystemAdd extends WildflyCompatibilityUtils.AbstractAddStepHandler {
    private static final MsgLogger log = AgentLoggers.getLogger(SubsystemAdd.class);
    static final SubsystemAdd INSTANCE = new SubsystemAdd();

    private SubsystemAdd() {
        super(SubsystemAttributes.ATTRIBUTES);
    }

    @Override
    protected void performRuntime(OperationContext context, ModelNode operation, ModelNode model)
            throws OperationFailedException {

        if (context.getRunningMode() == RunningMode.ADMIN_ONLY) {
            log.infoSubsystemDisabled();
            return;
        }

        // Our current address is either the top subsystem or one of its children. But we need the
        // top subsystem address so we can get the full configuration.
        // We may be running in standalone or domain mode - support both.
        PathAddress agentSubsystemAddress = null;
        PathAddress currentAddress = WildflyCompatibilityUtils.getCurrentAddress(context, operation);
        while (currentAddress.size() > 0 && agentSubsystemAddress == null) {
            if ("subsystem".equals(currentAddress.getLastElement().getKey()) &&
                    SubsystemExtension.SUBSYSTEM_NAME.equals(currentAddress.getLastElement().getValue())) {
                agentSubsystemAddress = currentAddress;
            } else {
                currentAddress = currentAddress.getParent();
            }
        }

        if (agentSubsystemAddress == null) {
            throw new OperationFailedException(
                    "Cannot get agent subsystem address from: " + WildflyCompatibilityUtils.getCurrentAddress(context, operation));
        }

        ModelNode subsystemConfig = Resource.Tools.readModel(context.readResourceFromRoot(agentSubsystemAddress));
        MonitorServiceConfiguration config = new MonitorServiceConfigurationBuilder(subsystemConfig, context).build();

        if (!config.isSubsystemEnabled()) {
            log.infoSubsystemDisabled();
            return;
        }

        createService(context.getServiceTarget(), config, context.getProcessType());
    }

    private void createService(final ServiceTarget target, final MonitorServiceConfiguration configuration,
            ProcessType processType) {

        // create and configure the service itself
        MonitorService service = new MonitorService(configuration, processType);

        // create the builder that will be responsible for preparing the service deployment
        ServiceBuilder<MonitorService> svcBuilder;
        svcBuilder = target.addService(SubsystemExtension.SERVICE_NAME, service);
        svcBuilder.setInitialMode(ServiceController.Mode.ACTIVE);
        service.addDependencies(target, svcBuilder);

        // install the monitor service
        svcBuilder.install();

        return;
    }
}
