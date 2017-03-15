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

/**
 * @author <a href="https://github.com/ppalaga">Peter Palaga</a>
 */
public abstract class DataPoint {

    protected final String key;
    protected final long timestamp;
    protected final String tenantId;

    /**
     * @param key the key to identify the data point
     * @param timestamp the time when the data point was collected
     * @param tenantId if not null, the tenant ID to be associated with this data point. If this is null,
     *                 the agent's tenant ID will be associated with the data point.
     */
    public DataPoint(String key, long timestamp, String tenantId) {
        super();
        this.key = key;
        this.timestamp = timestamp;
        this.tenantId = tenantId;
    }

    /**
     * @return when the metric was collected
     */
    public long getTimestamp() {
        return timestamp;
    }

    public String getKey() {
        return key;
    }

    /**
     * @return if not null, this is the tenant ID the data point should be associated with
     */
    public String getTenantId() {
        return tenantId;
    }

}
