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

import java.net.MalformedURLException;

/**
 * Just some basic utilities.
 */
public class Util {
    /**
     * Given a base URL (like 'http://localhost:8080') this will append the given
     * context string to it.
     *
     * This returns a StringBuilder so the caller can continue building its desired URL
     * by appending to it additional context paths, query strings, and the like.
     *
     * @param baseUrl base URL to append the given context to
     * @param context the context to add to the given base URL
     * @return the base URL with the context appended to it
     *
     * @throws MalformedURLException
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

}
