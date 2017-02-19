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

import java.util.concurrent.atomic.AtomicReference;

import org.hawkular.agent.monitor.service.MonitorService;
import org.hawkular.agent.monitor.service.ServiceStatus;
import org.hawkular.agent.monitor.util.WildflyCompatibilityUtils;
import org.jboss.as.controller.OperationContext;
import org.jboss.as.controller.OperationFailedException;
import org.jboss.as.controller.OperationStepHandler;
import org.jboss.as.controller.PathAddress;
import org.jboss.as.controller.registry.Resource;
import org.jboss.dmr.ModelNode;
import org.jboss.logging.Logger;
import org.jboss.msc.service.ServiceName;
import org.jboss.msc.service.ServiceNotFoundException;
import org.jboss.msc.service.ServiceRegistry;

public class OperationSubsystemStart implements OperationStepHandler {
    private static final Logger LOGGER = Logger.getLogger(OperationSubsystemStart.class);

    @Override
    public void execute(OperationContext opContext, ModelNode model) throws OperationFailedException {

        final AtomicReference<Thread> newThread = new AtomicReference<>();

        try {
            ServiceName name = SubsystemExtension.SERVICE_NAME;
            ServiceRegistry serviceRegistry = opContext.getServiceRegistry(true);
            MonitorService service = (MonitorService) serviceRegistry.getRequiredService(name).getValue();

            final boolean refresh = model.get("refresh").asBoolean(false);
            final boolean restart = refresh || model.get("restart").asBoolean(false);
            final long delay = model.get("delay").asLong(0L);
            final PathAddress address = refresh ? WildflyCompatibilityUtils.getCurrentAddress(opContext, model) : null;
            final ModelNode config = refresh ? Resource.Tools.readModel(opContext.readResourceFromRoot(address))
                    : null;
            final MonitorServiceConfiguration newConfig = refresh
                    ? new MonitorServiceConfigurationBuilder(config, opContext).build() : null;

            newThread.set(new Thread(new Runnable() {
                @Override
                public void run() {
                    try {
                        if (delay > 0) {
                            Thread.sleep(delay);
                        }

                        if (restart) {
                            LOGGER.warnf("Stopping Hawkular Monitor Service now, %s requested.",
                                    refresh ? "refresh" : "restart");
                            service.stopMonitorService();
                        }

                        if (service.getMonitorServiceStatus() == ServiceStatus.RUNNING) {
                            LOGGER.warn("Skipping Hawkular Monitor Service start, it is already started.");
                        } else {
                            LOGGER.warn("Starting Hawkular Monitor Service now.");
                            service.startMonitorService(newConfig);
                        }
                    } catch (Exception e) {
                        LOGGER.warn("Aborting start of the Hawkular Monitor service: " + e);
                        return;
                    }
                }
            }, "Hawkular WildFly Agent Operation Start Thread"));
            newThread.get().setDaemon(true);
            newThread.get().start();

        } catch (ServiceNotFoundException snfe) {
            throw new OperationFailedException("Cannot restart Hawkular Monitor service - it is disabled", snfe);
        } catch (Exception e) {
            if (newThread.get() != null) {
                newThread.get().interrupt();
            }
            throw new OperationFailedException("Cannot restart Hawkular Monitor service", e);
        }

        opContext.stepCompleted();
        return;
    }
}