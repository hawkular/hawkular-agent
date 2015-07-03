/*
 * Copyright 2015 Red Hat, Inc. and/or its affiliates
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
import org.hawkular.agent.monitor.extension.MonitorServiceConfiguration.Diagnostics;
import org.hawkular.agent.monitor.extension.SubsystemDefinition;
import org.hawkular.agent.monitor.scheduler.config.Interval;
import org.hawkular.agent.monitor.scheduler.config.LocalDMREndpoint;
import org.hawkular.agent.monitor.scheduler.polling.dmr.MetricDMRTask;
import org.hawkular.agent.monitor.service.ServerIdentifiers;
import org.hawkular.agent.monitor.storage.MetricDataPoint;
import org.hawkular.agent.monitor.storage.StorageAdapter;
import org.hawkular.dmrclient.Address;
import org.hawkular.metrics.client.common.MetricType;
import org.jboss.as.controller.PathAddress;

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

    private final Diagnostics diagnosticsConfig;
    private final StorageAdapter storageAdapter;
    private final Locale locale;
    private final Clock clock;
    private final DateFormat dateFormat;
    private final ServerIdentifiers selfId;

    private StorageReporter(MetricRegistry registry,
            Locale locale,
            Clock clock,
            TimeZone timeZone,
            TimeUnit rateUnit,
            TimeUnit durationUnit,
            MetricFilter filter,
            Diagnostics diagnosticsConfig,
            StorageAdapter storageAdapter,
            ServerIdentifiers selfId) {

        super(registry, "storage-reporter", filter, rateUnit, durationUnit);
        this.locale = locale;
        this.clock = clock;
        this.diagnosticsConfig = diagnosticsConfig;
        this.storageAdapter = storageAdapter;
        this.selfId = selfId;
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

        Interval interval = new Interval(diagnosticsConfig.interval, diagnosticsConfig.timeUnits);
        String pathStr = PathAddress.pathAddress(SubsystemDefinition.INSTANCE.getPathElement()).toCLIStyleString();
        Address ourAddr = Address.parse(pathStr);
        LocalDMREndpoint localDmrEndpoint = new LocalDMREndpoint("_self", this.selfId);

        if (!gauges.isEmpty()) {
            Set<MetricDataPoint> samples = new HashSet<>(gauges.size());
            for (Map.Entry<String, Gauge> entry : gauges.entrySet()) {
                Gauge<Integer> gauge = entry.getValue();
                samples.add(new MetricDataPoint(
                        new MetricDMRTask(interval, localDmrEndpoint, ourAddr, entry.getKey(), null, null),
                        gauge.getValue(),
                        MetricType.GAUGE));
            }
            storageAdapter.storeMetrics(samples);
        }

        if (!counters.isEmpty()) {
            Set<MetricDataPoint> samples = new HashSet<>(counters.size());
            for (Map.Entry<String, Counter> entry : counters.entrySet()) {
                samples.add(new MetricDataPoint(
                        new MetricDMRTask(interval, localDmrEndpoint, ourAddr, entry.getKey(), null, null),
                        entry.getValue().getCount(),
                        MetricType.COUNTER));
            }
            storageAdapter.storeMetrics(samples);

        }

        if (!meters.isEmpty()) {
            Set<MetricDataPoint> samples = new HashSet<>(meters.size());
            for (Map.Entry<String, Meter> entry : meters.entrySet()) {
                Meter meter = entry.getValue();
                samples.add(new MetricDataPoint(
                        new MetricDMRTask(interval, localDmrEndpoint, ourAddr, entry.getKey(), null, null),
                        meter.getOneMinuteRate(),
                        MetricType.GAUGE));
            }
            storageAdapter.storeMetrics(samples);
        }

        if (!timers.isEmpty()) {
            Set<MetricDataPoint> samples = new HashSet<>(timers.size());
            for (Map.Entry<String, Timer> entry : timers.entrySet()) {
                Timer timer = entry.getValue();
                samples.add(new MetricDataPoint(
                        new MetricDMRTask(interval, localDmrEndpoint, ourAddr, entry.getKey(), null, null),
                        timer.getSnapshot().get75thPercentile(),
                        MetricType.GAUGE));
            }
            storageAdapter.storeMetrics(samples);
        }
    }

    public static Builder forRegistry(MetricRegistry registry,
            MonitorServiceConfiguration.Diagnostics diagnosticsConfig, StorageAdapter storageAdapter,
            ServerIdentifiers selfId) {
        return new Builder(registry, diagnosticsConfig, storageAdapter, selfId);
    }

    public static class Builder {
        private final MetricRegistry registry;
        private final MonitorServiceConfiguration.Diagnostics diagnosticsConfig;
        private final StorageAdapter storageAdapter;
        private final ServerIdentifiers sid;
        private Locale locale;
        private Clock clock;
        private TimeZone timeZone;
        private TimeUnit rateUnit;
        private TimeUnit durationUnit;
        private MetricFilter filter;

        private Builder(MetricRegistry registry, MonitorServiceConfiguration.Diagnostics diagnosticsConfig,
                StorageAdapter storageAdapter, ServerIdentifiers selfId) {
            this.registry = registry;
            this.diagnosticsConfig = diagnosticsConfig;
            this.storageAdapter = storageAdapter;
            this.sid = selfId;
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

        public Builder filter(MetricFilter filter) {
            this.filter = filter;
            return this;
        }

        public StorageReporter build() {
            return new StorageReporter(registry,
                    locale,
                    clock,
                    timeZone,
                    rateUnit,
                    durationUnit,
                    filter,
                    diagnosticsConfig,
                    storageAdapter,
                    sid);
        }
    }
}
