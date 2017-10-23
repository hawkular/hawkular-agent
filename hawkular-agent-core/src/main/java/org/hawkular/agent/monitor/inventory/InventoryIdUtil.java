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
package org.hawkular.agent.monitor.inventory;

import org.hawkular.agent.monitor.config.AgentCoreEngineConfiguration.AbstractEndpointConfiguration;

/**
 * Basic functions used to generate and work with IDs for entities in inventory.
 *
 * @author John Mazzitelli
 */
public class InventoryIdUtil {
    public static class ResourceIdParts {

        private final String feedId;
        private final String managedServerName;
        private final String idPart;

        private ResourceIdParts(String feedId, String managedServerName, String idPart) {
            this.feedId = feedId;
            this.managedServerName = managedServerName;
            this.idPart = idPart;
        }

        public String getFeedId() {
            return feedId;
        }

        public String getManagedServerName() {
            return managedServerName;
        }

        public String getIdPart() {
            return idPart;
        }
    }

    /**
     * Given a resource ID generated via {@link InventoryIdUtil#generateResourceId}
     * this returns the different parts that make up that resource ID.
     *
     * @param resourceId the full resource ID to be parsed
     * @return the parts of the resource ID
     */
    public static ResourceIdParts parseResourceId(String resourceId) {
        if (resourceId == null) {
            throw new IllegalArgumentException("Invalid resource ID - cannot be null");
        }

        String[] parts = resourceId.split("~", 3);
        if (parts.length != 3) {
            throw new IllegalArgumentException("Cannot parse invalid ID: " + resourceId);
        }
        return new ResourceIdParts(parts[0], parts[1], parts[2]);
    }

    /**
     * Generates an ID for a resource.
     *
     * @param feedId the ID of the feed that owns the resource whose ID is to be generated
     * @param endpoint the endpoint where the resource is found
     * @param idPart a unique string that identifies the resource within the managed server
     *
     * @return the resource ID
     */
    public static ID generateResourceId(
            String feedId,
            MonitoredEndpoint<? extends AbstractEndpointConfiguration> endpoint,
            String idPart) {
        ID id = new ID(String.format("%s~%s~%s", feedId, endpoint.getName(), idPart));
        return id;
    }

    /**
     * Generates an ID for an {@link MetricInstance}.
     *
     * @param resource the resource that owns the MetricInstance
     * @param metricType the type of the MetricInstance whose ID is being generated
     *
     * @return the ID
     */
    public static ID generateMetricInstanceId(String feedId, ID resourceId, MetricType<?> metricType) {
        ID id = new ID(String.format("MI~R~[%s/%s]~MT~%s", feedId, resourceId, metricType.getID()));
        return id;
    }
}
