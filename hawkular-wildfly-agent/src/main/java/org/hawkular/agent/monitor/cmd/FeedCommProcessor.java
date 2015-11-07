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
package org.hawkular.agent.monitor.cmd;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicReference;

import org.hawkular.agent.monitor.extension.MonitorServiceConfiguration;
import org.hawkular.agent.monitor.log.AgentLoggers;
import org.hawkular.agent.monitor.log.MsgLogger;
import org.hawkular.agent.monitor.service.MonitorService;
import org.hawkular.agent.monitor.storage.HttpClientBuilder;
import org.hawkular.agent.monitor.util.Util;
import org.hawkular.bus.common.BasicMessage;
import org.hawkular.bus.common.BasicMessageWithExtraData;
import org.hawkular.bus.common.BinaryData;
import org.hawkular.cmdgw.api.ApiDeserializer;
import org.hawkular.cmdgw.api.AuthMessage;
import org.hawkular.cmdgw.api.Authentication;
import org.hawkular.cmdgw.api.GenericErrorResponse;
import org.hawkular.cmdgw.api.GenericErrorResponseBuilder;

import com.squareup.okhttp.Response;
import com.squareup.okhttp.ws.WebSocket;
import com.squareup.okhttp.ws.WebSocketCall;
import com.squareup.okhttp.ws.WebSocketListener;

import okio.Buffer;
import okio.BufferedSink;
import okio.BufferedSource;

public class FeedCommProcessor implements WebSocketListener {
    private static final MsgLogger log = AgentLoggers.getLogger(FeedCommProcessor.class);
    private static final Map<String, Class<? extends Command<?, ?>>> VALID_COMMANDS;

    static {
        VALID_COMMANDS = new HashMap<>();
        VALID_COMMANDS.put(EchoCommand.REQUEST_CLASS.getName(), EchoCommand.class);
        VALID_COMMANDS.put(GenericErrorResponseCommand.REQUEST_CLASS.getName(), GenericErrorResponseCommand.class);
        VALID_COMMANDS.put(ExecuteOperationCommand.REQUEST_CLASS.getName(), ExecuteOperationCommand.class);
        VALID_COMMANDS.put(DeployApplicationCommand.REQUEST_CLASS.getName(), DeployApplicationCommand.class);
        VALID_COMMANDS.put(AddJdbcDriverCommand.REQUEST_CLASS.getName(), AddJdbcDriverCommand.class);
        VALID_COMMANDS.put(AddDatasourceCommand.REQUEST_CLASS.getName(), AddDatasourceCommand.class);
        VALID_COMMANDS.put(ExportJdrCommand.REQUEST_CLASS.getName(), ExportJdrCommand.class);
        VALID_COMMANDS.put(RemoveDatasourceCommand.REQUEST_CLASS.getName(), RemoveDatasourceCommand.class);
        VALID_COMMANDS.put(RemoveJdbcDriverCommand.REQUEST_CLASS.getName(), RemoveJdbcDriverCommand.class);
        VALID_COMMANDS.put(UpdateDatasourceCommand.REQUEST_CLASS.getName(), UpdateDatasourceCommand.class);
    }

    private final int disconnectCode = 1000;
    private final String disconnectReason = "Shutting down FeedCommProcessor";

    private final HttpClientBuilder httpClientBuilder;
    private final MonitorServiceConfiguration config;
    private final MonitorService discoveryService;
    private final String feedcommUrl;
    private final ExecutorService sendExecutor = Executors.newSingleThreadExecutor();
    private final AtomicReference<ReconnectJobThread> reconnectJobThread = new AtomicReference<>();

    private WebSocketCall webSocketCall;
    private WebSocket webSocket;

    public FeedCommProcessor(HttpClientBuilder httpClientBuilder, MonitorServiceConfiguration config, String feedId,
            MonitorService discoveryService) {

        if (feedId == null || feedId.isEmpty()) {
            throw new IllegalArgumentException("Must have a valid feed ID to communicate with the server");
        }

        this.httpClientBuilder = httpClientBuilder;
        this.config = config;
        this.discoveryService = discoveryService;

        try {
            StringBuilder url;
            url = Util.getContextUrlString(config.getStorageAdapter().getUrl(),
                    config.getStorageAdapter().getFeedcommContext());
            url.append("feed/").append(feedId);
            this.feedcommUrl = url.toString().replaceFirst("https?:",
                    (config.getStorageAdapter().isUseSSL()) ? "wss:" : "ws:");
            log.infoFeedCommUrl(this.feedcommUrl);
        } catch (Exception e) {
            throw new IllegalArgumentException("Cannot build URL to the server command-gateway endpoint", e);
        }
    }

    /**
     * @return true if this object is currently connected to the websocket.
     */
    public boolean isConnected() {
        return webSocket != null;
    }

    /**
     * Connects to the websocket endpoint. This first attempts to disconnect to any existing connection.
     *
     * @throws Exception on failure
     */
    public void connect() throws Exception {
        disconnect(); // disconnect to any old connection we had

        log.debugf("About to connect a feed WebSocket client to endpoint [%s]", feedcommUrl);

        webSocketCall = httpClientBuilder.createWebSocketCall(feedcommUrl, null);
        webSocketCall.enqueue(this);
    }

    public void disconnect() {
        if (webSocket != null) {
            try {
                webSocket.close(disconnectCode, disconnectReason);
            } catch (Exception e) {
                log.warnFailedToCloseWebSocket(disconnectCode, disconnectReason, e);
            }
            webSocket = null;
        }

        if (webSocketCall != null) {
            try {
                webSocketCall.cancel();
            } catch (Exception e) {
                log.errorCannotCloseWebSocketCall(e);
            }
            webSocketCall = null;
        }
    }

    /**
     * Sends a message to the server asynchronously. This method returns immediately; the message may not go out until
     * some time in the future.
     *
     * @param message the message to send
     */
    public void sendAsync(BasicMessageWithExtraData<? extends BasicMessage> messageWithData) {
        if (webSocket == null) {
            throw new IllegalStateException("WebSocket connection was closed. Cannot send any messages");
        }

        BasicMessage message = messageWithData.getBasicMessage();
        configurationAuthentication(message);

        sendExecutor.execute(new Runnable() {
            @Override
            public void run() {
                try {
                    if (messageWithData.getBinaryData() == null) {
                        String messageString = ApiDeserializer.toHawkularFormat(message);
                        Buffer buffer = new Buffer();
                        buffer.writeUtf8(messageString);
                        FeedCommProcessor.this.webSocket.sendMessage(WebSocket.PayloadType.TEXT, buffer);
                    } else {
                        BinaryData messageData = ApiDeserializer.toHawkularFormat(message,
                                messageWithData.getBinaryData());
                        BufferedSink sink = FeedCommProcessor.this.webSocket
                                .newMessageSink(WebSocket.PayloadType.BINARY);
                        try {
                            emitToSink(messageData, sink);
                        } finally {
                            sink.close();
                        }
                    }
                } catch (Throwable t) {
                    log.errorFailedToSendOverFeedComm(message.getClass().getName(), t);
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
    public void sendSync(BasicMessageWithExtraData<? extends BasicMessage> messageWithData) throws Exception {
        if (webSocket == null) {
            throw new IllegalStateException("WebSocket connection was closed. Cannot send any messages");
        }

        BasicMessage message = messageWithData.getBasicMessage();
        configurationAuthentication(message);

        if (messageWithData.getBinaryData() == null) {
            String messageString = ApiDeserializer.toHawkularFormat(message);
            Buffer buffer = new Buffer();
            buffer.writeUtf8(messageString);
            FeedCommProcessor.this.webSocket.sendMessage(WebSocket.PayloadType.TEXT, buffer);
        } else {
            BinaryData messageData = ApiDeserializer.toHawkularFormat(message, messageWithData.getBinaryData());
            BufferedSink sink = FeedCommProcessor.this.webSocket.newMessageSink(WebSocket.PayloadType.BINARY);
            try {
                emitToSink(messageData, sink);
            } finally {
                sink.close();
            }
        }
    }

    private void emitToSink(BinaryData in, BufferedSink out) throws RuntimeException {
        int bufferSize = 32768;
        try {
            InputStream input = new BufferedInputStream(in, bufferSize);
            byte[] buffer = new byte[bufferSize];
            for (int bytesRead = input.read(buffer); bytesRead != -1; bytesRead = input.read(buffer)) {
                out.write(buffer, 0, bytesRead);
                out.flush();
            }
        } catch (IOException ioe) {
            throw new RuntimeException("Failed to emit to sink", ioe);
        }
    }

    @Override
    public void onOpen(WebSocket webSocket, Response response) {
        this.webSocket = webSocket;
        stopReconnectJobThread();
        log.infoOpenedFeedComm(feedcommUrl);
    }

    @Override
    public void onClose(int reasonCode, String reason) {
        webSocket = null;
        log.infoClosedFeedComm(feedcommUrl, reasonCode, reason);

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
                    startReconnectJobThread();
                    break;
                }
            }
        }
    }

    @Override
    public void onFailure(IOException e, Response response) {
        if (response == null) {
            // don't flood the log with these at the WARN level - its probably just because the server is down
            // and we can't reconnect - while the server is down, our reconnect logic will cause this error
            // to occur periodically. Our reconnect logic will log other messages.
            log.tracef("Feed communications had a failure - a reconnection is likely required: %s", e);
        } else {
            log.warnFeedCommFailure(response.toString(), e);
        }
    }

    @Override
    @SuppressWarnings({ "rawtypes", "unchecked" })
    public void onMessage(BufferedSource payload, WebSocket.PayloadType payloadType) throws IOException {

        BasicMessageWithExtraData<? extends BasicMessage> response;
        String requestClassName = "?";

        try {
            try {
                BasicMessageWithExtraData<? extends BasicMessage> msgWithData;

                switch (payloadType) {
                    case TEXT: {
                        String nameAndJsonStr = payload.readUtf8();
                        msgWithData = new ApiDeserializer().deserialize(nameAndJsonStr);
                        break;
                    }
                    case BINARY: {
                        InputStream input = payload.inputStream();
                        msgWithData = new ApiDeserializer().deserialize(input);
                        break;
                    }
                    default: {
                        throw new IllegalArgumentException(
                                "Unknown payload type, please report this bug: " + payloadType);
                    }
                }

                log.debug("Received message from server");

                BasicMessage msg = msgWithData.getBasicMessage();
                requestClassName = msg.getClass().getName();

                Class<? extends Command<?, ?>> commandClass = VALID_COMMANDS.get(requestClassName);
                if (commandClass == null) {
                    log.errorInvalidCommandRequestFeed(requestClassName);
                    String errorMessage = "Invalid command request: " + requestClassName;
                    GenericErrorResponse errorMsg = new GenericErrorResponseBuilder().setErrorMessage(errorMessage)
                            .build();
                    response = new BasicMessageWithExtraData<BasicMessage>(errorMsg, null);
                } else {
                    Command command = commandClass.newInstance();
                    CommandContext context = new CommandContext(this, this.config, this.discoveryService);
                    response = command.execute(msgWithData, context);
                }
            } finally {
                // must ensure payload is closed; this assumes if it was a stream that the command is finished with it
                payload.close();
            }
        } catch (Throwable t) {
            log.errorCommandExecutionFailureFeed(requestClassName, t);
            String errorMessage = "Command failed [" + requestClassName + "]";
            GenericErrorResponse errorMsg = new GenericErrorResponseBuilder().setThrowable(t)
                    .setErrorMessage(errorMessage).build();
            response = new BasicMessageWithExtraData<BasicMessage>(errorMsg, null);
        }

        // send the response back to the server
        if (response != null) {
            try {
                sendSync(response);
            } catch (Exception e) {
                log.errorFailedToSendOverFeedComm(response.getClass().getName(), e);
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
        if (this.config.getStorageAdapter().getSecurityKey() != null) {
            // rather than use an actual username and password, we will use the offline token identified by key/secret
            auth.setUsername(this.config.getStorageAdapter().getSecurityKey());
            auth.setPassword(this.config.getStorageAdapter().getSecuritySecret());
        } else {
            auth.setUsername(this.config.getStorageAdapter().getUsername());
            auth.setPassword(this.config.getStorageAdapter().getPassword());
        }
        authMessage.setAuthentication(auth);
    }

    private void startReconnectJobThread() {
        ReconnectJobThread newReconnectJob = new ReconnectJobThread();
        ReconnectJobThread oldReconnectJob = reconnectJobThread.getAndSet(newReconnectJob);
        if (oldReconnectJob != null) {
            oldReconnectJob.interrupt();
        }
        newReconnectJob.start();
    }

    private void stopReconnectJobThread() {
        ReconnectJobThread reconnectJob = reconnectJobThread.getAndSet(null);
        if (reconnectJob != null) {
            reconnectJob.interrupt();
        }
    }

    private class ReconnectJobThread extends Thread {
        public ReconnectJobThread() {
            super("Hawkular WildFly Monitor Websocket Reconnect Thread");
            setDaemon(true);
        }

        @Override
        public void run() {
            int attemptCount = 0;
            final long sleepInterval = 1000L;
            boolean keepTrying = true;
            while (keepTrying) {
                try {
                    attemptCount++;
                    Thread.sleep(sleepInterval);
                    if (!isConnected()) {
                        // only log a message for each minute that passes in which we couldn't reconnect
                        if (attemptCount % 60 == 0) {
                            log.errorCannotReconnectToWebSocket(new Exception("Attempt #" + attemptCount));
                        }
                        connect();
                    } else {
                        keepTrying = false;
                    }
                } catch (InterruptedException ie) {
                    keepTrying = false;
                } catch (Exception e) {
                    if (attemptCount % 60 == 0) {
                        log.errorCannotReconnectToWebSocket(e);
                    }
                }
            }
        }
    }
}
