/*
 * Copyright 2015 Red Hat, Inc. and/or its affiliates
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
import java.util.List;

import org.hawkular.agent.monitor.log.MsgLogger;
import org.hawkular.agent.monitor.service.MonitorService;
import org.jboss.as.controller.AbstractAddStepHandler;
import org.jboss.as.controller.OperationContext;
import org.jboss.as.controller.OperationFailedException;
import org.jboss.as.controller.PathAddress;
import org.jboss.as.controller.ServiceVerificationHandler;
import org.jboss.as.controller.registry.Resource;
import org.jboss.as.naming.ImmediateManagedReferenceFactory;
import org.jboss.as.naming.ManagedReferenceFactory;
import org.jboss.as.naming.ServiceBasedNamingStore;
import org.jboss.as.naming.deployment.ContextNames;
import org.jboss.as.naming.service.BinderService;
import org.jboss.dmr.ModelNode;
import org.jboss.msc.service.AbstractServiceListener;
import org.jboss.msc.service.ServiceBuilder;
import org.jboss.msc.service.ServiceController;
import org.jboss.msc.service.ServiceName;
import org.jboss.msc.service.ServiceTarget;

public class SubsystemAdd extends AbstractAddStepHandler {

    static final SubsystemAdd INSTANCE = new SubsystemAdd();

    private SubsystemAdd() {
    }

    @Override
    protected void populateModel(ModelNode operation, ModelNode model) throws OperationFailedException {
        this.attributes = Arrays.asList(SubsystemDefinition.ATTRIBUTES);
        super.populateModel(operation, model);
    }

    @Override
    protected void performRuntime(OperationContext context, ModelNode operation, ModelNode model,
            ServiceVerificationHandler verificationHandler, List<ServiceController<?>> newControllers)
            throws OperationFailedException {

        ModelNode subsystemConfig = Resource.Tools.readModel(context.readResource(PathAddress.EMPTY_ADDRESS));
        MonitorServiceConfiguration configuration = new MonitorServiceConfiguration(subsystemConfig, context);

        if (!configuration.subsystemEnabled) {
            MsgLogger.LOG.infoSubsystemDisabled();
            return;
        }

        createService(context.getServiceTarget(), newControllers, verificationHandler, configuration);
    }

    private void createService(final ServiceTarget target, final List<ServiceController<?>> newControllers,
            final ServiceVerificationHandler verificationHandler, final MonitorServiceConfiguration configuration) {

        // create and configure the service itself
        MonitorService service = new MonitorService();
        service.configure(configuration);

        // create the builder that will be responsible for preparing the service deployment
        ServiceBuilder<MonitorService> svcBuilder;
        svcBuilder = target.addService(SubsystemExtension.SERVICE_NAME, service);
        svcBuilder.addListener(verificationHandler);
        svcBuilder.setInitialMode(ServiceController.Mode.ACTIVE);
        service.addDependencies(svcBuilder);

        // bind the metrics API to JNDI so other apps can use it, and prepare to build the binder service
        String jndiObject = new String("dummy");
        String jndiName = configuration.metricsJndi;
        ContextNames.BindInfo bindInfo = ContextNames.bindInfoFor(jndiName);
        BinderService binderService = new BinderService(bindInfo.getBindName());
        ManagedReferenceFactory valueMRF = new ImmediateManagedReferenceFactory(jndiObject);
        String jndiObjectClassName = jndiObject.getClass().getName();
        ServiceName binderServiceName = bindInfo.getBinderServiceName();
        ServiceBuilder<?> binderBuilder = target
                .addService(binderServiceName, binderService)
                .addInjection(binderService.getManagedObjectInjector(), valueMRF)
                .setInitialMode(ServiceController.Mode.ACTIVE)
                .addDependency(bindInfo.getParentContextServiceName(), ServiceBasedNamingStore.class,
                        binderService.getNamingStoreInjector())
                .addListener(new AbstractServiceListener<Object>() {
                    public void transition(final ServiceController<? extends Object> controller,
                            final ServiceController.Transition transition) {
                        switch (transition) {
                            case STARTING_to_UP: {
                                MsgLogger.LOG.infoBindJndiResource(jndiName, jndiObjectClassName);
                                break;
                            }
                            case START_REQUESTED_to_DOWN: {
                                MsgLogger.LOG.infoUnbindJndiResource(jndiName);
                                break;
                            }
                            case REMOVING_to_REMOVED: {
                                MsgLogger.LOG.infoUnbindJndiResource(jndiName);
                                break;
                            }
                            default:
                                break;
                        }
                    }
                });

        // our monitor service will depend on the binder service
        svcBuilder.addDependency(binderServiceName);

        // install the monitor service and the binder service
        ServiceController<MonitorService> svcController = svcBuilder.install();
        newControllers.add(svcController);

        ServiceController<?> binderController = binderBuilder.install();
        newControllers.add(binderController);

        return;
    }
}
