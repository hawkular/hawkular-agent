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

import org.hawkular.metrics.client.common.MetricType;
import org.junit.Assert;
import org.junit.Test;

public class MetricDataPointTest {

    @Test
    public void numericValue() {
        NumericMetricDataPoint num = new NumericMetricDataPoint("key", 45678L, 123.456, MetricType.GAUGE, null);
        Assert.assertEquals("key", num.getKey());
        Assert.assertEquals(45678, num.getTimestamp());
        Assert.assertEquals(123.456, num.getMetricValue().doubleValue(), 0.1);
        Assert.assertEquals(MetricType.GAUGE, num.getMetricType());

        num = new NumericMetricDataPoint("key", 45678L, 123, MetricType.COUNTER, null);
        Assert.assertEquals("key", num.getKey());
        Assert.assertEquals(45678, num.getTimestamp());
        Assert.assertEquals(123, num.getMetricValue().longValue());
        Assert.assertEquals(MetricType.COUNTER, num.getMetricType());
    }

    @Test
    public void stringValue() {
        StringMetricDataPoint str = new StringMetricDataPoint("key", 45678L, "val", null);
        Assert.assertEquals("key", str.getKey());
        Assert.assertEquals(45678, str.getTimestamp());
        Assert.assertEquals("val", str.getMetricValue());
        Assert.assertEquals(MetricType.STRING, str.getMetricType());

        str = new StringMetricDataPoint("key", 45678L, null, null);
        Assert.assertEquals("key", str.getKey());
        Assert.assertEquals(45678, str.getTimestamp());
        Assert.assertEquals("", str.getMetricValue()); // note: null was converted to empty string
        Assert.assertEquals(MetricType.STRING, str.getMetricType());
    }

    @Test
    public void tenantId() {
        NumericMetricDataPoint num = new NumericMetricDataPoint("key", 1234L, 0.0, MetricType.GAUGE, null);
        Assert.assertNull(num.getTenantId());
        StringMetricDataPoint str = new StringMetricDataPoint("key", 1234L, "val", null);
        Assert.assertNull(str.getTenantId());

        num = new NumericMetricDataPoint("key", 1234L, 0.0, MetricType.GAUGE, "my-tenant");
        Assert.assertEquals("my-tenant", num.getTenantId());
        str = new StringMetricDataPoint("key", 1234L, "val", "my-tenant");
        Assert.assertEquals("my-tenant", str.getTenantId());
    }

    @Test(expected = IllegalArgumentException.class)
    public void badType1() {
        new NumericMetricDataPoint("key", 1234L, 0.0, MetricType.STRING, null);
    }

    @Test(expected = IllegalArgumentException.class)
    public void badType2() {
        new NumericMetricDataPoint("key", 1234L, 0.0, MetricType.AVAILABILITY, null);
    }

    @Test(expected = IllegalArgumentException.class)
    public void badType3() {
        new NumericMetricDataPoint("key", 1234L, 0.0, null, null);
    }

}
