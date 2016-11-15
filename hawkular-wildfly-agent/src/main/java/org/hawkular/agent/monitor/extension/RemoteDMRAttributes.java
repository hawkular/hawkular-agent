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

import org.hawkular.agent.monitor.api.Avail;
import org.jboss.as.controller.AttributeDefinition;
import org.jboss.as.controller.SimpleAttributeDefinition;
import org.jboss.as.controller.SimpleAttributeDefinitionBuilder;
import org.jboss.as.controller.operations.validation.EnumValidator;
import org.jboss.as.controller.registry.AttributeAccess;
import org.jboss.dmr.ModelNode;
import org.jboss.dmr.ModelType;

public interface RemoteDMRAttributes {

    SimpleAttributeDefinition ENABLED = new SimpleAttributeDefinitionBuilder("enabled",
            ModelType.BOOLEAN)
                    .setAllowNull(true)
                    .setDefaultValue(new ModelNode(true))
                    .setAllowExpression(true)
                    .addFlag(AttributeAccess.Flag.RESTART_NONE)
                    .build();

    SimpleAttributeDefinition PROTOCOL = new SimpleAttributeDefinitionBuilder("protocol",
            ModelType.STRING)
                    .setAllowNull(true)
                    .setAllowExpression(true)
                    .addFlag(AttributeAccess.Flag.RESTART_RESOURCE_SERVICES)
                    .build();

    SimpleAttributeDefinition HOST = new SimpleAttributeDefinitionBuilder("host",
            ModelType.STRING)
                    .setAllowNull(false)
                    .setAllowExpression(true)
                    .addFlag(AttributeAccess.Flag.RESTART_RESOURCE_SERVICES)
                    .build();

    SimpleAttributeDefinition PORT = new SimpleAttributeDefinitionBuilder("port",
            ModelType.INT)
                    .setAllowNull(false)
                    .setAllowExpression(true)
                    .addFlag(AttributeAccess.Flag.RESTART_RESOURCE_SERVICES)
                    .build();

    SimpleAttributeDefinition USERNAME = new SimpleAttributeDefinitionBuilder("username",
            ModelType.STRING)
                    .setAllowNull(true)
                    .setAllowExpression(true)
                    .addFlag(AttributeAccess.Flag.RESTART_RESOURCE_SERVICES)
                    .build();

    SimpleAttributeDefinition PASSWORD = new SimpleAttributeDefinitionBuilder("password",
            ModelType.STRING)
                    .setAllowNull(true)
                    .setAllowExpression(true)
                    .addFlag(AttributeAccess.Flag.RESTART_RESOURCE_SERVICES)
                    .build();

    SimpleAttributeDefinition USE_SSL = new SimpleAttributeDefinitionBuilder("use-ssl",
            ModelType.BOOLEAN)
                    .setAllowNull(true)
                    .setAllowExpression(true)
                    .addFlag(AttributeAccess.Flag.RESTART_RESOURCE_SERVICES)
                    .build();

    SimpleAttributeDefinition SECURITY_REALM = new SimpleAttributeDefinitionBuilder("security-realm",
            ModelType.STRING)
                    .setAllowNull(true)
                    .setAllowExpression(true)
                    .addFlag(AttributeAccess.Flag.RESTART_RESOURCE_SERVICES)
                    .build();

    SimpleAttributeDefinition SET_AVAIL_ON_SHUTDOWN = new SimpleAttributeDefinitionBuilder("set-avail-on-shutdown",
            ModelType.STRING)
                    .setAllowNull(true)
                    .setValidator(EnumValidator.create(Avail.class, true, true))
                    .setAllowExpression(true)
                    .addFlag(AttributeAccess.Flag.RESTART_RESOURCE_SERVICES)
                    .build();

    SimpleAttributeDefinition RESOURCE_TYPE_SETS = new SimpleAttributeDefinitionBuilder("resource-type-sets",
            ModelType.STRING)
                    .setAllowNull(true)
                    .setAllowExpression(true)
                    .addFlag(AttributeAccess.Flag.RESTART_RESOURCE_SERVICES)
                    .build();

    SimpleAttributeDefinition TENANT_ID = new SimpleAttributeDefinitionBuilder("tenant-id",
            ModelType.STRING)
                    .setAllowNull(true)
                    .setAllowExpression(true)
                    .addFlag(AttributeAccess.Flag.RESTART_RESOURCE_SERVICES)
                    .build();

    SimpleAttributeDefinition METRIC_ID_TEMPLATE = new SimpleAttributeDefinitionBuilder("metric-id-template",
            ModelType.STRING)
                    .setAllowNull(true)
                    .setAllowExpression(true)
                    .addFlag(AttributeAccess.Flag.RESTART_RESOURCE_SERVICES)
                    .build();

    SimpleAttributeDefinition METRIC_TAGS = new SimpleAttributeDefinitionBuilder("metric-tags",
            ModelType.STRING)
                    .setAllowNull(true)
                    .setAllowExpression(true)
                    .addFlag(AttributeAccess.Flag.RESTART_RESOURCE_SERVICES)
                    .build();

    AttributeDefinition[] ATTRIBUTES = {
            ENABLED,
            PROTOCOL,
            HOST,
            PORT,
            USERNAME,
            PASSWORD,
            USE_SSL,
            SECURITY_REALM,
            SET_AVAIL_ON_SHUTDOWN,
            RESOURCE_TYPE_SETS,
            TENANT_ID,
            METRIC_ID_TEMPLATE,
            METRIC_TAGS
    };
}