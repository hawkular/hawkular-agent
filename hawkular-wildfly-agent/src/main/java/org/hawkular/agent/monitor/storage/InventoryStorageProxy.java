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
package org.hawkular.agent.monitor.storage;

import org.hawkular.agent.monitor.api.InventoryEvent;
import org.hawkular.agent.monitor.api.InventoryStorage;
import org.hawkular.agent.monitor.extension.MonitorServiceConfiguration.StorageAdapterConfiguration;

/**
 * A proxy that delegates to a {@link StorageAdapterConfiguration}.
 */
public class InventoryStorageProxy implements InventoryStorage {

    private StorageAdapter storageAdapter;

    public InventoryStorageProxy() {
    }

    public void setStorageAdapter(StorageAdapter storageAdapter) {
        this.storageAdapter = storageAdapter;
    }

    @Override
    public <L> void discoverAllFinished(InventoryEvent<L> event) {
        if (storageAdapter == null) {
            throw new IllegalStateException("Storage infrastructure is not ready yet");
        }
        storageAdapter.discoverAllFinished(event);
    }

    @Override
    public <L> void resourcesAdded(InventoryEvent<L> event) {
        if (storageAdapter == null) {
            throw new IllegalStateException("Storage infrastructure is not ready yet");
        }
        storageAdapter.resourcesAdded(event);
    }

    @Override
    public <L> void resourceRemoved(InventoryEvent<L> event) {
        if (storageAdapter == null) {
            throw new IllegalStateException("Storage infrastructure is not ready yet");
        }
        storageAdapter.resourceRemoved(event);
    }

}
