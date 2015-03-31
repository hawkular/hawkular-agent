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
import java.util.Collection;
import java.util.concurrent.TimeUnit;

import org.hawkular.agent.monitor.scheduler.config.SchedulerConfiguration;
import org.jboss.as.controller.AttributeDefinition;
import org.jboss.as.controller.PathElement;
import org.jboss.as.controller.PersistentResourceDefinition;
import org.jboss.as.controller.SimpleAttributeDefinition;
import org.jboss.as.controller.SimpleAttributeDefinitionBuilder;
import org.jboss.as.controller.operations.validation.EnumValidator;
import org.jboss.as.controller.operations.validation.TimeUnitValidator;
import org.jboss.as.controller.registry.AttributeAccess;
import org.jboss.as.controller.registry.OperationEntry.Flag;
import org.jboss.dmr.ModelNode;
import org.jboss.dmr.ModelType;

public class DiagnosticsDefinition extends PersistentResourceDefinition {

    public static final DiagnosticsDefinition INSTANCE = new DiagnosticsDefinition();

    static final String DIAGNOSTICS = "diagnostics";

    static final SimpleAttributeDefinition ENABLED = new SimpleAttributeDefinitionBuilder("enabled",
            ModelType.BOOLEAN)
            .setAllowNull(true)
            .setDefaultValue(new ModelNode(true))
            .setAllowExpression(true)
            .addFlag(AttributeAccess.Flag.RESTART_RESOURCE_SERVICES)
            .build();

    static final SimpleAttributeDefinition INTERVAL = new SimpleAttributeDefinitionBuilder("interval",
            ModelType.INT)
            .setAllowNull(true)
            .setDefaultValue(new ModelNode(5))
            .setAllowExpression(true)
            .addFlag(AttributeAccess.Flag.RESTART_RESOURCE_SERVICES)
            .build();

    static final SimpleAttributeDefinition TIME_UNITS = new SimpleAttributeDefinitionBuilder("timeUnits",
            ModelType.STRING)
            .setAllowNull(true)
            .setDefaultValue(new ModelNode(TimeUnit.MINUTES.name()))
            .setAllowExpression(true)
            .setValidator(new TimeUnitValidator(true, true, TimeUnit.MILLISECONDS, TimeUnit.SECONDS,
                    TimeUnit.MINUTES))
            .addFlag(AttributeAccess.Flag.RESTART_RESOURCE_SERVICES)
            .build();

    static final SimpleAttributeDefinition REPORT_TO = new SimpleAttributeDefinitionBuilder("reportTo",
            ModelType.STRING)
            .setAllowNull(false)
            .setAllowExpression(true)
            .setDefaultValue(new ModelNode(SchedulerConfiguration.DiagnosticsReportTo.LOG.name()))
            .setValidator(EnumValidator.create(SchedulerConfiguration.DiagnosticsReportTo.class, false, true))
            .addFlag(AttributeAccess.Flag.RESTART_RESOURCE_SERVICES)
            .build();

    static final AttributeDefinition[] ATTRIBUTES = {
            ENABLED, INTERVAL, TIME_UNITS, REPORT_TO
    };

    private DiagnosticsDefinition() {
        super(PathElement.pathElement(DIAGNOSTICS, "default"),
                SubsystemExtension.getResourceDescriptionResolver(DIAGNOSTICS),
                DiagnosticsAdd.INSTANCE,
                DiagnosticsRemove.INSTANCE,
                Flag.RESTART_RESOURCE_SERVICES,
                Flag.RESTART_RESOURCE_SERVICES);
    }

    @Override
    public Collection<AttributeDefinition> getAttributes() {
        return Arrays.asList(ATTRIBUTES);
    }
}
