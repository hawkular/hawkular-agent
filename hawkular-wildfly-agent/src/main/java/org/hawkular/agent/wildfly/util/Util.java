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
package org.hawkular.agent.wildfly.util;

import java.util.ArrayList;
import java.util.Collection;

import org.hawkular.agent.monitor.config.AgentCoreEngineConfiguration;
import org.hawkular.agent.monitor.extension.MonitorServiceConfigurationBuilder;
import org.hawkular.agent.monitor.extension.SubsystemExtension;
import org.hawkular.agent.monitor.service.MonitorService;
import org.jboss.as.controller.AttributeDefinition;
import org.jboss.as.controller.OperationContext;
import org.jboss.as.controller.OperationFailedException;
import org.jboss.as.controller.PathAddress;
import org.jboss.as.controller.PathElement;
import org.jboss.as.controller.ReloadRequiredWriteAttributeHandler;
import org.jboss.as.controller.RestartParentWriteAttributeHandler;
import org.jboss.as.controller.registry.AttributeAccess;
import org.jboss.as.controller.registry.ManagementResourceRegistration;
import org.jboss.as.controller.registry.Resource;
import org.jboss.dmr.ModelNode;
import org.jboss.msc.service.ServiceName;
import org.jboss.msc.service.ServiceRegistry;

/**
 * Some utilities for use explicitly with WildFly app server components.
 *
 * @author John Mazzitelli
 */
public class Util {
    /**
     * This will register only those given attributes that require a restart.
     * Other attributes are left unregistered. It is the caller's responsibility to register those
     * other attributes. Typically those other attributes support changing the service at runtime
     * immediately when the attribute is changed (rather than requiring to restart the server to
     * pick up the change).
     *
     * @param resourceRegistration there the restart attributes will be registered
     * @param allAttributes a collection of attributes where some, all, or none will require a restart upon change.
     */
    public static void registerOnlyRestartAttributes(
            ManagementResourceRegistration resourceRegistration,
            Collection<AttributeDefinition> allAttributes) {
        Collection<AttributeDefinition> restartResourceServicesAttributes = new ArrayList<>();
        Collection<AttributeDefinition> restartAllServicesAttributes = new ArrayList<>();
        for (AttributeDefinition attribDef : allAttributes) {
            if (attribDef.getFlags().contains(AttributeAccess.Flag.RESTART_JVM)
                    || attribDef.getFlags().contains(AttributeAccess.Flag.RESTART_ALL_SERVICES)) {
                restartAllServicesAttributes.add(attribDef);
            } else if (attribDef.getFlags().contains(AttributeAccess.Flag.RESTART_RESOURCE_SERVICES)) {
                restartResourceServicesAttributes.add(attribDef);
            }
        }

        class CustomWriteAttributeHandler extends ReloadRequiredWriteAttributeHandler {
            public CustomWriteAttributeHandler(Collection<AttributeDefinition> attribs) {
                super(attribs);
            }

            @Override
            public void execute(OperationContext context, ModelNode operation) throws OperationFailedException {
                if (!context.isBooting()) {
                    ServiceName name = SubsystemExtension.SERVICE_NAME;
                    ServiceRegistry serviceRegistry = context.getServiceRegistry(true);
                    MonitorService agent = (MonitorService) serviceRegistry.getRequiredService(name).getValue();
                    if (agent.isImmutable()) {
                        throw new OperationFailedException(
                                "The agent is configured to be immutable - no changes are allowed.");
                    }
                }

                super.execute(context, operation);
            }
        }

        ReloadRequiredWriteAttributeHandler handler = new CustomWriteAttributeHandler(restartAllServicesAttributes);
        RestartParentWriteAttributeHandler restartParentHandler = //
                new WildflyCompatibilityUtils.EAP6MonitorServiceRestartParentAttributeHandler(
                        restartResourceServicesAttributes);

        for (AttributeDefinition attribDef : restartAllServicesAttributes) {
            resourceRegistration.registerReadWriteAttribute(attribDef, null, handler);
        }
        for (AttributeDefinition attribDef : restartResourceServicesAttributes) {
            resourceRegistration.registerReadWriteAttribute(attribDef, null, restartParentHandler);
        }
    }

    /**
     * Used by extension classes that need to get the subsystem's configuration.
     *
     * @param context context used to obtain the config
     * @return the subsystem config
     * @throws OperationFailedException
     */
    public static AgentCoreEngineConfiguration getMonitorServiceConfiguration(OperationContext context)
            throws OperationFailedException {
        PathAddress subsystemAddress = PathAddress
                .pathAddress(PathElement.pathElement("subsystem", SubsystemExtension.SUBSYSTEM_NAME));
        ModelNode subsystemConfig = Resource.Tools.readModel(context.readResourceFromRoot(subsystemAddress));
        AgentCoreEngineConfiguration config = new MonitorServiceConfigurationBuilder(subsystemConfig, context).build();
        return config;
    }
}
