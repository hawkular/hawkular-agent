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

import org.jboss.as.controller.AttributeDefinition;
import org.jboss.as.controller.SimpleAttributeDefinition;
import org.jboss.as.controller.SimpleAttributeDefinitionBuilder;
import org.jboss.as.controller.registry.AttributeAccess;
import org.jboss.dmr.ModelNode;
import org.jboss.dmr.ModelType;

public interface PlatformAttributes {

    SimpleAttributeDefinition ENABLED = new SimpleAttributeDefinitionBuilder("enabled",
            ModelType.BOOLEAN)
                    .setAllowNull(true)
                    .setDefaultValue(new ModelNode(true))
                    .setAllowExpression(true)
                    .addFlag(AttributeAccess.Flag.RESTART_NONE)
                    .build();

    SimpleAttributeDefinition MACHINE_ID = new SimpleAttributeDefinitionBuilder("machineId",
            ModelType.STRING)
                    .setAllowNull(true)
                    .setAllowExpression(true)
                    .addFlag(AttributeAccess.Flag.RESTART_RESOURCE_SERVICES)
                    .build();

    AttributeDefinition[] ATTRIBUTES = {
            ENABLED,
            MACHINE_ID
    };
}