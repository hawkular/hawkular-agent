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

import java.util.List;

import org.jboss.logging.Logger;
import org.jboss.logging.Logger.Level;

import io.prometheus.client.Metrics.LabelPair;
import io.prometheus.client.Metrics.MetricFamily;

/**
 * This implementation simply logs the metric values.
 */
public class LoggingPrometheusMetricsWalker extends AbstractPrometheusMetricsWalker {
    private static final Logger log = Logger.getLogger(LoggingPrometheusMetricsWalker.class);
    private Level logLevel;

    public LoggingPrometheusMetricsWalker(List<MetricFamily> metricFamilies) {
        this(metricFamilies, null);
    }

    public LoggingPrometheusMetricsWalker(List<MetricFamily> metricFamilies, Level logLevel) {
        super(metricFamilies);
        this.logLevel = (logLevel != null) ? logLevel : Level.DEBUG;
    }

    @Override
    public void walkStart() {
    }

    @Override
    public void walkFinish(int familiesProcessed, int metricsProcessed) {
    }

    @Override
    public void walkMetricFamily(MetricFamilyInformation familyInfo) {
        log.logf(getLogLevel(), "Metric Family [%s] of type [%s] has [%d] metrics: %s",
                familyInfo.getName(),
                familyInfo.getMetricType(),
                familyInfo.getTotalMetrics(),
                familyInfo.getHelp());
    }

    @Override
    public void walkCounterMetric(MetricInformation metricInfo, double value) {
        log.logf(getLogLevel(), "COUNTER: %s%s=%f",
                metricInfo.getMetricFamilyInformation().getName(),
                buildLabelListString(metricInfo.getLabels()),
                value);
    }

    @Override
    public void walkGaugeMetric(MetricInformation metricInfo, double value) {
        log.logf(getLogLevel(), "GAUGE: %s%s=%f",
                metricInfo.getMetricFamilyInformation().getName(),
                buildLabelListString(metricInfo.getLabels()),
                value);
    }

    @Override
    public void walkSummaryMetric(MetricInformation metricInfo, long sampleCount, double sampleSum) {
        log.logf(getLogLevel(), "SUMMARY: %s%s: count=%d, sum=%f",
                metricInfo.getMetricFamilyInformation().getName(),
                buildLabelListString(metricInfo.getLabels()),
                sampleCount,
                sampleSum);
    }

    /* histograms not yet supported - wait for prometheus to release 0.0.3 version of model API jar
    @Override
    public void walkHistogramMetric(MetricInfomation metricInfo, long sampleCount, double sampleSum,
            List<Bucket> bucketList) {
        StringBuilder bucketListString = new StringBuilder("{");
        for (Bucket bucket : bucketList) {
            if (bucketListString.length() > 1) {
                bucketListString.append(",");
            }
            bucketListString.append(bucket.getCumulativeCount());
        }
        bucketListString.append("}");
        log.logf(getLogLevel(), "HISTOGRAM: %s%s: count=%d, sum=%f, buckets=%s",
                metricInfo.getMetricName(),
                buildLabelListString(labels),
                sampleCount,
                sampleSum,
                bucketListString);
    }
    */

    /**
     * The default implementations of the walk methods will log the metric data with this given log level.
     *
     * @return the log level
     */
    protected Level getLogLevel() {
        return this.logLevel;
    }

    protected String buildLabelListString(List<LabelPair> labelList) {
        return "{" + super.buildLabelListString(labelList) + "}";
    }
}
