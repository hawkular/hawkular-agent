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

public interface MetricStorage {
    /**
     * @return a builder object suitable for wrapping metric data in a proper payload
     * message format to be sent to the storage backend.
     */
    MetricDataPayloadBuilder createMetricDataPayloadBuilder();

    /**
     * Stores the metric data found in the given builder.
     * This is an asynchronous call. But if a <code>waitMillis</code> is provided, it indicates the caller is willing
     * to wait up to that amount of milliseconds for the store to complete before returning.
     *
     * @param payloadBuilder contains the metric data to store
     * @param waitMillis the amount of milliseconds to wait for the store to complete before returning (0==no wait)
     */
    void store(MetricDataPayloadBuilder payloadBuilder, long waitMillis);
}