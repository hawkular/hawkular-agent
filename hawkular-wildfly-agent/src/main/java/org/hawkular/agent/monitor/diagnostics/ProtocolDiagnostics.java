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

import com.codahale.metrics.Meter;
import com.codahale.metrics.Timer;

/**
 * Diagnostic metrics for the collection of metrics and avails.
 *
 * @author <a href="https://github.com/ppalaga">Peter Palaga</a>
 */
public final class ProtocolDiagnostics {
    private final Meter errorRate;

    private final Timer requestTimer;
    public ProtocolDiagnostics(Timer requestTimer, Meter errorRate) {
        super();
        this.requestTimer = requestTimer;
        this.errorRate = errorRate;
    }
    /**
     * @return tracks the number of DMR while collecting metrics and avails
     */
    public Meter getErrorRate() {
        return errorRate;
    }

    /**
     * @return time it takes to execute the metric and avail collection requests
     */
    public Timer getRequestTimer() {
        return requestTimer;
    }

}
