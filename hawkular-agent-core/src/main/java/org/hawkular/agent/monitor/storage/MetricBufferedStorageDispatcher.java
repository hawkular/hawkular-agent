/*
 * Copyright 2015-2017 Red Hat, Inc. and/or its affiliates
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
package org.hawkular.agent.monitor.storage;

import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;

import org.hawkular.agent.monitor.diagnostics.Diagnostics;
import org.hawkular.agent.monitor.log.AgentLoggers;
import org.hawkular.agent.monitor.log.MsgLogger;
import org.hawkular.agent.monitor.scheduler.SchedulerConfiguration;
import org.hawkular.agent.monitor.util.Consumer;

/**
 * Buffers collected metric data and eventually stores them in a storage adapter.
 */
public class MetricBufferedStorageDispatcher implements Consumer<MetricDataPoint> {
    private static final MsgLogger log = AgentLoggers.getLogger(MetricBufferedStorageDispatcher.class);
    private final int maxBatchSize;
    private final int bufferSize;
    private final StorageAdapter storageAdapter;
    private final Diagnostics diagnostics;
    private final BlockingQueue<MetricDataPoint> queue;
    private final Worker worker;

    public MetricBufferedStorageDispatcher(SchedulerConfiguration config, StorageAdapter storageAdapter,
            Diagnostics diagnostics) {
        this.maxBatchSize = config.getMetricDispatcherMaxBatchSize();
        this.bufferSize = config.getMetricDispatcherBufferSize();
        this.storageAdapter = storageAdapter;
        this.diagnostics = diagnostics;
        this.queue = new ArrayBlockingQueue<MetricDataPoint>(bufferSize);
        this.worker = new Worker(queue);
    }

    public void start() {
        worker.start();
    }

    public void shutdown() {
        worker.setKeepRunning(false);
        worker.interrupt();
        try {
            worker.join(60_000L); // wait for it to finish, but not forever
        } catch (InterruptedException ie) {
            // Preserve interrupt status
            Thread.currentThread().interrupt();
        }
    }

    @Override
    public void accept(MetricDataPoint sample) {
        if (queue.remainingCapacity() > 0) {
            log.debugf("Metric collected: [%s]->[%s]", sample.getKey(), sample.getMetricValue());
            diagnostics.getMetricsStorageBufferSize().inc();
            queue.add(sample);
        } else {
            throw new RuntimeException("Metric dispatcher buffer capacity has been exceeded [" + bufferSize + "]");
        }
    }

    @Override
    public void report(Throwable e) {
        log.errorMetricCollectionFailed(e);
    }

    public class Worker extends Thread {
        private final BlockingQueue<MetricDataPoint> queue;
        private boolean keepRunning = true;

        public Worker(BlockingQueue<MetricDataPoint> queue) {
            super("Hawkular-WildFly-Agent-Storage-Dispatcher-Metric");
            this.queue = queue;
        }

        public void run() {
            try {
                while (keepRunning) {
                    // batch processing
                    MetricDataPoint sample = queue.take();
                    Set<MetricDataPoint> samples = new HashSet<>();
                    queue.drainTo(samples, maxBatchSize);
                    samples.add(sample);

                    diagnostics.getMetricsStorageBufferSize().dec(samples.size());

                    // dispatch
                    storageAdapter.storeMetrics(samples, 0);
                }
            } catch (InterruptedException ie) {
            }
        }

        public void setKeepRunning(boolean keepRunning) {
            this.keepRunning = keepRunning;
        }
    }
}
