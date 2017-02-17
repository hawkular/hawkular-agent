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

import java.util.Arrays;
import java.util.Collection;
import java.util.List;

import org.hawkular.agent.monitor.protocol.EndpointService;
import org.hawkular.agent.monitor.protocol.ProtocolService;
import org.hawkular.agent.monitor.protocol.ProtocolServices;
import org.hawkular.agent.monitor.protocol.platform.PlatformNodeLocation;
import org.hawkular.agent.monitor.protocol.platform.PlatformSession;
import org.hawkular.agent.monitor.scheduler.SchedulerService;
import org.hawkular.agent.monitor.service.MonitorService;
import org.hawkular.agent.monitor.service.ServiceStatus;
import org.hawkular.agent.monitor.util.Util;
import org.hawkular.agent.monitor.util.WildflyCompatibilityUtils;
import org.jboss.as.controller.AbstractWriteAttributeHandler;
import org.jboss.as.controller.AttributeDefinition;
import org.jboss.as.controller.OperationContext;
import org.jboss.as.controller.OperationFailedException;
import org.jboss.as.controller.PathElement;
import org.jboss.as.controller.PersistentResourceDefinition;
import org.jboss.as.controller.registry.ManagementResourceRegistration;
import org.jboss.as.controller.registry.OperationEntry.Flag;
import org.jboss.dmr.ModelNode;

public class PlatformDefinition extends MonitorPersistentResourceDefinition {

    public static final PlatformDefinition INSTANCE = new PlatformDefinition();

    static final String PLATFORM = "platform";

    private PlatformDefinition() {
        super(PathElement.pathElement(PLATFORM, "default"),
                SubsystemExtension.getResourceDescriptionResolver(PLATFORM),
                PlatformAdd.INSTANCE,
                PlatformRemove.INSTANCE,
                Flag.RESTART_NONE,
                Flag.RESTART_NONE);
    }

    @Override
    public Collection<AttributeDefinition> getAttributes() {
        return Arrays.asList(PlatformAttributes.ATTRIBUTES);
    }

    @Override
    protected List<? extends PersistentResourceDefinition> getChildren() {
        return Arrays.asList(FileStoresDefinition.INSTANCE, MemoryDefinition.INSTANCE, ProcessorsDefinition.INSTANCE,
                PowerSourcesDefinition.INSTANCE);
    }

    @Override
    public void registerAttributes(ManagementResourceRegistration resourceRegistration) {
        super.registerAttributes(resourceRegistration);

        resourceRegistration.registerReadWriteAttribute(PlatformAttributes.ENABLED, null,
                new MonitorServiceWriteAttributeHandler<Void>() {
                    @Override
                    protected boolean applyUpdateToRuntime(
                            OperationContext context,
                            ModelNode operation,
                            String attributeName,
                            ModelNode newValue,
                            ModelNode currentValue,
                            AbstractWriteAttributeHandler.HandbackHolder<Void> handbackHolder)
                            throws OperationFailedException {

                        if (context.isBooting()) {
                            return false;
                        }

                        super.applyUpdateToRuntime(context, operation, attributeName, newValue, currentValue,
                                handbackHolder);

                        boolean currBool = PlatformAttributes.ENABLED.resolveValue(context, currentValue).asBoolean();
                        boolean newBool = PlatformAttributes.ENABLED.resolveValue(context, newValue).asBoolean();
                        if (currBool == newBool) {
                            return false; // don't know if this would ever happen, but if it does, nothing changed
                        }

                        MonitorService monitorService = getMonitorService(context);
                        if (monitorService == null
                                || monitorService.getMonitorServiceStatus() == ServiceStatus.STARTING) {
                            return true; // caught service starting up, need to be restarted to pick up this change
                        }

                        ProtocolService<PlatformNodeLocation, PlatformSession> platformService = monitorService
                                .getProtocolServices().getPlatformProtocolService();
                        String thisEndpointName = WildflyCompatibilityUtils.getCurrentAddressValue(context, operation);

                        if (newBool) {
                            // add the endpoint so it begins to be monitored

                            // first get our subsystem config
                            MonitorServiceConfiguration config = Util.getMonitorServiceConfiguration(context);

                            // create a new endpoint service
                            ProtocolServices newServices = monitorService.createProtocolServicesBuilder()
                                    .platformProtocolService(config.getPlatformConfiguration())
                                    .build();
                            EndpointService<PlatformNodeLocation, PlatformSession> endpointService = newServices
                                    .getPlatformProtocolService().getEndpointServices().get(thisEndpointName);

                            // put the new endpoint service in the original protocol services container
                            platformService.add(endpointService);
                        } else {
                            // remove the endpoint so it is no longer monitored
                            SchedulerService schedulerService = monitorService.getSchedulerService();
                            platformService.remove(thisEndpointName, schedulerService);
                        }

                        return false;
                    }

                    @Override
                    protected void revertUpdateToRuntime(
                            OperationContext context,
                            ModelNode operation,
                            String attributeName,
                            ModelNode originalValue,
                            ModelNode newBadValue,
                            Void handback)
                            throws OperationFailedException {
                        // nothing to revert?
                    }
                });
    }

}
