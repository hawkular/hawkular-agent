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
package org.hawkular.agent.monitor.diagnostics;

import org.hawkular.agent.monitor.config.AgentCoreEngineConfiguration;

import com.codahale.metrics.Meter;
import com.codahale.metrics.MetricRegistry;
import com.codahale.metrics.Timer;

public class DiagnosticsImpl implements Diagnostics {
    private final MetricRegistry metricsRegistry;

    private final ProtocolDiagnostics dmrDiagnostics;
    private final ProtocolDiagnostics jmxDiagnostics;
    private final ProtocolDiagnostics platformDiagnostics;
    private final Meter storageError;
    private final Meter inventoryRate;
    private final Timer inventoryStorageRequestTimer;

    public static String name(String feedId, String name) {
        return MetricRegistry.name(feedId + ".diagnostics." + name);
    }

    public DiagnosticsImpl(
            AgentCoreEngineConfiguration.DiagnosticsConfiguration config,
            MetricRegistry registry,
            String feedId) {
        // we don't need config now, but maybe in future - so keep "config" param here for future API consistency
        this.dmrDiagnostics = newDiagnostics("dmr", feedId, registry);
        this.jmxDiagnostics = newDiagnostics("jmx", feedId, registry);
        this.platformDiagnostics = newDiagnostics("platform", feedId, registry);

        storageError = registry.meter(name(feedId, "storage.error-rate"));
        inventoryRate = registry.meter(name(feedId, "inventory.rate"));
        inventoryStorageRequestTimer = registry.timer(name(feedId, "inventory.storage-request-timer"));

        this.metricsRegistry = registry;
    }

    private static ProtocolDiagnostics newDiagnostics(String prefix, String feedId, MetricRegistry registry) {
        return new ProtocolDiagnostics(
                registry.timer(name(feedId, prefix + ".request-timer")),
                registry.meter(name(feedId, prefix + ".error-rate")),
                registry.timer(name(feedId, prefix + ".full-discovery-scan-timer")));
    }

    @Override
    public MetricRegistry getMetricRegistry() {
        return metricsRegistry;
    }

    @Override
    public ProtocolDiagnostics getDMRDiagnostics() {
        return dmrDiagnostics;
    }

    @Override
    public ProtocolDiagnostics getJMXDiagnostics() {
        return jmxDiagnostics;
    }

    @Override
    public ProtocolDiagnostics getPlatformDiagnostics() {
        return platformDiagnostics;
    }

    @Override
    public Meter getStorageErrorRate() {
        return storageError;
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
