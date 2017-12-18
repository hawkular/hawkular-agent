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

import java.util.HashMap;
import java.util.Map;

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

    @JsonProperty("metrics-exporter")
    private MetricsExporter metricsExporter = new MetricsExporter();

    @JsonProperty("storage-adapter")
    private StorageAdapter storageAdapter = new StorageAdapter();

    @JsonProperty
    private Diagnostics diagnostics = new Diagnostics();

    @JsonProperty("metric-set-dmr")
    private DMRMetricSet[] dmrMetricSets;

    @JsonProperty("resource-type-set-dmr")
    private DMRResourceTypeSet[] dmrResourceTypeSets;

    @JsonProperty("metric-set-jmx")
    private JMXMetricSet[] jmxMetricSets;

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
        this.metricsExporter = new MetricsExporter(original.metricsExporter);
        this.securityRealms = Util.cloneArray(original.securityRealms);
        this.storageAdapter = new StorageAdapter(original.storageAdapter);
        this.diagnostics = new Diagnostics(original.diagnostics);
        this.dmrMetricSets = Util.cloneArray(original.dmrMetricSets);
        this.dmrResourceTypeSets = Util.cloneArray(original.dmrResourceTypeSets);
        this.jmxMetricSets = Util.cloneArray(original.jmxMetricSets);
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
        if (dmrResourceTypeSets == null) {
            dmrResourceTypeSets = new DMRResourceTypeSet[0];
        }

        if (jmxMetricSets == null) {
            jmxMetricSets = new JMXMetricSet[0];
        }
        if (jmxResourceTypeSets == null) {
            jmxResourceTypeSets = new JMXResourceTypeSet[0];
        }

        // validate all components of the configuration

        subsystem.validate();
        metricsExporter.validate();
        storageAdapter.validate();
        for (SecurityRealm o : securityRealms) {
            o.validate();
        }
        diagnostics.validate();
        for (DMRMetricSet o : dmrMetricSets) {
            o.validate();
        }
        for (DMRResourceTypeSet o : dmrResourceTypeSets) {
            o.validate();
        }
        for (JMXMetricSet o : jmxMetricSets) {
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

    public MetricsExporter getMetricsExporter() {
        return metricsExporter;
    }

    public void setMetricsExporter(MetricsExporter metricsExporter) {
        this.metricsExporter = metricsExporter;
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

    public void addDmrMetricSets(DMRMetricSet[] additionalSets) {
        if (additionalSets == null || additionalSets.length == 0) {
            return;
        }
        if (this.dmrMetricSets == null) {
            this.dmrMetricSets = additionalSets;
            return;
        }
        // if a name is the same, the new one overwrites the old one
        Map<String, DMRMetricSet> combined = new HashMap<>();
        for (DMRMetricSet set : this.dmrMetricSets) {
            combined.put(set.getName(), set);
        }
        for (DMRMetricSet set : additionalSets) {
            combined.put(set.getName(), set);
        }
        this.dmrMetricSets = combined.values().toArray(new DMRMetricSet[combined.size()]);
    }

    public DMRResourceTypeSet[] getDmrResourceTypeSets() {
        return dmrResourceTypeSets;
    }

    public void setDmrResourceTypeSets(DMRResourceTypeSet[] dmrResourceTypeSets) {
        this.dmrResourceTypeSets = dmrResourceTypeSets;
    }

    public void addDmrResourceTypeSets(DMRResourceTypeSet[] additionalSets) {
        if (additionalSets == null || additionalSets.length == 0) {
            return;
        }
        if (this.dmrResourceTypeSets == null) {
            this.dmrResourceTypeSets = additionalSets;
            return;
        }
        // if a name is the same, the new one overwrites the old one
        Map<String, DMRResourceTypeSet> combined = new HashMap<>();
        for (DMRResourceTypeSet set : this.dmrResourceTypeSets) {
            combined.put(set.getName(), set);
        }
        for (DMRResourceTypeSet set : additionalSets) {
            combined.put(set.getName(), set);
        }
        this.dmrResourceTypeSets = combined.values().toArray(new DMRResourceTypeSet[combined.size()]);
    }

    public JMXMetricSet[] getJmxMetricSets() {
        return jmxMetricSets;
    }

    public void setJmxMetricSets(JMXMetricSet[] jmxMetricSets) {
        this.jmxMetricSets = jmxMetricSets;
    }

    public void addJmxMetricSets(JMXMetricSet[] additionalSets) {
        if (additionalSets == null || additionalSets.length == 0) {
            return;
        }
        if (this.jmxMetricSets == null) {
            this.jmxMetricSets = additionalSets;
            return;
        }
        // if a name is the same, the new one overwrites the old one
        Map<String, JMXMetricSet> combined = new HashMap<>();
        for (JMXMetricSet set : this.jmxMetricSets) {
            combined.put(set.getName(), set);
        }
        for (JMXMetricSet set : additionalSets) {
            combined.put(set.getName(), set);
        }
        this.jmxMetricSets = combined.values().toArray(new JMXMetricSet[combined.size()]);
    }

    public JMXResourceTypeSet[] getJmxResourceTypeSets() {
        return jmxResourceTypeSets;
    }

    public void setJmxResourceTypeSets(JMXResourceTypeSet[] jmxResourceTypeSets) {
        this.jmxResourceTypeSets = jmxResourceTypeSets;
    }

    public void addJmxResourceTypeSets(JMXResourceTypeSet[] additionalSets) {
        if (additionalSets == null || additionalSets.length == 0) {
            return;
        }
        if (this.jmxResourceTypeSets == null) {
            this.jmxResourceTypeSets = additionalSets;
            return;
        }
        // if a name is the same, the new one overwrites the old one
        Map<String, JMXResourceTypeSet> combined = new HashMap<>();
        for (JMXResourceTypeSet set : this.jmxResourceTypeSets) {
            combined.put(set.getName(), set);
        }
        for (JMXResourceTypeSet set : additionalSets) {
            combined.put(set.getName(), set);
        }
        this.jmxResourceTypeSets = combined.values().toArray(new JMXResourceTypeSet[combined.size()]);
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
