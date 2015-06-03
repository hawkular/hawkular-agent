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
package org.hawkular.agent.swarm;

public class RemoteDMR {

    private final String name;
    private boolean enabled = true;
    private String host = "127.0.0.1";
    private String port = "9990";
    private String username;
    private String password;
    private String resourceTypeSets;

    public RemoteDMR(String name) {
        this.name = name;
    }

    public String name() {
        return name;
    }

    public boolean enabled() {
        return enabled;
    }

    public RemoteDMR enabled(boolean enabled) {
        this.enabled = enabled;
        return this;
    }

    public String host() {
        return host;
    }

    public RemoteDMR host(String host) {
        this.host = host;
        return this;
    }

    public String port() {
        return port;
    }

    public RemoteDMR port(String port) {
        this.port = port;
        return this;
    }

    public String username() {
        return username;
    }

    public RemoteDMR username(String username) {
        this.username = username;
        return this;
    }

    public String password() {
        return password;
    }

    public RemoteDMR password(String password) {
        this.password = password;
        return this;
    }

    public String resourceTypeSets() {
        return resourceTypeSets;
    }

    public RemoteDMR resourceTypeSets(String resourceTypeSets) {
        this.resourceTypeSets = resourceTypeSets;
        return this;
    }
}
