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
package org.hawkular.agent.javaagent.config;

import java.net.URL;

import com.fasterxml.jackson.annotation.JsonProperty;

public class StorageAdapter implements Validatable {

    public enum Type {
        HAWKULAR, METRICS
    };

    @JsonProperty
    public Type type = Type.HAWKULAR;

    @JsonProperty
    public String url = "http://127.0.0.1:8080";

    @JsonProperty("tenant-id")
    public String tenantId = "hawkular";

    @JsonProperty
    public String username = "";

    @JsonProperty
    public String password = "";

    @JsonProperty("feed-id")
    public String feedId = "autogenerate";

    @JsonProperty("security-realm")
    public String securityRealmName;

    @JsonProperty("inventory-context")
    public String inventoryContext = "/hawkular/inventory/";

    @JsonProperty("metrics-context")
    public String metricsContext = "/hawkular/metrics/";

    @JsonProperty("feedcomm-context")
    public String feedcommContext = "/hawkular/command-gateway/";

    @JsonProperty("connect-timeout-secs")
    public Integer connectTimeoutSecs = 10;

    @JsonProperty("read-timeout-secs")
    public Integer readTimeoutSecs = 120;

    public StorageAdapter() {
    }

    public StorageAdapter(StorageAdapter original) {
        this.type = original.type;
        this.url = original.url;
        this.tenantId = original.tenantId;
        this.username = original.username;
        this.password = original.password;
        this.feedId = original.feedId;
        this.securityRealmName = original.securityRealmName;
        this.inventoryContext = original.inventoryContext;
        this.metricsContext = original.metricsContext;
        this.feedcommContext = original.feedcommContext;
        this.connectTimeoutSecs = original.connectTimeoutSecs;
        this.readTimeoutSecs = original.readTimeoutSecs;
    }

    /**
     * @return true if SSL is to be used when communicating with the storage backend.
     */
    public boolean useSSL() {
        return (url != null && url.startsWith("https:"));
    }

    @Override
    public void validate() throws Exception {
        try {
            new URL(url);
        } catch (Exception e) {
            throw new Exception("storage-adapter url is invalid", e);
        }

        // TODO: assume VM cert is good enough. Do we want to force security-realm?
        // if (useSSL()) {
        //     if (securityRealmName == null || securityRealmName.trim().length() == 0) {
        //         throw new Exception("storage-adapter: security-realm is needed for https");
        //     }
        // }

        if (connectTimeoutSecs != null && connectTimeoutSecs <= 0) {
            throw new Exception("storage-adapter connect-timeout-secs must be greater than or equal to 0");
        }

        if (readTimeoutSecs != null && readTimeoutSecs <= 0) {
            throw new Exception("storage-adapter read-timeout-secs must be greater than or equal to 0");
        }
    }
}
