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

import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.ALLOW_RESOURCE_SERVICE_RESTART;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.OPERATION_HEADERS;

import java.util.Collection;

import org.hawkular.agent.monitor.service.MonitorService;
import org.hawkular.agent.wildfly.util.WildflyCompatibilityUtils;
import org.jboss.as.controller.AttributeDefinition;
import org.jboss.as.controller.OperationContext;
import org.jboss.as.controller.OperationFailedException;
import org.jboss.as.controller.OperationStepHandler;
import org.jboss.as.controller.PathAddress;
import org.jboss.as.controller.RestartParentWriteAttributeHandler;
import org.jboss.as.controller.descriptions.ModelDescriptionConstants;
import org.jboss.dmr.ModelNode;
import org.jboss.msc.service.ServiceController;
import org.jboss.msc.service.ServiceName;
import org.jboss.msc.service.ServiceRegistry;

public class MonitorServiceRestartParentAttributeHandler extends RestartParentWriteAttributeHandler {

    private static final OperationContext.AttachmentKey<Integer> RECREATE_COUNTER = OperationContext.AttachmentKey
            .create(Integer.class);

    private static final OperationContext.AttachmentKey<Integer> REMOVE_COUNTER = OperationContext.AttachmentKey
            .create(Integer.class);

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
    protected boolean requiresRuntime(OperationContext context) {
        return true;
    }

    @Override
    protected void removeServices(OperationContext context, ServiceName parentService, ModelNode parentModel)
            throws OperationFailedException {
        incrementAttachedCounter(context, REMOVE_COUNTER);
        context.addStep(new OperationStepHandler() {
            @Override
            public void execute(OperationContext context, ModelNode operation) throws OperationFailedException {
                Integer counter = decrementAttachedCounter(context, REMOVE_COUNTER);
                if (counter == 0) {
                    SubsystemRemove.INSTANCE.performRuntime(context, null, parentModel);
                } else if (counter < 0) {
                    throw new OperationFailedException("The removeServices step got added more times than needed - " +
                            "This shouldn't happen.");
                }
                WildflyCompatibilityUtils.operationContextStepCompleted(context);
            }
        }, OperationContext.Stage.RUNTIME);
    }

    @Override
    protected void recreateParentService(OperationContext context, PathAddress parentAddress, ModelNode parentModel)
            throws OperationFailedException {
        incrementAttachedCounter(context, RECREATE_COUNTER);
        context.addStep(new OperationStepHandler() {
            @Override
            public void execute(OperationContext context, ModelNode operation) throws OperationFailedException {
                Integer counter = decrementAttachedCounter(context, RECREATE_COUNTER);
                if (counter == 0) {
                    SubsystemAdd.INSTANCE.performRuntime(context,
                            new ModelNode().set(ModelDescriptionConstants.OP_ADDR, parentAddress.toModelNode()),
                            parentModel);
                } else if (counter < 0) {
                    throw new OperationFailedException(
                            "The recreateParentService step got added more times than needed - " +
                                    "This shouldn't happen.");
                }
                WildflyCompatibilityUtils.operationContextStepCompleted(context);
            }
        }, OperationContext.Stage.RUNTIME);
    }

    // By default we always allow a restart
    @Override
    protected boolean isResourceServiceRestartAllowed(final OperationContext context,
            final ServiceController<?> service) {
        return true;
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

        // When updating agent config we by default allow immediate restarts, the caller does not have to be aware
        // of which attributes may or may not force the restart.  In some situations, namely updates made by
        // an agent command like UpdateCollectionIntervalsCommand, we need to defer the restart because otherwise
        // the command response will never be sent back.  To defer the restart the operation must explicitly
        // have the header ALLOW_RESOURCE_SERVICE_RESTART=false (note, each operation in a composite must assign
        // it individually). In this situation not only do we want to defer the restart, we want to avoid setting
        // the server to 'reload-required'.  To do this we temporarily mark the parent as restarted, and we must
        // also return false from this method.
        ModelNode headers = operation.has(OPERATION_HEADERS) ? operation.get(OPERATION_HEADERS) : null;
        boolean restartAllowedOptionSet = (headers != null) && headers.hasDefined(ALLOW_RESOURCE_SERVICE_RESTART);
        boolean restartAllowed = !restartAllowedOptionSet || headers.get(ALLOW_RESOURCE_SERVICE_RESTART).asBoolean();
        Object tempOwner = null;
        PathAddress address = null;
        try {
            if (!restartAllowed) {
                address = getParentAddress(WildflyCompatibilityUtils.getCurrentAddress(context, operation));
                tempOwner = new Object();
                context.markResourceRestarted(address, tempOwner);
            }
            super.applyUpdateToRuntime(context, operation, attributeName, resolvedValue, currentValue, handbackHolder);

        } finally {
            if (!restartAllowed && null != address && null != tempOwner) {
                context.revertResourceRestarted(address, tempOwner);
            }
        }
        return false;
    }

    protected MonitorService getMonitorService(OperationContext opContext) {
        ServiceName name = SubsystemExtension.SERVICE_NAME;
        ServiceRegistry serviceRegistry = opContext.getServiceRegistry(true);
        MonitorService service = (MonitorService) serviceRegistry.getRequiredService(name).getValue();
        return service;
    }

    private Integer incrementAttachedCounter(OperationContext context, OperationContext.AttachmentKey<Integer> key) {
        Integer value = context.getAttachment(key);
        if (value == null) {
            value = 0;
        }
        value = value + 1;
        context.attach(key, value);
        return value;
    }

    private Integer decrementAttachedCounter(OperationContext context, OperationContext.AttachmentKey<Integer> key) {
        Integer value = context.getAttachment(key);
        if (value == null) {
            return null;
        }
        value = value - 1;
        context.attach(key, value);
        return value;
    }

}
