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

import java.util.Map;

import org.hawkular.metrics.client.common.MetricType;

public interface MetricTagPayloadBuilder {

    /**
     * Add a tag to a metric type. When all metric tags are added, call
     * {@link #toPayload()} to get the payload message that can be used to
     * send to the storage backend via the storage adapter.
     *
     * @param key identifies the metric
     * @param name the name of the tag
     * @param value the value of the tag
     * @param metricType the type of metric
     */
    void addTag(String key, String name, String value, MetricType metricType);

    /**
     * Due to the way Hawkular Metrics REST API works, you can only add tags for a single metric.
     * So this method actually has the potential to return mulitple payloads - one for each metric that is
     * getting assigned tags. The key to the map is the relative path to the REST API that identifies
     * the metric whose tags are being added. For example, if a gauge metric of id "foo" is getting
     * tags, its key in the map will be "gauges/foo". The values of the map are the JSON encodings
     * of all the tags for the metric.
     *
     * @return the payloads in a format suitable for the storage adapter.
     */
    Map<String, String> toPayload();

    /**
     * @return the number of tags that were {@link #addTag(String, String, String, MetricType) added} to payload
     */
    int getNumberTags();

    /**
     * If the metric tags are to be stored with a special tenant ID, this sets that tenant ID.
     * If null is passed in, or if this method is not called, the agent's tenant ID is used.
     *
     * @param tenantId the tenant ID to associate the metric tags with. May be null.
     */
    void setTenantId(String tenantId);

    /**
     * @return the tenant ID to be associated with the metrics. If null, the agent's tenant ID is used.
     */
    String getTenantId();
}
