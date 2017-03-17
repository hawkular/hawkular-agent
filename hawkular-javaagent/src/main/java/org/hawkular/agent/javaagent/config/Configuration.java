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

import com.fasterxml.jackson.annotation.JsonAutoDetect;
import com.fasterxml.jackson.annotation.JsonAutoDetect.Visibility;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonAutoDetect( //
        fieldVisibility = Visibility.NONE, //
        getterVisibility = Visibility.NONE, //
        setterVisibility = Visibility.NONE, //
        isGetterVisibility = Visibility.NONE)
public class Configuration implements Validatable {

    @JsonProperty
    private Subsystem subsystem;

    @JsonProperty("security-realm")
    private SecurityRealm[] securityRealms;

    @JsonProperty("storage-adapter")
    private StorageAdapter storageAdapter = new StorageAdapter();

    @JsonProperty
    private Diagnostics diagnostics = new Diagnostics();

    @JsonProperty("metric-set-dmr")
    private DMRMetricSet[] dmrMetricSets;

    @JsonProperty("avail-set-dmr")
    private DMRAvailSet[] dmrAvailSets;

    @JsonProperty("resource-type-set-dmr")
    private DMRResourceTypeSet[] dmrResourceTypeSets;

    @JsonProperty("metric-set-jmx")
    private JMXMetricSet[] jmxMetricSets;

    @JsonProperty("avail-set-jmx")
    private JMXAvailSet[] jmxAvailSets;

    @JsonProperty("resource-type-set-jmx")
    private JMXResourceTypeSet[] jmxResourceTypeSets;

    @JsonProperty("managed-servers")
    private ManagedServers managedServers = new ManagedServers();

    @JsonProperty
    private Platform platform = new Platform();

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

    public Subsystem getSubsystem() {
        return subsystem;
    }

    public void setSubsystem(Subsystem subsystem) {
        this.subsystem = subsystem;
    }

    public SecurityRealm[] getSecurityRealms() {
        return securityRealms;
    }

    public void setSecurityRealms(SecurityRealm[] securityRealms) {
        this.securityRealms = securityRealms;
    }

    public StorageAdapter getStorageAdapter() {
        return storageAdapter;
    }

    public void setStorageAdapter(StorageAdapter storageAdapter) {
        this.storageAdapter = storageAdapter;
    }

    public Diagnostics getDiagnostics() {
        return diagnostics;
    }

    public void setDiagnostics(Diagnostics diagnostics) {
        this.diagnostics = diagnostics;
    }

    public DMRMetricSet[] getDmrMetricSets() {
        return dmrMetricSets;
    }

    public void setDmrMetricSets(DMRMetricSet[] dmrMetricSets) {
        this.dmrMetricSets = dmrMetricSets;
    }

    public DMRAvailSet[] getDmrAvailSets() {
        return dmrAvailSets;
    }

    public void setDmrAvailSets(DMRAvailSet[] dmrAvailSets) {
        this.dmrAvailSets = dmrAvailSets;
    }

    public DMRResourceTypeSet[] getDmrResourceTypeSets() {
        return dmrResourceTypeSets;
    }

    public void setDmrResourceTypeSets(DMRResourceTypeSet[] dmrResourceTypeSets) {
        this.dmrResourceTypeSets = dmrResourceTypeSets;
    }

    public JMXMetricSet[] getJmxMetricSets() {
        return jmxMetricSets;
    }

    public void setJmxMetricSets(JMXMetricSet[] jmxMetricSets) {
        this.jmxMetricSets = jmxMetricSets;
    }

    public JMXAvailSet[] getJmxAvailSets() {
        return jmxAvailSets;
    }

    public void setJmxAvailSets(JMXAvailSet[] jmxAvailSets) {
        this.jmxAvailSets = jmxAvailSets;
    }

    public JMXResourceTypeSet[] getJmxResourceTypeSets() {
        return jmxResourceTypeSets;
    }

    public void setJmxResourceTypeSets(JMXResourceTypeSet[] jmxResourceTypeSets) {
        this.jmxResourceTypeSets = jmxResourceTypeSets;
    }

    public ManagedServers getManagedServers() {
        return managedServers;
    }

    public void setManagedServers(ManagedServers managedServers) {
        this.managedServers = managedServers;
    }

    public Platform getPlatform() {
        return platform;
    }

    public void setPlatform(Platform platform) {
        this.platform = platform;
    }
}
