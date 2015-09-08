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

import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import org.hawkular.agent.monitor.extension.MonitorServiceConfiguration;
import org.hawkular.agent.monitor.inventory.ManagedServer;
import org.hawkular.agent.monitor.inventory.dmr.DMRInventoryManager;
import org.hawkular.agent.monitor.log.MsgLogger;
import org.hawkular.agent.monitor.service.Util;
import org.hawkular.agent.monitor.storage.HttpClientBuilder;
import org.hawkular.bus.common.BasicMessage;
import org.hawkular.bus.common.BasicMessageWithExtraData;
import org.hawkular.cmdgw.api.ApiDeserializer;
import org.hawkular.cmdgw.api.AuthMessage;
import org.hawkular.cmdgw.api.Authentication;
import org.hawkular.cmdgw.api.GenericErrorResponseBuilder;

import com.squareup.okhttp.Response;
import com.squareup.okhttp.ws.WebSocket;
import com.squareup.okhttp.ws.WebSocketCall;
import com.squareup.okhttp.ws.WebSocketListener;

import okio.Buffer;
import okio.BufferedSource;

public class FeedCommProcessor implements WebSocketListener {

    private static final Map<String, Class<? extends Command<?, ?>>> VALID_COMMANDS;
    static {
        VALID_COMMANDS = new HashMap<>();
        VALID_COMMANDS.put(EchoCommand.REQUEST_CLASS.getName(), EchoCommand.class);
        VALID_COMMANDS.put(GenericErrorResponseCommand.REQUEST_CLASS.getName(), GenericErrorResponseCommand.class);
        VALID_COMMANDS.put(ExecuteOperationCommand.REQUEST_CLASS.getName(), ExecuteOperationCommand.class);
        VALID_COMMANDS.put(DeployApplicationCommand.REQUEST_CLASS.getName(), DeployApplicationCommand.class);
        VALID_COMMANDS.put(AddJdbcDriverCommand.REQUEST_CLASS.getName(), AddJdbcDriverCommand.class);
    }

    private final int disconnectCode = 1000;
    private final String disconnectReason = "Shutting down FeedCommProcessor";

    private final HttpClientBuilder httpClientBuilder;
    private final MonitorServiceConfiguration config;
    private final Map<ManagedServer, DMRInventoryManager> dmrServerInventories;
    private final String feedcommUrl;
    private final ExecutorService sendExecutor = Executors.newSingleThreadExecutor();

    private WebSocketCall webSocketCall;
    private WebSocket webSocket;

    public FeedCommProcessor(HttpClientBuilder httpClientBuilder, MonitorServiceConfiguration config, String feedId,
            Map<ManagedServer, DMRInventoryManager> dmrServerInventories) {
        if (feedId == null || feedId.isEmpty()) {
            throw new IllegalArgumentException("Must have a valid feed ID to communicate with the server");
        }

        this.httpClientBuilder = httpClientBuilder;
        this.config = config;
        this.dmrServerInventories = dmrServerInventories;

        try {
            StringBuilder url;
            url = Util.getContextUrlString(config.storageAdapter.url, config.storageAdapter.feedcommContext);
            url.append("feed/").append(feedId);
            this.feedcommUrl = url.toString().replaceFirst("https?:", (config.storageAdapter.useSSL) ? "wss:" : "ws:");
            MsgLogger.LOG.infoFeedCommUrl(this.feedcommUrl);
        } catch (Exception e) {
            throw new IllegalArgumentException("Cannot build URL to the server command-gateway endpoint", e);
        }
    }

    public MonitorServiceConfiguration getMonitorServiceConfiguration() {
        return config;
    }

    public Map<ManagedServer, DMRInventoryManager> getDmrServerInventories() {
        return dmrServerInventories;
    }

    /**
     * @return true if this object is currently connected to the websocket.
     */
    public boolean isConnected() {
        return webSocket != null;
    }

    public void connect() throws Exception {
        disconnect(); // disconnect to any old connection we had

        webSocketCall = httpClientBuilder.createWebSocketCall(feedcommUrl, null);
        webSocketCall.enqueue(this);
    }

    public void disconnect() {
        if (webSocket != null) {
            try {
                webSocket.close(disconnectCode, disconnectReason);
            } catch (Exception e) {
                MsgLogger.LOG.warnFailedToCloseWebSocket(disconnectCode, disconnectReason, e);
            }
            webSocket = null;
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

    /**
     * Sends a message to the server asynchronously. This method returns immediately; the message
     * may not go out until some time in the future.
     *
     * @param message the message to send
     */
    public void sendAsync(BasicMessage message) {
        if (webSocket == null) {
            throw new IllegalStateException("WebSocket connection was closed. Cannot send any messages");
        }

        configurationAuthentication(message);

        String messageString = ApiDeserializer.toHawkularFormat(message);

        sendExecutor.execute(new Runnable() {
            @Override
            public void run() {
                try {
                    Buffer buffer = new Buffer();
                    buffer.writeUtf8(messageString);
                    FeedCommProcessor.this.webSocket.sendMessage(WebSocket.PayloadType.TEXT, buffer);
                } catch (Throwable t) {
                    MsgLogger.LOG.errorFailedToSendOverFeedComm(message.getClass().getName(), t);
                }
            }
        });
    }

    /**
     * Sends a message to the server synchronously. This will return only when the message has been sent.
     *
     * @param message the message to send
     * @throws IOException if the message failed to be sent
     */
    public void sendSync(BasicMessage message) throws Exception {
        if (webSocket == null) {
            throw new IllegalStateException("WebSocket connection was closed. Cannot send any messages");
        }

        configurationAuthentication(message);

        String messageString = ApiDeserializer.toHawkularFormat(message);
        Buffer buffer = new Buffer();
        buffer.writeUtf8(messageString);
        FeedCommProcessor.this.webSocket.sendMessage(WebSocket.PayloadType.TEXT, buffer);
    }

    @Override
    public void onOpen(WebSocket webSocket, Response response) {
        this.webSocket = webSocket;
        MsgLogger.LOG.infoOpenedFeedComm();
    }

    @Override
    public void onClose(int reasonCode, String reason) {
        webSocket = null;
        MsgLogger.LOG.infoClosedFeedComm(reasonCode, reason);

        // We always want a connection - so try to get another one.
        // Note that we don't try to get another connection if we think we'll never get one;
        // This avoids a potential infinite loop of continually trying (and failing) to get a connection.
        // We also don't try to get another one if we were explicitly told to disconnect.
        if (!(disconnectCode == reasonCode && disconnectReason.equals(reason))) {
            switch (reasonCode) {
                case 1008: { // VIOLATED POLICY - don't try again since it probably will fail again (bad credentials?)
                    break;
                }
                default: {
                    try {
                        connect();
                    } catch (Exception e) {
                        MsgLogger.LOG.errorCannotReconnectToWebSocket(e);
                    }
                    break;
                }
            }
        }
    }

    @Override
    public void onFailure(IOException e, Response response) {
        MsgLogger.LOG.warnFeedCommFailure((response != null) ? response.toString() : "?", e);
    }

    @Override
    @SuppressWarnings({ "rawtypes", "unchecked" })
    public void onMessage(BufferedSource payload, WebSocket.PayloadType payloadType) throws IOException {

        BasicMessage response;
        String requestClassName = "?";

        try {
            try {
                BasicMessageWithExtraData<? extends BasicMessage> msgWithData;

                switch (payloadType) {
                    case TEXT: {
                        String nameAndJsonStr = payload.readUtf8();
                        BasicMessage msgFromJson = new ApiDeserializer().deserialize(nameAndJsonStr);
                        msgWithData = new BasicMessageWithExtraData<BasicMessage>(msgFromJson, null);
                        break;
                    }
                    case BINARY: {
                        InputStream input = payload.inputStream();
                        msgWithData = new ApiDeserializer().deserialize(input);
                        break;
                    }
                    default: {
                        throw new IllegalArgumentException("Unknown payload type, please report this bug: "
                                + payloadType);
                    }
                }

                MsgLogger.LOG.debug("Received message from server");

                BasicMessage msg = msgWithData.getBasicMessage();
                requestClassName = msg.getClass().getName();

                Class<? extends Command<?, ?>> commandClass = VALID_COMMANDS.get(requestClassName);
                if (commandClass == null) {
                    MsgLogger.LOG.errorInvalidCommandRequestFeed(requestClassName);
                    String errorMessage = "Invalid command request: " + requestClassName;
                    response = new GenericErrorResponseBuilder().setErrorMessage(errorMessage).build();
                } else {
                    Command command = commandClass.newInstance();
                    CommandContext context = new CommandContext(this);
                    response = command.execute(msg, msgWithData.getBinaryData(), context);
                }
            } finally {
                // must ensure payload is closed; this assumes if it was a stream that the command is finished with it
                payload.close();
            }
        } catch (Throwable t) {
            MsgLogger.LOG.errorCommandExecutionFailureFeed(requestClassName, t);
            String errorMessage = "Command failed [" + requestClassName + "]";
            response = new GenericErrorResponseBuilder()
                    .setThrowable(t)
                    .setErrorMessage(errorMessage)
                    .build();
        }

        // send the response back to the server
        if (response != null) {
            try {
                sendSync(response);
            } catch (Exception e) {
                MsgLogger.LOG.errorFailedToSendOverFeedComm(response.getClass().getName(), e);
            }
        }
    }

    @Override
    public void onPong(Buffer buffer) {
        // no-op
    }

    private void configurationAuthentication(BasicMessage message) {
        if (!(message instanceof AuthMessage)) {
            return; // this message doesn't need authentication
        }

        AuthMessage authMessage = (AuthMessage) message;

        Authentication auth = authMessage.getAuthentication();
        if (auth != null) {
            return; // authentication already configured; assume whoever did it knew what they were doing and keep it
        }

        auth = new Authentication();
        auth.setUsername(this.config.storageAdapter.username);
        auth.setPassword(this.config.storageAdapter.password);
        authMessage.setAuthentication(auth);
    }
}
