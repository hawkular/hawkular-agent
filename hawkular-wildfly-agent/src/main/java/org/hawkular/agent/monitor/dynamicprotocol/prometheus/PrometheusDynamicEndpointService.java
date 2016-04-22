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
package org.hawkular.agent.monitor.dynamicprotocol.prometheus;

import java.net.MalformedURLException;
import java.util.Collection;

import org.hawkular.agent.monitor.api.HawkularWildFlyAgentContext;
import org.hawkular.agent.monitor.api.MetricDataPayloadBuilder;
import org.hawkular.agent.monitor.api.MetricStorage;
import org.hawkular.agent.monitor.dynamicprotocol.DynamicEndpointService;
import org.hawkular.agent.monitor.extension.MonitorServiceConfiguration.DynamicEndpointConfiguration;
import org.hawkular.agent.monitor.inventory.MonitoredEndpoint;
import org.hawkular.agent.monitor.inventory.Name;
import org.hawkular.agent.monitor.log.AgentLoggers;
import org.hawkular.agent.monitor.log.MsgLogger;
import org.hawkular.agent.monitor.protocol.EndpointService;
import org.hawkular.agent.prometheus.PrometheusScraper;
import org.hawkular.agent.prometheus.types.Counter;
import org.hawkular.agent.prometheus.types.Gauge;
import org.hawkular.agent.prometheus.types.Histogram;
import org.hawkular.agent.prometheus.types.Metric;
import org.hawkular.agent.prometheus.types.MetricFamily;
import org.hawkular.agent.prometheus.types.Summary;
import org.hawkular.agent.prometheus.walkers.PrometheusMetricsWalker;
import org.hawkular.metrics.client.common.MetricType;

/**
 * The endpoint service responsible for scraping metrics from a Prometheus endpoint and writing
 * the data to Hawkular Metrics.
 *
 * Prometheus has no concept of inventory (resources, resource types) so nothing is done with
 * Hawkular Inventory here.
 *
 * Note that if {@link DynamicEndpointService#getMetrics()} returns an empty collection, that means this
 * service will collect every metric that is scraped from the Prometheus endpoints. If it is not empty,
 * only those metrics named in that collection will be stored and all others will be ignored.
 *
 * @see EndpointService
 */
public class PrometheusDynamicEndpointService extends DynamicEndpointService {
    private static final MsgLogger log = AgentLoggers.getLogger(PrometheusDynamicEndpointService.class);

    public PrometheusDynamicEndpointService(String feedId, MonitoredEndpoint<DynamicEndpointConfiguration> endpoint,
            HawkularWildFlyAgentContext hawkularStorage, Collection<Name> metrics) {
        super(feedId, endpoint, hawkularStorage, metrics);
    }

    @Override
    public void run() {
        DynamicEndpointConfiguration endpointConfig = getMonitoredEndpoint().getEndpointConfiguration();
        log.tracef("Prometheus job starting: %s", endpointConfig);

        PrometheusScraper scraper;

        try {
            // TODO right now we do nothing with https, credentials, or security realm - assume its all unencrypted http
            scraper = new PrometheusScraper(endpointConfig.getConnectionData().getUri().toURL());
        } catch (MalformedURLException e) {
            log.errorf(e, "Error with Prometheus endpoint [%s], stopping this endpoint service", endpointConfig);
            // Throwing exception stops our job - which we want to do because no sense continuing with a bad URL.
            // Should never get here anyway, validity of URL is checked when agent started up and read the config.
            throw new RuntimeException(e);
        }

        try {
            scraper.scrape(new Walker());
        } catch (Throwable t) {
            log.errorf(t, "Failed to scrape data from Prometheus endpoint [%s]", endpointConfig);
        }
    }

    class Walker implements PrometheusMetricsWalker {

        private MetricStorage metricStorage;

        public Walker() {
        }

        @Override
        public void walkStart() {
            HawkularWildFlyAgentContext storage = getHawkularStorage();
            metricStorage = storage.getMetricStorage();
        }

        @Override
        public void walkFinish(int familiesProcessed, int metricsProcessed) {
            return; // no-op
        }

        @Override
        public void walkMetricFamily(MetricFamily family, int index) {
            if (shouldMetricBeIgnored(family.getName())) {
                return;
            }
            log.debugf("Processing Prometheus Metric Family [%d]: [%s] (%s) (%d total metrics)",
                    index + 1,
                    family.getName(),
                    family.getType(),
                    family.getMetrics().size());
        }

        @Override
        public void walkCounterMetric(MetricFamily family, Counter metric, int index) {
            if (shouldMetricBeIgnored(metric.getName())) {
                return;
            }
            log.debugf("Processing Prometheus Counter Metric [%s|%s]: value=%f",
                    family.getName(),
                    buildLabelListString(metric.getLabels(), null, null),
                    metric.getValue());

            String key = generateKey(metric);
            log.debugf("Storing counter in Hawkular Metrics with key: %s", key);
            MetricDataPayloadBuilder bldr = metricStorage.createMetricDataPayloadBuilder();
            bldr.addDataPoint(key, System.currentTimeMillis(), metric.getValue(), MetricType.COUNTER);
        }

        @Override
        public void walkGaugeMetric(MetricFamily family, Gauge metric, int index) {
            if (shouldMetricBeIgnored(metric.getName())) {
                return;
            }
            log.debugf("Processing Prometheus Gauge Metric [%s|%s]: value=%f",
                    family.getName(),
                    buildLabelListString(metric.getLabels(), null, null),
                    metric.getValue());

            String key = generateKey(metric);
            log.debugf("Storing gauge in Hawkular Metrics with key: %s", key);
            MetricDataPayloadBuilder bldr = metricStorage.createMetricDataPayloadBuilder();
            bldr.addDataPoint(key, System.currentTimeMillis(), metric.getValue(), MetricType.GAUGE);
        }

        @Override
        public void walkSummaryMetric(MetricFamily family, Summary metric, int index) {
            if (shouldMetricBeIgnored(metric.getName())) {
                return;
            }
            log.debugf("Prometheus SUMMARY metrics not yet supported - skipping [%s|%s|%s]",
                    family.getName(),
                    buildLabelListString(metric.getLabels(), null, null),
                    metric.getQuantiles());
        }

        @Override
        public void walkHistogramMetric(MetricFamily family, Histogram metric, int index) {
            if (shouldMetricBeIgnored(metric.getName())) {
                return;
            }
            log.debugf("Prometheus HISTOGRAM metrics not yet supported - skipping [%s|%s|%s]",
                    family.getName(),
                    buildLabelListString(metric.getLabels(), null, null),
                    metric.getBuckets());
        }

        private boolean shouldMetricBeIgnored(String metricName) {
            Collection<Name> metricsToProcess = getMetrics();
            if (metricsToProcess.isEmpty()) {
                return false; // empty list of metrics means we are to process every metric that is scraped
            }
            return !metricsToProcess.contains(new Name(metricName));
        }

        private String generateKey(Metric metric) {
            // TODO figure out a good key
            StringBuilder key = new StringBuilder();

            key.append(getFeedId()).append("_");
            if (!getMonitoredEndpoint().getEndpointConfiguration().getLabels().isEmpty()) {
                key.append(getMonitoredEndpoint().getEndpointConfiguration().getLabels().toString()).append("_");
            }
            key.append(metric.getName()).append("_");
            if (!metric.getLabels().isEmpty()) {
                key.append(buildLabelListString(metric.getLabels(), null, null)).append("_");
            }

            return key.toString();
        }
    }
}
