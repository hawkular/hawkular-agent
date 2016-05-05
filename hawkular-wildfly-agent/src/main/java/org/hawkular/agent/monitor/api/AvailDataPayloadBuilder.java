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
package org.hawkular.agent.monitor.api;

public interface AvailDataPayloadBuilder {

    /**
     * Add an availability data point. When all availability data points are added, call
     * {@link #toPayload()} to get the payload message that can be used to
     * send to the storage backend via the storage adapter.
     *
     * @param key identifies the availability data point
     * @param timestamp the time the availability was checked
     * @param value the value of the availability
     */
    void addDataPoint(String key, long timestamp, Avail value);

    /**
     * @return the payload in a format suitable for the storage adapter.
     */
    Object toPayload();

    /**
     * @return the number of data points that were {@link #addDataPoint(String, long, Avail) added} to the payload
     */
    int getNumberDataPoints();

    /**
     * If the availability data is to be stored with a special tenant ID, this sets that tenant ID.
     * If null is passed in, or if this method is not called, the agent's tenant ID is used.
     *
     * @param tenantId the tenant ID to associate the avail data with. May be null.
     */
    void setTenantId(String tenantId);

    /**
     * @return the tenant ID to be associated with the avail data. If null, the agent's tenant ID is used.
     */
    String getTenantId();
}
