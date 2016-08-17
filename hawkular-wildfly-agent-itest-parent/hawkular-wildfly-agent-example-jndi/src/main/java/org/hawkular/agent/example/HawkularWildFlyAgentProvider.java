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

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.annotation.PostConstruct;
import javax.ejb.Singleton;

import org.hawkular.agent.monitor.api.Avail;
import org.hawkular.agent.monitor.api.AvailDataPayloadBuilder;
import org.hawkular.agent.monitor.api.AvailStorage;
import org.hawkular.agent.monitor.api.DiscoveryEvent;
import org.hawkular.agent.monitor.api.HawkularWildFlyAgentContext;
import org.hawkular.agent.monitor.api.InventoryEvent;
import org.hawkular.agent.monitor.api.MetricDataPayloadBuilder;
import org.hawkular.agent.monitor.api.MetricStorage;
import org.hawkular.agent.monitor.inventory.Resource;
import org.hawkular.agent.monitor.inventory.ResourceType;
import org.hawkular.metrics.client.common.MetricType;
import org.jboss.logging.Logger;

/**
 * A singleton that provides the agent API to allow others to create inventory and store metrics.
 */
@Singleton
public class HawkularWildFlyAgentProvider {
    private static final Logger log = Logger.getLogger(HawkularWildFlyAgentProvider.class);
    private static final String AGENT_JNDI = "java:global/hawkular/agent/api";

    static final String TENANT_ID = "my-app-tenant";

    @javax.annotation.Resource(name = AGENT_JNDI)
    private HawkularWildFlyAgentContext hawkularAgent;

    private MyAppSamplingService myAppSamplingService;
    private MyAppInventory myAppInventory;

    @PostConstruct
    public void postConstruct() {
        if (hawkularAgent == null) {
            log.debugf("The Hawkular WildFly Agent is either disabled or not deployed. It is unavailable for use.");
        }

        myAppSamplingService = new MyAppSamplingService();
        myAppInventory = new MyAppInventory();
    }

    /**
     * Given a metric identifier and the metric data value, this writes the data to Hawkular.
     * @param metricKey identifies the metric
     * @param metricValue the value of the metric
     * @param metricType the type of metric (e.g. gauge or counter)
     */
    public void sendMetric(String metricKey, Double metricValue, MetricType metricType) {
        HawkularWildFlyAgentContext hawkularWildFlyAgent = getHawkularWildFlyAgent();
        MetricStorage metricStorage = hawkularWildFlyAgent.getMetricStorage();

        MetricDataPayloadBuilder payloadBuilder = metricStorage.createMetricDataPayloadBuilder();
        payloadBuilder.addDataPoint(metricKey, System.currentTimeMillis(), metricValue, metricType);
        payloadBuilder.setTenantId(TENANT_ID);
        metricStorage.store(payloadBuilder, 0);
    }

    /**
     * Given an availability identifier and the avail data value, this writes the data to Hawkular.
     * @param availKey identifies the availability metric
     * @param availValue the value of the availability
     */
    public void sendAvail(String availKey, Avail availValue) {
        HawkularWildFlyAgentContext hawkularWildFlyAgent = getHawkularWildFlyAgent();
        AvailStorage availStorage = hawkularWildFlyAgent.getAvailStorage();

        AvailDataPayloadBuilder payloadBuilder = availStorage.createAvailDataPayloadBuilder();
        payloadBuilder.addDataPoint(availKey, System.currentTimeMillis(), availValue);
        payloadBuilder.setTenantId(TENANT_ID);
        availStorage.store(payloadBuilder, 0);
    }

    /**
     * This will add the resource to the internal inventory and persist the full inventory to hawkular.
     *
     * @param resourceId the new resource ID
     */
    public void addResourceToInventory(String resourceId) {
        Resource<MyAppNodeLocation> newResource = myAppInventory.getResource(resourceId);
        if (newResource != null) {
            throw new RuntimeException("Cannot add an already known resource: " + newResource);
        }
        newResource = myAppInventory.instantiateResource(resourceId);

        myAppInventory.addResource(newResource);

        List<Resource<MyAppNodeLocation>> resources = Arrays.asList(newResource);
        InventoryEvent<MyAppNodeLocation> iEvent = new InventoryEvent<>(myAppSamplingService, resources);
        getHawkularWildFlyAgent().getInventoryStorage().resourcesAdded(iEvent);

        // we want to persist this - since we only added one, we can tell the
        // system our "discovery" is done now and our inventory has been updated
        syncAllResourceTypes();
        DiscoveryEvent<MyAppNodeLocation> dEvent;
        dEvent = new DiscoveryEvent<>(myAppSamplingService, myAppInventory.getResourceManager());
        getHawkularWildFlyAgent().getInventoryStorage().discoveryCompleted(dEvent);
    }

    /**
     * This will remove the resource from the internal inventory and persist the full inventory to hawkular.
     *
     * @param resource the resource to remove
     */
    public void removeResourceFromInventory(String doomedResourceId) {
        Resource<MyAppNodeLocation> doomedResource = myAppInventory.getResource(doomedResourceId);
        if (doomedResource == null) {
            throw new RuntimeException("Cannot remove unknown resource: " + doomedResource);
        }

        myAppInventory.removeResource(doomedResource);

        List<Resource<MyAppNodeLocation>> resources = Arrays.asList(doomedResource);
        InventoryEvent<MyAppNodeLocation> iEvent = new InventoryEvent<>(myAppSamplingService, resources);
        getHawkularWildFlyAgent().getInventoryStorage().resourcesRemoved(iEvent);

        // we want to persist this - since we only removed one, we can tell the
        // system our "discovery" is done now and our inventory has been updated
        syncAllResourceTypes();
        DiscoveryEvent<MyAppNodeLocation> dEvent;
        dEvent = new DiscoveryEvent<>(myAppSamplingService, myAppInventory.getResourceManager());
        getHawkularWildFlyAgent().getInventoryStorage().discoveryCompleted(dEvent);
    }

    private void syncAllResourceTypes() {
        Map<String, List<ResourceType<MyAppNodeLocation>>> allTypes = new HashMap<>();
        allTypes.put(TENANT_ID, myAppInventory.getResourceTypeManager().getResourceTypesBreadthFirst());
        getHawkularWildFlyAgent().getInventoryStorage().allResourceTypes(allTypes);
    }

    private HawkularWildFlyAgentContext getHawkularWildFlyAgent() throws UnsupportedOperationException {
        if (hawkularAgent == null) {
            throw new UnsupportedOperationException(
                    "The Hawkular WildFly Agent is either disabled or not deployed "
                            + "and thus is not available for use.");
        }
        return hawkularAgent;
    }
}
