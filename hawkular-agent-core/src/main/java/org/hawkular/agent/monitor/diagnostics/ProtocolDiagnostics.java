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

import com.codahale.metrics.Meter;
import com.codahale.metrics.Timer;

/**
 * Diagnostic metrics for tracking request times and request error rates.
 *
 * @author <a href="https://github.com/ppalaga">Peter Palaga</a>
 */
public final class ProtocolDiagnostics {
    private final Meter errorRate;
    private final Timer requestTimer;
    private final Timer fullDiscoveryScanTimer;

    public ProtocolDiagnostics(Timer requestTimer, Meter errorRate, Timer fullDiscoveryScanTimer) {
        super();
        this.requestTimer = requestTimer;
        this.errorRate = errorRate;
        this.fullDiscoveryScanTimer = fullDiscoveryScanTimer;
    }

    /**
     * @return tracks the failure rate when performing protocol requests (such as fetching DMR attributes).
     */
    public Meter getErrorRate() {
        return errorRate;
    }

    /**
     * @return tracks the time protocol requests take
     */
    public Timer getRequestTimer() {
        return requestTimer;
    }

    /**
     * @return tracks the time it takes to perform a full discovery scan
     */
    public Timer getFullDiscoveryScanTimer() {
        return fullDiscoveryScanTimer;
    }
}
