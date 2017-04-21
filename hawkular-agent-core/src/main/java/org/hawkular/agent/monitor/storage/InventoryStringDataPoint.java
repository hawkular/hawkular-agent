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
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * @author Joel Takvorian
 */
public class InventoryStringDataPoint implements Serializable {

    private static final String KEY_NBCHUNKS = "chunks";
    private static final String KEY_SIZE = "size";

    @JsonProperty("timestamp")
    private final long timestamp;
    @JsonProperty("value")
    private final byte[] inventoryStructure;
    @JsonProperty("tags")
    private final Map<String, String> tags;

    @JsonCreator
    public InventoryStringDataPoint(
            @JsonProperty("timestamp") long timestamp,
            @JsonProperty("value") byte[] inventoryStructure,
            @JsonProperty("tags") Map<String, String> tags) {
        this.timestamp = timestamp;
        this.inventoryStructure = inventoryStructure;
        this.tags = tags;
    }

    public long getTimestamp() {
        return timestamp;
    }

    public byte[] getInventoryStructure() {
        return inventoryStructure;
    }

    public Map<String, String> getTags() {
        return tags;
    }

    public static InventoryStringDataPoint create(long timestamp, byte[] inventoryStructure) {
        return new InventoryStringDataPoint(timestamp, inventoryStructure, new HashMap<>());
    }

    public void setMasterInfo(int nbChunks, int totalSize) {
        tags.put(KEY_NBCHUNKS, String.valueOf(nbChunks));
        tags.put(KEY_SIZE, String.valueOf(totalSize));
    }

    public Optional<String> getNbChunks() {
        return Optional.ofNullable(tags.get(KEY_NBCHUNKS));
    }
}
