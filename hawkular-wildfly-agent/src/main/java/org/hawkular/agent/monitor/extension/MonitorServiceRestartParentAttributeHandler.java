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

import java.util.Collection;
import java.util.NoSuchElementException;

import org.hawkular.agent.monitor.service.MonitorService;
import org.jboss.as.controller.AttributeDefinition;
import org.jboss.as.controller.OperationContext;
import org.jboss.as.controller.OperationFailedException;
import org.jboss.as.controller.OperationStepHandler;
import org.jboss.as.controller.PathAddress;
import org.jboss.as.controller.RestartParentWriteAttributeHandler;
import org.jboss.as.controller.descriptions.ModelDescriptionConstants;
import org.jboss.as.controller.registry.Resource;
import org.jboss.dmr.ModelNode;
import org.jboss.msc.service.ServiceController;
import org.jboss.msc.service.ServiceName;
import org.jboss.msc.service.ServiceRegistry;

public class MonitorServiceRestartParentAttributeHandler extends RestartParentWriteAttributeHandler {

    private static final OperationContext.AttachmentKey<Integer> RESTART_COUNTER =
            OperationContext.AttachmentKey.create(Integer.class);

    public MonitorServiceRestartParentAttributeHandler(AttributeDefinition... definitions) {
        super("subsystem", definitions);
    }

    public MonitorServiceRestartParentAttributeHandler(Collection<AttributeDefinition> definitions) {
        super("subsystem", definitions);
    }

    @Override
    protected ServiceName getParentServiceName(PathAddress parentAddress) {
        return SubsystemExtension.SERVICE_NAME;
    }

    @Override
    protected boolean isResourceServiceRestartAllowed(OperationContext context, ServiceController<?> service) {
        return true;
    }

    @Override
    protected boolean requiresRuntime(OperationContext context) {
        return true;
    }

    @Override
    protected void removeServices(OperationContext context, ServiceName parentService, ModelNode parentModel)
            throws OperationFailedException {
        SubsystemRemove.INSTANCE.performRuntime(context, null, parentModel);
    }

    @Override
    protected void recreateParentService(OperationContext context, PathAddress parentAddress, ModelNode parentModel)
            throws OperationFailedException {
        SubsystemAdd.INSTANCE.performRuntime(context,
                new ModelNode().set(ModelDescriptionConstants.OP_ADDR, parentAddress.toModelNode()),
                parentModel);
    }

    @Override
    protected boolean applyUpdateToRuntime(OperationContext context, ModelNode operation, String attributeName,
            ModelNode resolvedValue, ModelNode currentValue,
            org.jboss.as.controller.AbstractWriteAttributeHandler.HandbackHolder<ModelNode> handbackHolder)
            throws OperationFailedException {

        if (!context.isBooting()) {
            MonitorService agent = getMonitorService(context);
            if (agent.isImmutable()) {
                throw new OperationFailedException("The agent is configured to be immutable - no changes are allowed");
            }
        }

        final PathAddress address = getParentAddress(context.getCurrentAddress());
        final ServiceName serviceName = getParentServiceName(address);
        ServiceController<?> service = serviceName != null ?
                context.getServiceRegistry(false).getService(serviceName) : null;

        // No parent service, nothing to do
        if (service == null) {
            return false;
        }

        boolean restartServices = isResourceServiceRestartAllowed(context, service);

        if (restartServices) {
            final Integer ZERO = Integer.valueOf(0);
            Integer previous = context.getAttachment(RESTART_COUNTER);
            if (previous == null) {
                previous = ZERO;
            }
            context.attach(RESTART_COUNTER, previous + 1);
            context.addStep(new OperationStepHandler() {
                @Override
                public void execute(OperationContext context, ModelNode operation) throws OperationFailedException {
                    Integer current = context.getAttachment(RESTART_COUNTER);
                    if (current != null) {
                        if (current > 0) {
                            current = current - 1;
                            context.attach(RESTART_COUNTER, current);
                            if (current == 0) {
                                ModelNode parentModel = getModel(context, address);
                                if (parentModel != null && context.markResourceRestarted(address, this)) {
                                    removeServices(context, serviceName, parentModel);
                                    recreateParentService(context, address, parentModel);
                                }
                            }
                        }
                    }
                }
            }, OperationContext.Stage.RUNTIME);
        }
        // Fall back to server wide reload
        return !restartServices;
    }

    private ModelNode getModel(OperationContext ctx, PathAddress address) {
        try {
            Resource resource = ctx.readResourceFromRoot(address);
            return Resource.Tools.readModel(resource);
        } catch (NoSuchElementException e) {
            return null;
        }
    }

    protected MonitorService getMonitorService(OperationContext opContext) {
        ServiceName name = SubsystemExtension.SERVICE_NAME;
        ServiceRegistry serviceRegistry = opContext.getServiceRegistry(true);
        MonitorService service = (MonitorService) serviceRegistry.getRequiredService(name).getValue();
        return service;
    }
}
