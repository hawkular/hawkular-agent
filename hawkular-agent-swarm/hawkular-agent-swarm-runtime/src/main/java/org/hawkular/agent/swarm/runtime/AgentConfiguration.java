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
package org.hawkular.agent.swarm.runtime;

import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.ADD;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.EXTENSION;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.OP;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.OP_ADDR;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.SUBSYSTEM;

import java.util.ArrayList;
import java.util.List;

import org.hawkular.agent.swarm.AgentFraction;
import org.hawkular.agent.swarm.DMRAvail;
import org.hawkular.agent.swarm.DMRAvailSet;
import org.hawkular.agent.swarm.DMRMetric;
import org.hawkular.agent.swarm.DMRMetricSet;
import org.hawkular.agent.swarm.DMRResourceType;
import org.hawkular.agent.swarm.DMRResourceTypeSet;
import org.hawkular.agent.swarm.Diagnostics;
import org.hawkular.agent.swarm.LocalDMR;
import org.hawkular.agent.swarm.RemoteDMR;
import org.hawkular.agent.swarm.StorageAdapter;
import org.jboss.as.controller.PathAddress;
import org.jboss.as.controller.PathElement;
import org.jboss.dmr.ModelNode;
import org.wildfly.swarm.runtime.container.AbstractServerConfiguration;

public class AgentConfiguration extends AbstractServerConfiguration<AgentFraction> {

    private PathAddress agentAddress = PathAddress.pathAddress(PathElement.pathElement(SUBSYSTEM, "hawkular-monitor"));

    public AgentConfiguration() {
        super(AgentFraction.class);
    }

    @Override
    public AgentFraction defaultFraction() {
        return new AgentFraction();
    }

    @Override
    public List<ModelNode> getList(AgentFraction fraction) {

        List<ModelNode> list = new ArrayList<>();

        ModelNode address = new ModelNode();

        address.setEmptyList();

        ModelNode add = new ModelNode();
        add.get(OP_ADDR).set(address).add(EXTENSION, "org.hawkular.agent.monitor");
        add.get(OP).set(ADD);
        list.add(add);

        ModelNode node = new ModelNode();
        node.get(OP_ADDR).set(agentAddress.toModelNode());
        node.get(OP).set(ADD);
        list.add(node);

        if (fraction.numMetricSchedulerThreads() != 0) {
            node.get("numMetricSchedulerThreads").set(fraction.numMetricSchedulerThreads());
        }
        if (fraction.numAvailSchedulerThreads() != 0) {
            node.get("numAvailSchedulerThreads").set(fraction.numAvailSchedulerThreads());
        }
        if (fraction.numDmrSchedulerThreads() != 0) {
            node.get("numDmrSchedulerThreads").set(fraction.numDmrSchedulerThreads());
        }
        if (fraction.metricDispatcherBufferSize() != 0) {
            node.get("metricDispatcherBufferSize").set(fraction.metricDispatcherBufferSize());
        }
        if (fraction.metricDispatcherMaxBatchSize() != 0) {
            node.get("metricDispatcherMaxBatchSize").set(fraction.metricDispatcherMaxBatchSize());
        }
        if (fraction.availDispatcherBufferSize() != 0) {
            node.get("availDispatcherBufferSize").set(fraction.availDispatcherBufferSize());
        }
        if (fraction.availDispatcherMaxBatchSize() != 0) {
            node.get("availDispatcherMaxBatchSize").set(fraction.availDispatcherMaxBatchSize());
        }

        addDiagnostics(fraction, list);
        addStorageAdapter(fraction, list);
        addDMRMetricSets(fraction, list);
        addDMRAvailSets(fraction, list);
        addDMRResourceTypeSets(fraction, list);
        addManagedServers(fraction, list);

        return list;
    }

    protected void addDiagnostics(AgentFraction fraction, List<ModelNode> list) {
        ModelNode node = new ModelNode();
        node.get(OP_ADDR).set(agentAddress.append("diagnostics", "default").toModelNode());
        node.get(OP).set(ADD);
        list.add(node);

        Diagnostics diagnostics = fraction.diagnostics();
        if (diagnostics != null) {
            node.get("enabled").set(diagnostics.enabled());
            node.get("reportTo").set(diagnostics.reportTo());
            node.get("interval").set(diagnostics.interval());
            node.get("timeUnits").set(diagnostics.timeUnits());
        }
    }

    protected void addStorageAdapter(AgentFraction fraction, List<ModelNode> list) {
        ModelNode node = new ModelNode();
        node.get(OP_ADDR).set(agentAddress.append("storage-adapter", "default").toModelNode());
        node.get(OP).set(ADD);
        list.add(node);

        StorageAdapter storageAdapter = fraction.storageAdapter();
        if (storageAdapter != null) {
            node.get("type").set(storageAdapter.type());
            node.get("username").set(storageAdapter.username());
            node.get("password").set(storageAdapter.password());
            node.get("url").set(storageAdapter.url());
            if (storageAdapter.tenantId() != null) {
                node.get("tenantId").set(storageAdapter.tenantId());
            }
        }
    }

    protected void addDMRMetricSets(AgentFraction fraction, List<ModelNode> list) {
        for (DMRMetricSet each : fraction.dmrMetricSets()) {
            addDMRMetricSet(each, list);
        }
    }

    protected void addDMRMetricSet(DMRMetricSet metricSet, List<ModelNode> list) {
        ModelNode node = new ModelNode();
        node.get(OP_ADDR).set(agentAddress.append("metric-set-dmr", metricSet.name()).toModelNode());
        node.get( OP ).set( ADD );
        list.add(node);

        node.get("enabled").set(metricSet.enabled());

        for (DMRMetric each : metricSet.dmrMetrics()) {
            addDMRMetric(metricSet.name(), each, list);
        }
    }

    protected void addDMRMetric(String metricSetName, DMRMetric metric, List<ModelNode> list) {
        ModelNode node = new ModelNode();
        node.get(OP_ADDR).set(agentAddress.append("metric-set-dmr", metricSetName)
                .append("metric-dmr", metric.name()).toModelNode());
        node.get(OP).set(ADD);
        list.add(node);

        node.get("interval").set(metric.interval());
        node.get("timeUnits").set(metric.timeUnits());
        node.get("path").set(metric.path());
        node.get("attribute").set(metric.attribute());
        if (metric.metricUnits() != null) {
            node.get("metricUnits").set(metric.metricUnits());
        }
    }

    protected void addDMRAvailSets(AgentFraction fraction, List<ModelNode> list) {
        for (DMRAvailSet each : fraction.dmrAvailSets()) {
            addDMRAvailSet(each, list);
        }
    }

    protected void addDMRAvailSet(DMRAvailSet availSet, List<ModelNode> list) {
        ModelNode node = new ModelNode();
        node.get(OP_ADDR).set(agentAddress.append("avail-set-dmr", availSet.name()).toModelNode());
        node.get(OP).set(ADD);
        list.add(node);

        node.get("enabled").set(availSet.enabled());

        for (DMRAvail each : availSet.dmrAvails()) {
            addDMRAvail(availSet.name(), each, list);
        }
    }

    protected void addDMRAvail(String availSetName, DMRAvail avail, List<ModelNode> list) {
        ModelNode node = new ModelNode();
        node.get(OP_ADDR).set(agentAddress.append("avail-set-dmr", availSetName)
                .append("avail-dmr", avail.name()).toModelNode());
        node.get(OP).set(ADD);
        list.add(node);

        node.get("interval").set(avail.interval());
        node.get("timeUnits").set(avail.timeUnits());
        node.get("path").set(avail.path());
        node.get("attribute").set(avail.attribute());
        if (avail.upRegex() != null) {
            node.get("upRegex").set(avail.upRegex());
        }
    }

    protected void addDMRResourceTypeSets(AgentFraction fraction, List<ModelNode> list) {
        for (DMRResourceTypeSet each : fraction.dmrResourceTypeSets()) {
            addDMRResourceTypeSet(each, list);
        }
    }

    protected void addDMRResourceTypeSet(DMRResourceTypeSet resourceTypeSet, List<ModelNode> list) {
        ModelNode node = new ModelNode();
        node.get(OP_ADDR).set(agentAddress.append("resource-type-set-dmr", resourceTypeSet.name()).toModelNode());
        node.get(OP).set(ADD);
        list.add(node);

        node.get("enabled").set(resourceTypeSet.enabled());

        for (DMRResourceType each : resourceTypeSet.dmrResourceTypes()) {
            addDMRResourceType(resourceTypeSet.name(), each, list);
        }
    }

    protected void addDMRResourceType(String resourceTypeSetName, DMRResourceType rt, List<ModelNode> list) {
        ModelNode node = new ModelNode();
        node.get(OP_ADDR).set(agentAddress.append("resource-type-set-dmr", resourceTypeSetName)
                .append("resource-type-dmr", rt.name()).toModelNode());
        node.get(OP).set(ADD);
        list.add(node);

        node.get("resourceNameTemplate").set(rt.resourceNameTemplate());
        node.get("path").set(rt.path());
        if (rt.parents() != null) {
            node.get("parents").set(rt.parents());
        }
        if (rt.metricSets() != null) {
            node.get("metricSets").set(rt.metricSets());
        }
        if (rt.availSets() != null) {
            node.get("availSets").set(rt.availSets());
        }
    }

    protected void addManagedServers(AgentFraction fraction, List<ModelNode> list) {
        ModelNode node = new ModelNode();
        node.get(OP_ADDR).set(agentAddress.append("managed-servers", "default").toModelNode());
        node.get(OP).set(ADD);
        list.add(node);

        if (fraction.managedServers() != null) {
            addManagedServerLocalDMR(fraction.managedServers().localDmr(), list);

            for (RemoteDMR each : fraction.managedServers().remoteDmrs()) {
                addManagedServerRemoteDMR(each, list);
            }
        }
    }

    protected void addManagedServerLocalDMR(LocalDMR localDmr, List<ModelNode> list) {
        ModelNode node = new ModelNode();
        node.get(OP_ADDR).set(agentAddress.append("managed-servers", "default")
                .append("local-dmr", localDmr.name()).toModelNode());
        node.get(OP).set(ADD);
        list.add(node);

        node.get("enabled").set(localDmr.enabled());
        if (localDmr.resourceTypeSets() != null) {
            node.get("resourceTypeSets").set(localDmr.resourceTypeSets());
        }
    }


    protected void addManagedServerRemoteDMR(RemoteDMR remoteDmr, List<ModelNode> list) {
        ModelNode node = new ModelNode();
        node.get(OP_ADDR).set(agentAddress.append("managed-servers", "default")
                .append("remote-dmr", remoteDmr.name()).toModelNode());
        node.get(OP).set(ADD);
        list.add(node);

        node.get("enabled").set(remoteDmr.enabled());
        node.get("host").set(remoteDmr.host());
        node.get("port").set(remoteDmr.port());
        if (remoteDmr.username() != null) {
            node.get("username").set(remoteDmr.username());
        }
        if (remoteDmr.password() != null) {
            node.get("password").set(remoteDmr.password());
        }
        if (remoteDmr.resourceTypeSets() != null) {
            node.get("resourceTypeSets").set(remoteDmr.resourceTypeSets());
        }
    }
}
