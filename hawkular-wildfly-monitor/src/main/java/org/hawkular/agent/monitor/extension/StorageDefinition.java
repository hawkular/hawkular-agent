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

import org.hawkular.agent.monitor.scheduler.config.SchedulerConfiguration;
import org.jboss.as.controller.AttributeDefinition;
import org.jboss.as.controller.PathElement;
import org.jboss.as.controller.PersistentResourceDefinition;
import org.jboss.as.controller.SimpleAttributeDefinition;
import org.jboss.as.controller.SimpleAttributeDefinitionBuilder;
import org.jboss.as.controller.operations.validation.EnumValidator;
import org.jboss.as.controller.registry.AttributeAccess;
import org.jboss.as.controller.registry.OperationEntry.Flag;
import org.jboss.dmr.ModelNode;
import org.jboss.dmr.ModelType;

public class StorageDefinition extends PersistentResourceDefinition {

    public static final StorageDefinition INSTANCE = new StorageDefinition();

    static final String STORAGE_ADAPTER = "storage-adapter";

    static final SimpleAttributeDefinition TYPE = new SimpleAttributeDefinitionBuilder("type",
            ModelType.STRING)
            .setAllowNull(false)
            .setAllowExpression(true)
            .setDefaultValue(new ModelNode(SchedulerConfiguration.StorageReportTo.HAWKULAR.name()))
            .setValidator(EnumValidator.create(SchedulerConfiguration.StorageReportTo.class, false, true))
            .addFlag(AttributeAccess.Flag.RESTART_RESOURCE_SERVICES)
            .build();

    static final SimpleAttributeDefinition TENANT_ID = new SimpleAttributeDefinitionBuilder("tenantId",
            ModelType.STRING)
            .setAllowNull(false)
            .setAllowExpression(true)
            .setDefaultValue(new ModelNode("test"))
            .addFlag(AttributeAccess.Flag.RESTART_RESOURCE_SERVICES)
            .build();

    static final SimpleAttributeDefinition URL = new SimpleAttributeDefinitionBuilder("url",
            ModelType.STRING)
            .setAllowNull(false)
            .setAllowExpression(true)
            .addFlag(AttributeAccess.Flag.RESTART_RESOURCE_SERVICES)
            .build();

    static final SimpleAttributeDefinition BUS_CONTEXT = new SimpleAttributeDefinitionBuilder("busContext",
            ModelType.STRING)
            .setAllowNull(true)
            .setAllowExpression(true)
            .setDefaultValue(new ModelNode("/hawkular-bus/message/"))
            .addFlag(AttributeAccess.Flag.RESTART_RESOURCE_SERVICES)
            .build();

    static final SimpleAttributeDefinition INVENTORY_CONTEXT = new SimpleAttributeDefinitionBuilder(
            "inventoryContext",
            ModelType.STRING)
            .setAllowNull(true)
            .setAllowExpression(true)
            .setDefaultValue(new ModelNode("/hawkular-inventory/"))
            .addFlag(AttributeAccess.Flag.RESTART_RESOURCE_SERVICES)
            .build();

    static final SimpleAttributeDefinition METRICS_CONTEXT = new SimpleAttributeDefinitionBuilder("metricsContext",
            ModelType.STRING)
            .setAllowNull(true)
            .setAllowExpression(true)
            .setDefaultValue(new ModelNode("/hawkular-metrics/"))
            .addFlag(AttributeAccess.Flag.RESTART_RESOURCE_SERVICES)
            .build();

    static final SimpleAttributeDefinition USER = new SimpleAttributeDefinitionBuilder("user",
            ModelType.STRING)
            .setAllowNull(true)
            .setAllowExpression(true)
            .addFlag(AttributeAccess.Flag.RESTART_RESOURCE_SERVICES)
            .build();

    static final SimpleAttributeDefinition PASSWORD = new SimpleAttributeDefinitionBuilder("password",
            ModelType.STRING)
            .setAllowNull(true)
            .setAllowExpression(true)
            .addFlag(AttributeAccess.Flag.RESTART_RESOURCE_SERVICES)
            .build();

    static final AttributeDefinition[] ATTRIBUTES = {
            TYPE, TENANT_ID, URL, BUS_CONTEXT, INVENTORY_CONTEXT, METRICS_CONTEXT, USER, PASSWORD
    };

    private StorageDefinition() {
        super(PathElement.pathElement(STORAGE_ADAPTER, "default"),
                SubsystemExtension.getResourceDescriptionResolver(STORAGE_ADAPTER),
                StorageAdd.INSTANCE,
                StorageRemove.INSTANCE,
                Flag.RESTART_RESOURCE_SERVICES,
                Flag.RESTART_RESOURCE_SERVICES);
    }

    @Override
    public Collection<AttributeDefinition> getAttributes() {
        return Arrays.asList(ATTRIBUTES);
    }
}
