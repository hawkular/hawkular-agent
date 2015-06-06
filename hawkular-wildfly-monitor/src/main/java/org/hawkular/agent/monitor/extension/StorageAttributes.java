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

import org.jboss.as.controller.AttributeDefinition;
import org.jboss.as.controller.SimpleAttributeDefinition;
import org.jboss.as.controller.SimpleAttributeDefinitionBuilder;
import org.jboss.as.controller.operations.validation.EnumValidator;
import org.jboss.as.controller.registry.AttributeAccess;
import org.jboss.dmr.ModelNode;
import org.jboss.dmr.ModelType;

public interface StorageAttributes {

    SimpleAttributeDefinition TYPE = new SimpleAttributeDefinitionBuilder("type",
            ModelType.STRING)
            .setAllowNull(false)
            .setAllowExpression(true)
            .setDefaultValue(new ModelNode(MonitorServiceConfiguration.StorageReportTo.HAWKULAR.name()))
            .setValidator(EnumValidator.create(MonitorServiceConfiguration.StorageReportTo.class, false, true))
            .addFlag(AttributeAccess.Flag.RESTART_RESOURCE_SERVICES)
            .build();

    SimpleAttributeDefinition USERNAME = new SimpleAttributeDefinitionBuilder("username",
            ModelType.STRING)
            .setAllowNull(false)
            .setAllowExpression(true)
            .addFlag(AttributeAccess.Flag.RESTART_RESOURCE_SERVICES)
            .build();

    SimpleAttributeDefinition PASSWORD = new SimpleAttributeDefinitionBuilder("password",
            ModelType.STRING)
            .setAllowNull(false)
            .setAllowExpression(true)
            .addFlag(AttributeAccess.Flag.RESTART_RESOURCE_SERVICES)
            .build();

    SimpleAttributeDefinition TENANT_ID = new SimpleAttributeDefinitionBuilder("tenantId",
            ModelType.STRING)
            .setAllowNull(true)
            .setAllowExpression(true)
            .addFlag(AttributeAccess.Flag.RESTART_RESOURCE_SERVICES)
            .build();

    SimpleAttributeDefinition URL = new SimpleAttributeDefinitionBuilder("url",
            ModelType.STRING)
            .setAllowNull(true)
            .setAllowExpression(true)
            .addFlag(AttributeAccess.Flag.RESTART_RESOURCE_SERVICES)
            .build();

    SimpleAttributeDefinition SERVER_OUTBOUND_SOCKET_BINDING_REF = new SimpleAttributeDefinitionBuilder(
            "serverOutboundSocketBindingRef",
            ModelType.STRING)
            .setAllowNull(true)
            .setAllowExpression(true)
            .addFlag(AttributeAccess.Flag.RESTART_RESOURCE_SERVICES)
            .build();

    SimpleAttributeDefinition BUS_CONTEXT = new SimpleAttributeDefinitionBuilder("busContext",
            ModelType.STRING)
            .setAllowNull(true)
            .setAllowExpression(true)
            .setDefaultValue(new ModelNode("/hawkular-bus/message/"))
            .addFlag(AttributeAccess.Flag.RESTART_RESOURCE_SERVICES)
            .build();

    SimpleAttributeDefinition INVENTORY_CONTEXT = new SimpleAttributeDefinitionBuilder(
            "inventoryContext",
            ModelType.STRING)
            .setAllowNull(true)
            .setAllowExpression(true)
            .setDefaultValue(new ModelNode("/hawkular/inventory/"))
            .addFlag(AttributeAccess.Flag.RESTART_RESOURCE_SERVICES)
            .build();

    SimpleAttributeDefinition METRICS_CONTEXT = new SimpleAttributeDefinitionBuilder("metricsContext",
            ModelType.STRING)
            .setAllowNull(true)
            .setAllowExpression(true)
            .setDefaultValue(new ModelNode("/hawkular-metrics/"))
            .addFlag(AttributeAccess.Flag.RESTART_RESOURCE_SERVICES)
            .build();

    AttributeDefinition[] ATTRIBUTES = {
            TYPE,
            USERNAME,
            PASSWORD,
            TENANT_ID,
            URL,
            SERVER_OUTBOUND_SOCKET_BINDING_REF,
            BUS_CONTEXT,
            INVENTORY_CONTEXT,
            METRICS_CONTEXT
    };
}