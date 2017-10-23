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

import java.io.IOException;
import java.util.Collections;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import org.hawkular.agent.monitor.api.InventoryEvent;
import org.hawkular.agent.monitor.api.NotificationPayloadBuilder;
import org.hawkular.agent.monitor.config.AgentCoreEngineConfiguration;
import org.hawkular.agent.monitor.config.AgentCoreEngineConfiguration.StorageReportTo;
import org.hawkular.agent.monitor.diagnostics.Diagnostics;
import org.hawkular.agent.monitor.log.AgentLoggers;
import org.hawkular.agent.monitor.log.MsgLogger;
import org.hawkular.agent.monitor.util.Util;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.Request;
import okhttp3.Response;

public class HawkularStorageAdapter implements StorageAdapter {
    private static final MsgLogger log = AgentLoggers.getLogger(HawkularStorageAdapter.class);

    private AgentCoreEngineConfiguration.StorageAdapterConfiguration config;
    private Diagnostics diagnostics;
    private HttpClientBuilder httpClientBuilder;
    private AsyncInventoryStorage inventoryStorage;
    private Map<String, String> agentTenantIdHeader;

    public HawkularStorageAdapter() {
    }

    @Override
    public void initialize(
            String feedId,
            AgentCoreEngineConfiguration.StorageAdapterConfiguration config,
            Diagnostics diag,
            HttpClientBuilder httpClientBuilder) {
        this.config = config;
        this.diagnostics = diag;
        this.httpClientBuilder = httpClientBuilder;
        this.agentTenantIdHeader = getTenantHeader(config.getTenantId());

        switch (config.getType()) {
            case HAWKULAR:
                // We are in a full hawkular environment - so we will integrate with inventory.
                this.inventoryStorage = new AsyncInventoryStorage(
                        feedId,
                        config,
                        httpClientBuilder,
                        diagnostics);
                break;

            case METRICS:
                // We are only integrating with standalone Hawkular Metrics which does not support inventory.
                this.inventoryStorage = null;
                break;

            default:
                throw new IllegalArgumentException("Invalid type. Please report this bug: " + config.getType());
        }
    }

    @Override
    public AgentCoreEngineConfiguration.StorageAdapterConfiguration getStorageAdapterConfiguration() {
        return config;
    }

    @Override
    public <L> void receivedEvent(InventoryEvent<L> event) {
        if (inventoryStorage != null) {
            inventoryStorage.receivedEvent(event);
        }
    }

    @Override
    public void shutdown() {
        if (inventoryStorage != null) {
            inventoryStorage.shutdown();
        }
    }

    /**
     * Builds the header necessary for the tenant ID.
     *
     * @param tenantId the tenant ID string - this is the value of the returned map
     * @return the tenant header consisting of the header key and the value
     */
    private Map<String, String> getTenantHeader(String tenantId) {
        return Collections.singletonMap("Hawkular-Tenant", tenantId);
    }

    @Override
    public NotificationPayloadBuilder createNotificationPayloadBuilder() {
        return new NotificationPayloadBuilderImpl();
    }

    @Override
    public void store(NotificationPayloadBuilder payloadBuilder, long waitMillis) {
        // if we are not in full hawkular mode, there is nothing for us to do
        if (this.config.getType() != StorageReportTo.HAWKULAR) {
            return;
        }

        try {
            // get the payload
            String payload = Util.toJson(payloadBuilder.toPayload());

            // build the REST URL...
            StringBuilder url = Util.getContextUrlString(config.getUrl(), config.getHawkularContext());
            url.append("notification");

            // now send the REST request
            Request request = this.httpClientBuilder.buildJsonPutRequest(url.toString(), agentTenantIdHeader, payload);
            final CountDownLatch latch = (waitMillis <= 0) ? null : new CountDownLatch(1);

            this.httpClientBuilder.getHttpClient().newCall(request).enqueue(new Callback() {
                @Override
                public void onFailure(Call call, IOException e) {
                    try {
                        log.errorFailedToStoreNotification(e, payload);
                        diagnostics.getStorageErrorRate().mark(1);
                    } finally {
                        if (latch != null) {
                            latch.countDown();
                        }
                    }
                }

                @Override
                public void onResponse(Call call, Response response) throws IOException {
                    try {
                        // HTTP status of 200 means success; anything else is an error
                        if (response.code() != 200) {
                            IOException e = new IOException("status-code=[" + response.code() + "], reason=["
                                    + response.message() + "], url=[" + request.url().toString() + "]");
                            log.errorFailedToStoreNotification(e, payload);
                            diagnostics.getStorageErrorRate().mark(1);
                        }
                    } finally {
                        if (latch != null) {
                            latch.countDown();
                        }
                        response.body().close();
                    }
                }
            });

            if (latch != null) {
                latch.await(waitMillis, TimeUnit.MILLISECONDS);
            }

        } catch (Throwable t) {
            log.errorFailedToStoreNotification(t, String.valueOf(payloadBuilder.toPayload()));
            diagnostics.getStorageErrorRate().mark(1);
        }
    }
}
