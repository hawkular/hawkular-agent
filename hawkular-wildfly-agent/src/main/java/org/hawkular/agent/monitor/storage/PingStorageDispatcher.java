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
package org.hawkular.agent.monitor.storage;

import static org.hawkular.agent.monitor.api.Avail.UP;

import java.util.Set;
import java.util.stream.Collectors;

import org.hawkular.agent.monitor.diagnostics.Diagnostics;
import org.hawkular.agent.monitor.log.AgentLoggers;
import org.hawkular.agent.monitor.log.MsgLogger;
import org.hawkular.agent.monitor.scheduler.SchedulerConfiguration;

/**
 * Buffers availability check data and eventually stores them in a storage adapter.
 */
public class PingStorageDispatcher implements Runnable {
    private static final MsgLogger log = AgentLoggers.getLogger(PingStorageDispatcher.class);

    private final SchedulerConfiguration config;
    private final StorageAdapter storageAdapter;
    private final Diagnostics diagnostics; // TODO: Any diagnostics for this?
    private final String metricId;

    public PingStorageDispatcher(SchedulerConfiguration config, StorageAdapter storageAdapter,
            Diagnostics diagnostics) {
        this.config = config;
        this.storageAdapter = storageAdapter;
        this.diagnostics = diagnostics;
        this.metricId = "hawkular-feed-availability-" + this.config.getPingDispatcherFeedId();
    }

    public SchedulerConfiguration getConfig() {
        return config;
    }

    public StorageAdapter getStorageAdapter() {
        return storageAdapter;
    }

    public Diagnostics getDiagnostics() {
        return diagnostics;
    }

    @Override
    public void run() {
        long now = System.currentTimeMillis();

        Set<AvailDataPoint> pings = this.config.getPingDispatcherTenantIds().stream()
                .map(t -> new AvailDataPoint(metricId, now, UP, t)).collect(Collectors.toSet());

        log.tracef("Sending agent availability pings: %s", pings);

        // dispatch
        storageAdapter.storeAvails(pings, 0);
    }

}
