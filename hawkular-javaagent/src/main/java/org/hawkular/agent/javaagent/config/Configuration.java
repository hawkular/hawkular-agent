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

import org.hawkular.agent.javaagent.Util;

import com.fasterxml.jackson.annotation.JsonProperty;

public class Configuration implements Validatable {

    @JsonProperty
    public Subsystem subsystem;

    @JsonProperty("security-realm")
    public SecurityRealm[] securityRealms;

    @JsonProperty("storage-adapter")
    public StorageAdapter storageAdapter = new StorageAdapter();

    @JsonProperty
    public Diagnostics diagnostics = new Diagnostics();

    @JsonProperty("metric-set-dmr")
    public DMRMetricSet[] dmrMetricSets;

    @JsonProperty("avail-set-dmr")
    public DMRAvailSet[] dmrAvailSets;

    @JsonProperty("resource-type-set-dmr")
    public DMRResourceTypeSet[] dmrResourceTypeSets;

    @JsonProperty("metric-set-jmx")
    public JMXMetricSet[] jmxMetricSets;

    @JsonProperty("avail-set-jmx")
    public JMXAvailSet[] jmxAvailSets;

    @JsonProperty("resource-type-set-jmx")
    public JMXResourceTypeSet[] jmxResourceTypeSets;

    @JsonProperty("managed-servers")
    public ManagedServers managedServers = new ManagedServers();

    @JsonProperty
    public Platform platform = new Platform();

    public Configuration() {
    }

    /** copy constructor */
    public Configuration(Configuration original) {
        this.subsystem = new Subsystem(original.subsystem);
        this.securityRealms = Util.cloneArray(original.securityRealms);
        this.storageAdapter = new StorageAdapter(original.storageAdapter);
        this.diagnostics = new Diagnostics(original.diagnostics);
        this.dmrMetricSets = Util.cloneArray(original.dmrMetricSets);
        this.dmrAvailSets = Util.cloneArray(original.dmrAvailSets);
        this.dmrResourceTypeSets = Util.cloneArray(original.dmrResourceTypeSets);
        this.jmxMetricSets = Util.cloneArray(original.jmxMetricSets);
        this.jmxAvailSets = Util.cloneArray(original.jmxAvailSets);
        this.jmxResourceTypeSets = Util.cloneArray(original.jmxResourceTypeSets);
        this.managedServers = new ManagedServers(original.managedServers);
        this.platform = new Platform(original.platform);
    }

    @Override
    public void validate() throws Exception {
        if (subsystem == null) {
            throw new Exception("subsystem must be specified");
        }

        // create 0 length arrays so we don't have any null arrays

        if (securityRealms == null) {
            securityRealms = new SecurityRealm[0];
        }

        if (dmrMetricSets == null) {
            dmrMetricSets = new DMRMetricSet[0];
        }
        if (dmrAvailSets == null) {
            dmrAvailSets = new DMRAvailSet[0];
        }
        if (dmrResourceTypeSets == null) {
            dmrResourceTypeSets = new DMRResourceTypeSet[0];
        }

        if (jmxMetricSets == null) {
            jmxMetricSets = new JMXMetricSet[0];
        }
        if (jmxAvailSets == null) {
            jmxAvailSets = new JMXAvailSet[0];
        }
        if (jmxResourceTypeSets == null) {
            jmxResourceTypeSets = new JMXResourceTypeSet[0];
        }

        // validate all components of the configuration

        subsystem.validate();
        storageAdapter.validate();
        for (SecurityRealm o : securityRealms) {
            o.validate();
        }
        diagnostics.validate();
        for (DMRMetricSet o : dmrMetricSets) {
            o.validate();
        }
        for (DMRAvailSet o : dmrAvailSets) {
            o.validate();
        }
        for (DMRResourceTypeSet o : dmrResourceTypeSets) {
            o.validate();
        }
        for (JMXMetricSet o : jmxMetricSets) {
            o.validate();
        }
        for (JMXAvailSet o : jmxAvailSets) {
            o.validate();
        }
        for (JMXResourceTypeSet o : jmxResourceTypeSets) {
            o.validate();
        }
        managedServers.validate();
        platform.validate();
    }
}
