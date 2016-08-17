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
package org.hawkular.agent.example;

import java.util.Collections;

import org.hawkular.agent.monitor.inventory.ID;
import org.hawkular.agent.monitor.inventory.Name;
import org.hawkular.agent.monitor.inventory.Resource;
import org.hawkular.agent.monitor.inventory.ResourceManager;
import org.hawkular.agent.monitor.inventory.ResourceType;
import org.hawkular.agent.monitor.inventory.ResourceTypeManager;

/**
 * A simple object to manage the resources in MyApp's inventory. This inventory supports a single
 * resource type but any number of resources of that type.
 */
public class MyAppInventory {

    private ResourceManager<MyAppNodeLocation> myAppResources;
    private ResourceTypeManager<MyAppNodeLocation> myAppResourceTypes;

    public MyAppInventory() {
        // our simple app starts with an empty inventory and supports one type of resource
        ResourceType<MyAppNodeLocation> resourceType = ResourceType.<MyAppNodeLocation> builder()
                .id(new ID("MyAppResourceType"))
                .name(new Name("My App Resource Type"))
                .location(new MyAppNodeLocation("/"))
                .build();

        myAppResources = new ResourceManager<>();
        myAppResourceTypes = new ResourceTypeManager<>(Collections.singleton(resourceType));
    }

    public ResourceManager<MyAppNodeLocation> getResourceManager() {
        return myAppResources;
    }

    public ResourceTypeManager<MyAppNodeLocation> getResourceTypeManager() {
        return myAppResourceTypes;
    }

    /**
     * Creates a resource with the given ID. If you want it added to inventory, you must
     * pass the returned resource to {@link #addResource(Resource)}.
     *
     * @param resourceId the ID of the resource to create
     * @return the resource instance
     */
    public Resource<MyAppNodeLocation> instantiateResource(String resourceId) {
        // we know our simple app only has a single type that we use
        ResourceType<MyAppNodeLocation> type = myAppResourceTypes.getRootResourceTypes().iterator().next();

        Resource<MyAppNodeLocation> resource = Resource.<MyAppNodeLocation> builder()
                .type(type)
                .id(new ID(resourceId))
                .name(new Name("My App Resource " + resourceId))
                .parent(null)
                .location(new MyAppNodeLocation("/" + resourceId))
                .build();
        return resource;
    }

    public void addResource(Resource<MyAppNodeLocation> newResource) {
        myAppResources.addResource(newResource);
    }

    public void removeResource(Resource<MyAppNodeLocation> doomedResource) {
        myAppResources.removeResource(doomedResource);
    }

    public Resource<MyAppNodeLocation> getResource(String id) {
        return myAppResources.getResource(new ID(id));
    }
}
