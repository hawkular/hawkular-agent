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

import java.io.IOException;
import java.lang.reflect.Field;
import java.net.InetAddress;
import java.net.Socket;
import java.net.UnknownHostException;

import javax.net.ssl.SSLSocketFactory;

import org.jboss.as.controller.PathAddress;
import org.jboss.as.controller.PathElement;

/**
 * Utilities that are required to help run when the JVM is loaded with older EAP6 specific jars.
 */
public class WildflyCompatibilityUtils {

    public static class EAP6WrappedSSLSocketFactory extends SSLSocketFactory {
        private final SSLSocketFactory wrapped;

        public EAP6WrappedSSLSocketFactory(SSLSocketFactory wrapped) {
            this.wrapped = wrapped;
        }

        @Override
        public String[] getDefaultCipherSuites() {
            return wrapped.getDefaultCipherSuites();
        }

        @Override
        public String[] getSupportedCipherSuites() {
            return wrapped.getSupportedCipherSuites();
        }

        @Override
        public Socket createSocket(Socket socket, String s, int i, boolean b) throws IOException {
            Socket newSocket = wrapped.createSocket(socket, s, i, b);
            if (newSocket == null) {
                try {
                    Field innerWrappedField = wrapped.getClass().getDeclaredField("wrapped");
                    innerWrappedField.setAccessible(true);
                    SSLSocketFactory innerWrapped = (SSLSocketFactory) innerWrappedField.get(wrapped);
                    newSocket = innerWrapped.createSocket(socket, s, i, b);
                } catch (NoSuchFieldException e) {
                    throw new RuntimeException(e);
                } catch (IllegalAccessException e) {
                    throw new RuntimeException(e);
                }
            }
            return newSocket;
        }

        @Override
        public Socket createSocket(String s, int i) throws IOException, UnknownHostException {
            return wrapped.createSocket(s, i);
        }

        @Override
        public Socket createSocket(String s, int i, InetAddress inetAddress, int i1)
                throws IOException, UnknownHostException {
            return wrapped.createSocket(s, i, inetAddress, i1);
        }

        @Override
        public Socket createSocket(InetAddress inetAddress, int i) throws IOException {
            return wrapped.createSocket(inetAddress, i);
        }

        @Override
        public Socket createSocket(InetAddress inetAddress, int i, InetAddress inetAddress1, int i1)
                throws IOException {
            return wrapped.createSocket(inetAddress, i, inetAddress1, i1);
        }
    }

    /**
     * PathAddress methods found on wildfly-core
     * https://github.com/wildfly/wildfly-core/blob/588145a00ca5ed3beb0027f8f44be87db6689db3/controller/src/main/java/org/jboss/as/controller/PathAddress.java
     */
    public static PathAddress parseCLIStyleAddress(String address) throws IllegalArgumentException {
        try {
            return PathAddress.parseCLIStyleAddress(address);
        } catch (NoSuchMethodError _nsme) {
            // jboss-as doesn't have parseCLIStyleAddress
            // Ignore the error and use the provided alternative
            return _parseCLIStyleAddress(address);
        }
    }

    private static PathAddress _parseCLIStyleAddress(String address) throws IllegalArgumentException {
        PathAddress parsedAddress = PathAddress.EMPTY_ADDRESS;
        if (address == null || address.trim().isEmpty()) {
            return parsedAddress;
        }
        String trimmedAddress = address.trim();
        if (trimmedAddress.charAt(0) != '/' || !Character.isAlphabetic(trimmedAddress.charAt(1))) {
            // https://github.com/wildfly/wildfly-core/blob/588145a00ca5ed3beb0027f8f44be87db6689db3/controller/src/main/java/org/jboss/as/controller/logging/ControllerLogger.java#L3296-L3297
            throw new IllegalArgumentException(
                    "Illegal path address '" + address + "' , it is not in a correct CLI format");
        }
        char[] characters = address.toCharArray();
        boolean escaped = false;
        StringBuilder keyBuffer = new StringBuilder();
        StringBuilder valueBuffer = new StringBuilder();
        StringBuilder currentBuffer = keyBuffer;
        for (int i = 1; i < characters.length; i++) {
            switch (characters[i]) {
                case '/':
                    if (escaped) {
                        escaped = false;
                        currentBuffer.append(characters[i]);
                    } else {
                        parsedAddress = addpathAddressElement(parsedAddress, address, keyBuffer, valueBuffer);
                        keyBuffer = new StringBuilder();
                        valueBuffer = new StringBuilder();
                        currentBuffer = keyBuffer;
                    }
                    break;
                case '\\':
                    if (escaped) {
                        escaped = false;
                        currentBuffer.append(characters[i]);
                    } else {
                        escaped = true;
                    }
                    break;
                case '=':
                    if (escaped) {
                        escaped = false;
                        currentBuffer.append(characters[i]);
                    } else {
                        currentBuffer = valueBuffer;
                    }
                    break;
                default:
                    currentBuffer.append(characters[i]);
                    break;
            }
        }
        parsedAddress = addpathAddressElement(parsedAddress, address, keyBuffer, valueBuffer);
        return parsedAddress;
    }

    private static PathAddress addpathAddressElement(PathAddress parsedAddress, String address,
            StringBuilder keyBuffer, StringBuilder valueBuffer) {
        if (keyBuffer.length() > 0) {
            if (valueBuffer.length() > 0) {
                return parsedAddress.append(PathElement.pathElement(keyBuffer.toString(), valueBuffer.toString()));
            }
            // https://github.com/wildfly/wildfly-core/blob/588145a00ca5ed3beb0027f8f44be87db6689db3/controller/src/main/java/org/jboss/as/controller/logging/ControllerLogger.java#L3296-L3297
            throw new IllegalArgumentException(
                    "Illegal path address '" + address + "' , it is not in a correct CLI format");
        }
        return parsedAddress;
    }
}
