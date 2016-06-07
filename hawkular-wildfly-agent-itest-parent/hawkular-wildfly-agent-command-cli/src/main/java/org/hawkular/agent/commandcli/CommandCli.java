/*
 * Copyright 2015-2016 Red Hat, Inc. and/or its affiliates
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
package org.hawkular.agent.commandcli;

import java.io.BufferedInputStream;
import java.io.Console;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.StandardOpenOption;
import java.util.Arrays;
import java.util.Iterator;
import java.util.Map;
import java.util.Map.Entry;
import java.util.concurrent.CountDownLatch;
import java.util.regex.Pattern;

import org.hawkular.bus.common.BasicMessage;
import org.hawkular.bus.common.BasicMessageWithExtraData;
import org.hawkular.bus.common.BinaryData;
import org.hawkular.cmdgw.api.ApiDeserializer;
import org.jboss.aesh.cl.CommandLine;
import org.jboss.aesh.cl.internal.OptionType;
import org.jboss.aesh.cl.internal.ProcessedCommand;
import org.jboss.aesh.cl.internal.ProcessedCommandBuilder;
import org.jboss.aesh.cl.internal.ProcessedOptionBuilder;
import org.jboss.aesh.cl.parser.CommandLineParser;
import org.jboss.aesh.cl.parser.CommandLineParserBuilder;
import org.jboss.aesh.cl.parser.CommandLineParserException;
import org.jboss.logging.Logger;

import com.squareup.okhttp.Credentials;
import com.squareup.okhttp.MediaType;
import com.squareup.okhttp.OkHttpClient;
import com.squareup.okhttp.Request;
import com.squareup.okhttp.RequestBody;
import com.squareup.okhttp.Response;
import com.squareup.okhttp.ResponseBody;
import com.squareup.okhttp.ws.WebSocket;
import com.squareup.okhttp.ws.WebSocketCall;
import com.squareup.okhttp.ws.WebSocketListener;

import okio.Buffer;
import okio.BufferedSink;

/**
 * A simple CLI that lets you send JSON commands to a Hawkular Server.
 */
public class CommandCli {
    private static final Logger log = Logger.getLogger(CommandCli.class);

    private static final String COMMAND_NAME = "hawkular-command";

    private static final String OPT_OUTPUT_DIR = "output-dir"; // where to put the response output
    private static final String OPT_SERVER_WEB_SOCKET_URL = "server-url"; // where the hawkular server is
    private static final String OPT_USERNAME = "username";
    private static final String OPT_PASSWORD = "password";
    private static final String OPT_COMMAND = "command";
    private static final String OPT_REQUEST_FILE = "request-file";
    private static final String OPT_BINARY_DATA_FILE = "binary-data-file";
    private static final char OPT_PROPERTY = 'P';

    private static class Config {
        File outputDir;
        String serverUrl;
        String username;
        String password;
        String command;
        String jsonRequest;
        File binaryDataFile;

        public String toString() {
            StringBuilder str = new StringBuilder("CLI Configuration:\n");
            str.append("Server URL:       ").append(this.serverUrl).append("\n");
            str.append("Username:         ").append(this.username).append("\n");
            str.append("Password:         ").append(this.password).append("\n");
            str.append("Output Directory: ").append(this.outputDir).append("\n");
            str.append("Command:          ").append(this.command).append("\n");
            str.append("Binary Data File: ").append(this.binaryDataFile).append("\n");
            str.append("JSON Request:     ").append(this.jsonRequest).append("\n");
            return str.toString();
        }
    }

    private static class CliWebSocketListener implements WebSocketListener {
        private final Config config;
        private final OkHttpClient httpClient;
        private final WebSocketCall webSocketCall;
        private final CountDownLatch latch = new CountDownLatch(1);
        private WebSocket webSocket;

        public CliWebSocketListener(OkHttpClient httpClient, WebSocketCall wsc, Config config) {
            this.httpClient = httpClient;
            this.webSocketCall = wsc;
            this.config = config;
            wsc.enqueue(this);
        }

        public void waitForResponse() throws InterruptedException {
            latch.await();
        }

        @Override
        public void onOpen(WebSocket ws, Response response) {
            this.webSocket = ws;
            log.infof("Web socket opened to [%s]", config.serverUrl);
            try {
                sendCommandNow();
            } catch (Exception e) {
                log.errorf(e, "Failed to send command");
                shutdown(e);
            }
        }

        @Override
        public void onClose(int code, String reason) {
            log.infof("Web socket closed. code=[%d], reason=[%s]", code, reason);
            shutdown(null);
        }

        @Override
        public void onFailure(IOException error, Response response) {
            log.errorf(error, "Command failed: %s", response);
            shutdown(error);
        }

        @Override
        public void onMessage(ResponseBody responseBody) throws IOException {

            boolean finished = true; // stop after this message unless we know we should expect more
            try {
                BasicMessageWithExtraData<? extends BasicMessage> msgWithData;

                if (responseBody.contentType().equals(WebSocket.TEXT)) {
                    String nameAndJsonStr = responseBody.string();
                    msgWithData = new ApiDeserializer().deserialize(nameAndJsonStr);
                } else if (responseBody.contentType().equals(WebSocket.BINARY)) {
                    InputStream input = responseBody.byteStream();
                    msgWithData = new ApiDeserializer().deserialize(input);
                } else {
                    throw new IllegalArgumentException(
                            "Unknown mediatype type, please report this bug: " + responseBody.contentType());
                }

                BasicMessage msg = msgWithData.getBasicMessage();
                BinaryData binary = msgWithData.getBinaryData();

                String messageName = msg.getClass().getSimpleName();
                log.debugf("Received message from server: " + messageName);

                long now = System.currentTimeMillis();
                File jsonMessageFile = new File(config.outputDir, messageName + now + ".json");
                Files.write(jsonMessageFile.toPath(), msg.toJSON().getBytes(), StandardOpenOption.CREATE_NEW);
                if (binary != null) {
                    File binaryFile = new File(config.outputDir, messageName + now + ".binary");
                    Files.copy(binary, binaryFile.toPath());
                }

                // the response of our command should always be the same name of the command except
                // with the word "Response" in the name as opposed to "Request". Remember that JSON commands
                // can be specified with just the simple class name of the fully qualified class name. We
                // should check for both combinations. So if we send "EchoRequest" command, we should expect
                // "EchoResponse" back. If we send "org.abc.MyRequest" we can expect "org.abc.MyResponse"
                finished = (Arrays.asList(msg.getClass().getSimpleName(), msg.getClass().getName())
                        .contains(config.command.replace("Request", "Response")));
            } finally {
                responseBody.close();
                if (finished) {
                    shutdown(null);
                }
            }
        }

        @Override
        public void onPong(Buffer buf) {
        }

        private void sendCommandNow() throws Exception {
            if (config.binaryDataFile == null) {
                Buffer buffer = new Buffer();
                buffer.writeUtf8(config.jsonRequest);
                RequestBody requestBody = RequestBody.create(WebSocket.TEXT, buffer.readByteArray());
                webSocket.sendMessage(requestBody);
            } else {
                RequestBody requestBody = new RequestBody() {
                    @Override
                    public MediaType contentType() {
                        return WebSocket.BINARY;
                    }

                    @Override
                    public void writeTo(BufferedSink bufferedSink) throws IOException {
                        // write the JSON request itself
                        bufferedSink.write(config.jsonRequest.getBytes());

                        // now stream the binary data
                        int bufferSize = 32768;
                        try (InputStream input = new BufferedInputStream(new FileInputStream(config.binaryDataFile),
                                bufferSize)) {
                            byte[] buffer = new byte[bufferSize];
                            for (int bytesRead = input.read(buffer); bytesRead != -1; bytesRead = input.read(buffer)) {
                                bufferedSink.write(buffer, 0, bytesRead);
                                bufferedSink.flush();
                            }
                        } catch (IOException ioe) {
                            throw new RuntimeException("Failed to emit to sink", ioe);
                        }
                    }
                };

                webSocket.sendMessage(requestBody);
            }
        }

        private void shutdown(Exception e) {
            try {
                if (webSocket != null) {
                    if (e != null) {
                        webSocket.close(1011, e.getMessage());
                    } else {
                        webSocket.close(1000, "Done");
                    }
                }

                webSocketCall.cancel();

                httpClient.getDispatcher().getExecutorService().shutdown();

            } catch (Exception e2) {
                log.errorf(e2, "Cannot fully close websocket");
            } finally {
                latch.countDown();
            }
        }
    }

    public static void main(String[] args) throws Exception {
        ProcessedCommand<?> options = buildCommandLineOptions();

        try {
            Config config = parseCommandLine(options, args);
            CliWebSocketListener listener = sendCommand(config);
            listener.waitForResponse();
        } catch (CommandLineParserException pe) {
            log.errorf(pe, "Failed to parse command line.");
            printHelp(options);
        } catch (Exception ex) {
            log.errorf(ex, "Unexpected error");
        }
    }

    private static CliWebSocketListener sendCommand(Config config) throws Exception {

        OkHttpClient httpClient = new OkHttpClient();

        Request request = new Request.Builder()
                .url(config.serverUrl)
                .addHeader("Authorization", Credentials.basic(config.username, config.password))
                .addHeader("Accept", "application/json")
                .build();

        WebSocketCall wsc = WebSocketCall.create(httpClient, request);
        CliWebSocketListener listener = new CliWebSocketListener(httpClient, wsc, config);
        return listener;
    }

    private static Config parseCommandLine(ProcessedCommand<?> options, String[] args) throws Exception {
        StringBuilder argLine = new StringBuilder(COMMAND_NAME);
        for (String str : args) {
            argLine.append(' ').append(str);
        }

        CommandLineParser<?> parser = new CommandLineParserBuilder().processedCommand(options).create();

        CommandLine<?> commandLine = parser.parse(argLine.toString());
        if (commandLine.getParserException() != null) {
            throw commandLine.getParserException();
        }

        File outputDir = new File(commandLine.getOptionValue(OPT_OUTPUT_DIR, "."));
        String urlStr = commandLine.getOptionValue(OPT_SERVER_WEB_SOCKET_URL,
                "ws://127.0.0.1:8080/hawkular/command-gateway/ui/ws");
        String username = commandLine.getOptionValue(OPT_USERNAME);
        String password = commandLine.getOptionValue(OPT_PASSWORD);
        String command = commandLine.getOptionValue(OPT_COMMAND);
        String jsonRequestFileStr = commandLine.getOptionValue(OPT_REQUEST_FILE);
        String binaryDataFileStr = commandLine.getOptionValue(OPT_BINARY_DATA_FILE);
        Map<String, String> jsonProperties = commandLine.getOptionProperties("P");

        if (!Pattern.matches(".*[^/]/[^/].*", urlStr)) {
            log.infof("URL [%s] did not specify a path - using '/hawkular/command-gateway/ui/ws'", urlStr);
            urlStr = urlStr + (urlStr.endsWith("/") ? "" : "/") + "hawkular/command-gateway/ui/ws";
        }

        if (password == null || password.isEmpty()) {
            password = readSecretFromStdin("Password:");
            if (password == null || password.isEmpty()) {
                throw new Exception("Password was not provided");
            }
        }

        if (!outputDir.isDirectory()) {
            outputDir.mkdirs();
            if (!outputDir.isDirectory()) {
                throw new Exception("Cannot create response directory: " + outputDir);
            }
        }

        if (binaryDataFileStr != null) {
            File file = new File(binaryDataFileStr);
            if (!file.isFile()) {
                throw new Exception(
                        "Binary data file does not exist or is not a regular file: " + binaryDataFileStr);
            }
            if (!(file.canRead())) {
                throw new Exception("Cannot read binary data file: " + binaryDataFileStr);
            }
        }

        StringBuilder jsonRequest = new StringBuilder().append(command).append("=");

        if (jsonRequestFileStr != null) {
            // the JSON request was given to us via file
            jsonRequest.append(new String(Files.readAllBytes(new File(jsonRequestFileStr).toPath())));
        } else {
            // build the JSON using the properties passed on the command line
            jsonRequest.append("{");
            Iterator<Entry<String, String>> iter = jsonProperties.entrySet().iterator();
            while (iter.hasNext()) {
                Entry<String, String> jsonProperty = iter.next();
                jsonRequest.append('"').append(jsonProperty.getKey()).append('"');
                jsonRequest.append(':');
                jsonRequest.append('"').append(jsonProperty.getValue()).append('"');
                if (iter.hasNext()) {
                    jsonRequest.append(",");
                }
            }
            jsonRequest.append("}");
        }

        Config config = new Config();
        config.outputDir = outputDir;
        config.serverUrl = urlStr;
        config.username = username;
        config.password = password;
        config.command = command;
        config.jsonRequest = jsonRequest.toString();
        config.binaryDataFile = (binaryDataFileStr == null) ? null : new File(binaryDataFileStr);

        log.info(config);

        return config;
    }

    private static void printHelp(ProcessedCommand<?> options) {
        if (options == null) {
            throw new RuntimeException("Cannot print help - options is null");
        }
        System.out.println(options.printHelp());
    }

    /**
     * Reads a secret from the console (stdin).
     *
     * @param message to present before reading
     * @return secret value or null if console is not available
     */
    private static String readSecretFromStdin(String message) {
        Console console = System.console();
        if (console == null) {
            return null;
        }
        console.writer().write(message);
        console.writer().flush();
        return String.valueOf(console.readPassword());
    }

    private static ProcessedCommand<?> buildCommandLineOptions() throws Exception {
        ProcessedCommandBuilder cmd = new ProcessedCommandBuilder();

        cmd.name(COMMAND_NAME);
        cmd.description("Sends commands to Hawkular Server");

        cmd.addOption(new ProcessedOptionBuilder()
                .name(OPT_OUTPUT_DIR)
                .optionType(OptionType.NORMAL)
                .type(String.class)
                .description("Directory to store the response. Default is the current directory.")
                .create());
        cmd.addOption(new ProcessedOptionBuilder()
                .name(OPT_SERVER_WEB_SOCKET_URL)
                .optionType(OptionType.NORMAL)
                .type(String.class)
                .description("The URL to connect to the Hawkular Server web socket. "
                        + "Default is ws://127.0.0.1:8080/hawkular/command-gateway/ui/ws")
                .create());
        cmd.addOption(new ProcessedOptionBuilder()
                .name(OPT_USERNAME)
                .optionType(OptionType.NORMAL)
                .type(String.class)
                .description("User the CLI will use when connecting to Hawkular Server.")
                .required(true)
                .create());
        cmd.addOption(new ProcessedOptionBuilder()
                .name(OPT_PASSWORD)
                .optionType(OptionType.NORMAL)
                .type(String.class)
                .description("Credentials the CLI will use when connecting to Hawkular Server.")
                .required(false) // if not specified on command line, it will be requested on stdin later
                .create());
        cmd.addOption(new ProcessedOptionBuilder()
                .name(OPT_COMMAND)
                .optionType(OptionType.NORMAL)
                .type(String.class)
                .description("The name of the JSON command request to send.")
                .required(true)
                .create());
        cmd.addOption(new ProcessedOptionBuilder()
                .name(OPT_BINARY_DATA_FILE)
                .optionType(OptionType.NORMAL)
                .type(String.class)
                .description("File containing binary data to send along with the commmand (optional).")
                .create());
        cmd.addOption(new ProcessedOptionBuilder()
                .name(OPT_REQUEST_FILE)
                .optionType(OptionType.NORMAL)
                .type(String.class)
                .description("File containing the JSON request (optional).")
                .create());
        cmd.addOption(new ProcessedOptionBuilder()
                .name(String.valueOf(OPT_PROPERTY))
                .shortName(OPT_PROPERTY)
                .optionType(OptionType.GROUP)
                .type(String.class)
                .valueSeparator('=')
                .description("<name>=<value> properties that make up the JSON request. "
                        + "Ignored if --" + OPT_REQUEST_FILE + " is specified.")
                .create());

        return cmd.create();
    }

}
