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

import org.hawkular.agent.monitor.config.AgentCoreEngineConfiguration;
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
                    .setAllowNull(true)
                    .setAllowExpression(true)
                    .setDefaultValue(new ModelNode(AgentCoreEngineConfiguration.StorageReportTo.HAWKULAR.name()))
                    .setValidator(EnumValidator.create(AgentCoreEngineConfiguration.StorageReportTo.class, false, true))
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

    SimpleAttributeDefinition TENANT_ID = new SimpleAttributeDefinitionBuilder("tenant-id",
            ModelType.STRING)
                    .setAllowNull(true)
                    .setAllowExpression(true)
                    .setDefaultValue(new ModelNode("hawkular"))
                    .addFlag(AttributeAccess.Flag.RESTART_RESOURCE_SERVICES)
                    .build();

    SimpleAttributeDefinition FEED_ID = new SimpleAttributeDefinitionBuilder("feed-id",
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

    SimpleAttributeDefinition KEYSTORE_PATH = new SimpleAttributeDefinitionBuilder("keystore-path",
            ModelType.STRING)
                    .setAllowNull(true)
                    .setAllowExpression(true)
                    .addFlag(AttributeAccess.Flag.RESTART_RESOURCE_SERVICES)
                    .build();

    SimpleAttributeDefinition KEYSTORE_PASSWORD = new SimpleAttributeDefinitionBuilder("keystore-password",
            ModelType.STRING)
                    .setAllowNull(true)
                    .setAllowExpression(true)
                    .addFlag(AttributeAccess.Flag.RESTART_RESOURCE_SERVICES)
                    .build();

    SimpleAttributeDefinition SERVER_OUTBOUND_SOCKET_BINDING_REF = new SimpleAttributeDefinitionBuilder(
            "server-outbound-socket-binding-ref",
            ModelType.STRING)
                    .setAllowNull(true)
                    .setAllowExpression(true)
                    .addFlag(AttributeAccess.Flag.RESTART_RESOURCE_SERVICES)
                    .build();

    SimpleAttributeDefinition METRICS_CONTEXT = new SimpleAttributeDefinitionBuilder("metrics-context",
            ModelType.STRING)
                    .setAllowNull(true)
                    .setAllowExpression(true)
                    .setDefaultValue(new ModelNode("/hawkular/metrics/"))
                    .addFlag(AttributeAccess.Flag.RESTART_RESOURCE_SERVICES)
                    .build();

    SimpleAttributeDefinition FEEDCOMM_CONTEXT = new SimpleAttributeDefinitionBuilder("feedcomm-context",
            ModelType.STRING)
                    .setAllowNull(true)
                    .setAllowExpression(true)
                    .setDefaultValue(new ModelNode("/hawkular/command-gateway/"))
                    .addFlag(AttributeAccess.Flag.RESTART_RESOURCE_SERVICES)
                    .build();
    SimpleAttributeDefinition CONNECT_TIMEOUT_SECONDS = new SimpleAttributeDefinitionBuilder("connect-timeout-secs",
            ModelType.INT)
                    .setAllowNull(true)
                    .setAllowExpression(true)
                    .setDefaultValue(new ModelNode(10))
                    .addFlag(AttributeAccess.Flag.RESTART_RESOURCE_SERVICES)
                    .build();
    SimpleAttributeDefinition READ_TIMEOUT_SECONDS = new SimpleAttributeDefinitionBuilder("read-timeout-secs",
            ModelType.INT)
                    .setAllowNull(true)
                    .setAllowExpression(true)
                    .setDefaultValue(new ModelNode(120)) /* e.g. bulk inserts may take long */
                    .addFlag(AttributeAccess.Flag.RESTART_RESOURCE_SERVICES)
                    .build();

    AttributeDefinition[] ATTRIBUTES = {
            TYPE,
            USERNAME,
            PASSWORD,
            TENANT_ID,
            FEED_ID,
            URL,
            USE_SSL,
            SECURITY_REALM,
            KEYSTORE_PATH,
            KEYSTORE_PASSWORD,
            SERVER_OUTBOUND_SOCKET_BINDING_REF,
            METRICS_CONTEXT,
            FEEDCOMM_CONTEXT,
            CONNECT_TIMEOUT_SECONDS,
            READ_TIMEOUT_SECONDS
    };

}
