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
import io.prometheus.client.Metrics.MetricType;

public class XMLPrometheusMetricsWalker extends AbstractPrometheusMetricsWalker {

    private final URL url;

    public XMLPrometheusMetricsWalker(List<MetricFamily> metricFamilies) {
        this(metricFamilies, null);
    }

    /**
     * Use this constructor if you know the URL where the metric data came from.
     *
     * @param metricFamilies
     * @param url the protocol endpoint that supplied the Prometheus metric data
     */
    public XMLPrometheusMetricsWalker(List<MetricFamily> metricFamilies, URL url) {
        super(metricFamilies);
        this.url = url;
    }

    @Override
    public void walkStart() {
        System.out.printf("<metricFamilies>\n");

        // only provide the URL endpoint element if we know the URL where the metrics came from
        if (url != null) {
            System.out.printf("  <url>%s</url>\n", url);
        }
    }

    @Override
    public void walkFinish(int familiesProcessed, int metricsProcessed) {
        if (familiesProcessed > 0) {
            System.out.printf("  </metricFamily>\n");
        }
        System.out.println("</metricFamilies>");
    }

    @Override
    public void walkMetricFamily(MetricFamilyInformation familyInfo) {
        if (familyInfo.getIndex() > 0) {
            System.out.printf("  </metricFamily>\n");
        }

        System.out.printf("  <metricFamily>\n");
        System.out.printf("    <name>%s</name>\n", familyInfo.getName());
        System.out.printf("    <type>%s</type>\n", familyInfo.getMetricType());
        System.out.printf("    <help>%s</help>\n", familyInfo.getHelp());
    }

    @Override
    public void walkCounterMetric(MetricInformation info, double value) {
        System.out.printf("    <metric>\n");
        System.out.printf("      <name>%s</name>\n", info.getMetricFamilyInformation().getName());
        System.out.printf("      <type>%s</type>\n", MetricType.COUNTER);
        System.out.printf("      <labels>%s</labels>\n", buildLabelListString(info.getLabels()));
        System.out.printf("      <value>%f</value>\n", value);
        System.out.printf("    </metric>\n");
    }

    @Override
    public void walkGaugeMetric(MetricInformation info, double value) {
        System.out.printf("    <metric>\n");
        System.out.printf("      <name>%s</name>\n", info.getMetricFamilyInformation().getName());
        System.out.printf("      <type>%s</type>\n", MetricType.GAUGE);
        System.out.printf("      <labels>%s</labels>\n", buildLabelListString(info.getLabels()));
        System.out.printf("      <value>%f</value>\n", value);
        System.out.printf("    </metric>\n");
    }

    @Override
    public void walkSummaryMetric(MetricInformation info, long count, double sum) {
        System.out.printf("    <metric>\n");
        System.out.printf("      <name>%s</name>\n", info.getMetricFamilyInformation().getName());
        System.out.printf("      <type>%s</type>\n", MetricType.SUMMARY);
        System.out.printf("      <labels>%s</labels>\n", buildLabelListString(info.getLabels()));
        System.out.printf("      <count>%d</count>\n", count);
        System.out.printf("      <sum>%f</sum>\n", sum);
        System.out.printf("    </metric>\n");
    }
}