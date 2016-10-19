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
package org.hawkular.agent.monitor.extension.config;

import org.hawkular.agent.monitor.extension.StorageAttributes;

import com.fasterxml.jackson.annotation.JsonProperty;

public class StorageAdapter {

    public enum Type {
        HAWKULAR, METRICS
    };

    @JsonProperty
    public Type type = Type.HAWKULAR;

    @JsonProperty
    public String url;

    @JsonProperty("tenant-id")
    public String tenantId = StorageAttributes.TENANT_ID.getDefaultValue().asString();

    @JsonProperty
    public String username;

    @JsonProperty
    public String password;

    @JsonProperty("feed-id")
    public String feedId;

    @JsonProperty("security-realm")
    public String securityRealm;

    @JsonProperty("keystore-path")
    public String keystorePath;

    @JsonProperty("keystore-password")
    public String keystorePassword;

    @JsonProperty("inventory-context")
    public String inventoryContext = StorageAttributes.INVENTORY_CONTEXT.getDefaultValue().asString();

    @JsonProperty("metrics-context")
    public String metricsContext = StorageAttributes.METRICS_CONTEXT.getDefaultValue().asString();

    @JsonProperty("feedcomm-context")
    public String feedcommContext = StorageAttributes.FEEDCOMM_CONTEXT.getDefaultValue().asString();

    @JsonProperty("connect-timeout-secs")
    public Integer connectTimeoutSecs = StorageAttributes.CONNECT_TIMEOUT_SECONDS.getDefaultValue().asInt();

    @JsonProperty("read-timeout-secs")
    public Integer readTimeoutSecs = StorageAttributes.READ_TIMEOUT_SECONDS.getDefaultValue().asInt();
}
