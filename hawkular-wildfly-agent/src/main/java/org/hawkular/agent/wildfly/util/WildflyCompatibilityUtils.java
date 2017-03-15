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

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.Collection;
import java.util.List;

import org.hawkular.agent.monitor.extension.MonitorServiceRestartParentAttributeHandler;
import org.hawkular.inventory.api.Log;
import org.jboss.as.controller.AttributeDefinition;
import org.jboss.as.controller.OperationContext;
import org.jboss.as.controller.OperationFailedException;
import org.jboss.as.controller.PathAddress;
import org.jboss.as.controller.ServiceVerificationHandler;
import org.jboss.as.controller.SubsystemRegistration;
import org.jboss.as.controller.descriptions.ModelDescriptionConstants;
import org.jboss.as.naming.ImmediateManagedReferenceFactory;
import org.jboss.as.naming.ManagedReference;
import org.jboss.as.naming.ManagedReferenceFactory;
import org.jboss.as.naming.ServiceBasedNamingStore;
import org.jboss.as.naming.service.BinderService;
import org.jboss.as.network.OutboundSocketBinding;
import org.jboss.dmr.ModelNode;
import org.jboss.msc.inject.Injector;
import org.jboss.msc.service.ServiceController;

/**
 * Utilities that are required to help run when the JVM is loaded with older EAP6 specific jars.
 * These methods require access to server-side jars that may not be found on the client - so be careful what methods
 * you put in here.
 */
public class WildflyCompatibilityUtils {

    public static InetAddress outboundSocketBindingGetResolvedDestinationAddress(OutboundSocketBinding serverBinding)
            throws UnknownHostException {
        try {
            return serverBinding.getResolvedDestinationAddress();
        } catch (NoSuchMethodError _nsme) {
            // When using jboss-as we need to use the now deprecated getDestinationAddress
            return serverBinding.getDestinationAddress();
        }
    }

    public static void subsystemSetHostCapable(SubsystemRegistration subsystem) {
        try {
            subsystem.setHostCapable();
        } catch (NoSuchMethodError _nsme) {
            // This method doesn't exist on jboss-as
            // We should detect if we are running on domain mode and warn that we can not
            // use hawkular-agent on domain mode
        }
    }

    public static ManagedReferenceFactory getImmediateManagedReferenceFactory(Object jndiObject) {
        try {
            return new ImmediateManagedReferenceFactory(jndiObject);
        } catch (NoClassDefFoundError _ncdfe) {
            // This class does not exist on jboss-as.
            return new EAP6ImmediateManagedReferenceFactory(jndiObject);
        }
    }

    public static Injector<ManagedReferenceFactory> getManagedObjectInjectorFromBinderService(
            BinderService binderService) {
        try {
            return binderService.getManagedObjectInjector();
        } catch (NoSuchMethodError _nsme) {
            // Since wildfly it returns a InjectedValue<*> (which is still an Injector<*>)
            // but the runtime (when running jboss-as) excepts a method that returns the first.
            // Use reflection to fetch it.
            try {
                Method getManagedObjectInjectorMethod = BinderService.class.getMethod("getManagedObjectInjector");
                return (Injector<ManagedReferenceFactory>) getManagedObjectInjectorMethod.invoke(binderService);
            } catch (NoSuchMethodException e) {
                throw new RuntimeException(e);
            } catch (IllegalAccessException e) {
                throw new RuntimeException(e);
            } catch (InvocationTargetException e) {
                throw new RuntimeException(e);
            }
        }
    }

    public static Injector<ServiceBasedNamingStore> getNamingStoreInjectorFromBinderService(
            BinderService binderService) {
        try {
            return binderService.getNamingStoreInjector();
        } catch (NoSuchMethodError _nsme) {
            // See getManagedObjectInjectorFromBinderService comment on this exception for details.
            try {
                Method getManagedObjectInjectorMethod = BinderService.class.getMethod("getNamingStoreInjector");
                return (Injector<ServiceBasedNamingStore>) getManagedObjectInjectorMethod.invoke(binderService);
            } catch (NoSuchMethodException e) {
                throw new RuntimeException(e);
            } catch (IllegalAccessException e) {
                throw new RuntimeException(e);
            } catch (InvocationTargetException e) {
                throw new RuntimeException(e);
            }
        }
    }

    public static PathAddress getCurrentAddress(OperationContext context, ModelNode operation) {
        try {
            return context.getCurrentAddress();
        } catch (NoSuchMethodError _nsme) {
            return PathAddress.pathAddress(
                    operation.require(ModelDescriptionConstants.OP_ADDR));
        }
    }

    public static String getCurrentAddressValue(OperationContext context, ModelNode operation) {
        try {
            return context.getCurrentAddressValue();
        } catch (NoSuchMethodError _nsme) {
            return PathAddress.pathAddress(
                    operation.require(ModelDescriptionConstants.OP_ADDR)).getLastElement().getValue();
        }
    }

    /**
     * ManagedReferenceFactory based on:
     * https://github.com/wildfly/wildfly/blob/c6f48cac0710457d9374eb24cc82093899bdd7bf/naming/src/main/java/org/jboss/as/naming/ImmediateManagedReferenceFactory.java
     */
    private static class EAP6ImmediateManagedReferenceFactory implements ManagedReferenceFactory {
        private final ManagedReference reference;

        public EAP6ImmediateManagedReferenceFactory(final Object instance) {
            this.reference = new EAP6ImmediateManagedReference(instance);
        }

        @Override
        public ManagedReference getReference() {
            return this.reference;
        }
    }

    /**
     * ManagedReference based on:
     * https://github.com/wildfly/wildfly/blob/2b6526250242810b9075c3dc79c5f06e6ea8cfe4/naming/src/main/java/org/jboss/as/naming/ImmediateManagedReference.java
     */
    private static class EAP6ImmediateManagedReference implements ManagedReference {
        private final Object instance;

        public EAP6ImmediateManagedReference(final Object instance) {
            this.instance = instance;
        }

        @Override
        public void release() {

        }

        @Override
        public Object getInstance() {
            return this.instance;
        }
    }

    public static class AbstractAddStepHandler extends org.jboss.as.controller.AbstractAddStepHandler {

        public AbstractAddStepHandler(AttributeDefinition... attributes) {
            super(attributes);
        }

        @Override
        protected void performRuntime(OperationContext context, ModelNode operation, ModelNode model,
                ServiceVerificationHandler verificationHandler,
                List<ServiceController<?>> newControllers) throws OperationFailedException {
            performRuntime(context, operation, model);
        }

        @Override
        protected void performRuntime(OperationContext context, ModelNode operation, ModelNode model)
                throws OperationFailedException {
        }
    }

    public static class EAP6MonitorServiceRestartParentAttributeHandler
            extends MonitorServiceRestartParentAttributeHandler {

        public EAP6MonitorServiceRestartParentAttributeHandler(AttributeDefinition... definitions) {
            super(definitions);
        }

        public EAP6MonitorServiceRestartParentAttributeHandler(Collection<AttributeDefinition> definitions) {
            super(definitions.toArray(new AttributeDefinition[definitions.size()]));
        }

        @Override
        @SuppressWarnings("deprecation")
        protected void recreateParentService(OperationContext context, PathAddress parentAddress,
                ModelNode parentModel, ServiceVerificationHandler verificationHandler)
                throws OperationFailedException {
            recreateParentService(context, parentAddress, parentModel);
        }

    }

    public static void operationContextStepCompleted(OperationContext context) {
        // This method is now deprecated, but we are required to call it under EAP6.4 to mark an Step
        // as completed else we will get: "Operation handler failed to complete"
        // Since is deprecated we would like to know if it still exists before calling it.
        Method stepCompletedMethod = null;
        try {
            stepCompletedMethod = OperationContext.class.getMethod("stepCompleted");
            stepCompletedMethod.invoke(context);
        } catch (ReflectiveOperationException roe) {
            if (stepCompletedMethod != null) {
                // The method exists, but for some reason we couldn't invoke it. If we are on EAP6.4 we are most
                // likely see errors.
                Log.LOGGER.warn("We couldn't execute stepCompleted", roe);
            } else {
                // We couldn't find this method, lets just ignore this exception.
            }
        }
    }
}
