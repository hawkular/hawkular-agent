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

import static com.codahale.metrics.MetricRegistry.name;

import org.hawkular.agent.monitor.service.ServerIdentifiers;

import com.codahale.metrics.Counter;
import com.codahale.metrics.Meter;
import com.codahale.metrics.MetricRegistry;
import com.codahale.metrics.Timer;

public class DiagnosticsImpl implements Diagnostics {
    private final Timer dmrRequestTimer;
    private final Meter dmrDelayCounter;
    private final Meter dmrErrorCounter;
    private final Meter storageError;
    private final Counter metricsStorageBuffer;
    private final Meter metricRate;

    public DiagnosticsImpl(MetricRegistry metrics, ServerIdentifiers selfId) {
        dmrRequestTimer = metrics.timer(name(selfId + ".diagnostics.dmr-request-timer"));
        dmrDelayCounter = metrics.meter(name(selfId + ".diagnostics.dmr-delay-rate"));
        dmrErrorCounter = metrics.meter(name(selfId + ".diagnostics.dmr-error-rate"));
        storageError = metrics.meter(name(selfId + ".diagnostics.storage-error-rate"));
        metricsStorageBuffer = metrics.counter(name(selfId + ".diagnostics.metrics-storage-buffer-size"));
        metricRate = metrics.meter(name(selfId + ".diagnostics.metric-rate"));
    }

    @Override
    public Timer getDMRRequestTimer() {
        return dmrRequestTimer;
    }

    @Override
    public Meter getDMRDelayedRate() {
        return dmrDelayCounter;
    }

    @Override
    public Meter getDMRErrorRate() {
        return dmrErrorCounter;
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
}