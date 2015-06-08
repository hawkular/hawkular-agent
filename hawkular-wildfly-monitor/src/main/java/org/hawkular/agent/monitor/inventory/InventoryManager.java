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
package org.hawkular.agent.monitor.inventory;

import java.util.Collection;

import org.hawkular.agent.monitor.scheduler.config.MonitoredEndpoint;

/**
 * Just a container that holds the different managers needed to keep track of inventory.
 *
 * @param <RT> resource type
 * @param <RTS> resource type set
 * @param <MT> metric type
 * @param <MTS> metric type set
 * @param <AT> avail type
 * @param <ATS> avail type set
 * @param <RCPT> resource configuration property definition
 * @param <R> resource
 * @param <ME> monitored endpoint
 *
 * @author John Mazzitelli
 */
public abstract class InventoryManager< //
RT extends ResourceType<MT, AT, O, RCPT>, //
RTS extends ResourceTypeSet<RT>, //
MT extends MetricType, //
MTS extends MetricTypeSet<MT>, //
AT extends AvailType, //
ATS extends AvailTypeSet<AT>, //
O extends Operation<RT>, //
RCPT extends ResourceConfigurationPropertyType<RT>, //
R extends Resource<RT, ?, ?, ?, ?>, //
ME extends MonitoredEndpoint> {

    private final ResourceTypeManager<RT, RTS> resourceTypeManager;
    private final MetricTypeManager<MT, MTS> metricTypeManager;
    private final AvailTypeManager<AT, ATS> availTypeManager;
    private final ResourceManager<R> resourceManager;
    private final ManagedServer managedServer;
    private final ME endpoint;

    public InventoryManager(ResourceTypeManager<RT, RTS> resourceTypeManager,
            MetricTypeManager<MT, MTS> metricTypeManager,
            AvailTypeManager<AT, ATS> availTypeManager,
            ResourceManager<R> resourceManager,
            ManagedServer managedServer,
            ME endpoint) {
        this.resourceTypeManager = resourceTypeManager;
        this.metricTypeManager = metricTypeManager;
        this.availTypeManager = availTypeManager;
        this.resourceManager = resourceManager;
        this.managedServer = managedServer;
        this.endpoint = endpoint;
    }

    public ResourceTypeManager<RT, RTS> getResourceTypeManager() {
        return resourceTypeManager;
    }

    public MetricTypeManager<MT, MTS> getMetricTypeManager() {
        return metricTypeManager;
    }

    public AvailTypeManager<AT, ATS> getAvailTypeManager() {
        return availTypeManager;
    }

    public ResourceManager<R> getResourceManager() {
        return resourceManager;
    }

    public ManagedServer getManagedServer() {
        return managedServer;
    }

    public ME getEndpoint() {
        return endpoint;
    }

    /**
     * Given a resource type, this will fill in its metric types and avail types.
     *
     * @param resourceType the type whose metric and avail types are to be retrieved
     */
    public void populateMetricAndAvailTypesForResourceType(RT resourceType) {

        Collection<MT> metricTypes = resourceType.getMetricTypes();
        if (metricTypes.isEmpty()) {
            for (Name metricSetName : resourceType.getMetricSets()) {
                MTS metricSet = getMetricTypeManager().getMetricSet(metricSetName);
                if (metricSet != null) {
                    metricTypes.addAll(metricSet.getMetricTypeMap().values());
                }
            }
        }

        Collection<AT> availTypes = resourceType.getAvailTypes();
        if (availTypes.isEmpty()) {
            for (Name availSetName : resourceType.getAvailSets()) {
                ATS availSet = getAvailTypeManager().getAvailSet(availSetName);
                if (availSet != null) {
                    availTypes.addAll(availSet.getAvailTypeMap().values());
                }
            }
        }
    }

    /**
     * Populate the inventory with resources that can be automatically discovered.
     * Only those resources of known types (see {@link #getResourceTypeManager()}) will be discovered.
     *
     * Once this returns successfully, {@link #getResourceManager()} should be populated
     * with resources.
     */
    public abstract void discoverResources();
}
