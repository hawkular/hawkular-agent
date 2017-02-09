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

import org.hawkular.agent.monitor.service.MonitorService;
import org.jboss.as.controller.AbstractWriteAttributeHandler;
import org.jboss.as.controller.AttributeDefinition;
import org.jboss.as.controller.OperationContext;
import org.jboss.as.controller.OperationFailedException;
import org.jboss.dmr.ModelNode;
import org.jboss.msc.service.ServiceName;
import org.jboss.msc.service.ServiceRegistry;

/**
 * Base class to all the write attribute handlers. Simply provides a method to obtain the service.
 * Subclasses should also call {@link #applyUpdateToRuntime()} to ensure the agent is allowed
 * to write the attribute.
 *
 * @param <T> used to assist in the revert functionality. Can be <code>Void</code> if not needed.
 */
public abstract class MonitorServiceWriteAttributeHandler<T> extends AbstractWriteAttributeHandler<T> {

    public MonitorServiceWriteAttributeHandler(AttributeDefinition... definitions) {
        super(definitions);
    }

    public MonitorServiceWriteAttributeHandler(Collection<AttributeDefinition> definitions) {
        super(definitions);
    }

    protected MonitorService getMonitorService(OperationContext opContext) {
        ServiceName name = SubsystemExtension.SERVICE_NAME;
        ServiceRegistry serviceRegistry = opContext.getServiceRegistry(true);
        MonitorService service = (MonitorService) serviceRegistry.getRequiredService(name).getValue();
        return service;
    }

    @Override
    protected boolean applyUpdateToRuntime(OperationContext context, ModelNode operation, String attributeName,
            ModelNode resolvedValue, ModelNode currentValue,
            org.jboss.as.controller.AbstractWriteAttributeHandler.HandbackHolder<T> handbackHolder)
            throws OperationFailedException {

        if (!context.isBooting()) {
            MonitorService agent = getMonitorService(context);
            if (agent.isImmutable()) {
                throw new OperationFailedException("The agent is configured to be immutable - no changes are allowed");
            }
        }

        return false; // assume no reload required
    }
}
