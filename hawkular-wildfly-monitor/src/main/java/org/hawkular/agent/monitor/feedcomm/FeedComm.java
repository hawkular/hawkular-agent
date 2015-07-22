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
package org.hawkular.agent.monitor.feedcomm;

import java.net.MalformedURLException;
import java.util.Map;

import org.hawkular.agent.monitor.extension.MonitorServiceConfiguration;
import org.hawkular.agent.monitor.inventory.ManagedServer;
import org.hawkular.agent.monitor.inventory.dmr.DMRInventoryManager;
import org.hawkular.agent.monitor.log.MsgLogger;
import org.hawkular.agent.monitor.service.Util;
import org.hawkular.agent.monitor.storage.HttpClientBuilder;
import org.hawkular.bus.common.BasicMessage;

import com.squareup.okhttp.ws.WebSocketCall;

/**
 * Managed connection to the server-side feed communications service.
 */
public class FeedComm {

    private final HttpClientBuilder httpClientBuilder;
    private final String feedcommUrl;
    private final Map<ManagedServer, DMRInventoryManager> dmrServerInventories;

    private FeedCommProcessor commProcessor;
    private WebSocketCall webSocketCall;

    public FeedComm(HttpClientBuilder httpClientBuilder, MonitorServiceConfiguration config, String feedId,
            Map<ManagedServer, DMRInventoryManager> dmrServerInventories) {
        this.dmrServerInventories = dmrServerInventories;
        if (feedId == null || feedId.isEmpty()) {
            throw new IllegalArgumentException("Must have a valid feed ID to communicate with the server");
        }

        this.httpClientBuilder = httpClientBuilder;

        try {
            StringBuilder url;
            url = Util.getContextUrlString(config.storageAdapter.url, config.storageAdapter.feedcommContext);
            url.append("feed/").append(feedId);
            this.feedcommUrl = url.toString().replaceFirst("https?:", (config.storageAdapter.useSSL) ? "wss:" : "ws:");
            MsgLogger.LOG.infoFeedCommUrl(this.feedcommUrl);
        } catch (MalformedURLException e) {
            throw new IllegalArgumentException("Cannot build URL to the server feed-comm endpoint");
        }
    }

    public void connect() throws Exception {
        if (webSocketCall != null) {
            throw new IllegalStateException("Already connected - call cancel first");
        }

        webSocketCall = httpClientBuilder.createWebSocketCall(feedcommUrl, null);
        commProcessor = new FeedCommProcessor(this.dmrServerInventories);

        webSocketCall.enqueue(this.commProcessor);
    }

    public void disconnect() {
        if (commProcessor != null) {
            try {
                commProcessor.close(1000, "Disconnect");
            } catch (Exception e) {
                MsgLogger.LOG.errorCannotCloseCommProcessor(e);
            }
            commProcessor = null;
        }

        if (webSocketCall != null) {
            try {
                webSocketCall.cancel();
            } catch (Exception e) {
                MsgLogger.LOG.errorCannotCloseWebSocketCall(e);
            }
            webSocketCall = null;
        }
    }

    public void sendAsync(BasicMessage message) {
        if (commProcessor == null) {
            throw new IllegalStateException("Feed communications channel has been disconnected, cannot send message");
        }
        commProcessor.sendAsync(message);
    }

    public void sendSync(BasicMessage message) throws Exception {
        if (commProcessor == null) {
            throw new IllegalStateException("Feed communications channel has been disconnected, cannot send message");
        }
        commProcessor.sendSync(message);
    }
}
