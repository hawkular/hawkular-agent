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

import org.hawkular.agent.monitor.api.Avail;

/**
 * Availability check data that was collected.
 */
public class AvailDataPoint extends DataPoint {
    private Avail value;

    public AvailDataPoint(String key, long timestamp, Avail value, String tenantId) {
        super(key, timestamp, tenantId);
        this.value = value;
    }

    /**
     * @return when the availability was checked
     */
    public long getTimestamp() {
        return timestamp;
    }

    /**
     * @return the actual availability status
     */
    public Avail getValue() {
        return value;
    }

    @Override
    public String toString() {
        return "AvailDataPoint [value=" + value + ", key=" + key + ", timestamp=" + timestamp + "]";
    }

}
