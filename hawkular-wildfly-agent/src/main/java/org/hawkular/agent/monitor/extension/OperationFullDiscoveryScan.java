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

import org.hawkular.agent.monitor.service.MonitorService;
import org.hawkular.agent.monitor.service.ServiceStatus;
import org.jboss.as.controller.OperationContext;
import org.jboss.as.controller.OperationFailedException;
import org.jboss.as.controller.OperationStepHandler;
import org.jboss.dmr.ModelNode;
import org.jboss.msc.service.ServiceName;
import org.jboss.msc.service.ServiceNotFoundException;
import org.jboss.msc.service.ServiceRegistry;

public class OperationFullDiscoveryScan implements OperationStepHandler {

    @Override
    public void execute(OperationContext opContext, ModelNode model) throws OperationFailedException {
        try {
            ServiceName name = SubsystemExtension.SERVICE_NAME;
            ServiceRegistry serviceRegistry = opContext.getServiceRegistry(true);
            MonitorService service = (MonitorService) serviceRegistry.getRequiredService(name).getValue();
            ServiceStatus status = service.getStatus();
            if (status == ServiceStatus.RUNNING) {
                long start = System.currentTimeMillis();
                service.getProtocolServices().discoverAll();
                long duration = System.currentTimeMillis() - start;
                opContext.getResult()
                        .set(String.format("Full inventory discovery scan completed in [%d] milliseconds", duration));
            } else {
                throw new OperationFailedException("Agent is not running - status is [" + status + "]");
            }
        } catch (OperationFailedException ofe) {
            throw ofe;
        } catch (ServiceNotFoundException snfe) {
            throw new OperationFailedException("Agent is not deployed: " + snfe.toString());
        } catch (Exception e) {
            throw new OperationFailedException(e.toString());
        }
        opContext.stepCompleted();
    }
}
