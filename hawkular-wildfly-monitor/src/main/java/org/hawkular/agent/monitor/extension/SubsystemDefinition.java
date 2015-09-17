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

import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.SUBSYSTEM;

import java.util.Arrays;
import java.util.Collection;
import java.util.List;

import org.jboss.as.controller.AttributeDefinition;
import org.jboss.as.controller.PathElement;
import org.jboss.as.controller.PersistentResourceDefinition;
import org.jboss.as.controller.SimpleAttributeDefinition;
import org.jboss.as.controller.SimpleAttributeDefinitionBuilder;
import org.jboss.as.controller.SimpleOperationDefinition;
import org.jboss.as.controller.SimpleOperationDefinitionBuilder;
import org.jboss.as.controller.registry.ManagementResourceRegistration;
import org.jboss.as.controller.registry.OperationEntry.Flag;
import org.jboss.dmr.ModelNode;
import org.jboss.dmr.ModelType;

public class SubsystemDefinition extends PersistentResourceDefinition {

    public static final SubsystemDefinition INSTANCE = new SubsystemDefinition();

    // OPERATIONS

    static final SimpleAttributeDefinition OPPARAM_RESTART = new SimpleAttributeDefinitionBuilder(
            "restart", ModelType.BOOLEAN)
            .setAllowExpression(true)
            .setDefaultValue(new ModelNode(false))
            .build();

    static final SimpleOperationDefinition OP_START = new SimpleOperationDefinitionBuilder(
            "start", SubsystemExtension.getResourceDescriptionResolver())
            .addParameter(OPPARAM_RESTART)
            .build();

    static final SimpleOperationDefinition OP_STOP = new SimpleOperationDefinitionBuilder(
            "stop", SubsystemExtension.getResourceDescriptionResolver())
            .build();

    static final SimpleOperationDefinition OP_STATUS = new SimpleOperationDefinitionBuilder(
            "status", SubsystemExtension.getResourceDescriptionResolver())
            .build();

    static final SimpleOperationDefinition OP_FULL_DISCOVERY_SCAN = new SimpleOperationDefinitionBuilder(
            "fullDiscoveryScan", SubsystemExtension.getResourceDescriptionResolver())
            .build();

    private SubsystemDefinition() {
        super(PathElement.pathElement(SUBSYSTEM, SubsystemExtension.SUBSYSTEM_NAME),
                SubsystemExtension.getResourceDescriptionResolver(),
                SubsystemAdd.INSTANCE,
                SubsystemRemove.INSTANCE,
                Flag.RESTART_RESOURCE_SERVICES,
                Flag.RESTART_RESOURCE_SERVICES);
    }

    @Override
    public Collection<AttributeDefinition> getAttributes() {
        return Arrays.asList(SubsystemAttributes.ATTRIBUTES);
    }

    @Override
    protected List<? extends PersistentResourceDefinition> getChildren() {
        return Arrays.asList(
                StorageDefinition.INSTANCE,
                DiagnosticsDefinition.INSTANCE,
                ManagedServersDefinition.INSTANCE,
                DMRResourceTypeSetDefinition.INSTANCE,
                DMRMetricSetDefinition.INSTANCE,
                DMRAvailSetDefinition.INSTANCE);
    }

    @Override
    public void registerOperations(ManagementResourceRegistration rr) {
        super.registerOperations(rr);

        rr.registerOperationHandler(OP_START, new OperationSubsystemStart());
        rr.registerOperationHandler(OP_STOP, new OperationSubsystemStop());
        rr.registerOperationHandler(OP_STATUS, new OperationSubsystemStatus());
        rr.registerOperationHandler(OP_FULL_DISCOVERY_SCAN, new OperationFullDiscoveryScan());
    }

}
