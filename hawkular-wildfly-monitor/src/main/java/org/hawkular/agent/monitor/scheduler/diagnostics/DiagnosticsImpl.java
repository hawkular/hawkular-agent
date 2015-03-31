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
package org.hawkular.agent.monitor.scheduler.diagnostics;

import static com.codahale.metrics.MetricRegistry.name;

import com.codahale.metrics.Counter;
import com.codahale.metrics.Meter;
import com.codahale.metrics.MetricRegistry;
import com.codahale.metrics.Timer;

public class DiagnosticsImpl implements Diagnostics {
    private final Timer requestTimer;
    private final Meter delayCounter;
    private final Meter taskErrorCounter;
    private final Meter storageError;
    private final Counter storageBuffer;

    public DiagnosticsImpl(MetricRegistry metrics) {
        requestTimer = metrics.timer(name("dmr-request-timer"));
        delayCounter = metrics.meter(name("task-delay-rate"));
        taskErrorCounter = metrics.meter(name("task-error-rate"));
        storageError = metrics.meter(name("storage-error-rate"));
        storageBuffer = metrics.counter(name("storage-buffer-size"));
    }

    @Override
    public Timer getRequestTimer() {
        return requestTimer;
    }

    @Override
    public Meter getDelayedRate() {
        return delayCounter;
    }

    @Override
    public Meter getErrorRate() {
        return taskErrorCounter;
    }

    @Override
    public Meter getStorageErrorRate() {
        return storageError;
    }

    @Override
    public Counter getStorageBufferSize() {
        return storageBuffer;
    }
}