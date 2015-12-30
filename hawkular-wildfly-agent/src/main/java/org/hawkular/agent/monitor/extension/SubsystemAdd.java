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

import org.hawkular.agent.monitor.api.HawkularWildFlyAgentContext;
import org.hawkular.agent.monitor.log.AgentLoggers;
import org.hawkular.agent.monitor.log.MsgLogger;
import org.hawkular.agent.monitor.service.MonitorService;
import org.jboss.as.controller.AbstractAddStepHandler;
import org.jboss.as.controller.OperationContext;
import org.jboss.as.controller.OperationFailedException;
import org.jboss.as.controller.PathAddress;
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
    private static final MsgLogger log = AgentLoggers.getLogger(SubsystemAdd.class);
    static final SubsystemAdd INSTANCE = new SubsystemAdd();

    private SubsystemAdd() {
        super(SubsystemAttributes.ATTRIBUTES);
    }

    @Override
    protected void performRuntime(OperationContext context, ModelNode operation, ModelNode model)
            throws OperationFailedException {

        ModelNode subsystemConfig = Resource.Tools.readModel(context.readResource(PathAddress.EMPTY_ADDRESS));
        MonitorServiceConfiguration configuration = new MonitorServiceConfigurationBuilder(subsystemConfig, context)
                .build();

        if (!configuration.isSubsystemEnabled()) {
            log.infoSubsystemDisabled();
            return;
        }

        createService(context.getServiceTarget(), configuration);
    }

    private void createService(final ServiceTarget target, final MonitorServiceConfiguration configuration) {

        // create and configure the service itself
        MonitorService service = new MonitorService(configuration);

        // create the builder that will be responsible for preparing the service deployment
        ServiceBuilder<MonitorService> svcBuilder;
        svcBuilder = target.addService(SubsystemExtension.SERVICE_NAME, service);
        svcBuilder.setInitialMode(ServiceController.Mode.ACTIVE);
        service.addDependencies(svcBuilder);

        // bind the API to JNDI so other apps can use it, and prepare to build the binder service
        String jndiName = configuration.getApiJndi();
        boolean bindJndi = (jndiName == null || jndiName.isEmpty()) ? false : true;
        if (bindJndi) {
            Object jndiObject = service.getHawkularMonitorContext();
            ContextNames.BindInfo bindInfo = ContextNames.bindInfoFor(jndiName);
            BinderService binderService = new BinderService(bindInfo.getBindName());
            ManagedReferenceFactory valueMRF = new ImmediateManagedReferenceFactory(jndiObject);
            String jndiObjectClassName = HawkularWildFlyAgentContext.class.getName();
            ServiceName binderServiceName = bindInfo.getBinderServiceName();
            ServiceBuilder<?> binderBuilder = target
                    .addService(binderServiceName, binderService)
                    .addInjection(binderService.getManagedObjectInjector(), valueMRF)
                    .setInitialMode(ServiceController.Mode.ACTIVE)
                    .addDependency(bindInfo.getParentContextServiceName(),
                            ServiceBasedNamingStore.class,
                            binderService.getNamingStoreInjector())
                    .addListener(new JndiBindListener(jndiName, jndiObjectClassName));

            // our monitor service will depend on the binder service
            svcBuilder.addDependency(binderServiceName);

            // install the binder service
            binderBuilder.install();
        }

        // install the monitor service
        svcBuilder.install();

        return;
    }

    private final class JndiBindListener extends AbstractServiceListener<Object> {
        private final String jndiName;
        private final String jndiObjectClassName;

        public JndiBindListener(String jndiName, String jndiObjectClassName) {
            this.jndiName = jndiName;
            this.jndiObjectClassName = jndiObjectClassName;
        }

        public void transition(final ServiceController<? extends Object> controller,
                final ServiceController.Transition transition) {
            switch (transition) {
                case STARTING_to_UP: {
                    log.infoBindJndiResource(jndiName, jndiObjectClassName);
                    break;
                }
                case START_REQUESTED_to_DOWN: {
                    log.infoUnbindJndiResource(jndiName);
                    break;
                }
                case REMOVING_to_REMOVED: {
                    log.infoUnbindJndiResource(jndiName);
                    break;
                }
                default:
                    break;
            }
        }
    }
}
