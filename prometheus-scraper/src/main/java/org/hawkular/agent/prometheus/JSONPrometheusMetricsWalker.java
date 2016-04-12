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

import java.util.Iterator;
import java.util.List;

import io.prometheus.client.Metrics.LabelPair;
import io.prometheus.client.Metrics.MetricFamily;

public class JSONPrometheusMetricsWalker extends AbstractPrometheusMetricsWalker {

    public JSONPrometheusMetricsWalker(List<MetricFamily> metricFamilies) {
        super(metricFamilies);
    }

    @Override
    public void walkStart() {
        System.out.println("[");
    }

    @Override
    public void walkFinish(int familiesProcessed, int metricsProcessed) {
        if (familiesProcessed > 0) {
            System.out.println("    ]");
            System.out.println("  }");
        }
        System.out.println("]");
    }

    @Override
    public void walkMetricFamily(MetricFamilyInformation familyInfo) {
        if (familyInfo.getIndex() > 0) {
            System.out.printf("    ]\n");
            System.out.printf("  },\n");
        }

        System.out.printf("  {\n");
        System.out.printf("    \"name\":\"%s\",\n", familyInfo.getName());
        System.out.printf("    \"help\":\"%s\",\n", familyInfo.getHelp());
        System.out.printf("    \"type\":\"%s\",\n", familyInfo.getMetricType());
        System.out.printf("    \"metrics\":[\n");
    }

    @Override
    public void walkCounterMetric(MetricInformation info, double value) {
        System.out.printf("      {\n");
        outputLabels(info.getLabels());
        System.out.printf("        \"value\":\"%f\"\n", value);
        if ((info.getIndex() + 1) == info.getMetricFamilyInformation().getTotalMetrics()) {
            System.out.printf("      }\n");
        } else {
            System.out.printf("      },\n"); // there are more coming
        }
    }

    @Override
    public void walkGaugeMetric(MetricInformation info, double value) {
        System.out.printf("      {\n");
        outputLabels(info.getLabels());
        System.out.printf("        \"value\":\"%f\"\n", value);
        if ((info.getIndex() + 1) == info.getMetricFamilyInformation().getTotalMetrics()) {
            System.out.printf("      }\n");
        } else {
            System.out.printf("      },\n"); // there are more coming
        }
    }

    @Override
    public void walkSummaryMetric(MetricInformation info, long count, double sum) {
        System.out.printf("      {\n");
        outputLabels(info.getLabels());
        System.out.printf("        \"count\":\"%d\",\n", count);
        System.out.printf("        \"sum\":\"%f\"\n", sum);
        if ((info.getIndex() + 1) == info.getMetricFamilyInformation().getTotalMetrics()) {
            System.out.printf("      }\n");
        } else {
            System.out.printf("      },\n"); // there are more coming
        }
    }

    private void outputLabels(List<LabelPair> labels) {
        if (labels == null || labels.isEmpty()) {
            return;
        }
        System.out.printf("        \"labels\":{\n");
        Iterator<LabelPair> iter = labels.iterator();
        while (iter.hasNext()) {
            LabelPair labelPair = iter.next();
            String comma = (iter.hasNext()) ? "," : "";
            System.out.printf("          \"%s\":\"%s\"%s\n", labelPair.getName(), labelPair.getValue(), comma);
        }
        System.out.printf("        },\n");
    }
}