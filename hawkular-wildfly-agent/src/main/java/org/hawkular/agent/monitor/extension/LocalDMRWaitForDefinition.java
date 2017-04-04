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

public class LocalDMRWaitForDefinition extends MonitorPersistentResourceDefinition {

    public static final LocalDMRWaitForDefinition INSTANCE = new LocalDMRWaitForDefinition();

    static final String WAIT_FOR = "wait-for";

    private LocalDMRWaitForDefinition() {
        super(PathElement.pathElement(WAIT_FOR),
                SubsystemExtension.getResourceDescriptionResolver(ManagedServersDefinition.MANAGED_SERVERS,
                        LocalDMRDefinition.LOCAL_DMR, WAIT_FOR),
                LocalDMRWaitForAdd.INSTANCE,
                LocalDMRWaitForRemove.INSTANCE,
                Flag.RESTART_NONE,
                Flag.RESTART_NONE);
    }

    @Override
    public Collection<AttributeDefinition> getAttributes() {
        return Arrays.asList(LocalDMRWaitForAttributes.ATTRIBUTES);
    }
}
