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

import java.net.URL;
import java.util.List;

import io.prometheus.client.Metrics.MetricFamily;

public class SimplePrometheusMetricsWalker extends AbstractPrometheusMetricsWalker {

    private final URL url;

    public SimplePrometheusMetricsWalker(List<MetricFamily> metricFamilies) {
        this(metricFamilies, null);
    }

    /**
     * Use this constructor if you know the URL where the metric data came from.
     *
     * @param metricFamilies
     * @param url the protocol endpoint that supplied the Prometheus metric data
     */
    public SimplePrometheusMetricsWalker(List<MetricFamily> metricFamilies, URL url) {
        super(metricFamilies);
        this.url = url;
    }

    @Override
    public void walkStart() {
        if (url != null) {
            System.out.println("Scraping metrics from Prometheus protocol endpoint: " + url);
        }
    }

    @Override
    public void walkFinish(int familiesProcessed, int metricsProcessed) {
        if (metricsProcessed == 0) {
            System.out.println("There are no metrics");
        }
    }

    @Override
    public void walkMetricFamily(MetricFamilyInformation familyInfo) {
        System.out.printf("* %s (%s): %s\n", familyInfo.getName(), familyInfo.getMetricType(), familyInfo.getHelp());
    }

    @Override
    public void walkCounterMetric(MetricInformation metricInfo, double value) {
        System.out.printf("  +%2d. %s%s [%f]\n",
                metricInfo.getIndex(),
                metricInfo.getMetricFamilyInformation().getName(),
                buildLabelListString(metricInfo.getLabels()),
                value);
    }

    @Override
    public void walkGaugeMetric(MetricInformation metricInfo, double value) {
        System.out.printf("  +%2d. %s%s [%f]\n",
                metricInfo.getIndex(),
                metricInfo.getMetricFamilyInformation().getName(),
                buildLabelListString(metricInfo.getLabels()),
                value);
    }

    @Override
    public void walkSummaryMetric(MetricInformation metricInfo, long count, double sum) {
        System.out.printf("  +%2d. %s%s [%d/%f]\n",
                metricInfo.getIndex(),
                metricInfo.getMetricFamilyInformation().getName(),
                buildLabelListString(metricInfo.getLabels()),
                count,
                sum);
    }
}