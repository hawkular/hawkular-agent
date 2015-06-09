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

import java.util.List;
import java.util.Map;

import org.junit.Assert;
import org.junit.Test;

public class MetricDataPayloadBuilderTest {

    @Test
    public void testEmpty() {
        MetricsOnlyMetricDataPayloadBuilder builder = new MetricsOnlyMetricDataPayloadBuilder();
        Assert.assertEquals(0, builder.toObjectPayload().size());
        Assert.assertEquals("[]", builder.toPayload());
    }

    @SuppressWarnings("unchecked")
    @Test
    public void testWithOneMetricId() {
        List<Map<String, Object>> objectPayload;
        Map<String, Object> dataListById;

        MetricsOnlyMetricDataPayloadBuilder builder = new MetricsOnlyMetricDataPayloadBuilder();
        builder.addDataPoint("one", 12345, 1.2);
        objectPayload = builder.toObjectPayload();
        Assert.assertEquals(1, objectPayload.size());
        dataListById = (Map<String, Object>) objectPayload.get(0);
        Assert.assertEquals(3, dataListById.size());
        Assert.assertEquals("one", dataListById.get("id"));
        Assert.assertEquals("gauge", dataListById.get("type"));
        List<Map<String, Number>> dataList = (List<Map<String, Number>>) dataListById.get("data");
        Assert.assertEquals(1, dataList.size());
        Assert.assertEquals(12345, dataList.get(0).get("timestamp").longValue());
        Assert.assertEquals(1.200, dataList.get(0).get("value").doubleValue(), 0.1);

        builder.addDataPoint("one", 54321, 9.8);
        objectPayload = builder.toObjectPayload();
        Assert.assertEquals(1, objectPayload.size()); // still 1, we just added more metrics for id "one"
        dataListById = (Map<String, Object>) objectPayload.get(0);
        Assert.assertEquals(3, dataListById.size());
        Assert.assertEquals("one", dataListById.get("id"));
        Assert.assertEquals("gauge", dataListById.get("type"));
        dataList = (List<Map<String, Number>>) dataListById.get("data");
        Assert.assertEquals(2, dataList.size());
        Assert.assertEquals(12345, dataList.get(0).get("timestamp").longValue());
        Assert.assertEquals(1.200, dataList.get(0).get("value").doubleValue(), 0.1);
        Assert.assertEquals(54321, dataList.get(1).get("timestamp").longValue());
        Assert.assertEquals(9.800, dataList.get(1).get("value").doubleValue(), 0.1);

        Assert.assertNotNull(builder.toPayload());
    }

    @SuppressWarnings("unchecked")
    @Test
    public void testWithMultipleMetricIds() {
        List<Map<String, Object>> objectPayload;
        Map<String, Object> oneDataListById;
        Map<String, Object> twoDataListById;

        MetricsOnlyMetricDataPayloadBuilder builder = new MetricsOnlyMetricDataPayloadBuilder();
        builder.addDataPoint("one", 12345, 1.2);
        builder.addDataPoint("two", 45678, 11.22);
        builder.addDataPoint("one", 56789, 9.8);
        builder.addDataPoint("two", 87654, 99.88);

        objectPayload = builder.toObjectPayload();
        Assert.assertEquals(2, objectPayload.size());
        oneDataListById = (Map<String, Object>) objectPayload.get(0);
        twoDataListById = (Map<String, Object>) objectPayload.get(1);
        Assert.assertEquals(3, oneDataListById.size());
        Assert.assertEquals(3, twoDataListById.size());
        Assert.assertEquals("one", oneDataListById.get("id"));
        Assert.assertEquals("two", twoDataListById.get("id"));
        Assert.assertEquals("gauge", oneDataListById.get("type"));
        Assert.assertEquals("gauge", twoDataListById.get("type"));

        List<Map<String, Number>> dataList = (List<Map<String, Number>>) oneDataListById.get("data");
        Assert.assertEquals(2, dataList.size());
        Assert.assertEquals(12345, dataList.get(0).get("timestamp").longValue());
        Assert.assertEquals(1.200, dataList.get(0).get("value").doubleValue(), 0.1);
        Assert.assertEquals(56789, dataList.get(1).get("timestamp").longValue());
        Assert.assertEquals(9.800, dataList.get(1).get("value").doubleValue(), 0.1);

        dataList = (List<Map<String, Number>>) twoDataListById.get("data");
        Assert.assertEquals(2, dataList.size());
        Assert.assertEquals(45678, dataList.get(0).get("timestamp").longValue());
        Assert.assertEquals(11.22, dataList.get(0).get("value").doubleValue(), 0.1);
        Assert.assertEquals(87654, dataList.get(1).get("timestamp").longValue());
        Assert.assertEquals(99.88, dataList.get(1).get("value").doubleValue(), 0.1);

        Assert.assertNotNull(builder.toPayload());
    }
}
