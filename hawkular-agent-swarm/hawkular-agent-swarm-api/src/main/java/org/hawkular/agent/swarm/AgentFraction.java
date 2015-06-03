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

import java.util.ArrayList;
import java.util.List;

import org.wildfly.swarm.container.Fraction;

public class AgentFraction implements Fraction {

    private int numMetricSchedulerThreads;
    private int numAvailSchedulerThreads;
    private int numDmrSchedulerThreads;
    private int metricDispatcherBufferSize;
    private int metricDispatcherMaxBatchSize;
    private int availDispatcherBufferSize;
    private int availDispatcherMaxBatchSize;

    private Diagnostics diagnostics;
    private StorageAdapter storageAdapter;
    private List<DMRMetricSet> dmrMetricSets = new ArrayList<>();
    private List<DMRAvailSet> dmrAvailSets = new ArrayList<>();
    private List<DMRResourceTypeSet> dmrResourceTypeSets = new ArrayList<>();
    private ManagedServers managedServers;

    public AgentFraction() {
    }

    public AgentFraction numMetricSchedulerThreads(int value) {
        this.numMetricSchedulerThreads = value;
        return this;
    }

    public int numMetricSchedulerThreads() {
        return this.numMetricSchedulerThreads;
    }

    public AgentFraction numAvailSchedulerThreads(int value) {
        this.numAvailSchedulerThreads = value;
        return this;
    }

    public int numAvailSchedulerThreads() {
        return this.numAvailSchedulerThreads;
    }

    public AgentFraction numDmrSchedulerThreads(int value) {
        this.numDmrSchedulerThreads = value;
        return this;
    }

    public int numDmrSchedulerThreads() {
        return this.numDmrSchedulerThreads;
    }

    public AgentFraction metricDispatcherBufferSize(int value) {
        this.metricDispatcherBufferSize = value;
        return this;
    }

    public int metricDispatcherBufferSize() {
        return this.metricDispatcherBufferSize;
    }

    public AgentFraction metricDispatcherMaxBatchSize(int value) {
        this.metricDispatcherMaxBatchSize = value;
        return this;
    }

    public int metricDispatcherMaxBatchSize() {
        return this.metricDispatcherMaxBatchSize;
    }

    public AgentFraction availDispatcherBufferSize(int value) {
        this.availDispatcherBufferSize = value;
        return this;
    }

    public int availDispatcherBufferSize() {
        return this.availDispatcherBufferSize;
    }

    public AgentFraction availDispatcherMaxBatchSize(int value) {
        this.availDispatcherMaxBatchSize = value;
        return this;
    }

    public int availDispatcherMaxBatchSize() {
        return this.availDispatcherMaxBatchSize;
    }

    public AgentFraction diagnostics(Diagnostics diagnostics) {
        this.diagnostics = diagnostics;
        return this;
    }

    public Diagnostics diagnostics() {
        return this.diagnostics;
    }

    public AgentFraction storageAdapter(StorageAdapter storageAdapter) {
        this.storageAdapter = storageAdapter;
        return this;
    }

    public StorageAdapter storageAdapter() {
        return this.storageAdapter;
    }

    public AgentFraction dmrMetricSet(DMRMetricSet dmrMetricSet) {
        this.dmrMetricSets.add(dmrMetricSet);
        return this;
    }

    public List<DMRMetricSet> dmrMetricSets() {
        return this.dmrMetricSets;
    }

    public AgentFraction dmrAvailSet(DMRAvailSet dmrAvailSet) {
        this.dmrAvailSets.add(dmrAvailSet);
        return this;
    }

    public List<DMRAvailSet> dmrAvailSets() {
        return this.dmrAvailSets;
    }

    public AgentFraction dmrResourceTypeSet(DMRResourceTypeSet dmrResourceTypeSet) {
        this.dmrResourceTypeSets.add(dmrResourceTypeSet);
        return this;
    }

    public List<DMRResourceTypeSet> dmrResourceTypeSets() {
        return this.dmrResourceTypeSets;
    }

    public ManagedServers managedServers() {
        return this.managedServers;
    }

    public AgentFraction managedServers(ManagedServers managedServers) {
        this.managedServers = managedServers;
        return this;
    }
}
