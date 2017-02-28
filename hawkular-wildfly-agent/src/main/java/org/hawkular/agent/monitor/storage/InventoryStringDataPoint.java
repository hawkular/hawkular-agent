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

import java.io.Serializable;

import org.hawkular.agent.monitor.util.Util;
import org.hawkular.inventory.api.model.InventoryStructure;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * @author Joel Takvorian
 */
public class InventoryStringDataPoint implements Serializable {
    @JsonProperty("timestamp")
    private final long timestamp;
    @JsonProperty("value")
    private final String inventoryStructure;

    public InventoryStringDataPoint(long timestamp, InventoryStructure<?> inventoryStructure) {
        this.timestamp = timestamp;
        this.inventoryStructure = Util.toJson(inventoryStructure);
    }

    @JsonCreator
    public InventoryStringDataPoint(@JsonProperty("timestamp") long timestamp, @JsonProperty("value") String inventoryStructure) {
        this.timestamp = timestamp;
        this.inventoryStructure = inventoryStructure;
    }

    public long getTimestamp() {
        return timestamp;
    }

    public String getInventoryStructure() {
        return inventoryStructure;
    }
}
