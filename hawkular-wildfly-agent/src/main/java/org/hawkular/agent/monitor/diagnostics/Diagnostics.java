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
package org.hawkular.agent.monitor.diagnostics;

import com.codahale.metrics.Counter;
import com.codahale.metrics.Meter;
import com.codahale.metrics.MetricRegistry;
import com.codahale.metrics.Timer;

/**
 * Provides diagnostic metrics for the scheduler itself.
 */
public interface Diagnostics {
    /**
     * @return the registry of all metrics
     */
    MetricRegistry getMetricRegistry();

    /**
     * @return the DMR sampling an discovery related diagnostics
     */
    ProtocolDiagnostics getDMRDiagnostics();

    /**
     * @return the JMX sampling an discovery related diagnostics
     */
    ProtocolDiagnostics getJMXDiagnostics();

    /**
     * @return the platform sampling an discovery related diagnostics
     */
    ProtocolDiagnostics getPlatformDiagnostics();

    /**
     * @return tracks how many errors occurred while trying to store data to Hawkular storage
     */
    Meter getStorageErrorRate();

    /**
     * @return tracks the size of the buffer that holds metrics waiting to get stored
     */
    Counter getMetricsStorageBufferSize();

    /**
     * @return tracks the size of the buffer that holds availability statuses waiting to get stored
     */
    Counter getAvailStorageBufferSize();

    /**
     * @return tracks the number of metrics that have been stored
     */
    Meter getMetricRate();

    /**
     * @return tracks the number of availability statuses that have been stored
     */
    Meter getAvailRate();

    /**
     * @return tracks the number of resources that have been stored/synced in inventory over time
     */
    Meter getInventoryRate();

    /**
     * @return time it takes to execute inventory storage requests
     */
    Timer getInventoryStorageRequestTimer();
}
