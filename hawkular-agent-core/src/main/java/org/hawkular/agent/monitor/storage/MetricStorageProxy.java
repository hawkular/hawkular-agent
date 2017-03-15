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

import org.hawkular.agent.monitor.api.MetricDataPayloadBuilder;
import org.hawkular.agent.monitor.api.MetricStorage;
import org.hawkular.agent.monitor.api.MetricTagPayloadBuilder;
import org.hawkular.agent.monitor.config.AgentCoreEngineConfiguration.StorageAdapterConfiguration;

/**
 * A proxy that delegates to a {@link StorageAdapterConfiguration}.
 */
public class MetricStorageProxy implements MetricStorage {

    private StorageAdapter storageAdapter;

    public MetricStorageProxy() {
    }

    public void setStorageAdapter(StorageAdapter storageAdapter) {
        this.storageAdapter = storageAdapter;
    }

    @Override
    public MetricDataPayloadBuilder createMetricDataPayloadBuilder() {
        if (storageAdapter == null) {
            throw new IllegalStateException("Storage infrastructure is not ready yet");
        }
        return storageAdapter.createMetricDataPayloadBuilder();
    }

    @Override
    public void store(MetricDataPayloadBuilder payloadBuilder, long waitMillis) {
        if (storageAdapter == null) {
            throw new IllegalStateException("Storage infrastructure is not ready yet");
        }
        storageAdapter.store(payloadBuilder, waitMillis);
    }

    @Override
    public MetricTagPayloadBuilder createMetricTagPayloadBuilder() {
        if (storageAdapter == null) {
            throw new IllegalStateException("Storage infrastructure is not ready yet");
        }
        return storageAdapter.createMetricTagPayloadBuilder();
    }

    @Override
    public void store(MetricTagPayloadBuilder payloadBuilder, long waitMillis) {
        if (storageAdapter == null) {
            throw new IllegalStateException("Storage infrastructure is not ready yet");
        }
        storageAdapter.store(payloadBuilder, waitMillis);
    }
}
