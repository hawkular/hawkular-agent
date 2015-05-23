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
package org.hawkular.agent.monitor.storage;

import java.util.ArrayList;

import org.hawkular.agent.monitor.api.InventoryDataPayloadBuilder;
import org.hawkular.agent.monitor.inventory.Resource;
import org.hawkular.agent.monitor.inventory.ResourceType;

public class HawkularInventoryDataPayloadBuilder implements InventoryDataPayloadBuilder {
    private String tenantId = null;

    public void setTenantId(String tenantId) {
        this.tenantId = tenantId;
    }

    private final ArrayList<ResourceType<?, ?>> resourceTypes = new ArrayList<>();
    private final ArrayList<Resource<?, ?, ?, ?>> resources = new ArrayList<>();

    @Override
    public void addResourceType(ResourceType<?, ?> resourceType) {
        // TODO Auto-generated method stub
        resourceTypes.add(resourceType);
    }

    @Override
    public void addResource(Resource<?, ?, ?, ?> resource) {
        // TODO Auto-generated method stub
        resources.add(resource);
    }

    @Override
    public Object toPayload() {
        // TODO Auto-generated method stub
        return String.format("tenantId=[%s], resourceTypes=[%s], resources=[%s]", tenantId, resourceTypes, resources);
    }
}
