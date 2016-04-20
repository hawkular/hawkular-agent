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

import org.jboss.as.controller.Extension;
import org.jboss.as.controller.ExtensionContext;
import org.jboss.as.controller.SubsystemRegistration;
import org.jboss.as.controller.descriptions.StandardResourceDescriptionResolver;
import org.jboss.as.controller.operations.common.GenericSubsystemDescribeHandler;
import org.jboss.as.controller.parsing.ExtensionParsingContext;
import org.jboss.as.controller.registry.ManagementResourceRegistration;
import org.jboss.msc.service.ServiceName;

public class SubsystemExtension implements Extension {

    static final String SUBSYSTEM_NAME = "hawkular-wildfly-agent";
    static final String NAMESPACE = "urn:org.hawkular.agent:agent:1.0";
    static final int MAJOR_VERSION = 1;
    static final int MINOR_VERSION = 0;
    static final int MICRO_VERSION = 0;
    static final ServiceName SERVICE_NAME = ServiceName.of("org.hawkular.agent.").append(SUBSYSTEM_NAME);

    private static final String RESOURCE_NAME = SubsystemExtension.class.getPackage().getName() + ".LocalDescriptions";

    static StandardResourceDescriptionResolver getResourceDescriptionResolver(final String... keyPrefix) {
        StringBuilder prefix = new StringBuilder(SUBSYSTEM_NAME);
        if (keyPrefix != null) {
            for (String kp : keyPrefix) {
                prefix.append('.').append(kp);
            }
        }
        return new StandardResourceDescriptionResolver(prefix.toString(), RESOURCE_NAME,
                SubsystemExtension.class.getClassLoader(), true, false);
    }

    @Override
    public void initializeParsers(ExtensionParsingContext context) {
        context.setSubsystemXmlMapping(SUBSYSTEM_NAME, NAMESPACE, SubsystemParser.INSTANCE);
    }

    @Override
    public void initialize(ExtensionContext context) {
        final SubsystemRegistration subsystem = context.registerSubsystem(SUBSYSTEM_NAME,
                MAJOR_VERSION, MINOR_VERSION, MICRO_VERSION);

        // This subsystem should be runnable on a host
        subsystem.setHostCapable();

        final ManagementResourceRegistration registration = subsystem
                .registerSubsystemModel(SubsystemDefinition.INSTANCE);

        // add the "describe" operation we should always have
        registration.registerOperationHandler(GenericSubsystemDescribeHandler.DEFINITION,
                GenericSubsystemDescribeHandler.INSTANCE);

        subsystem.registerXMLElementWriter(SubsystemParser.INSTANCE);
    }
}
