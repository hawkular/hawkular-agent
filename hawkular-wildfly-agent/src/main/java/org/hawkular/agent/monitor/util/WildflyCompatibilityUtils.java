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
package org.hawkular.agent.monitor.util;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.List;

import org.jboss.as.controller.AttributeDefinition;
import org.jboss.as.controller.OperationContext;
import org.jboss.as.controller.OperationFailedException;
import org.jboss.as.controller.PathAddress;
import org.jboss.as.controller.PathElement;
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

    public static Injector<ManagedReferenceFactory> getManagedObjectInjectorFromBinderService(BinderService binderService) {
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

    public static Injector<ServiceBasedNamingStore> getNamingStoreInjectorFromBinderService(BinderService binderService) {
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

    /**
     * PathAddress methods found on wildfly-core
     * https://github.com/wildfly/wildfly-core/blob/588145a00ca5ed3beb0027f8f44be87db6689db3/controller/src/main/java/org/jboss/as/controller/PathAddress.java
     */
    public static PathAddress parseCLIStyleAddress(String address) throws IllegalArgumentException {
        try {
            return PathAddress.parseCLIStyleAddress(address);
        } catch (NoSuchMethodError _nsme) {
            // jboss-as doesn't have parseCLIStyleAddress
            // Ignore the error and use the provided alternative
            return _parseCLIStyleAddress(address);
        }
    }

    private static PathAddress _parseCLIStyleAddress(String address) throws IllegalArgumentException {
        PathAddress parsedAddress = PathAddress.EMPTY_ADDRESS;
        if (address == null || address.trim().isEmpty()) {
            return parsedAddress;
        }
        String trimmedAddress = address.trim();
        if (trimmedAddress.charAt(0) != '/' || !Character.isAlphabetic(trimmedAddress.charAt(1))) {
            // https://github.com/wildfly/wildfly-core/blob/588145a00ca5ed3beb0027f8f44be87db6689db3/controller/src/main/java/org/jboss/as/controller/logging/ControllerLogger.java#L3296-L3297
            throw new IllegalArgumentException(
                    "Illegal path address '" + address + "' , it is not in a correct CLI format");
        }
        char[] characters = address.toCharArray();
        boolean escaped = false;
        StringBuilder keyBuffer = new StringBuilder();
        StringBuilder valueBuffer = new StringBuilder();
        StringBuilder currentBuffer = keyBuffer;
        for (int i = 1; i < characters.length; i++) {
            switch (characters[i]) {
                case '/':
                    if (escaped) {
                        escaped = false;
                        currentBuffer.append(characters[i]);
                    } else {
                        parsedAddress = addpathAddressElement(parsedAddress, address, keyBuffer, valueBuffer);
                        keyBuffer = new StringBuilder();
                        valueBuffer = new StringBuilder();
                        currentBuffer = keyBuffer;
                    }
                    break;
                case '\\':
                    if (escaped) {
                        escaped = false;
                        currentBuffer.append(characters[i]);
                    } else {
                        escaped = true;
                    }
                    break;
                case '=':
                    if (escaped) {
                        escaped = false;
                        currentBuffer.append(characters[i]);
                    } else {
                        currentBuffer = valueBuffer;
                    }
                    break;
                default:
                    currentBuffer.append(characters[i]);
                    break;
            }
        }
        parsedAddress = addpathAddressElement(parsedAddress, address, keyBuffer, valueBuffer);
        return parsedAddress;
    }

    private static PathAddress addpathAddressElement(PathAddress parsedAddress, String address, StringBuilder keyBuffer, StringBuilder valueBuffer) {
        if (keyBuffer.length() > 0) {
            if (valueBuffer.length() > 0) {
                return parsedAddress.append(PathElement.pathElement(keyBuffer.toString(), valueBuffer.toString()));
            }
            // https://github.com/wildfly/wildfly-core/blob/588145a00ca5ed3beb0027f8f44be87db6689db3/controller/src/main/java/org/jboss/as/controller/logging/ControllerLogger.java#L3296-L3297
            throw new IllegalArgumentException(
                    "Illegal path address '" + address + "' , it is not in a correct CLI format");
        }
        return parsedAddress;
    }

    public static String getCurrentAddressValue(OperationContext context, ModelNode operation) {
        try {
            return context.getCurrentAddressValue();
        } catch (NoSuchMethodError _nsme) {
            return PathAddress.pathAddress(
                    operation.require(ModelDescriptionConstants.OP_ADDR)
            ).getLastElement().getValue();
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
}
