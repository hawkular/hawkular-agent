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

package org.hawkular.agent.prometheus;

import java.io.InputStream;
import java.net.URL;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import org.jboss.logging.Logger.Level;
import org.junit.Assert;
import org.junit.Test;

import io.prometheus.client.Metrics.MetricFamily;

public class PrometheusScraperTest {
    @Test
    public void testGetMetricFamily() throws Exception {

        List<MetricFamily> metrics;
        PrometheusScraper scraper = new PrometheusScraper();
        try (InputStream testData = this.getClass().getClassLoader().getResourceAsStream("prometheus.data")) {
            metrics = scraper.scrape(testData);
        }
        assertMetrics(metrics);
    }

    @Test
    public void testUrl() throws Exception {
        URL testData = this.getClass().getClassLoader().getResource("prometheus.data");
        List<MetricFamily> metrics;
        PrometheusScraperUrl scraper = new PrometheusScraperUrl(testData);
        metrics = scraper.scrape();
        assertMetrics(metrics);
    }

    /**
     * Both our tests load in the same metric data set - this method checks that everything is as expected.
     *
     * @param metricFamilies metrics our test scraped
     */
    private void assertMetrics(List<MetricFamily> metricFamilies) {
        System.out.println("\n=== TESTING METRICS ===\n");

        Assert.assertNotNull(metricFamilies);
        Assert.assertEquals(71, metricFamilies.size());

        // walk the data and make sure it has what we expect
        final AtomicInteger familyCount = new AtomicInteger(0);
        final AtomicInteger fullCount = new AtomicInteger(0);
        new LoggingPrometheusMetricsWalker(metricFamilies, Level.INFO) {
            public void walkMetricFamily(MetricFamilyInformation familyInfo) {
                super.walkMetricFamily(familyInfo);
                familyCount.incrementAndGet();
                fullCount.addAndGet(familyInfo.getTotalMetrics());
            }
        }.walk();
        Assert.assertEquals(metricFamilies.size(), familyCount.get());
        Assert.assertEquals(126, fullCount.get());
    }
}
