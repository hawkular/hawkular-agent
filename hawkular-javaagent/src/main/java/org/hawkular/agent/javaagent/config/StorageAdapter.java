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

import org.hawkular.agent.javaagent.config.StringExpression.StringValue;

import com.fasterxml.jackson.annotation.JsonAutoDetect;
import com.fasterxml.jackson.annotation.JsonAutoDetect.Visibility;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonAutoDetect( //
        fieldVisibility = Visibility.NONE, //
        getterVisibility = Visibility.NONE, //
        setterVisibility = Visibility.NONE, //
        isGetterVisibility = Visibility.NONE)
public class StorageAdapter implements Validatable {

    public enum Type {
        HAWKULAR, METRICS
    };

    @JsonProperty
    private Type type = Type.HAWKULAR;

    @JsonProperty
    private StringExpression url = new StringExpression("http://127.0.0.1:8080");

    @JsonProperty("tenant-id")
    private StringExpression tenantId = new StringExpression("hawkular");

    @JsonProperty
    private StringExpression username = new StringExpression("");

    @JsonProperty
    private StringExpression password = new StringExpression("");

    @JsonProperty("feed-id")
    private StringExpression feedId = new StringExpression("autogenerate");

    @JsonProperty("security-realm")
    private String securityRealmName;

    @JsonProperty("metrics-context")
    private String metricsContext = "/hawkular/metrics/";

    @JsonProperty("feedcomm-context")
    private String feedcommContext = "/hawkular/command-gateway/";

    @JsonProperty("connect-timeout-secs")
    private Integer connectTimeoutSecs = 10;

    @JsonProperty("read-timeout-secs")
    private Integer readTimeoutSecs = 120;

    public StorageAdapter() {
    }

    public StorageAdapter(StorageAdapter original) {
        this.type = original.type;
        this.url = original.url == null ? null : new StringExpression(original.url);
        this.tenantId = original.tenantId == null ? null : new StringExpression(original.tenantId);
        this.username = original.username == null ? null : new StringExpression(original.username);
        this.password = original.password == null ? null : new StringExpression(original.password);
        this.feedId = original.feedId == null ? null : new StringExpression(original.feedId);
        this.securityRealmName = original.securityRealmName;
        this.metricsContext = original.metricsContext;
        this.feedcommContext = original.feedcommContext;
        this.connectTimeoutSecs = original.connectTimeoutSecs;
        this.readTimeoutSecs = original.readTimeoutSecs;
    }

    /**
     * @return true if SSL is to be used when communicating with the storage backend.
     */
    public boolean useSSL() {
        return (url != null && url.get().toString().startsWith("https:"));
    }

    @Override
    public void validate() throws Exception {
        try {
            new URL(url.get().toString());
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

    public Type getType() {
        return type;
    }

    public void setType(Type type) {
        this.type = type;
    }

    public String getUrl() {
        return url == null ? null : url.get().toString();
    }

    public void setUrl(String url) {
        if (this.url != null) {
            this.url.set(new StringValue(url));
        } else {
            this.url = new StringExpression(new StringValue(url));
        }
    }

    public String getTenantId() {
        return tenantId == null ? null : tenantId.get().toString();
    }

    public void setTenantId(String tenantId) {
        if (this.tenantId != null) {
            this.tenantId.set(new StringValue(tenantId));
        } else {
            this.tenantId = new StringExpression(tenantId);
        }
    }

    public String getUsername() {
        return username == null ? null : username.get().toString();
    }

    public void setUsername(String username) {
        if (this.username != null) {
            this.username.set(new StringValue(username));
        } else {
            this.username = new StringExpression(new StringValue(username));
        }
    }

    public String getPassword() {
        return password == null ? null : password.get().toString();
    }

    public void setPassword(String password) {
        if (this.password != null) {
            this.password.set(new StringValue(password));
        } else {
            this.password = new StringExpression(new StringValue(password));
        }
    }

    public String getFeedId() {
        return feedId == null ? null : feedId.get().toString();
    }

    public void setFeedId(String feedId) {
        if (this.feedId != null) {
            this.feedId.set(new StringValue(feedId));
        } else {
            this.feedId = new StringExpression(new StringValue(feedId));
        }
    }

    public String getSecurityRealmName() {
        return securityRealmName;
    }

    public void setSecurityRealmName(String securityRealmName) {
        this.securityRealmName = securityRealmName;
    }

    public String getMetricsContext() {
        return metricsContext;
    }

    public void setMetricsContext(String metricsContext) {
        this.metricsContext = metricsContext;
    }

    public String getFeedcommContext() {
        return feedcommContext;
    }

    public void setFeedcommContext(String feedcommContext) {
        this.feedcommContext = feedcommContext;
    }

    public Integer getConnectTimeoutSecs() {
        return connectTimeoutSecs;
    }

    public void setConnectTimeoutSecs(Integer connectTimeoutSecs) {
        this.connectTimeoutSecs = connectTimeoutSecs;
    }

    public Integer getReadTimeoutSecs() {
        return readTimeoutSecs;
    }

    public void setReadTimeoutSecs(Integer readTimeoutSecs) {
        this.readTimeoutSecs = readTimeoutSecs;
    }
}
