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
package org.hawkular.agent.monitor.diagnostics;

import org.hawkular.agent.monitor.extension.MonitorServiceConfiguration;

import com.codahale.metrics.Counter;
import com.codahale.metrics.Meter;
import com.codahale.metrics.MetricRegistry;
import com.codahale.metrics.Timer;

public class DiagnosticsImpl implements Diagnostics {
    private final MetricRegistry metricsRegistry;

    private final Timer dmrRequestTimer;
    private final Meter dmrErrorCounter;
    private final Timer jmxRequestTimer;
    private final Meter jmxErrorCounter;
    private final Meter storageError;
    private final Counter metricsStorageBuffer;
    private final Meter metricRate;
    private final Counter availStorageBuffer;
    private final Meter availRate;
    private final Counter inventoryStorageBuffer;
    private final Meter inventoryRate;
    private final Timer inventoryStorageRequestTimer;

    public static String name(String feedId, String name) {
        return MetricRegistry.name(feedId + ".diagnostics." + name);
    }

    public DiagnosticsImpl(MonitorServiceConfiguration.DiagnosticsConfiguration config, MetricRegistry registry,
            String feedId) {
        // we don't need config now, but maybe in future - so keep "config" param here for future API consistency
        dmrRequestTimer = registry.timer(name(feedId, "dmr.request-timer"));
        dmrErrorCounter = registry.meter(name(feedId, "dmr.error-rate"));
        jmxRequestTimer = registry.timer(name(feedId, "jmx.request-timer"));
        jmxErrorCounter = registry.meter(name(feedId, "jmx.error-rate"));
        storageError = registry.meter(name(feedId, "storage.error-rate"));
        metricsStorageBuffer = registry.counter(name(feedId, "metrics.storage-buffer-size"));
        metricRate = registry.meter(name(feedId, "metric.rate"));
        availStorageBuffer = registry.counter(name(feedId, "avail.storage-buffer-size"));
        availRate = registry.meter(name(feedId, "avail.rate"));
        inventoryStorageBuffer = registry.counter(name(feedId, "inventory.storage-buffer-size"));
        inventoryRate = registry.meter(name(feedId, "inventory.rate"));
        inventoryStorageRequestTimer = registry.timer(name(feedId, "inventory.storage-request-timer"));

        this.metricsRegistry = registry;
    }

    @Override
    public MetricRegistry getMetricRegistry() {
        return metricsRegistry;
    }

    @Override
    public Timer getDMRRequestTimer() {
        return dmrRequestTimer;
    }

    @Override
    public Meter getDMRErrorRate() {
        return dmrErrorCounter;
    }

    @Override
    public Timer getJMXRequestTimer() {
        return jmxRequestTimer;
    }

    @Override
    public Meter getJMXErrorRate() {
        return jmxErrorCounter;
    }

    @Override
    public Meter getStorageErrorRate() {
        return storageError;
    }

    @Override
    public Counter getMetricsStorageBufferSize() {
        return metricsStorageBuffer;
    }

    @Override
    public Meter getMetricRate() {
        return metricRate;
    }

    @Override
    public Counter getAvailStorageBufferSize() {
        return availStorageBuffer;
    }

    @Override
    public Meter getAvailRate() {
        return availRate;
    }

    @Override
    public Counter getInventoryStorageBufferSize() {
        return inventoryStorageBuffer;
    }

    @Override
    public Meter getInventoryRate() {
        return inventoryRate;
    }

    @Override
    public Timer getInventoryStorageRequestTimer() {
        return inventoryStorageRequestTimer;
    }
}