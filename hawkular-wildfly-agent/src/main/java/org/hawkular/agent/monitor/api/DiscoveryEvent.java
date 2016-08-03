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
package org.hawkular.agent.monitor.api;

import org.hawkular.agent.monitor.inventory.ResourceManager;
import org.hawkular.agent.monitor.inventory.ResourceTypeManager;

/**
 * A event for discovery scans.
 *
 * @see InventoryListener
 */
public class DiscoveryEvent<L> {

    private final SamplingService<L> samplingService;
    private final ResourceManager<L> resourceManager;
    private final ResourceTypeManager<L> resourceTypeManager;

    /**
     * Creates a discovery event.
     *
     * @param samplingService a service that provides details such as feed ID and endpoint information that helps
     *                        identify the resources in the event, plus has methods that can be used to monitor
     *                        the resources in the event.
     * @param resourceManager the resources associated with the discovery
     * @param resourceTypeManager the resource types associated with the discovery
     */
    public DiscoveryEvent(SamplingService<L> samplingService, ResourceManager<L> resourceManager,
            ResourceTypeManager<L> resourceTypeManager) {
        if (samplingService == null) {
            throw new IllegalArgumentException("Sampling service cannot be null");
        }

        if (resourceManager == null) {
            throw new IllegalArgumentException("Resource manager cannot be null");
        }

        if (resourceTypeManager == null) {
            throw new IllegalArgumentException("Resource type manager cannot be null");
        }

        this.samplingService = samplingService;
        this.resourceManager = resourceManager;
        this.resourceTypeManager = resourceTypeManager;
    }

    /**
     * @return the resource manager that was populated by the discovery scan
     */
    public ResourceManager<L> getResourceManager() {
        return resourceManager;
    }

    /**
     * @return the resource type manager that has all the types used by the discovery scan
     */
    public ResourceTypeManager<L> getResourceTypeManager() {
        return resourceTypeManager;
    }

    /**
     * @return the sampling service associated with the discovery that was performed
     */
    public SamplingService<L> getSamplingService() {
        return samplingService;
    }

}
