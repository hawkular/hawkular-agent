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

import java.util.Set;

import org.hawkular.agent.monitor.api.AvailStorage;
import org.hawkular.agent.monitor.api.InventoryStorage;
import org.hawkular.agent.monitor.api.MetricStorage;
import org.hawkular.agent.monitor.diagnostics.Diagnostics;
import org.hawkular.agent.monitor.extension.MonitorServiceConfiguration;
import org.hawkular.agent.monitor.service.ServerIdentifiers;

public interface StorageAdapter extends MetricStorage, AvailStorage, InventoryStorage {

    /**
     * Initializes the storage adapter.
     *
     * @param config the configuration of the storage adapter
     * @param diag the object used to track internal diagnostic data for the storage adapter
     * @param selfId helps identify where we are hosted
     * @param httpClientBuilder used to communicate with the storage server
     */
    void initialize(MonitorServiceConfiguration.StorageAdapter config, Diagnostics diag, ServerIdentifiers selfId,
            HttpClientBuilder httpClientBuilder);

    MonitorServiceConfiguration.StorageAdapter getStorageAdapterConfiguration();

    /**
     * Stores the given collected metric data points.
     * @param datapoints the data to be stored
     */
    void storeMetrics(Set<MetricDataPoint> datapoints);

    /**
     * Stores the given availability check data points.
     * @param datapoints the data to be stored
     */
    void storeAvails(Set<AvailDataPoint> datapoints);
}
