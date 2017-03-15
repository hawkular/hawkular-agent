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

import java.util.List;
import java.util.Map;

import org.hawkular.metrics.client.common.MetricType;
import org.junit.Assert;
import org.junit.Test;

public class MetricDataPayloadBuilderTest {

    @Test
    public void testEmpty() {
        MetricDataPayloadBuilderImpl builder = new MetricDataPayloadBuilderImpl();
        Assert.assertEquals(4, builder.toObjectPayload().size());
        Assert.assertEquals("{\"counters\":[],\"strings\":[],\"availabilities\":[],\"gauges\":[]}",
                getPayloadJson(builder));
    }

    @SuppressWarnings("unchecked")
    @Test
    public void testWithOneMetricId() {
        List<Map<String, Object>> objectPayload;
        Map<String, Object> dataListById;

        MetricDataPayloadBuilderImpl builder = new MetricDataPayloadBuilderImpl();
        builder.addDataPoint("one", 12345, 1.2, MetricType.GAUGE);
        objectPayload = builder.toObjectPayload().get("gauges");
        Assert.assertEquals(1, objectPayload.size());
        dataListById = (Map<String, Object>) objectPayload.get(0);
        Assert.assertEquals(2, dataListById.size());
        Assert.assertEquals("one", dataListById.get("id"));
        List<Map<String, Number>> dataList = (List<Map<String, Number>>) dataListById.get("data");
        Assert.assertEquals(1, dataList.size());
        Assert.assertEquals(12345, dataList.get(0).get("timestamp").longValue());
        Assert.assertEquals(1.200, dataList.get(0).get("value").doubleValue(), 0.1);

        builder.addDataPoint("one", 54321, 9.8, MetricType.GAUGE);
        objectPayload = builder.toObjectPayload().get("gauges");
        Assert.assertEquals(1, objectPayload.size()); // still 1, we just added more metrics for id "one"
        dataListById = (Map<String, Object>) objectPayload.get(0);
        Assert.assertEquals(2, dataListById.size());
        Assert.assertEquals("one", dataListById.get("id"));
        dataList = (List<Map<String, Number>>) dataListById.get("data");
        Assert.assertEquals(2, dataList.size());
        Assert.assertEquals(12345, dataList.get(0).get("timestamp").longValue());
        Assert.assertEquals(1.200, dataList.get(0).get("value").doubleValue(), 0.1);
        Assert.assertEquals(54321, dataList.get(1).get("timestamp").longValue());
        Assert.assertEquals(9.800, dataList.get(1).get("value").doubleValue(), 0.1);

        Assert.assertNotNull(getPayloadJson(builder));
    }

    @SuppressWarnings("unchecked")
    @Test
    public void testWithMultipleMetricIds() {
        List<Map<String, Object>> objectPayload;
        Map<String, Object> oneDataListById;
        Map<String, Object> twoDataListById;

        MetricDataPayloadBuilderImpl builder = new MetricDataPayloadBuilderImpl();
        builder.addDataPoint("one", 12345, 1.2, MetricType.GAUGE);
        builder.addDataPoint("two", 45678, 11.22, MetricType.GAUGE);
        builder.addDataPoint("one", 56789, 9.8, MetricType.GAUGE);
        builder.addDataPoint("two", 87654, 99.88, MetricType.GAUGE);

        objectPayload = builder.toObjectPayload().get("gauges");
        Assert.assertEquals(2, objectPayload.size());
        oneDataListById = (Map<String, Object>) objectPayload.get(0);
        twoDataListById = (Map<String, Object>) objectPayload.get(1);
        Assert.assertEquals(2, oneDataListById.size());
        Assert.assertEquals(2, twoDataListById.size());
        Assert.assertEquals("one", oneDataListById.get("id"));
        Assert.assertEquals("two", twoDataListById.get("id"));

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

        Assert.assertNotNull(getPayloadJson(builder));
    }

    @SuppressWarnings("unchecked")
    @Test
    public void testWithStringMetricsOneMetricId() {
        List<Map<String, Object>> objectPayload;
        Map<String, Object> dataListById;

        MetricDataPayloadBuilderImpl builder = new MetricDataPayloadBuilderImpl();
        builder.addDataPoint("one", 12345, "string value");
        objectPayload = builder.toObjectPayload().get("strings");
        Assert.assertEquals(1, objectPayload.size());
        dataListById = (Map<String, Object>) objectPayload.get(0);
        Assert.assertEquals(2, dataListById.size());
        Assert.assertEquals("one", dataListById.get("id"));
        List<Map<String, Object>> dataList = (List<Map<String, Object>>) dataListById.get("data");
        Assert.assertEquals(1, dataList.size());
        Assert.assertEquals(12345, ((Number) dataList.get(0).get("timestamp")).longValue());
        Assert.assertEquals("string value", ((String) dataList.get(0).get("value")).toString());

        builder.addDataPoint("one", 54321, "another value");
        objectPayload = builder.toObjectPayload().get("strings");
        Assert.assertEquals(1, objectPayload.size()); // still 1, we just added more metrics for id "one"
        dataListById = (Map<String, Object>) objectPayload.get(0);
        Assert.assertEquals(2, dataListById.size());
        Assert.assertEquals("one", dataListById.get("id"));
        dataList = (List<Map<String, Object>>) dataListById.get("data");
        Assert.assertEquals(2, dataList.size());
        Assert.assertEquals(12345, ((Number) dataList.get(0).get("timestamp")).longValue());
        Assert.assertEquals("string value", ((String) dataList.get(0).get("value")).toString());
        Assert.assertEquals(54321, ((Number) dataList.get(1).get("timestamp")).longValue());
        Assert.assertEquals("another value", ((String) dataList.get(1).get("value")).toString());

        Assert.assertNotNull(getPayloadJson(builder));
    }

    @SuppressWarnings("unchecked")
    @Test
    public void testWithStringMetricsMultipleMetricIds() {
        List<Map<String, Object>> objectPayload;
        Map<String, Object> oneDataListById;
        Map<String, Object> twoDataListById;

        MetricDataPayloadBuilderImpl builder = new MetricDataPayloadBuilderImpl();
        builder.addDataPoint("one", 12345, "one value1");
        builder.addDataPoint("two", 45678, "two value1");
        builder.addDataPoint("one", 56789, "one value2");
        builder.addDataPoint("two", 87654, "two value2");

        objectPayload = builder.toObjectPayload().get("strings");
        Assert.assertEquals(2, objectPayload.size());
        oneDataListById = (Map<String, Object>) objectPayload.get(0);
        twoDataListById = (Map<String, Object>) objectPayload.get(1);
        Assert.assertEquals(2, oneDataListById.size());
        Assert.assertEquals(2, twoDataListById.size());
        Assert.assertEquals("one", oneDataListById.get("id"));
        Assert.assertEquals("two", twoDataListById.get("id"));

        List<Map<String, Object>> dataList = (List<Map<String, Object>>) oneDataListById.get("data");
        Assert.assertEquals(2, dataList.size());
        Assert.assertEquals(12345, ((Number) dataList.get(0).get("timestamp")).longValue());
        Assert.assertEquals("one value1", ((String) dataList.get(0).get("value").toString()));
        Assert.assertEquals(56789, ((Number) dataList.get(1).get("timestamp")).longValue());
        Assert.assertEquals("one value2", ((String) dataList.get(1).get("value").toString()));

        dataList = (List<Map<String, Object>>) twoDataListById.get("data");
        Assert.assertEquals(2, dataList.size());
        Assert.assertEquals(45678, ((Number) dataList.get(0).get("timestamp")).longValue());
        Assert.assertEquals("two value1", ((String) dataList.get(0).get("value").toString()));
        Assert.assertEquals(87654, ((Number) dataList.get(1).get("timestamp")).longValue());
        Assert.assertEquals("two value2", ((String) dataList.get(1).get("value").toString()));

        Assert.assertNotNull(getPayloadJson(builder));
    }

    private String getPayloadJson(MetricDataPayloadBuilderImpl builder) {
        String payload = builder.toPayload();
        System.out.println("=======\n" + payload + "\n=======");
        return payload;
    }
}
