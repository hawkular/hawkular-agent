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

import java.util.Collections;
import java.util.List;

import io.prometheus.client.Metrics.LabelPair;
import io.prometheus.client.Metrics.Metric;
import io.prometheus.client.Metrics.MetricFamily;
import io.prometheus.client.Metrics.MetricType;

/**
 * This will iterate over a list of Prometheus metrics.
 *
 * Subclasses can extend this class and implement the different walkXYZ methods.
 */
public abstract class AbstractPrometheusMetricsWalker {
    private final List<MetricFamily> metricFamilies;

    /**
     * Provides some basic information about the metric family currently being processed,
     * such as its name and type. No metric details are stored here, only information
     * common for the metric family itself.
     */
    public class MetricFamilyInformation {
        private final String name;
        private final MetricType metricType;
        private final int totalMetrics;
        private final String help;
        private final int index;
        private final int totalFamilies;

        public MetricFamilyInformation(MetricFamily metricFamily, int index, int totalFamilies) {
            this.name = metricFamily.getName();
            this.metricType = metricFamily.getType();
            this.totalMetrics = metricFamily.getMetricCount();
            this.help = metricFamily.getHelp();
            this.index = index;
            this.totalFamilies = totalFamilies;
        }

        /**
         * @return The name of the metric family. Each metric in the family have this as their metric name.
         */
        public String getName() {
            return name;
        }

        /**
         * @return All metrics within this family have this same type.
         */
        public MetricType getMetricType() {
            return metricType;
        }

        /**
         * @return the total number of metrics that are found in this metric family being walked.
         */
        public int getTotalMetrics() {
            return totalMetrics;
        }

        /**
         * @return the metric family help description
         */
        public String getHelp() {
            return help;
        }

        /**
         * This is the position of the metric family as it was found in the list of metric families.
         * A metric family index ranges from 0 to ({@link #getTotalFamilies()}-1).
         * A metric family with index 0 indicates it was the first metric family found in the
         * Prometheus data buffer.
         *
         * @return the metric family's position within the entire set of metric families
         */
        public int getIndex() {
            return index;
        }

        /**
         * @return the total number of metric families that are found in the entire Prometheus data buffer
         * that is currently being processed.
         */
        public int getTotalFamilies() {
            return totalFamilies;
        }
    }

    /**
     * Provides some basic information about the specific metric being processed,
     * such as its name and labels. No metric values are stored here, only information
     * common across all metric types are stored here.
     */
    public class MetricInformation {
        private final MetricFamilyInformation metricFamily;
        private final List<LabelPair> labels;
        private final int index;

        public MetricInformation(MetricFamilyInformation metricFamily, Metric metric, int index) {
            this.metricFamily = metricFamily;
            this.labels = metric.getLabelList();
            this.index = index;
        }

        /**
         * @return The information about the metric family such as metric name, and
         * total number of metrics in the family.
         */
        public MetricFamilyInformation getMetricFamilyInformation() {
            return metricFamily;
        }

        /**
         * @return the labels associated with this metric
         */
        public List<LabelPair> getLabels() {
            return labels;
        }

        /**
         * This is the position of the metric as it was found in the list of metrics within its metric family.
         * A metric index ranges from 0 to ({@link #getTotalInFamily()}-1). A metric with index 0 indicates it was
         * the first metric found in the metric family's list of metrics.
         *
         * @return the metric's position within the metric family
         */
        public int getIndex() {
            return index;
        }
    }

    /**
     * Constructor that builds a walker which will walk the given metrics.
     *
     * @param metricFamilies these are the Prometheus metrics this walker will traverse.
     */
    public AbstractPrometheusMetricsWalker(List<MetricFamily> metricFamilies) {
        this.metricFamilies = (metricFamilies != null) ? Collections.unmodifiableList(metricFamilies)
                : Collections.emptyList();
    }

    /**
     * This will iterate over the list of given metrics and notify the walker of each metric found.
     *
     * @param metricFamilies the metrics to walk
     * @param walker the object notified when metrics are found during the walk
     */
    public final void walk() {

        // tell the walker we are starting
        walkStart();

        int totalFamilies = this.metricFamilies.size();
        int totalMetrics = 0;
        int familyIndex = 0;

        for (MetricFamily metricFamily : this.metricFamilies) {
            MetricFamilyInformation familyInfo = new MetricFamilyInformation(metricFamily, familyIndex++,
                    totalFamilies);

            // let the walker know we are traversing a new family of metrics
            walkMetricFamily(familyInfo);

            // walk through each metric in the family
            int metricIndex = 0;

            for (Metric metric : metricFamily.getMetricList()) {
                MetricInformation metricInfo = new MetricInformation(familyInfo, metric, metricIndex++);

                switch (familyInfo.getMetricType()) {
                    case COUNTER:
                        walkCounterMetric(metricInfo, metric.getCounter().getValue());
                        break;

                    case GAUGE:
                        walkGaugeMetric(metricInfo, metric.getGauge().getValue());
                        break;

                    case SUMMARY:
                        walkSummaryMetric(metricInfo,
                                metric.getSummary().getSampleCount(),
                                metric.getSummary().getSampleSum());
                        break;

                    /* histograms not yet supported - wait for prometheus to release 0.0.3 version of model API jar
                    case HISTOGRAM:
                        walkHistogramMetric(info,
                                metric.getHistogram().getSampleCount(),
                                metric.getHistogram().getSampleSum(),
                                metric.getHistogram().getBucketList());
                        break;
                    */
                }
            }

            // finished processing the metrics for the current family
            totalMetrics += metricFamily.getMetricCount();
        }

        // tell the walker we have finished
        walkFinish(totalFamilies, totalMetrics);
    }

    /**
     * Called when a walk has been started.
     */
    public abstract void walkStart();

    /**
     * Called when a walk has traversed all the metrics.
     * @param familiesProcessed total number of families processed
     * @param metricsProcessed total number of metrics across all families processed
     */
    public abstract void walkFinish(int familiesProcessed, int metricsProcessed);

    /**
     * Called when a new metric family is about to be traversed.
     *
     * @param familyInfo information about the family being traversed such as the name, help description, etc.
     */
    public abstract void walkMetricFamily(MetricFamilyInformation familyInfo);

    /**
     * Called when a new counter metric is found.
     *
     * @param metricInfo information about the current metric being traversed
     * @param value the actual counter value
     */
    public abstract void walkCounterMetric(MetricInformation metricInfo, double value);

    /**
     * Called when a new gauge metric is found.
     *
     * @param metricInfo information about the current metric being traversed
     * @param value the actual gauge value
     */
    public abstract void walkGaugeMetric(MetricInformation metricInfo, double value);

    /**
     * Called when a new summary metric is found.
     *
     * @param metricInfo information about the current metric being traversed
     * @param sampleCount the number of samples in the summary
     * @param sampleSum the sum of the samples in the summary
     */
    public abstract void walkSummaryMetric(MetricInformation metricInfo, long sampleCount, double sampleSum);

    /**
     * Called when a new histogram metric is found.
     *
     * @param metricInfo information about the current metric being traversed
     * @param sampleCount the number of samples in the histogram
     * @param sampleSum the sum of the samples in the histogram
     * @param bucketList the buckets in the histogram
     */
    /* histograms not yet supported - wait for prometheus to release 0.0.3 version of model API jar
    public abstract void walkHistogramMetric(MetricInformation metricInfo, long sampleCount, double sampleSum,
            List<Bucket> bucketList);
    */

    /**
     * Convienence method that takes the given label list and returns a string in the form of
     * "labelName1=labelValue1,labelName2=labelValue2,..."
     *
     * @param labelList the label list
     * @return the string form of the labe list
     */
    protected String buildLabelListString(List<LabelPair> labelList) {
        if (labelList == null) {
            return "";
        }

        StringBuilder str = new StringBuilder("");
        for (LabelPair pair : labelList) {
            if (str.length() > 0) {
                str.append(",");
            }
            str.append(pair.getName()).append("=").append(pair.getValue());
        }
        return str.toString();
    }
}
