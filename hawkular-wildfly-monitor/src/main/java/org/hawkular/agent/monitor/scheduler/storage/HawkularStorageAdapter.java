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
package org.hawkular.agent.monitor.scheduler.storage;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import org.apache.http.HttpResponse;
import org.apache.http.StatusLine;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.DefaultHttpClient;
import org.hawkular.agent.monitor.log.MsgLogger;
import org.hawkular.agent.monitor.scheduler.config.SchedulerConfiguration;
import org.hawkular.agent.monitor.scheduler.diagnostics.Diagnostics;
import org.hawkular.agent.monitor.scheduler.polling.Task;
import org.hawkular.metrics.client.common.Batcher;
import org.hawkular.metrics.client.common.SingleMetric;

public class HawkularStorageAdapter implements StorageAdapter {

    private SchedulerConfiguration config;
    private Diagnostics diagnostics;
    private final HttpClient httpclient;
    private final KeyResolution keyResolution;

    public HawkularStorageAdapter() {
        this.httpclient = new DefaultHttpClient();
        this.keyResolution = new KeyResolution();
    }

    @Override
    public SchedulerConfiguration getSchedulerConfiguration() {
        return config;
    }

    @Override
    public void setSchedulerConfiguration(SchedulerConfiguration config) {
        this.config = config;
    }

    @Override
    public void setDiagnostics(Diagnostics diag) {
        this.diagnostics = diag;
    }

    @Override
    public void store(Set<DataPoint> datapoints) {
        HttpPost post = new HttpPost(config.getStorageAdapterConfig().url);
        try {
            List<SingleMetric> metrics = new ArrayList<>();

            for (DataPoint datapoint : datapoints) {
                Task task = datapoint.getTask();
                String key = keyResolution.resolve(task);
                metrics.add(new SingleMetric(key, datapoint.getTimestamp(), datapoint.getValue()));
            }

            if (metrics.size() > 0) {
                post.setHeader("Content-Type", "application/json;charset=utf-8");
                post.setEntity(new StringEntity(Batcher.metricListToJson(metrics)));

                HttpResponse httpResponse = httpclient.execute(post);
                StatusLine statusLine = httpResponse.getStatusLine();

                if (statusLine.getStatusCode() != 200) {
                    throw new Exception("HTTP Status " + statusLine.getStatusCode() + ": " + statusLine);
                }


            }
        } catch (Throwable t) {
            MsgLogger.LOG.errorFailedToStoreData(t, datapoints);
            diagnostics.getStorageErrorRate().mark(1);
        }
        finally {
            post.releaseConnection();
        }

    }
}
