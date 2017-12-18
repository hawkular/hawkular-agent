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
package org.hawkular.agent.monitor.protocol.dmr;

import java.util.Collections;
import java.util.List;

import org.hawkular.agent.monitor.log.AgentLoggers;
import org.hawkular.agent.monitor.log.MsgLogger;
import org.hawkular.agent.monitor.util.WildflyCompatibilityUtils;
import org.hawkular.dmr.api.OperationBuilder;
import org.hawkular.dmr.api.OperationBuilder.CompositeOperationBuilder;
import org.hawkular.dmr.api.OperationBuilder.OperationResult;
import org.jboss.as.controller.PathAddress;
import org.jboss.as.controller.client.ModelControllerClient;

/**
 * Turns on or off statistics for several WildFly subsystems.
 */
public class StatisticsControl {
    private static final MsgLogger log = AgentLoggers.getLogger(StatisticsControl.class);

    public StatisticsControl() {
    }

    public void enableStatistics(ModelControllerClient mcc) {
        execute(mcc, true);
    }

    public void disableStatistics(ModelControllerClient mcc) {
        execute(mcc, false);
    }

    private void execute(ModelControllerClient mcc, boolean enable) {

        try {
            List<String> hosts = getDomainHosts(mcc);
            if (hosts.isEmpty()) {
                executeForServer(mcc, enable, "");
            } else {
                for (String host : hosts) {
                    List<String> servers = getDomainHostServers(mcc, host);
                    for (String server : servers) {
                        // I was trying to be too clever. This would work awesomely if WildFly would just allow it.
                        // However, this error occurs when you try to set attributes on slave servers:
                        // "User operations are not permitted to directly update the persistent configuration
                        // of a server in a managed domain."
                        // So, for now, comment this out until perhaps in the future WildFly/EAP will allow this.
                        // This means when in domain mode, the user must manually turn on statistics themselves.
                        //executeForServer(mcc, enable, String.format("/host=%s/server=%s", host, server));
                        log.debugf("Statistics for server [/host=%s/server=%s] must be enabled manually", host,
                                server);
                    }
                }
            }
        } catch (Exception e) {
            log.errorf(e, "Aborting statistics enablement");
        }
    }

    private void executeForServer(ModelControllerClient mcc, boolean enable, String serverPrefix) {

        try {
            String enableStr = String.valueOf(enable);
            CompositeOperationBuilder<?> batch;

            // datasources
            batch = OperationBuilder.composite();
            List<String> dsList = getChildrenNames(
                    WildflyCompatibilityUtils.parseCLIStyleAddress(serverPrefix + "/subsystem=datasources"),
                    "data-source", mcc);
            List<String> xaList = getChildrenNames(
                    WildflyCompatibilityUtils.parseCLIStyleAddress(serverPrefix + "/subsystem=datasources"),
                    "xa-data-source", mcc);
            for (String ds : dsList) {
                String dsAddr = String.format(serverPrefix + "/subsystem=datasources/data-source=%s", ds);
                batch.writeAttribute()
                        .address(WildflyCompatibilityUtils.parseCLIStyleAddress(dsAddr))
                        .attribute("statistics-enabled", enableStr)
                        .parentBuilder();
            }
            for (String xa : xaList) {
                String xaAddr = String.format(serverPrefix + "/subsystem=datasources/xa-data-source=%s", xa);
                batch.writeAttribute()
                        .address(WildflyCompatibilityUtils.parseCLIStyleAddress(xaAddr))
                        .attribute("statistics-enabled", enableStr)
                        .parentBuilder();
            }
            execute(mcc, batch, "datasources", enable);

            // ejb3
            batch = OperationBuilder.composite();
            batch.writeAttribute()
                    .address(WildflyCompatibilityUtils.parseCLIStyleAddress(serverPrefix + "/subsystem=ejb3"))
                    .attribute("enable-statistics", enableStr)
                    .parentBuilder();
            execute(mcc, batch, "ejb3", enable);

            // infinispan
            batch = OperationBuilder.composite();
            List<String> infinispanList = getChildrenNames(
                    WildflyCompatibilityUtils.parseCLIStyleAddress(serverPrefix + "/subsystem=infinispan"),
                    "cache-container", mcc);
            for (String name : infinispanList) {
                String addr = String.format(serverPrefix + "/subsystem=infinispan/cache-container=%s", name);
                batch.writeAttribute()
                        .address(WildflyCompatibilityUtils.parseCLIStyleAddress(addr))
                        .attribute("statistics-enabled", enableStr)
                        .parentBuilder();
            }
            execute(mcc, batch, "infinispan", enable);

            // activemq
            batch = OperationBuilder.composite();
            List<String> activeMqList = getChildrenNames(
                    WildflyCompatibilityUtils.parseCLIStyleAddress(serverPrefix + "/subsystem=messaging-activemq"),
                    "server", mcc);
            for (String name : activeMqList) {
                String addr = String.format(serverPrefix + "/subsystem=messaging-activemq/server=%s", name);
                batch.writeAttribute()
                        .address(WildflyCompatibilityUtils.parseCLIStyleAddress(addr))
                        .attribute("statistics-enabled", enableStr)
                        .parentBuilder();
            }
            execute(mcc, batch, "activemq", enable);

            // transactions
            batch = OperationBuilder.composite();
            batch.writeAttribute()
                    .address(WildflyCompatibilityUtils.parseCLIStyleAddress(serverPrefix + "/subsystem=transactions"))
                    .attribute("enable-statistics", enableStr)
                    .parentBuilder();
            execute(mcc, batch, "transactions", enable);

            // undertow
            batch = OperationBuilder.composite();
            batch.writeAttribute()
                    .address(WildflyCompatibilityUtils.parseCLIStyleAddress(serverPrefix + "/subsystem=undertow"))
                    .attribute("statistics-enabled", enableStr)
                    .parentBuilder();
            execute(mcc, batch, "undertow", enable);

        } catch (Exception e) {
            log.errorf(e, "Aborting statistics enablement");
        }
    }

    private void execute(ModelControllerClient controllerClient, CompositeOperationBuilder<?> batch, String name,
            boolean enable) {
        OperationResult<?> opResult = null;
        try {
            opResult = batch.execute(controllerClient).assertSuccess();
            log.debugf("%s statistics for [%s]", enable ? "Enabled" : "Disabled", name);
        } catch (Exception e) {
            String errorStr = (opResult != null) ? opResult.getResultNode().toJSONString(true) : "unknown result";
            log.debugf(e, "Cannot set statistics for [%s]: %s", name, errorStr);
        }
    }

    private List<String> getChildrenNames(PathAddress parentPath, String childType, ModelControllerClient mcc) {
        try {
            return OperationBuilder.readChildrenNames()
                    .address(parentPath)
                    .childType(childType)
                    .execute(mcc)
                    .assertSuccess()
                    .getList();
        } catch (Exception e) {
            log.debugf("Cannot get children of [" + parentPath + "] of type [" + childType + "]");
            return Collections.emptyList();
        }
    }

    // returns empty list if not in domain mode
    private List<String> getDomainHosts(ModelControllerClient mcc) {
        return getChildrenNames(PathAddress.EMPTY_ADDRESS, "host", mcc);
    }

    // returns empty list if not in domain mode
    private List<String> getDomainHostServers(ModelControllerClient mcc, String hostName) {
        return getChildrenNames(PathAddress.EMPTY_ADDRESS.append("host", hostName), "server", mcc);
    }
}
