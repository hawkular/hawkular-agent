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
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Map.Entry;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.logging.LogManager;
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

import okhttp3.Credentials;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import okhttp3.ResponseBody;
import okhttp3.ws.WebSocket;
import okhttp3.ws.WebSocketCall;
import okhttp3.ws.WebSocketListener;
import okio.Buffer;
import okio.BufferedSink;

/**
 * A simple CLI that lets you send JSON commands to a Hawkular Server.
 */
public class CommandCli {
    // read our logging config from our jar if the user didn't provide one
    static {
        if (System.getProperty("java.util.logging.config.file") == null) {
            String filename = Boolean.getBoolean("debug") ? "/logging-debug.properties" : "/logging.properties";
            final InputStream inputStream = CommandCli.class.getResourceAsStream(filename);
            try {
                LogManager.getLogManager().readConfiguration(inputStream);
            } catch (final IOException e) {
                java.util.logging.Logger.getAnonymousLogger().severe("Could not load " + filename);
                java.util.logging.Logger.getAnonymousLogger().severe(e.getMessage());
            }
        }
    }

    private static final Logger log = Logger.getLogger(CommandCli.class);

    private static final String COMMAND_NAME = "hawkular-command";

    private static final String OPT_OUTPUT_DIR = "output-dir"; // where to put the response output
    private static final String OPT_SERVER_WEB_SOCKET_URL = "server-url"; // where the hawkular server is
    private static final String OPT_USERNAME = "username";
    private static final String OPT_PASSWORD = "password";
    private static final String OPT_COMMAND = "command";
    private static final String OPT_EXPECTED_RESPONSE = "expected-response";
    private static final String OPT_REQUEST_FILE = "request-file";
    private static final String OPT_BINARY_DATA_FILE = "binary-data-file";
    private static final char OPT_PROPERTY = 'P';
    private static final char OPT_MAP = 'M';

    private static class Config {
        File outputDir;
        String serverUrl;
        String username;
        String password;
        String command;
        String expectedResponse;
        String jsonRequest;
        File binaryDataFile;

        @Override
        public String toString() {
            StringBuilder str = new StringBuilder("CLI Configuration:\n");
            str.append("Server URL:        ").append(this.serverUrl).append("\n");
            str.append("Username:          ").append(this.username).append("\n");
            str.append("Password:          ").append("***").append("\n");
            str.append("Output Directory:  ").append(this.outputDir).append("\n");
            str.append("Command:           ").append(this.command).append("\n");
            str.append("Expected Response: ").append(this.expectedResponse).append("\n");
            str.append("Binary Data File:  ").append(this.binaryDataFile).append("\n");
            str.append("JSON Request:      ").append(this.jsonRequest).append("\n");
            return str.toString();
        }

        public boolean willRespond() {
            boolean willRespond = !jsonRequest.toLowerCase().contains(":\"stop\"");
            return willRespond;
        }
    }

    private static class CliWebSocketListener implements WebSocketListener {
        private final Config config;
        private final OkHttpClient httpClient;
        private final CountDownLatch latch = new CountDownLatch(1);
        private WebSocket webSocket;

        public CliWebSocketListener(OkHttpClient httpClient, WebSocketCall wsc, Config config) {
            this.httpClient = httpClient;
            this.config = config;
            wsc.enqueue(this);
        }

        public void waitForResponse() throws InterruptedException {
            latch.await();
        }

        @Override
        public void onOpen(WebSocket ws, Response response) {
            this.webSocket = ws;
            log.debugf("Web socket opened to [%s]", config.serverUrl);
            try {
                sendCommandNow();
            } catch (Exception e) {
                log.errorf(e, "Failed to send command");
                shutdown(e);
            }
        }

        @Override
        public void onClose(int code, String reason) {
            log.debugf("Web socket closed. code=[%d], reason=[%s]", code, reason);
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
                log.debugf("JSON response: %s=%s", messageName, msg.toJSON());

                long now = System.currentTimeMillis();
                File jsonMessageFile = new File(config.outputDir, messageName + now + ".json");
                Files.write(jsonMessageFile.toPath(), msg.toJSON().getBytes(), StandardOpenOption.CREATE_NEW);
                log.infof("JSON response stored in file: %s", jsonMessageFile);
                if (binary != null) {
                    File binaryFile = new File(config.outputDir, messageName + now + ".binary");
                    Files.copy(binary, binaryFile.toPath());
                    log.infof("Binary data was in response and has been stored in file: %s", binaryFile);
                }

                // See if the response was the expected one. If so, we can stop.
                // If we weren't given an exepcted response, then we assume the response is the
                // same name of the command except with the word "Response" in the name as opposed to "Request".
                // Also, remember that JSON commands can be specified with just the simple class name of the
                // fully qualified class name. We should check for both combinations. So if we send
                // "EchoRequest" command, we can expect "EchoResponse" back. If we send
                // "org.abc.MyRequest" we can expect "org.abc.MyResponse"
                // NOTE: if we get the GenericErrorResponse, we immediately abort.
                if (msg.getClass().getName().contains("GenericErrorResponse")) {
                    finished = true;
                } else {
                    String expectedResponse = config.expectedResponse;
                    if (expectedResponse == null || expectedResponse.isEmpty()) {
                        expectedResponse = config.command.replace("Request", "Response");
                    }
                    finished = (Arrays.asList(msg.getClass().getSimpleName(), msg.getClass().getName())
                            .contains(expectedResponse));
                }
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
            if (latch.getCount() == 0) {
                return;
            }

            try {
                if (webSocket != null) {
                    try {
                        if (e != null) {
                            webSocket.close(1011, e.getMessage());
                        } else {
                            webSocket.close(1000, CommandCli.class.getSimpleName() + " Done");
                        }
                    } catch (Exception closeException) {
                        // ignore this if it is just telling us the websocket is already closed
                        if (!(closeException instanceof IllegalStateException)
                                && !(closeException.getMessage().contains("Socket closed"))) {
                            throw closeException;
                        }
                    }
                }

                httpClient.dispatcher().executorService().shutdown();

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
            if (config.willRespond()) {
                listener.waitForResponse();
            } else {
                // ensure the call is running (no longer queued) before shutting down the listener
                while (listener.httpClient.dispatcher().runningCallsCount() == 0) {
                    try {
                        Thread.sleep(100);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    }
                }
                listener.shutdown(null);
            }
        } catch (CommandLineParserException pe) {
            log.errorf(pe, "Failed to parse command line.");
            printHelp(options);
        } catch (Exception ex) {
            log.errorf(ex, "Unexpected error");
        }
    }

    private static CliWebSocketListener sendCommand(Config config) throws Exception {

        OkHttpClient httpClient = new OkHttpClient.Builder()
                .connectTimeout(10, TimeUnit.SECONDS)
                .readTimeout(5, TimeUnit.MINUTES).build();

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
            argLine.append(' ').append('\'').append(str).append('\'');
        }
        log.debugf("Command line: %s", argLine);

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
        String expectedResponse = commandLine.getOptionValue(OPT_EXPECTED_RESPONSE);
        String jsonRequestFileStr = commandLine.getOptionValue(OPT_REQUEST_FILE);
        String binaryDataFileStr = commandLine.getOptionValue(OPT_BINARY_DATA_FILE);
        Map<String, String> jsonProperties = commandLine.getOptionProperties(String.valueOf(OPT_PROPERTY));
        Map<String, String> jsonMaps = commandLine.getOptionProperties(String.valueOf(OPT_MAP));

        if (!Pattern.matches(".*[^/]/[^/].*", urlStr)) {
            log.debugf("URL [%s] did not specify a path - using '/hawkular/command-gateway/ui/ws'", urlStr);
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
            String requestString = new String(Files.readAllBytes(new File(jsonRequestFileStr).toPath()));

            // if the file had a newline or other characters padding its end, remove them
            int endOfJson = requestString.lastIndexOf('}');
            if (endOfJson == -1) {
                log.warnf("The request file [%s] does not appear to be valid JSON", jsonRequestFileStr);
                jsonRequest.append(requestString); // just send the whole thing and expect an error later
            } else {
                if (endOfJson == (requestString.length() - 1)) {
                    jsonRequest.append(requestString);
                } else {
                    int ignoring = requestString.length() - endOfJson - 1;
                    log.debugf("Ignoring [%d] non-JSON characters found at the end of the request file [%s]",
                            ignoring, jsonRequestFileStr);
                    jsonRequest.append(requestString.substring(0, endOfJson + 1));
                }
            }
        } else {
            // build the JSON using the properties and maps passed on the command line
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

            // build the parameter maps and then put it in the JSON
            if (!jsonProperties.isEmpty() && !jsonMaps.isEmpty()) {
                jsonRequest.append(",");
            }

            Map<String, Map<String, String>> maps = new HashMap<>();
            for (Entry<String, String> mapNameKeyValue : jsonMaps.entrySet()) {
                String[] mapNameKey = mapNameKeyValue.getKey().split(":", 2);
                if (mapNameKey.length != 2) {
                    throw new Exception("Invalid -M argument: " + mapNameKeyValue.getKey());
                }
                String mapName = mapNameKey[0];
                String mapKey = mapNameKey[1];
                String mapValue = mapNameKeyValue.getValue();

                Map<String, String> parameterMap = maps.get(mapName);
                if (parameterMap == null) {
                    parameterMap = new HashMap<>();
                    maps.put(mapName, parameterMap);
                }
                parameterMap.put(mapKey, mapValue);
            }

            Iterator<Entry<String, Map<String, String>>> iterMap = maps.entrySet().iterator();
            while (iterMap.hasNext()) {
                Entry<String, Map<String, String>> jsonMapEntry = iterMap.next();
                jsonRequest.append('"').append(jsonMapEntry.getKey()).append('"');
                jsonRequest.append(':');
                jsonRequest.append('{');

                Iterator<Entry<String, String>> iterMapProperty = jsonMapEntry.getValue().entrySet().iterator();
                while (iterMapProperty.hasNext()) {
                    Entry<String, String> jsonMapPropertyEntry = iterMapProperty.next();
                    jsonRequest.append('"').append(jsonMapPropertyEntry.getKey()).append('"');
                    jsonRequest.append(':');
                    jsonRequest.append('"').append(jsonMapPropertyEntry.getValue()).append('"');
                    if (iterMapProperty.hasNext()) {
                        jsonRequest.append(",");
                    }
                }

                jsonRequest.append('}');

                // go on to the next map
                if (iterMap.hasNext()) {
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
        config.expectedResponse = expectedResponse;
        config.jsonRequest = jsonRequest.toString();
        config.binaryDataFile = (binaryDataFileStr == null) ? null : new File(binaryDataFileStr);

        log.debug(config);

        return config;
    }

    private static void printHelp(ProcessedCommand<?> options) {
        if (options == null) {
            throw new RuntimeException("Cannot print help - options is null");
        }
        System.out.println(options.printHelp());
        System.out.println("NOTE: Set the 'debug' system property to 'true' for debug logging");
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
                .name(OPT_EXPECTED_RESPONSE)
                .optionType(OptionType.NORMAL)
                .type(String.class)
                .description("The expected name of the JSON response. "
                        + "If not specified, a guess will be made based on the name of the command request.")
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
        cmd.addOption(new ProcessedOptionBuilder()
                .name(String.valueOf(OPT_MAP))
                .shortName(OPT_MAP)
                .optionType(OptionType.GROUP)
                .type(String.class)
                .valueSeparator('=')
                .description("<name>:<key>=<value> map properties that make up the JSON request. "
                        + "This lets you build up command parameters that are maps of key/value pairs. "
                        + "<name> is the name of the map parameter and <key> is an entry in that map. "
                        + "Ignored if --" + OPT_REQUEST_FILE + " is specified.")
                .create());

        return cmd.create();
    }

}
