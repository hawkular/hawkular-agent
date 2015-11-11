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
package org.hawkular.agent.monitor.inventory;

import java.net.URI;
import java.net.URISyntaxException;

/**
 * The data to use when connecting to a remote host. Note username and password are stored in this class while that
 * hostname, port, path, etc. are stored in the {@link #uri}.
 *
 * @author <a href="https://github.com/ppalaga">Peter Palaga</a>
 */
public class ConnectionData {

    /**
     * A convenience method to create a new {@link URI} out of a protocol, host and port, hiding the
     * {@link URISyntaxException} behind a {@link RuntimeException}.
     *
     * @param protocol the protocol such as http
     * @param host hostname or IP address
     * @param port port number
     * @return a new {@link URI}
     */
    private static URI createUri(String protocol, String host, int port) {
        try {
            return new URI(protocol, null, host, port, null, null, null);
        } catch (URISyntaxException e) {
            throw new RuntimeException(e);
        }
    }

    private final String password;
    private final URI uri;
    private final String username;

    public ConnectionData(String protocol, String host, int port, String username, String password) {
        this(createUri(protocol, host, port), username, password);
    }

    public ConnectionData(URI uri, String username, String password) {
        super();
        if (uri == null) {
            throw new IllegalArgumentException("Cannot create a new ["+ getClass().getName() +"] with a new uri");
        }
        this.uri = uri;
        this.username = username;
        this.password = password;
    }

    public String getPassword() {
        return password;
    }

    public URI getUri() {
        return uri;
    }

    public String getUsername() {
        return username;
    }

    @Override
    public String toString() {
        return getUri().toString();
    }
}
