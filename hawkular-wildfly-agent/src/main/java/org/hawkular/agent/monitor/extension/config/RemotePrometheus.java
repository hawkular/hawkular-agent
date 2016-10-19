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

import org.hawkular.agent.monitor.extension.RemotePrometheusAttributes;

import com.fasterxml.jackson.annotation.JsonProperty;

public class RemotePrometheus {

    @JsonProperty
    public String name;

    @JsonProperty
    public Boolean enabled = RemotePrometheusAttributes.ENABLED.getDefaultValue().asBoolean();

    @JsonProperty
    public String url;

    @JsonProperty
    public String username;

    @JsonProperty
    public String password;

    @JsonProperty
    public Integer interval = RemotePrometheusAttributes.INTERVAL.getDefaultValue().asInt();

    @JsonProperty("time-units")
    public TimeUnits timeUnits = TimeUnits
            .valueOf(RemotePrometheusAttributes.TIME_UNITS.getDefaultValue().asString().toLowerCase());

    @JsonProperty("tenant-id")
    public String tenantId;

    @JsonProperty("metric-id-template")
    public String metricIdTemplate;

    @JsonProperty("metric-tags")
    public String metricTags;

}
