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

import org.jgrapht.graph.DefaultEdge;
import org.jgrapht.traverse.BreadthFirstIterator;

/**
 * Just a container that holds the different metadata managers needed to keep track of type information.
 *
 * @param <RT> resource type
 * @param <RTS> resource type set
 * @param <MT> metric type
 * @param <MTS> metric type set
 * @param <AT> avail type
 * @param <ATS> avail type set
 * @param <O> operation types
 * @param <RCPT> resource configuration property definition
 *
 * @author John Mazzitelli
 */
public abstract class MetadataManager< //
RT extends ResourceType<MT, AT, O, RCPT>, //
RTS extends ResourceTypeSet<RT>, //
MT extends MetricType, //
MTS extends MetricTypeSet<MT>, //
AT extends AvailType, //
ATS extends AvailTypeSet<AT>, //
O extends Operation<RT>, //
RCPT extends ResourceConfigurationPropertyType<RT>> {

    private final ResourceTypeManager<RT, RTS> resourceTypeManager;
    private final MetricTypeManager<MT, MTS> metricTypeManager;
    private final AvailTypeManager<AT, ATS> availTypeManager;

    public MetadataManager(
            ResourceTypeManager<RT, RTS> resourceTypeManager,
            MetricTypeManager<MT, MTS> metricTypeManager,
            AvailTypeManager<AT, ATS> availTypeManager) {
        this.resourceTypeManager = resourceTypeManager;
        this.metricTypeManager = metricTypeManager;
        this.availTypeManager = availTypeManager;
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

    /**
     * This will fill in metric types and avail types for all known resource types.
     */
    public void populateMetricAndAvailTypesForAllResourceTypes() {
        BreadthFirstIterator<RT, DefaultEdge> iter = getResourceTypeManager().getBreadthFirstIterator();
        while (iter.hasNext()) {
            RT resourceType = iter.next();
            populateMetricAndAvailTypesForResourceType(resourceType);
        }
        return;
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
}
