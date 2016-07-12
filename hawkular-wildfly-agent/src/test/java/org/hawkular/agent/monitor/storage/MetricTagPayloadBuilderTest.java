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

import java.util.Map;

import org.hawkular.metrics.client.common.MetricType;
import org.junit.Assert;
import org.junit.Test;

public class MetricTagPayloadBuilderTest {

    @Test
    public void testEmpty() {
        MetricTagPayloadBuilderImpl builder = new MetricTagPayloadBuilderImpl();
        Assert.assertEquals(0, builder.toPayload().size());
    }

    @Test
    public void testWithOneMetricId() {
        String json;

        MetricTagPayloadBuilderImpl builder = new MetricTagPayloadBuilderImpl();

        builder.addTag("metric1", "tagname1", "tagvalue1", MetricType.GAUGE);
        json = builder.toPayload().get("gauges/metric1");
        Assert.assertEquals("{\"tagname1\":\"tagvalue1\"}", json);

        builder.addTag("metric1", "tagname2", "tagvalue2", MetricType.GAUGE);
        json = builder.toPayload().get("gauges/metric1");
        Assert.assertEquals("{\"tagname1\":\"tagvalue1\",\"tagname2\":\"tagvalue2\"}", json);
    }

    @Test
    public void testWithMultipleMetricIds() {
        MetricTagPayloadBuilderImpl builder = new MetricTagPayloadBuilderImpl();

        builder.addTag("metric1", "m1tagname1", "m1tagvalue1", MetricType.GAUGE);
        builder.addTag("metric1", "m1tagname2", "m1tagvalue2", MetricType.GAUGE);

        builder.addTag("metric2", "m2tagname1", "m2tagvalue1", MetricType.GAUGE);
        builder.addTag("metric2", "m2tagname2", "m2tagvalue2", MetricType.GAUGE);

        builder.addTag("metric3", "m3tagname1", "m3tagvalue1", MetricType.COUNTER);
        builder.addTag("metric3", "m3tagname2", "m3tagvalue2", MetricType.COUNTER);

        builder.addTag("metric4", "m4tagname1", "m4tagvalue1", MetricType.COUNTER);
        builder.addTag("metric4", "m4tagname2", "m4tagvalue2", MetricType.COUNTER);

        builder.addTag("metric5", "m5tagname1", "m5tagvalue1", MetricType.AVAILABILITY);
        builder.addTag("metric5", "m5tagname2", "m5tagvalue2", MetricType.AVAILABILITY);

        builder.addTag("metric6", "m6tagname1", "m6tagvalue1", MetricType.AVAILABILITY);
        builder.addTag("metric6", "m6tagname2", "m6tagvalue2", MetricType.AVAILABILITY);

        Map<String, String> payload = builder.toPayload();

        String json;

        json = payload.get("gauges/metric1");
        Assert.assertEquals("{\"m1tagname1\":\"m1tagvalue1\",\"m1tagname2\":\"m1tagvalue2\"}", json);
        json = payload.get("gauges/metric2");
        Assert.assertEquals("{\"m2tagname1\":\"m2tagvalue1\",\"m2tagname2\":\"m2tagvalue2\"}", json);
        json = payload.get("counters/metric3");
        Assert.assertEquals("{\"m3tagname1\":\"m3tagvalue1\",\"m3tagname2\":\"m3tagvalue2\"}", json);
        json = payload.get("counters/metric4");
        Assert.assertEquals("{\"m4tagname1\":\"m4tagvalue1\",\"m4tagname2\":\"m4tagvalue2\"}", json);
        json = payload.get("availability/metric5");
        Assert.assertEquals("{\"m5tagname1\":\"m5tagvalue1\",\"m5tagname2\":\"m5tagvalue2\"}", json);
        json = payload.get("availability/metric6");
        Assert.assertEquals("{\"m6tagname1\":\"m6tagvalue1\",\"m6tagname2\":\"m6tagvalue2\"}", json);

    }
}
