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

import org.hawkular.agent.monitor.config.AgentCoreEngineConfiguration;
import org.hawkular.agent.monitor.protocol.EndpointService;
import org.hawkular.agent.monitor.protocol.ProtocolService;
import org.hawkular.agent.monitor.protocol.ProtocolServices;
import org.hawkular.agent.monitor.protocol.jmx.JMXNodeLocation;
import org.hawkular.agent.monitor.protocol.jmx.JMXSession;
import org.hawkular.agent.monitor.scheduler.SchedulerService;
import org.hawkular.agent.monitor.service.MonitorService;
import org.hawkular.agent.monitor.service.ServiceStatus;
import org.hawkular.agent.wildfly.util.Util;
import org.jboss.as.controller.AbstractWriteAttributeHandler;
import org.jboss.as.controller.AttributeDefinition;
import org.jboss.as.controller.OperationContext;
import org.jboss.as.controller.OperationFailedException;
import org.jboss.as.controller.PathElement;
import org.jboss.as.controller.registry.ManagementResourceRegistration;
import org.jboss.as.controller.registry.OperationEntry.Flag;
import org.jboss.dmr.ModelNode;

public class RemoteJMXDefinition extends MonitorPersistentResourceDefinition {

    public static final RemoteJMXDefinition INSTANCE = new RemoteJMXDefinition();

    static final String REMOTE_JMX = "remote-jmx";

    private RemoteJMXDefinition() {
        super(PathElement.pathElement(REMOTE_JMX),
                SubsystemExtension.getResourceDescriptionResolver(ManagedServersDefinition.MANAGED_SERVERS,
                        REMOTE_JMX),
                RemoteJMXAdd.INSTANCE,
                RemoteJMXRemove.INSTANCE,
                Flag.RESTART_NONE,
                Flag.RESTART_NONE);
    }

    @Override
    public Collection<AttributeDefinition> getAttributes() {
        return Arrays.asList(RemoteJMXAttributes.ATTRIBUTES);
    }

    @Override
    public void registerAttributes(ManagementResourceRegistration resourceRegistration) {
        super.registerAttributes(resourceRegistration);

        resourceRegistration.registerReadWriteAttribute(RemoteJMXAttributes.ENABLED, null,
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

                        boolean currBool = RemoteJMXAttributes.ENABLED.resolveValue(context, currentValue).asBoolean();
                        boolean newBool = RemoteJMXAttributes.ENABLED.resolveValue(context, newValue).asBoolean();
                        if (currBool == newBool) {
                            return false; // don't know if this would ever happen, but if it does, nothing changed
                        }

                        MonitorService monitorService = getMonitorService(context);
                        if (monitorService == null || monitorService.getStatus() == ServiceStatus.STARTING) {
                            return true; // caught service starting up, need to be restarted to pick up this change
                        }

                        ProtocolService<JMXNodeLocation, JMXSession> jmxService = monitorService.getProtocolServices()
                                .getJmxProtocolService();
                        String thisEndpointName = context.getCurrentAddressValue();

                        if (newBool) {
                            // add the endpoint so it begins to be monitored

                            // first get our subsystem config
                            AgentCoreEngineConfiguration config = Util.getMonitorServiceConfiguration(context);

                            // create a new endpoint service
                            ProtocolServices newServices = monitorService.createProtocolServicesBuilder()
                                    .jmxProtocolService(config.getJmxConfiguration())
                                    .build();
                            EndpointService<JMXNodeLocation, JMXSession> endpointService = newServices
                                    .getJmxProtocolService().getEndpointServices().get(thisEndpointName);

                            // put the new endpoint service in the original protocol services container
                            jmxService.add(endpointService);
                        } else {
                            // remove the endpoint so it is no longer monitored
                            SchedulerService schedulerService = monitorService.getSchedulerService();
                            jmxService.remove(thisEndpointName, schedulerService);
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
