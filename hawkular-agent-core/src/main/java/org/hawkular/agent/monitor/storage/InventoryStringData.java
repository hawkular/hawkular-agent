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
import java.util.List;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * @author Joel Takvorian
 */
public class InventoryStringData implements Serializable {

    @JsonProperty("id")
    private final String id;
    @JsonProperty("data")
    private final List<InventoryStringDataPoint> data;

    @JsonCreator
    public InventoryStringData(@JsonProperty("id") String id, @JsonProperty("data") List<InventoryStringDataPoint> data) {
        this.id = id;
        this.data = data;
    }

    public String getId() {
        return id;
    }

    public List<InventoryStringDataPoint> getData() {
        return data;
    }
}
