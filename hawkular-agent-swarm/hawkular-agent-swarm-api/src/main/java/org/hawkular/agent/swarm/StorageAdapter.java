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

public class StorageAdapter {
    private String type = "HAWKULAR"; // HAWKULAR | METRICS
    private String username = "SET_ME";
    private String password = "SET_ME";
    private String url = "http://localhost:8080";
    private String tenantId = null;

    public StorageAdapter() {
    }

    public String type() {
        return type;
    }

    public StorageAdapter type(String type) {
        this.type = type;
        return this;
    }

    public String username() {
        return username;
    }

    public StorageAdapter username(String username) {
        this.username = username;
        return this;
    }

    public String password() {
        return password;
    }

    public StorageAdapter password(String password) {
        this.password = password;
        return this;
    }

    public String url() {
        return url;
    }

    public StorageAdapter url(String url) {
        this.url = url;
        return this;
    }

    public String tenantId() {
        return tenantId;
    }

    public StorageAdapter tenantId(String tenantId) {
        this.tenantId = tenantId;
        return this;
    }
}
