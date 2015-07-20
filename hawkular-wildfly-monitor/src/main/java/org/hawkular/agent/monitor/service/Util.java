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
package org.hawkular.agent.monitor.service;

import java.io.BufferedInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.UnsupportedEncodingException;
import java.net.MalformedURLException;
import java.net.URLEncoder;
import java.util.Base64;

import com.fasterxml.jackson.annotation.JsonAutoDetect;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.hawkular.inventory.json.InventoryJacksonConfig;

/**
 * Just some basic utilities.
 */
public class Util {
    private static ObjectMapper mapper;
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

    /**
     * Encodes the given string so it can be placed inside a URL.
     *
     * @param str string to encode
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
     * Given a base URL (like 'http://localhost:8080') this will append the given
     * context string to it and will return the URL with a forward-slash as its last character.
     *
     * This returns a StringBuilder so the caller can continue building its desired URL
     * by appending to it additional context paths, query strings, and the like.
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

    // TODO this is to support some kind of bug where inventory won't let use talk to it over https;
    //      we need to fix that problem and get rid of this hack
    @Deprecated
    public static StringBuilder convertToNonSecureUrl(String url) {
        if (url.startsWith("http:")) {
            return new StringBuilder(url); // it already is non-secure
        }
        String securePort = System.getProperty("hawkular.hack.secure-port", "8443");
        String nonsecurePort = System.getProperty("hawkular.hack.nonsecure-port", "8080");
        url = url.replace("https:", "http:").replace(":" + securePort, ":" + nonsecurePort);
        return new StringBuilder(url);
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
        int bufferSize = 32768;
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
     * Given an input stream, its data will be slurped in memory and returned as a String.
     * The input stream will be closed when this method returns.
     * WARNING: do not slurp large streams to avoid out-of-memory errors.
     *
     * @param input the input stream to slup
     * @return the input stream data as a String
     */
    public static String slurpStream(InputStream input) {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        copyStream(input, baos, true);
        return baos.toString();
    }

    /**
     * Encodes a string using Base64 encoding.
     *
     * @param plainTextString the string to encode
     * @return the given string as a Base64 encoded string.
     */
    public static String base64Encode(String plainTextString) {
        try {
            String encoded = Base64.getMimeEncoder().encodeToString(plainTextString.getBytes("UTF-8"));
            return encoded;
        } catch (UnsupportedEncodingException e) {
            // UTF-8 should always be there; if it isn't, that's very bad
            throw new RuntimeException("Charset UTF-8 is not available");
        }
    }
}
