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
package org.hawkular.agent.monitor.util;

import java.io.BufferedInputStream;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.Reader;
import java.io.UnsupportedEncodingException;
import java.io.Writer;
import java.net.MalformedURLException;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Collection;
import java.util.stream.Collectors;

import org.hawkular.agent.monitor.extension.MonitorServiceConfiguration;
import org.hawkular.agent.monitor.extension.MonitorServiceConfigurationBuilder;
import org.hawkular.agent.monitor.extension.SubsystemExtension;
import org.hawkular.agent.monitor.log.AgentLoggers;
import org.hawkular.agent.monitor.log.MsgLogger;
import org.hawkular.inventory.json.InventoryJacksonConfig;
import org.jboss.as.controller.AttributeDefinition;
import org.jboss.as.controller.OperationContext;
import org.jboss.as.controller.OperationFailedException;
import org.jboss.as.controller.PathAddress;
import org.jboss.as.controller.PathElement;
import org.jboss.as.controller.ReloadRequiredWriteAttributeHandler;
import org.jboss.as.controller.registry.AttributeAccess;
import org.jboss.as.controller.registry.ManagementResourceRegistration;
import org.jboss.as.controller.registry.Resource;
import org.jboss.dmr.ModelNode;

import com.fasterxml.jackson.annotation.JsonAutoDetect;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Just some basic utilities.
 *
 * @author John Mazzitelli
 */
public class Util {
    private static final MsgLogger log = AgentLoggers.getLogger(Util.class);

    private static final String ENCODING_UTF_8 = "utf-8";
    private static final int BUFFER_SIZE = 128;
    private static final String HAWKULAR_AGENT_MACHINE_ID = "hawkular.agent.machine.id";
    private static ObjectMapper mapper;
    private static String systemId;

    static {
        try {
            mapper = new ObjectMapper();
            mapper.setVisibilityChecker(mapper.getSerializationConfig().getDefaultVisibilityChecker()
                    .withFieldVisibility(JsonAutoDetect.Visibility.ANY)
                    .withGetterVisibility(JsonAutoDetect.Visibility.NONE)
                    .withSetterVisibility(JsonAutoDetect.Visibility.NONE)
                    .withCreatorVisibility(JsonAutoDetect.Visibility.NONE));
            InventoryJacksonConfig.configure(mapper);
        } catch (Throwable t) {
            // don't break the class loading
        }
    }

    public static String toJson(Object obj) {
        final String json;
        try {
            json = mapper.writeValueAsString(obj);
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException("Object cannot be parsed as JSON", e);
        }
        return json;
    }

    public static <T> T fromJson(String json, Class<T> clazz) {
        final T obj;
        try {
            obj = mapper.readValue(json, clazz);
        } catch (IOException e) {
            throw new IllegalArgumentException("JSON message cannot be converted to object of type:" + clazz, e);
        }
        return obj;
    }

    public static <T> T fromJson(Reader in, TypeReference<T> typeReference) {
        final T obj;
        try {
            obj = mapper.readValue(in, typeReference);
        } catch (IOException e) {
            throw new IllegalArgumentException("JSON message cannot be converted to object of type:" + typeReference,
                    e);
        }
        return obj;
    }

    /**
     * Encodes the given string so it can be placed inside a URL. This should not be the query part of the URL, for
     * that use {@link #urlEncodeQuery(String)}.
     *
     * @param str non-query string to encode
     * @return encoded string that can be placed inside a URL
     */
    public static String urlEncode(String str) {
        try {
            String encodeForForm = URLEncoder.encode(str, "UTF-8");
            String encodeForUrl = encodeForForm.replace("+", "%20");
            return encodeForUrl;
        } catch (UnsupportedEncodingException e) {
            throw new IllegalStateException("JVM does not support UTF-8");
        }
    }

    /**
     * Encodes the given string so it can be placed inside a URL. This should oly be used for
     * the query part of the URL, for other parts, like path, use {@link #urlEncode(String)}.
     *
     * @param str non-query string to encode
     * @return encoded string that can be placed inside a URL
     */
    public static String urlEncodeQuery(String str) {
        try {
            String encodeForForm = URLEncoder.encode(str, "UTF-8");
            return encodeForForm;
        } catch (UnsupportedEncodingException e) {
            throw new IllegalStateException("JVM does not support UTF-8");
        }
    }

    /**
     * Given a base URL (like 'http://localhost:8080') this will append the given context string to it and will return
     * the URL with a forward-slash as its last character.
     *
     * This returns a StringBuilder so the caller can continue building its desired URL by appending to it additional
     * context paths, query strings, and the like.
     *
     * @param baseUrl base URL to append the given context to
     * @param context the context to add to the given base URL
     * @return the base URL with the context appended to it
     *
     * @throws MalformedURLException if URL cannot be built
     */
    public static StringBuilder getContextUrlString(String baseUrl, String context) throws MalformedURLException {
        StringBuilder urlStr = new StringBuilder(baseUrl);
        ensureEndsWithSlash(urlStr);
        if (context != null && context.length() > 0) {
            if (context.startsWith("/")) {
                urlStr.append(context.substring(1));
            } else {
                urlStr.append(context);
            }
            ensureEndsWithSlash(urlStr);
        }
        return urlStr;
    }

    /**
     * Given a string builder, this ensures its last character is a forward-slash.
     *
     * @param str string builder to have a forward-slash character as its last when this method returns
     */
    public static void ensureEndsWithSlash(StringBuilder str) {
        if (str.length() == 0 || str.charAt(str.length() - 1) != '/') {
            str.append('/');
        }
    }

    /**
     * Copies one stream to another, optionally closing the streams.
     *
     * @param input the data to copy
     * @param output where to copy the data
     * @param closeStreams if true input and output will be closed when the method returns
     * @return the number of bytes copied
     * @throws RuntimeException if the copy failed
     */
    public static long copyStream(InputStream input, OutputStream output, boolean closeStreams)
            throws RuntimeException {
        long numBytesCopied = 0;
        int bufferSize = BUFFER_SIZE;
        try {
            // make sure we buffer the input
            input = new BufferedInputStream(input, bufferSize);
            byte[] buffer = new byte[bufferSize];
            for (int bytesRead = input.read(buffer); bytesRead != -1; bytesRead = input.read(buffer)) {
                output.write(buffer, 0, bytesRead);
                numBytesCopied += bytesRead;
            }
            output.flush();
        } catch (IOException ioe) {
            throw new RuntimeException("Stream data cannot be copied", ioe);
        } finally {
            if (closeStreams) {
                try {
                    output.close();
                } catch (IOException ioe2) {
                    // what to do?
                }
                try {
                    input.close();
                } catch (IOException ioe2) {
                    // what to do?
                }
            }
        }
        return numBytesCopied;
    }

    /**
     * Given an input stream, its data will be slurped in memory and returned as a String. The input stream will be
     * closed when this method returns. WARNING: do not slurp large streams to avoid out-of-memory errors.
     *
     * @param input the input stream to slup
     * @param encoding the encoding to use when reading from {@code input}
     * @return the input stream data as a String
     * @throws IOException in IO problems
     */
    public static String slurpStream(InputStream input, String encoding) throws IOException {
        try (Reader r = new InputStreamReader(input, encoding)) {
            StringBuilder result = new StringBuilder();
            char[] buffer = new char[BUFFER_SIZE];
            int len = 0;
            while ((len = r.read(buffer, 0, BUFFER_SIZE)) >= 0) {
                result.append(buffer, 0, len);
            }
            return result.toString();
        }
    }

    /**
     * Read the content of the given {@code file} using {@link #ENCODING_UTF_8} and return it as a {@link String}.
     * @param file the {@link File} to read from
     * @return the content of the given {@code file}
     * @throws IOException in IO problems
     */
    public static String read(File file) throws IOException {
        return slurpStream(new FileInputStream(file), ENCODING_UTF_8);
    }

    /**
     * Stores {@code string} to {@code file} using {@link #ENCODING_UTF_8}.
     *
     * @param string the {@link String} to store
     * @param file the file to store to
     * @throws IOException on IO errors
     */
    public static void write(String string, File file) throws IOException {
        try (Writer w = new OutputStreamWriter(new FileOutputStream(file), ENCODING_UTF_8)) {
            w.write(string);
        }
    }

    /**
     * Encodes a string using Base64 encoding.
     *
     * @param plainTextString the string to encode
     * @return the given string as a Base64 encoded string.
     */
    public static String base64Encode(String plainTextString) {
        String encoded = new String(Base64.getEncoder().encode(plainTextString.getBytes()));
        return encoded;
    }

    /**
     * This will register only those given attributes that require a restart.
     * Other attributes are left unregistered. It is the caller's responsibility to register those
     * other attributes. Typically those other attributes support changing the service at runtime
     * immediately when the attribute is changed (rather than requiring to restart the server to
     * pick up the change).
     *
     * @param resourceRegistration there the restart attributes will be registered
     * @param allAttributes a collection of attributes where some, all, or none will require a restart upon change.
     */
    public static void registerOnlyRestartAttributes(
            ManagementResourceRegistration resourceRegistration,
            Collection<AttributeDefinition> allAttributes) {
        Collection<AttributeDefinition> restartResourceServicesAttributes = new ArrayList<>();
        for (AttributeDefinition attribDef : allAttributes) {
            if (attribDef.getFlags().contains(AttributeAccess.Flag.RESTART_RESOURCE_SERVICES)
                    || attribDef.getFlags().contains(AttributeAccess.Flag.RESTART_JVM)
                    || attribDef.getFlags().contains(AttributeAccess.Flag.RESTART_ALL_SERVICES)) {
                restartResourceServicesAttributes.add(attribDef);
            }
        }
        ReloadRequiredWriteAttributeHandler handler = new ReloadRequiredWriteAttributeHandler(
                restartResourceServicesAttributes);
        for (AttributeDefinition attribDef : restartResourceServicesAttributes) {
            resourceRegistration.registerReadWriteAttribute(attribDef, null, handler);
        }
    }

    /**
     * Used by extension classes that need to get the subsystem's configuration.
     *
     * @param context context used to obtain the config
     * @return the subsystem config
     * @throws OperationFailedException
     */
    public static MonitorServiceConfiguration getMonitorServiceConfiguration(OperationContext context)
            throws OperationFailedException {
        PathAddress subsystemAddress = PathAddress
                .pathAddress(PathElement.pathElement("subsystem", SubsystemExtension.SUBSYSTEM_NAME));
        ModelNode subsystemConfig = Resource.Tools.readModel(context.readResourceFromRoot(subsystemAddress));
        MonitorServiceConfiguration config = new MonitorServiceConfigurationBuilder(subsystemConfig, context).build();
        return config;
    }

    /**
     * Tries to determine the system ID for the machine where this JVM is located.
     * First check if the user explicitly set it. If not try to read it from
     * /etc/machine-id
     *
     * @return system ID or null if cannot determine
     */
    public static String getSystemId() {

        if (systemId == null) {
            systemId = System.getProperty(HAWKULAR_AGENT_MACHINE_ID);
            if (systemId != null) {
                log.infof("MachineId was explicitly set to [%s]", systemId);
            }
        }

        if (systemId == null) {
            File machineIdFile = new File("/etc/machine-id");
            if (machineIdFile.exists() && machineIdFile.canRead()) {
                try (Reader reader = new InputStreamReader(new FileInputStream(machineIdFile))) {
                    systemId = new BufferedReader(reader).lines().collect(Collectors.joining("\n"));
                } catch (IOException e) {
                    log.warnf(e, "/etc/machine-id exists and is readable, but exception was raised when reading it");
                    systemId = "";
                }
            } else {
                log.warnf("/etc/machine-id does not exist or is unreadable");
                // for the future, we might want to check additional places and try different things
                systemId = "";
            }
        }

        return (systemId.isEmpty()) ? null : systemId;
    }
}
