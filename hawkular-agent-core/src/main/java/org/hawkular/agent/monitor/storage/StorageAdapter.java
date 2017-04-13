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
package org.hawkular.agent.monitor.storage;

import java.util.Set;

import org.hawkular.agent.monitor.api.AvailStorage;
import org.hawkular.agent.monitor.api.InventoryStorage;
import org.hawkular.agent.monitor.api.MetricStorage;
import org.hawkular.agent.monitor.config.AgentCoreEngineConfiguration;
import org.hawkular.agent.monitor.diagnostics.Diagnostics;

public interface StorageAdapter extends MetricStorage, AvailStorage, InventoryStorage {

    /**
     * Initializes the storage adapter.
     *
     * @param feedId identifies the feed that is storing data
     * @param config the configuration of the storage adapter
     * @param autoDiscoveryScanPeriodSeconds the auto discovery frequency in seconds
     * @param diag the object used to track internal diagnostic data for the storage adapter
     * @param httpClientBuilder used to communicate with the storage server
     */
    void initialize(
            String feedId,
            AgentCoreEngineConfiguration.StorageAdapterConfiguration config,
            int autoDiscoveryScanPeriodSeconds,
            Diagnostics diag,
            HttpClientBuilder httpClientBuilder);

    /**
     * Clean up and stop whatever the storage adapter is doing.
     */
    void shutdown();

    /**
     * @return the storage adapter's configuration settings
     */
    AgentCoreEngineConfiguration.StorageAdapterConfiguration getStorageAdapterConfiguration();

    /**
     * Stores the given collected metric data points.
     * This is an asynchronous call. But if a <code>waitMillis</code> is provided, it indicates the caller is willing
     * to wait up to that amount of milliseconds for the store to complete before returning.
     *
     * Note that if the given data points contain data across multiple tenants, the waitMillis will be the wait
     * time for each storage attempt per tenant (in other words, if you ask to wait 1000 milliseconds and pass in
     * data points for two different tenants, the wait time could be up to 2000 milliseconds because you will
     * wait 1000 for storing the data for tenant 1 and 1000 for storing data for tenant 2).
     *
     * @param datapoints the data to be stored
     * @param waitMillis the amount of milliseconds to wait for the store to complete before returning (0==no wait).
     */
    void storeMetrics(Set<MetricDataPoint> datapoints, long waitMillis);

    /**
     * Stores the given availability check data points.
     * This is an asynchronous call. But if a <code>waitMillis</code> is provided, it indicates the caller is willing
     * to wait up to that amount of milliseconds for the store to complete before returning.
     *
     * Note that if the given data points contain data across multiple tenants, the waitMillis will be the wait
     * time for each storage attempt per tenant (in other words, if you ask to wait 1000 milliseconds and pass in
     * data points for two different tenants, the wait time could be up to 2000 milliseconds because you will
     * wait 1000 for storing the data for tenant 1 and 1000 for storing data for tenant 2).
     *
     * @param datapoints the data to be stored
     * @param waitMillis the amount of milliseconds to wait for the store to complete before returning (0==no wait).
     */
    void storeAvails(Set<AvailDataPoint> datapoints, long waitMillis);
}
