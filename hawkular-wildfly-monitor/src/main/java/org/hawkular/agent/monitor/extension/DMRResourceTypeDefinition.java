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

import org.jboss.as.controller.AttributeDefinition;
import org.jboss.as.controller.PathElement;
import org.jboss.as.controller.PersistentResourceDefinition;
import org.jboss.as.controller.SimpleAttributeDefinition;
import org.jboss.as.controller.SimpleAttributeDefinitionBuilder;
import org.jboss.as.controller.registry.AttributeAccess;
import org.jboss.as.controller.registry.OperationEntry.Flag;
import org.jboss.dmr.ModelType;

public class DMRResourceTypeDefinition extends PersistentResourceDefinition {

    public static final DMRResourceTypeDefinition INSTANCE = new DMRResourceTypeDefinition();

    static final String RESOURCE_TYPE = "resource-type-dmr";

    static final SimpleAttributeDefinition PATH = new SimpleAttributeDefinitionBuilder("path",
            ModelType.STRING)
            .setAllowNull(false)
            .setAllowExpression(true)
            .addFlag(AttributeAccess.Flag.RESTART_RESOURCE_SERVICES)
            .build();

    static final SimpleAttributeDefinition RESOURCE_NAME_TEMPLATE = new SimpleAttributeDefinitionBuilder(
            "resourceNameTemplate", ModelType.STRING)
            .setAllowNull(false)
            .setAllowExpression(true)
            .addFlag(AttributeAccess.Flag.RESTART_RESOURCE_SERVICES)
            .build();

    static final SimpleAttributeDefinition PARENTS = new SimpleAttributeDefinitionBuilder("parents",
            ModelType.STRING)
            .setAllowNull(true)
            .setAllowExpression(true)
            .addFlag(AttributeAccess.Flag.RESTART_RESOURCE_SERVICES)
            .build();

    static final SimpleAttributeDefinition METRIC_SETS = new SimpleAttributeDefinitionBuilder("metricSets",
            ModelType.STRING)
            .setAllowNull(true)
            .setAllowExpression(true)
            .addFlag(AttributeAccess.Flag.RESTART_RESOURCE_SERVICES)
            .build();

    static final SimpleAttributeDefinition AVAIL_SETS = new SimpleAttributeDefinitionBuilder("availSets",
            ModelType.STRING)
            .setAllowNull(true)
            .setAllowExpression(true)
            .addFlag(AttributeAccess.Flag.RESTART_RESOURCE_SERVICES)
            .build();

    static final AttributeDefinition[] ATTRIBUTES = {
            RESOURCE_NAME_TEMPLATE,
            PATH,
            PARENTS,
            METRIC_SETS,
            AVAIL_SETS
    };

    private DMRResourceTypeDefinition() {
        super(PathElement.pathElement(RESOURCE_TYPE),
                SubsystemExtension.getResourceDescriptionResolver(DMRResourceTypeSetDefinition.RESOURCE_TYPE_SET,
                        RESOURCE_TYPE),
                DMRResourceTypeAdd.INSTANCE,
                DMRResourceTypeRemove.INSTANCE,
                Flag.RESTART_RESOURCE_SERVICES,
                Flag.RESTART_RESOURCE_SERVICES);
    }

    @Override
    public Collection<AttributeDefinition> getAttributes() {
        return Arrays.asList(ATTRIBUTES);
    }
}
