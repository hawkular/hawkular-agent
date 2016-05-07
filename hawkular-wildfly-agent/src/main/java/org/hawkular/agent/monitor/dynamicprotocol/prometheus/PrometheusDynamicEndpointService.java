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
import java.util.HashSet;
import java.util.Set;
import java.util.regex.Pattern;

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

    private final Set<String> metricExactNames;
    private final Set<Pattern> metricRegexNames;
    private final String tenantId;

    public PrometheusDynamicEndpointService(String feedId, MonitoredEndpoint<DynamicEndpointConfiguration> endpoint,
            HawkularWildFlyAgentContext hawkularStorage, Collection<Name> metrics) {
        super(feedId, endpoint, hawkularStorage, metrics);

        // set the tenant ID in case our endpoint wants to associate its data with a special tenant
        tenantId = endpoint.getEndpointConfiguration().getTenantId();

        // if no metric sets were assigned to us, that means we are to collect every metric.
        if (metrics.isEmpty()) {
            metricExactNames = null;
            metricRegexNames = null;
        } else {
            // Because processing regex patterns can be expensive (compared to just looking up exact names in a hashed
            // set) we will only process a metric name as a regex if it really is a regex. By definition, Prometheus
            // metric names contain only ASCII letters, digits (except for the first character), underscores, and
            // colons (that is, metric names must match the regex "[a-zA-Z_:][a-zA-Z0-9_:]*"). If a metric name has
            // only those characters, we know it is not a regex so we store them for exact name checking. Anything
            // else and we assume the metric name is really a regex, so we'll precompile its Pattern and store it so
            // we can match against it when we start collecting metrics
            metricExactNames = new HashSet<>();
            metricRegexNames = new HashSet<>();
            for (Name name : metrics) {
                String nameString = name.getNameString();
                if (nameString.matches("[a-zA-Z_:][a-zA-Z0-9_:]*")) {
                    metricExactNames.add(nameString);
                } else {
                    metricRegexNames.add(Pattern.compile(nameString));
                }
            }
        }

    }

    @Override
    public void run() {
        DynamicEndpointConfiguration endpointConfig = getMonitoredEndpoint().getEndpointConfiguration();
        log.tracef("Prometheus job starting: %s", endpointConfig);

        PrometheusScraper scraper;

        try {
            scraper = new HawkularPrometheusScraper(endpointConfig, getMonitoredEndpoint().getSSLContext());
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
        private MetricDataPayloadBuilder payloadBuilder;

        public Walker() {
        }

        @Override
        public void walkStart() {
            HawkularWildFlyAgentContext storage = getHawkularStorage();
            metricStorage = storage.getMetricStorage();

            payloadBuilder = metricStorage.createMetricDataPayloadBuilder();
            payloadBuilder.setTenantId(tenantId);
        }

        @Override
        public void walkFinish(int familiesProcessed, int metricsProcessed) {
            log.debugf("Storing [%d] of [%d] Prometheus metrics from endpoint [%s]",
                    payloadBuilder.getNumberDataPoints(), metricsProcessed, getMonitoredEndpoint());
            metricStorage.store(payloadBuilder, 0);
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
            log.debugf("Will store counter in Hawkular Metrics with key: %s", key);
            payloadBuilder.addDataPoint(key, System.currentTimeMillis(), metric.getValue(), MetricType.COUNTER);
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
            log.debugf("Will store gauge in Hawkular Metrics with key: %s", key);
            payloadBuilder.addDataPoint(key, System.currentTimeMillis(), metric.getValue(), MetricType.GAUGE);
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
            if (metricExactNames == null) {
                return false; // we are to process every metric that is scraped - do not ignore anything
            }

            if (metricExactNames.contains(metricName)) {
                return false; // the metric name matched exactly one that we are looking for - do not ignore it
            }

            // see if the metric name matches one of the regex patterns we were given
            for (Pattern pattern : metricRegexNames) {
                if (pattern.matcher(metricName).matches()) {
                    return false; // the metric name matches one of our regex patterns - do not ignore it
                }
            }

            // this metric name didn't match anything - we are to ignore it
            return true;
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
