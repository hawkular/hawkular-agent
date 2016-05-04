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

import org.hawkular.agent.monitor.service.MonitorService;
import org.jboss.as.controller.AbstractRemoveStepHandler;
import org.jboss.as.controller.OperationContext;
import org.jboss.msc.service.ServiceController;
import org.jboss.msc.service.ServiceName;
import org.jboss.msc.service.ServiceRegistry;

/**
 * Base class to any remove-step handler that requires common functionality like a method
 * to obtain the service.
 */
public abstract class MonitorServiceRemoveStepHandler extends AbstractRemoveStepHandler {

    public MonitorServiceRemoveStepHandler() {
    }

    /**
     * @param opContext context this call is coming from
     * @return the actual service - this will be null if the agent was not enabled
     */
    protected MonitorService getMonitorService(OperationContext opContext) {
        ServiceName name = SubsystemExtension.SERVICE_NAME;
        ServiceRegistry serviceRegistry = opContext.getServiceRegistry(true);
        ServiceController<?> service = serviceRegistry.getService(name);
        return (service != null) ? (MonitorService) service.getValue() : null;
    }
}
