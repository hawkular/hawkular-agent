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
package org.hawkular.agent.monitor.scheduler.diagnostics;

import java.util.Map.Entry;
import java.util.SortedMap;
import java.util.concurrent.TimeUnit;

import org.jboss.logging.Logger;

import com.codahale.metrics.Counter;
import com.codahale.metrics.Gauge;
import com.codahale.metrics.Histogram;
import com.codahale.metrics.Meter;
import com.codahale.metrics.MetricFilter;
import com.codahale.metrics.MetricRegistry;
import com.codahale.metrics.ScheduledReporter;
import com.codahale.metrics.Snapshot;
import com.codahale.metrics.Timer;

public class JBossLoggingReporter extends ScheduledReporter {

    public static Builder forRegistry(MetricRegistry registry) {
        return new Builder(registry);
    }

    public enum LoggingLevel {
        TRACE, DEBUG, INFO
    }

    public static class Builder {
        private final MetricRegistry registry;
        private Logger logger;
        private LoggingLevel loggingLevel;
        private TimeUnit rateUnit;
        private TimeUnit durationUnit;
        private MetricFilter filter;

        private Builder(MetricRegistry registry) {
            this.registry = registry;
            this.logger = Logger.getLogger(JBossLoggingReporter.class);
            this.rateUnit = TimeUnit.SECONDS;
            this.durationUnit = TimeUnit.MILLISECONDS;
            this.filter = MetricFilter.ALL;
            this.loggingLevel = LoggingLevel.INFO;
        }

        public Builder outputTo(Logger logger) {
            this.logger = logger;
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

        public Builder withLoggingLevel(LoggingLevel loggingLevel) {
            this.loggingLevel = loggingLevel;
            return this;
        }

        public JBossLoggingReporter build() {
            LoggerProxy loggerProxy;
            switch (loggingLevel) {
                case TRACE:
                    loggerProxy = new TraceLoggerProxy(logger);
                    break;
                case INFO:
                    loggerProxy = new InfoLoggerProxy(logger);
                    break;
                case DEBUG:
                default:
                    loggerProxy = new DebugLoggerProxy(logger);
                    break;
            }
            return new JBossLoggingReporter(registry, loggerProxy, rateUnit, durationUnit, filter);
        }
    }

    private final LoggerProxy loggerProxy;

    private JBossLoggingReporter(MetricRegistry registry,
            LoggerProxy loggerProxy,
            TimeUnit rateUnit,
            TimeUnit durationUnit,
            MetricFilter filter) {
        super(registry, "hawkular-monitor", filter, rateUnit, durationUnit);
        this.loggerProxy = loggerProxy;
    }

    @Override
    public void report(SortedMap<String, Gauge> gauges,
            SortedMap<String, Counter> counters,
            SortedMap<String, Histogram> histograms,
            SortedMap<String, Meter> meters,
            SortedMap<String, Timer> timers) {

        if (!loggerProxy.isEnabled()) {
            return;
        }

        StringBuilder logMessage = new StringBuilder("DIAGNOSTICS:\n==========\n");

        for (Entry<String, Gauge> entry : gauges.entrySet()) {
            logMessage.append(logGauge(entry.getKey(), entry.getValue()));
            logMessage.append("\n");
        }

        for (Entry<String, Counter> entry : counters.entrySet()) {
            logMessage.append(logCounter(entry.getKey(), entry.getValue()));
            logMessage.append("\n");
        }

        for (Entry<String, Histogram> entry : histograms.entrySet()) {
            logMessage.append(logHistogram(entry.getKey(), entry.getValue()));
            logMessage.append("\n");
        }

        for (Entry<String, Meter> entry : meters.entrySet()) {
            logMessage.append(logMeter(entry.getKey(), entry.getValue()));
            logMessage.append("\n");
        }

        for (Entry<String, Timer> entry : timers.entrySet()) {
            logMessage.append(logTimer(entry.getKey(), entry.getValue()));
            logMessage.append("\n");
        }

        logMessage.append("==========");

        loggerProxy.log(logMessage.toString());
    }

    private String logTimer(String name, Timer timer) {
        final Snapshot snapshot = timer.getSnapshot();
        return String.format(
                "%s: type=[timer], count=[%d], min=[%f], max=[%f], mean=[%f], stddev=[%f], median=[%f], " +
                        "p75=[%f], p95=[%f], p98=[%f], p99=[%f], p999=[%f], mean_rate=[%f], m1=[%f], m5=[%f], " +
                        "m15=[%f], rate_unit=[%s], duration_unit=[%s]",
                name,
                timer.getCount(),
                convertDuration(snapshot.getMin()),
                convertDuration(snapshot.getMax()),
                convertDuration(snapshot.getMean()),
                convertDuration(snapshot.getStdDev()),
                convertDuration(snapshot.getMedian()),
                convertDuration(snapshot.get75thPercentile()),
                convertDuration(snapshot.get95thPercentile()),
                convertDuration(snapshot.get98thPercentile()),
                convertDuration(snapshot.get99thPercentile()),
                convertDuration(snapshot.get999thPercentile()),
                convertRate(timer.getMeanRate()),
                convertRate(timer.getOneMinuteRate()),
                convertRate(timer.getFiveMinuteRate()),
                convertRate(timer.getFifteenMinuteRate()),
                getRateUnit(),
                getDurationUnit());
    }

    private String logMeter(String name, Meter meter) {
        return String.format(
                "%s: type=[meter], count=[%d], mean_rate=[%f], m1=[%f], m5=[%f], m15=[%f], rate_unit=[%s]",
                name,
                meter.getCount(),
                convertRate(meter.getMeanRate()),
                convertRate(meter.getOneMinuteRate()),
                convertRate(meter.getFiveMinuteRate()),
                convertRate(meter.getFifteenMinuteRate()),
                getRateUnit());
    }

    private String logHistogram(String name, Histogram histogram) {
        final Snapshot snapshot = histogram.getSnapshot();
        return String.format(
                "%s: type=[histogram], count=[%d], min=[%d], max=[%d], mean=[%f], stddev=[%f], " +
                        "median=[%f], p75=[%f], p95=[%f], p98=[%f], p99=[%f], p999=[%f]",
                name,
                histogram.getCount(),
                snapshot.getMin(),
                snapshot.getMax(),
                snapshot.getMean(),
                snapshot.getStdDev(),
                snapshot.getMedian(),
                snapshot.get75thPercentile(),
                snapshot.get95thPercentile(),
                snapshot.get98thPercentile(),
                snapshot.get99thPercentile(),
                snapshot.get999thPercentile());
    }

    private String logCounter(String name, Counter counter) {
        return String.format("%s: type=[counter], count=[%d]", name, counter.getCount());
    }

    private String logGauge(String name, Gauge gauge) {
        return String.format("%s: type=[gauge], value=[%s]", name, gauge.getValue());
    }

    @Override
    protected String getRateUnit() {
        return "events/" + super.getRateUnit();
    }

    abstract static class LoggerProxy {
        protected final Logger logger;

        public LoggerProxy(Logger logger) {
            this.logger = logger;
        }

        abstract void log(String format, Object... arguments);

        abstract boolean isEnabled();
    }

    private static class DebugLoggerProxy extends LoggerProxy {
        public DebugLoggerProxy(Logger logger) {
            super(logger);
        }

        @Override
        public void log(String format, Object... arguments) {
            logger.debugf(format, arguments);
        }

        @Override
        boolean isEnabled() {
            return logger.isDebugEnabled();
        }
    }

    private static class TraceLoggerProxy extends LoggerProxy {
        public TraceLoggerProxy(Logger logger) {
            super(logger);
        }

        @Override
        public void log(String format, Object... arguments) {
            logger.tracef(format, arguments);
        }

        @Override
        boolean isEnabled() {
            return logger.isTraceEnabled();
        }
    }

    private static class InfoLoggerProxy extends LoggerProxy {
        public InfoLoggerProxy(Logger logger) {
            super(logger);
        }

        @Override
        public void log(String format, Object... arguments) {
            logger.infof(format, arguments);
        }

        @Override
        boolean isEnabled() {
            return logger.isInfoEnabled();
        }
    }
}