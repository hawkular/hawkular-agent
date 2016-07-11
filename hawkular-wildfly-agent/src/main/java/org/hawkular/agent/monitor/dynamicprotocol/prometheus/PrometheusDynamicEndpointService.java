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
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Pattern;

import org.hawkular.agent.monitor.api.HawkularWildFlyAgentContext;
import org.hawkular.agent.monitor.api.MetricDataPayloadBuilder;
import org.hawkular.agent.monitor.api.MetricStorage;
import org.hawkular.agent.monitor.api.MetricTagPayloadBuilder;
import org.hawkular.agent.monitor.dynamicprotocol.DynamicEndpointService;
import org.hawkular.agent.monitor.dynamicprotocol.MetricMetadata;
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

    private final Map<String, MetricMetadata> metricExactNames;
    private final Map<Pattern, MetricMetadata> metricRegexNames;
    private final String tenantId;

    public PrometheusDynamicEndpointService(String feedId, MonitoredEndpoint<DynamicEndpointConfiguration> endpoint,
            HawkularWildFlyAgentContext hawkularStorage, Collection<MetricMetadata> metrics) {
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
            metricExactNames = new HashMap<>();
            metricRegexNames = new HashMap<>();
            for (MetricMetadata metric : metrics) {
                String nameString = metric.getName().getNameString();
                if (nameString.matches("[a-zA-Z_:][a-zA-Z0-9_:]*")) {
                    metricExactNames.put(nameString, metric);
                } else {
                    metricRegexNames.put(Pattern.compile(nameString), metric);
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
        private MetricDataPayloadBuilder dataPayloadBuilder;
        private MetricTagPayloadBuilder tagPayloadBuilder;

        public Walker() {
        }

        @Override
        public void walkStart() {
            HawkularWildFlyAgentContext storage = getHawkularStorage();
            metricStorage = storage.getMetricStorage();

            dataPayloadBuilder = metricStorage.createMetricDataPayloadBuilder();
            dataPayloadBuilder.setTenantId(tenantId);

            tagPayloadBuilder = metricStorage.createMetricTagPayloadBuilder();
            tagPayloadBuilder.setTenantId(tenantId);
        }

        @Override
        public void walkFinish(int familiesProcessed, int metricsProcessed) {
            log.debugf("Storing [%d] of [%d] Prometheus metrics from endpoint [%s]",
                    dataPayloadBuilder.getNumberDataPoints(), metricsProcessed, getMonitoredEndpoint());
            metricStorage.store(dataPayloadBuilder, 0);
            metricStorage.store(tagPayloadBuilder, 0);
        }

        @Override
        public void walkMetricFamily(MetricFamily family, int index) {
            if (metricToBeCollected(family.getName()) == null) {
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
            MetricMetadata metricMetadata = metricToBeCollected(metric.getName());
            if (metricMetadata == null) {
                return;
            }
            log.debugf("Processing Prometheus Counter Metric [%s|%s]: value=%f",
                    family.getName(),
                    buildLabelListString(metric.getLabels(), null, null),
                    metric.getValue());

            String key = generateKey(family, metric, metricMetadata);
            log.debugf("Will store counter in Hawkular Metrics with key: %s", key);
            dataPayloadBuilder.addDataPoint(key, System.currentTimeMillis(), metric.getValue(), MetricType.COUNTER);

            Map<String, String> tags = generateTags(family, metric, metricMetadata);
            for (Map.Entry<String, String> entry : tags.entrySet()) {
                tagPayloadBuilder.addTag(key, entry.getKey(), entry.getValue(), MetricType.COUNTER);
            }
        }

        @Override
        public void walkGaugeMetric(MetricFamily family, Gauge metric, int index) {
            MetricMetadata metricMetadata = metricToBeCollected(metric.getName());
            if (metricMetadata == null) {
                return;
            }
            log.debugf("Processing Prometheus Gauge Metric [%s|%s]: value=%f",
                    family.getName(),
                    buildLabelListString(metric.getLabels(), null, null),
                    metric.getValue());

            String key = generateKey(family, metric, metricMetadata);
            log.debugf("Will store gauge in Hawkular Metrics with key: %s", key);
            dataPayloadBuilder.addDataPoint(key, System.currentTimeMillis(), metric.getValue(), MetricType.GAUGE);

            Map<String, String> tags = generateTags(family, metric, metricMetadata);
            for (Map.Entry<String, String> entry : tags.entrySet()) {
                tagPayloadBuilder.addTag(key, entry.getKey(), entry.getValue(), MetricType.GAUGE);
            }
        }

        @Override
        public void walkSummaryMetric(MetricFamily family, Summary metric, int index) {
            if (metricToBeCollected(metric.getName()) == null) {
                return;
            }
            log.debugf("Prometheus SUMMARY metrics not yet supported - skipping [%s|%s|%s]",
                    family.getName(),
                    buildLabelListString(metric.getLabels(), null, null),
                    metric.getQuantiles());
        }

        @Override
        public void walkHistogramMetric(MetricFamily family, Histogram metric, int index) {
            if (metricToBeCollected(metric.getName()) == null) {
                return;
            }
            log.debugf("Prometheus HISTOGRAM metrics not yet supported - skipping [%s|%s|%s]",
                    family.getName(),
                    buildLabelListString(metric.getLabels(), null, null),
                    metric.getBuckets());
        }

        private MetricMetadata metricToBeCollected(String metricName) {
            if (metricExactNames == null) {
                // we are to process every metric that is scraped - do not ignore anything
                return new MetricMetadata(new Name(metricName), null, null);
            }

            MetricMetadata exactMatchMetric = metricExactNames.get(metricName);
            if (exactMatchMetric != null) {
                // the metric name matched exactly one that we are looking for - do not ignore it
                return exactMatchMetric;
            }

            // see if the metric name matches one of the regex patterns we were given
            for (Map.Entry<Pattern, MetricMetadata> entry : metricRegexNames.entrySet()) {
                Pattern pattern = entry.getKey();
                if (pattern.matcher(metricName).matches()) {
                    return entry.getValue(); // the metric name matches one of our regex patterns - do not ignore it
                }
            }

            // this metric name didn't match anything - we are to ignore it
            return null;
        }

        private String generateKey(MetricFamily family, Metric metric, MetricMetadata metricMetadata) {
            StringBuilder key = new StringBuilder();

            DynamicEndpointConfiguration config = getMonitoredEndpoint().getEndpointConfiguration();

            // See if the metric had a configured metric ID (in either metric metadata or the endpoint config).
            // If not, generate our own metric id key.
            String metricIdTemplate = metricMetadata.getMetricIdTemplate();
            if (metricIdTemplate == null || metricIdTemplate.isEmpty()) {
                metricIdTemplate = config.getMetricIdTemplate();
            }

            if (metricIdTemplate == null || metricIdTemplate.isEmpty()) {
                key.append(getFeedId())
                        .append("_")
                        .append(config.getName())
                        .append("_")
                        .append(metric.getName());
            } else {
                key.append(replaceTokens(family, metric, config, metricIdTemplate));
            }

            return key.toString();
        }

        private Map<String, String> generateTags(MetricFamily family, Metric metric, MetricMetadata metricMetadata) {
            Map<String, String> generatedTags;
            DynamicEndpointConfiguration config = getMonitoredEndpoint().getEndpointConfiguration();

            // See if the metric had configured metric tags (in either metric metadata or the endpoint config).
            // Notice that if the same tag is defined in both the endpoint config and metric metadata, the metric
            // metadata wins (i.e. it overrides the endpoing config tag definition).
            Map<String, String> tokenizedTags = new HashMap<>();
            if (config.getMetricTags() != null) {
                tokenizedTags.putAll(config.getMetricTags());
            }
            if (metricMetadata.getMetricTags() != null) {
                tokenizedTags.putAll(metricMetadata.getMetricTags());
            }

            if (tokenizedTags.isEmpty()) {
                generatedTags = new HashMap<>();
            } else {
                generatedTags = new HashMap<>();
                for (Map.Entry<String, String> tokenizedTag : tokenizedTags.entrySet()) {
                    String name = replaceTokens(family, metric, config, tokenizedTag.getKey());
                    String value = replaceTokens(family, metric, config, tokenizedTag.getValue());
                    generatedTags.put(name, value);
                }
            }

            // for each prometheus label, add a tag
            generatedTags.putAll(metric.getLabels());

            return generatedTags;
        }

        private String replaceTokens(MetricFamily family, Metric metric, DynamicEndpointConfiguration config,
                String string) {
            return string
                    .replaceAll("%FeedId", getFeedId())
                    .replaceAll("%ManagedServerName", config.getName())
                    .replaceAll("%MetricTypeName", family.getType().name())
                    .replaceAll("%MetricName", metric.getName());
        }
    }
}
