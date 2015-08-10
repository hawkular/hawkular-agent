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
import org.hawkular.bus.common.BasicMessage;
import org.hawkular.bus.common.BasicMessageWithExtraData;
import org.hawkular.feedcomm.api.ApiDeserializer;
import org.hawkular.feedcomm.api.GenericErrorResponseBuilder;

import com.squareup.okhttp.Response;
import com.squareup.okhttp.ws.WebSocket;
import com.squareup.okhttp.ws.WebSocketListener;

import okio.Buffer;
import okio.BufferedSource;

public class FeedCommProcessor implements WebSocketListener {

    private static final Map<String, Class<? extends Command<?, ?>>> VALID_COMMANDS;

    private final MonitorServiceConfiguration config;
    private final Map<ManagedServer, DMRInventoryManager> dmrServerInventories;
    private final ExecutorService sendExecutor = Executors.newSingleThreadExecutor();

    private WebSocket webSocket;


    static {
        VALID_COMMANDS = new HashMap<>();
        VALID_COMMANDS.put(EchoCommand.REQUEST_CLASS.getName(), EchoCommand.class);
        VALID_COMMANDS.put(GenericErrorResponseCommand.REQUEST_CLASS.getName(), GenericErrorResponseCommand.class);
        VALID_COMMANDS.put(ExecuteOperationCommand.REQUEST_CLASS.getName(), ExecuteOperationCommand.class);
        VALID_COMMANDS.put(DeployApplicationCommand.REQUEST_CLASS.getName(), DeployApplicationCommand.class);
    }

    public FeedCommProcessor(MonitorServiceConfiguration config,
            Map<ManagedServer, DMRInventoryManager> dmrServerInventories) {
        this.config = config;
        this.dmrServerInventories = dmrServerInventories;
    }

    public MonitorServiceConfiguration getMonitorServiceConfiguration() {
        return config;
    }

    public Map<ManagedServer, DMRInventoryManager> getDmrServerInventories() {
        return dmrServerInventories;
    }

    /**
     * If the connection is open, this will return the websocket object that can
     * be used to send messages to the server. If the connection is closed, null
     * will be returned.
     *
     * @return the websocket if connection is open, null otherwise
     */
    public WebSocket getWebSocket() {
        return this.webSocket;
    }

    /**
     * Close the web socket with the given reason and code.
     *
     * @param code the close code
     * @param reason the close reason
     */
    public void close(int code, String reason) {
        if (this.webSocket != null) {
            try {
                this.webSocket.close(code, reason);
            } catch (Exception e) {
                MsgLogger.LOG.warnFailedToCloseWebSocket(code, reason, e);
            }
        }
    }

    /**
     * Sends a message to the server asynchronously. This method returns immediately; the message
     * may not go out until some time in the future.
     *
     * @param message the message to send
     */
    public void sendAsync(BasicMessage message) {
        if (this.webSocket == null) {
            throw new IllegalStateException("The connection to the server is closed. Cannot send any messages");
        }

        String messageString = ApiDeserializer.toHawkularFormat(message);

        this.sendExecutor.execute(new Runnable() {
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
        if (this.webSocket == null) {
            throw new IllegalStateException("The connection to the server is closed. Cannot send any messages");
        }

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
        this.webSocket = null;
        MsgLogger.LOG.infoClosedFeedComm(reasonCode, reason);
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
}
