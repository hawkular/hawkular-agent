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

import org.hawkular.agent.monitor.extension.RemoteDMRAttributes;

import com.fasterxml.jackson.annotation.JsonProperty;

public class RemoteDMR {

    @JsonProperty
    public String name;

    @JsonProperty
    public Boolean enabled = RemoteDMRAttributes.ENABLED.getDefaultValue().asBoolean();

    @JsonProperty
    public String host;

    @JsonProperty
    public Integer port;

    @JsonProperty("use-ssl")
    public Boolean useSsl;

    @JsonProperty
    public String username;

    @JsonProperty
    public String password;

    @JsonProperty("tenant-id")
    public String tenantId;

    @JsonProperty("resource-type-sets")
    public String resourceTypeSets;

    @JsonProperty("metric-id-template")
    public String metricIdTemplate;

    @JsonProperty("metric-tags")
    public String metricTags;

}
