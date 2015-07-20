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
import org.hawkular.agent.monitor.scheduler.polling.AvailCompletionHandler;

/**
 * Buffers availability check data and eventually stores them in a storage adapter.
 */
public class AvailBufferedStorageDispatcher implements AvailCompletionHandler {
    private final int maxBatchSize;
    private final int bufferSize;
    private final SchedulerConfiguration config;
    private final StorageAdapter storageAdapter;
    private final Diagnostics diagnostics;
    private final BlockingQueue<AvailDataPoint> queue;
    private final Worker worker;

    public AvailBufferedStorageDispatcher(SchedulerConfiguration config, StorageAdapter storageAdapter,
            Diagnostics diagnostics) {
        this.config = config;
        this.maxBatchSize = config.getAvailDispatcherMaxBatchSize();
        this.bufferSize = config.getAvailDispatcherBufferSize();
        this.storageAdapter = storageAdapter;
        this.diagnostics = diagnostics;
        this.queue = new ArrayBlockingQueue<AvailDataPoint>(bufferSize);
        this.worker = new Worker(queue);
    }

    public void start() {
        worker.start();
    }

    public void shutdown() {
        worker.setKeepRunning(false);
    }

    @Override
    public void onCompleted(AvailDataPoint sample) {
        if (queue.remainingCapacity() > 0) {
            MsgLogger.LOG.debugf("Availability checked: [%s]->[%s]", sample.getTask(), sample.getValue());
            diagnostics.getAvailStorageBufferSize().inc();
            queue.add(sample);
        }
        else {
            throw new RuntimeException("Avail dispatcher buffer capacity has been exceeded [" + bufferSize + "]");
        }
    }

    @Override
    public void onFailed(Throwable e) {
        MsgLogger.LOG.errorAvailCheckFailed(e);
    }

    public class Worker extends Thread {
        private final BlockingQueue<AvailDataPoint> queue;
        private boolean keepRunning = true;

        public Worker(BlockingQueue<AvailDataPoint> queue) {
            super("Hawkular-Monitor-Storage-Dispatcher-Avail");
            this.queue = queue;
        }

        public void run() {
            try {
                while (keepRunning) {
                    // batch processing
                    AvailDataPoint sample = queue.take();
                    Set<AvailDataPoint> samples = new HashSet<>();
                    queue.drainTo(samples, maxBatchSize);
                    samples.add(sample);

                    diagnostics.getAvailStorageBufferSize().dec(samples.size());

                    // dispatch
                    storageAdapter.storeAvails(samples);
                }
            } catch (InterruptedException ie) {
            }
        }

        public void setKeepRunning(boolean keepRunning) {
            this.keepRunning = keepRunning;
        }
    }
}

