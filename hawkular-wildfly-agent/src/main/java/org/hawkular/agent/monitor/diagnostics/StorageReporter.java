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
package org.hawkular.agent.monitor.diagnostics;

import java.text.DateFormat;
import java.util.HashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.SortedMap;
import java.util.TimeZone;
import java.util.concurrent.TimeUnit;

import org.hawkular.agent.monitor.extension.MonitorServiceConfiguration;
import org.hawkular.agent.monitor.extension.MonitorServiceConfiguration.DiagnosticsConfiguration;
import org.hawkular.agent.monitor.storage.MetricDataPoint;
import org.hawkular.agent.monitor.storage.NumericMetricDataPoint;
import org.hawkular.agent.monitor.storage.StorageAdapter;
import org.hawkular.metrics.client.common.MetricType;

import com.codahale.metrics.Clock;
import com.codahale.metrics.Counter;
import com.codahale.metrics.Gauge;
import com.codahale.metrics.Histogram;
import com.codahale.metrics.Meter;
import com.codahale.metrics.MetricFilter;
import com.codahale.metrics.MetricRegistry;
import com.codahale.metrics.ScheduledReporter;
import com.codahale.metrics.Timer;

public class StorageReporter extends ScheduledReporter {

    private final DiagnosticsConfiguration diagnosticsConfig;
    private final StorageAdapter storageAdapter;
    private final Locale locale;
    private final Clock clock;
    private final DateFormat dateFormat;
    private final String feedId;

    private StorageReporter(String feedId,
            MetricRegistry registry,
            Locale locale,
            Clock clock,
            TimeZone timeZone,
            TimeUnit rateUnit,
            TimeUnit durationUnit,
            MetricFilter filter,
            DiagnosticsConfiguration diagnosticsConfig,
            StorageAdapter storageAdapter) {

        super(registry, "storage-reporter", filter, rateUnit, durationUnit);
        this.feedId = feedId;
        this.locale = locale;
        this.clock = clock;
        this.diagnosticsConfig = diagnosticsConfig;
        this.storageAdapter = storageAdapter;
        this.dateFormat = DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.MEDIUM, locale);
        this.dateFormat.setTimeZone(timeZone);
    }

    @Override
    public void report(
            SortedMap<String, Gauge> gauges,
            SortedMap<String, Counter> counters,
            SortedMap<String, Histogram> histograms,
            SortedMap<String, Meter> meters,
            SortedMap<String, Timer> timers) {

        String ourName = "_self";

        if (!gauges.isEmpty()) {
            Set<MetricDataPoint> samples = new HashSet<>(gauges.size());
            for (Map.Entry<String, Gauge> entry : gauges.entrySet()) {
                Gauge<Integer> gauge = entry.getValue();
                String key = feedId + "." + ourName + "." + entry.getKey();
                samples.add(new NumericMetricDataPoint(
                        key,
                        System.currentTimeMillis(),
                        gauge.getValue(),
                        MetricType.GAUGE,
                        null));
            }
            storageAdapter.storeMetrics(samples, 0);
        }

        if (!counters.isEmpty()) {
            Set<MetricDataPoint> samples = new HashSet<>(counters.size());
            for (Map.Entry<String, Counter> entry : counters.entrySet()) {
                String key = feedId + "." + ourName + "." + entry.getKey();
                samples.add(new NumericMetricDataPoint(
                        key,
                        System.currentTimeMillis(),
                        entry.getValue().getCount(),
                        MetricType.COUNTER,
                        null));
            }
            storageAdapter.storeMetrics(samples, 0);

        }

        if (!meters.isEmpty()) {
            Set<MetricDataPoint> samples = new HashSet<>(meters.size());
            for (Map.Entry<String, Meter> entry : meters.entrySet()) {
                Meter meter = entry.getValue();
                String key = feedId + "." + ourName + "." + entry.getKey();
                samples.add(new NumericMetricDataPoint(
                        key,
                        System.currentTimeMillis(),
                        meter.getOneMinuteRate(),
                        MetricType.GAUGE,
                        null));
            }
            storageAdapter.storeMetrics(samples, 0);
        }

        if (!timers.isEmpty()) {
            Set<MetricDataPoint> samples = new HashSet<>(timers.size());
            for (Map.Entry<String, Timer> entry : timers.entrySet()) {
                Timer timer = entry.getValue();
                String key = feedId + "." + ourName + "." + entry.getKey();
                samples.add(new NumericMetricDataPoint(
                        key,
                        System.currentTimeMillis(),
                        timer.getSnapshot().get75thPercentile(),
                        MetricType.GAUGE,
                        null));
            }
            storageAdapter.storeMetrics(samples, 0);
        }
    }

    public static Builder forRegistry(MetricRegistry registry,
            MonitorServiceConfiguration.DiagnosticsConfiguration diagnosticsConfig, StorageAdapter storageAdapter) {
        return new Builder(registry, diagnosticsConfig, storageAdapter);
    }

    public static class Builder {
        private final MetricRegistry registry;
        private final MonitorServiceConfiguration.DiagnosticsConfiguration diagnosticsConfig;
        private final StorageAdapter storageAdapter;
        private Locale locale;
        private Clock clock;
        private TimeZone timeZone;
        private TimeUnit rateUnit;
        private TimeUnit durationUnit;
        private MetricFilter filter;
        private String feedId;

        private Builder(MetricRegistry registry,
                MonitorServiceConfiguration.DiagnosticsConfiguration diagnosticsConfig,
                StorageAdapter storageAdapter) {
            this.registry = registry;
            this.diagnosticsConfig = diagnosticsConfig;
            this.storageAdapter = storageAdapter;
            this.locale = Locale.getDefault();
            this.clock = Clock.defaultClock();
            this.timeZone = TimeZone.getDefault();
            this.rateUnit = TimeUnit.SECONDS;
            this.durationUnit = TimeUnit.MILLISECONDS;
            this.filter = MetricFilter.ALL;
        }

        public Builder formattedFor(Locale locale) {
            this.locale = locale;
            return this;
        }

        public Builder withClock(Clock clock) {
            this.clock = clock;
            return this;
        }

        public Builder formattedFor(TimeZone timeZone) {
            this.timeZone = timeZone;
            return this;
        }

        public Builder convertRatesTo(TimeUnit rateUnit) {
            this.rateUnit = rateUnit;
            return this;
        }

        public Builder convertDurationsTo(TimeUnit durationUnit) {
            this.durationUnit = durationUnit;
            return this;
        }

        public Builder feedId(String feedId) {
            this.feedId = feedId;
            return this;
        }

        public Builder filter(MetricFilter filter) {
            this.filter = filter;
            return this;
        }

        public StorageReporter build() {
            return new StorageReporter(feedId,
                    registry,
                    locale,
                    clock,
                    timeZone,
                    rateUnit,
                    durationUnit,
                    filter,
                    diagnosticsConfig,
                    storageAdapter);
        }
    }
}
