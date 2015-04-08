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
package org.hawkular.agent.monitor.storage;

import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;

import org.hawkular.agent.monitor.diagnostics.Diagnostics;
import org.hawkular.agent.monitor.log.MsgLogger;
import org.hawkular.agent.monitor.scheduler.config.SchedulerConfiguration;
import org.hawkular.agent.monitor.scheduler.polling.MetricCompletionHandler;
import org.jboss.logging.Logger;

/**
 * Buffers collected metric data and eventually stores them in a storage adapter.
 */
public class MetricBufferedStorageDispatcher implements MetricCompletionHandler {
    private static final Logger LOGGER = Logger.getLogger(MetricBufferedStorageDispatcher.class);

    private static final int MAX_BATCH_SIZE = 24; // TODO make configurable
    private static final int BUFFER_SIZE = 100; // TODO make configurable
    private final SchedulerConfiguration config;
    private final StorageAdapter storageAdapter;
    private final Diagnostics diagnostics;
    private final BlockingQueue<DataPoint> queue;
    private final Worker worker;

    public MetricBufferedStorageDispatcher(SchedulerConfiguration config, StorageAdapter storageAdapter,
            Diagnostics diagnostics) {
        this.config = config;
        this.storageAdapter = storageAdapter;
        this.diagnostics = diagnostics;
        this.queue = new ArrayBlockingQueue<DataPoint>(BUFFER_SIZE);
        this.worker = new Worker(queue);
    }

    public void start() {
        worker.start();
    }

    public void shutdown() {
        worker.setKeepRunning(false);
    }

    @Override
    public void onCompleted(DataPoint sample) {
        if (queue.remainingCapacity() > 0) {
            LOGGER.debugf("Metric collected: [%s]->[%f]", sample.getTask(), sample.getValue());
            diagnostics.getMetricsStorageBufferSize().inc();
            queue.add(sample);
        }
        else {
            throw new RuntimeException("buffer capacity exceeded");
        }
    }

    @Override
    public void onFailed(Throwable e) {
        MsgLogger.LOG.errorMetricCollectionFailed(e);
    }

    public class Worker extends Thread {
        private final BlockingQueue<DataPoint> queue;
        private boolean keepRunning = true;

        public Worker(BlockingQueue<DataPoint> queue) {
            this.queue = queue;
        }

        public void run() {
            try {
                while (keepRunning) {
                    // batch processing
                    DataPoint sample = queue.take();
                    Set<DataPoint> samples = new HashSet<>();
                    queue.drainTo(samples, MAX_BATCH_SIZE);
                    samples.add(sample);

                    diagnostics.getMetricsStorageBufferSize().dec(samples.size());

                    // dispatch
                    storageAdapter.store(samples);
                }
            } catch (InterruptedException ie) {
            }
        }

        public void setKeepRunning(boolean keepRunning) {
            this.keepRunning = keepRunning;
        }
    }
}

