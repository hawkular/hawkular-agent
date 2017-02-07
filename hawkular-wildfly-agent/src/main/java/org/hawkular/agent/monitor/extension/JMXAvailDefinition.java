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

import java.util.Arrays;
import java.util.Collection;

import org.jboss.as.controller.AttributeDefinition;
import org.jboss.as.controller.PathElement;
import org.jboss.as.controller.registry.OperationEntry.Flag;

public class JMXAvailDefinition extends MonitorPersistentResourceDefinition {

    public static final JMXAvailDefinition INSTANCE = new JMXAvailDefinition();

    static final String AVAIL = "avail-jmx";

    private JMXAvailDefinition() {
        super(PathElement.pathElement(AVAIL),
                SubsystemExtension.getResourceDescriptionResolver(JMXAvailSetDefinition.AVAIL_SET, AVAIL),
                JMXAvailAdd.INSTANCE,
                JMXAvailRemove.INSTANCE,
                Flag.RESTART_RESOURCE_SERVICES,
                Flag.RESTART_RESOURCE_SERVICES);
    }

    @Override
    public Collection<AttributeDefinition> getAttributes() {
        return Arrays.asList(JMXAvailAttributes.ATTRIBUTES);
    }
}
