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

import com.fasterxml.jackson.annotation.JsonProperty;

public class Configuration {

    @JsonProperty
    public Subsystem subsystem;

    @JsonProperty("storage-adapter")
    public StorageAdapter storageAdapter;

    @JsonProperty("managed-servers")
    public ManagedServers managedServers;

    @JsonProperty
    public Platform platform;

}
