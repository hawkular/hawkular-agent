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
package org.hawkular.agent.monitor.cmd;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Optional;

import org.hawkular.agent.monitor.extension.MonitorServiceConfiguration.AbstractEndpointConfiguration;
import org.hawkular.agent.monitor.inventory.ID;
import org.hawkular.agent.monitor.inventory.MonitoredEndpoint;
import org.hawkular.agent.monitor.inventory.Resource;
import org.hawkular.agent.monitor.inventory.ResourceManager;
import org.hawkular.agent.monitor.log.AgentLoggers;
import org.hawkular.agent.monitor.log.MsgLogger;
import org.hawkular.agent.monitor.protocol.EndpointService;
import org.hawkular.agent.monitor.protocol.dmr.DMRNodeLocation;
import org.hawkular.agent.monitor.protocol.dmr.DMRSession;
import org.hawkular.bus.common.BasicMessageWithExtraData;
import org.hawkular.bus.common.BinaryData;
import org.hawkular.cmdgw.api.MessageUtils;
import org.hawkular.cmdgw.api.ResponseStatus;
import org.hawkular.cmdgw.api.StatisticsControlRequest;
import org.hawkular.cmdgw.api.StatisticsControlResponse;
import org.hawkular.cmdgw.api.StatisticsSetting;
import org.hawkular.dmr.api.OperationBuilder;
import org.hawkular.dmr.api.OperationBuilder.CompositeOperationBuilder;
import org.hawkular.dmr.api.OperationBuilder.OperationResult;
import org.hawkular.dmrclient.JBossASClient;
import org.hawkular.inventory.api.model.CanonicalPath;
import org.jboss.as.controller.PathAddress;
import org.jboss.as.controller.client.ModelControllerClient;
import org.jboss.dmr.ModelNode;
import org.jboss.dmr.ModelType;

/**
 * Turns on or off statistics for several WildFly subsystems.
 * This command can also be used to obtain the state of the statistics enable flags
 * of all the subsystems, even if you don't want to turn on or off any of them.
 */
public class StatisticsControlCommand
        extends AbstractResourcePathCommand<StatisticsControlRequest, StatisticsControlResponse> {
    private static final MsgLogger log = AgentLoggers.getLogger(StatisticsControlCommand.class);
    public static final Class<StatisticsControlRequest> REQUEST_CLASS = StatisticsControlRequest.class;

    public StatisticsControlCommand() {
        super("Statistics Control", "Server");
    }

    @Override
    protected BinaryData execute(ModelControllerClient controllerClient,
            EndpointService<DMRNodeLocation, DMRSession> endpointService,
            String modelNodePath,
            BasicMessageWithExtraData<StatisticsControlRequest> envelope,
            StatisticsControlResponse response,
            CommandContext context,
            DMRSession dmrContext)
                    throws Exception {

        final StatisticsControlRequest request = envelope.getBasicMessage();
        final String resourcePath = request.getResourcePath();
        final CanonicalPath canonicalPath = CanonicalPath.fromString(resourcePath);
        final String resourceId = canonicalPath.ids().getResourcePath().getSegment().getElementId();
        final ResourceManager<DMRNodeLocation> resourceManager = endpointService.getResourceManager();
        final Resource<DMRNodeLocation> resource = resourceManager.getResource(new ID(resourceId));

        if (resource == null) {
            throw new IllegalArgumentException(
                    String.format("Cannot change statistics flags: unknown resource [%s]", resourcePath));
        }

        if (!resource.getLocation().getPathAddress().toCLIStyleString().equals("/")) {
            throw new IllegalArgumentException(
                    String.format("Cannot change statistics flags: not a server resource [%s]", resourcePath));
        }

        // populate the basic response
        MessageUtils.prepareResourcePathResponse(request, response);

        // determine which statistics it wants to enable, disable, or leave the same.
        Optional<Boolean> datasources = getStatisticsEnabledFlag(request.getDatasources());
        Optional<Boolean> ejb3 = getStatisticsEnabledFlag(request.getEjb3());
        Optional<Boolean> infinispan = getStatisticsEnabledFlag(request.getInfinispan());
        Optional<Boolean> messaging = getStatisticsEnabledFlag(request.getMessaging());
        Optional<Boolean> transactions = getStatisticsEnabledFlag(request.getTransactions());
        Optional<Boolean> web = getStatisticsEnabledFlag(request.getWeb());

        boolean somethingToDo = false;
        final CompositeOperationBuilder<?> batch = OperationBuilder.composite();

        if (datasources.isPresent()) {
            somethingToDo = true;
            List<String> dsList = getChildrenNames(PathAddress.parseCLIStyleAddress("/subsystem=datasources"),
                    "data-source", controllerClient);
            List<String> xaList = getChildrenNames(PathAddress.parseCLIStyleAddress("/subsystem=datasources"),
                    "xa-data-source", controllerClient);

            for (String ds : dsList) {
                String dsAddr = String.format("/subsystem=datasources/data-source=%s", ds);
                batch.writeAttribute()
                        .address(PathAddress.parseCLIStyleAddress(dsAddr))
                        .attribute("statistics-enabled", datasources.get().toString())
                        .parentBuilder();
            }
            for (String xa : xaList) {
                String xaAddr = String.format("/subsystem=datasources/xa-data-source=%s", xa);
                batch.writeAttribute()
                        .address(PathAddress.parseCLIStyleAddress(xaAddr))
                        .attribute("statistics-enabled", datasources.get().toString())
                        .parentBuilder();
            }
        }

        if (ejb3.isPresent()) {
            somethingToDo = true;
            batch.writeAttribute()
                    .address(PathAddress.parseCLIStyleAddress("/subsystem=ejb3"))
                    .attribute("enable-statistics", ejb3.get().toString())
                    .parentBuilder();
        }

        if (infinispan.isPresent()) {
            somethingToDo = true;
            List<String> list = getChildrenNames(PathAddress.parseCLIStyleAddress("/subsystem=infinispan"),
                    "cache-container", controllerClient);

            for (String name : list) {
                String addr = String.format("/subsystem=infinispan/cache-container=%s", name);
                batch.writeAttribute()
                        .address(PathAddress.parseCLIStyleAddress(addr))
                        .attribute("statistics-enabled", infinispan.get().toString())
                        .parentBuilder();
            }
        }

        if (messaging.isPresent()) {
            somethingToDo = true;
            List<String> list = getChildrenNames(PathAddress.parseCLIStyleAddress("/subsystem=messaging-activemq"),
                    "server", controllerClient);

            for (String name : list) {
                String addr = String.format("/subsystem=messaging-activemq/server=%s", name);
                batch.writeAttribute()
                        .address(PathAddress.parseCLIStyleAddress(addr))
                        .attribute("statistics-enabled", messaging.get().toString())
                        .parentBuilder();
            }
        }

        if (transactions.isPresent()) {
            somethingToDo = true;
            batch.writeAttribute()
                    .address(PathAddress.parseCLIStyleAddress("/subsystem=transactions"))
                    .attribute("enable-statistics", transactions.get().toString())
                    .parentBuilder();
        }

        if (web.isPresent()) {
            somethingToDo = true;
            batch.writeAttribute()
                    .address(PathAddress.parseCLIStyleAddress("/subsystem=undertow"))
                    .attribute("statistics-enabled", web.get().toString())
                    .parentBuilder();
        }

        if (somethingToDo) {
            OperationResult<?> opResult = batch.execute(controllerClient).assertSuccess();
            setServerRefreshIndicator(opResult, response);
        }

        // Tell requestor what the current state is of all the enable flags,
        // even if they didn't ask to change some or all of them.
        // We need to do this here in execute() as opposed to in success()
        // because we still need to talk to the server and it is only here where we have the client.

        if (request.getDatasources() != null) {
            response.setDatasources(request.getDatasources());
        } else {
            // ask the server
            StatisticsSetting currentState;
            currentState = getCurrentStateFromServer(controllerClient,
                    "/subsystem=datasources/data-source=*", "statistics-enabled");
            if (currentState == null) {
                currentState = getCurrentStateFromServer(controllerClient,
                        "/subsystem=datasources/xa-data-source=*", "statistics-enabled");
            }
            response.setDatasources(currentState);
        }

        if (request.getEjb3() != null) {
            response.setEjb3(request.getEjb3());
        } else {
            // ask the server
            StatisticsSetting currentState = getCurrentStateFromServer(controllerClient,
                    "/subsystem=ejb3", "enable-statistics");
            response.setEjb3(currentState);
        }

        if (request.getInfinispan() != null) {
            response.setInfinispan(request.getInfinispan());
        } else {
            // ask the server
            StatisticsSetting currentState = getCurrentStateFromServer(controllerClient,
                    "/subsystem=infinispan/cache-container=*", "statistics-enabled");
            response.setInfinispan(currentState);
        }

        if (request.getMessaging() != null) {
            response.setMessaging(request.getMessaging());
        } else {
            // ask the server
            StatisticsSetting currentState = getCurrentStateFromServer(controllerClient,
                    "/subsystem=messaging-activemq/server=*", "statistics-enabled");
            response.setMessaging(currentState);
        }

        if (request.getTransactions() != null) {
            response.setTransactions(request.getTransactions());
        } else {
            // ask the server
            StatisticsSetting currentState = getCurrentStateFromServer(controllerClient,
                    "/subsystem=transactions", "enable-statistics");
            response.setTransactions(currentState);
        }

        if (request.getWeb() != null) {
            response.setWeb(request.getWeb());
        } else {
            // ask the server
            StatisticsSetting currentState = getCurrentStateFromServer(controllerClient,
                    "/subsystem=undertow", "statistics-enabled");
            response.setWeb(currentState);
        }

        return null;
    }

    private StatisticsSetting getCurrentStateFromServer(ModelControllerClient controllerClient, String addr,
            String attribName) {

        try {
            ModelNode result = new OperationBuilder().readAttribute()
                    .address(PathAddress.parseCLIStyleAddress(addr))
                    .name(attribName)
                    .execute(controllerClient)
                    .assertSuccess()
                    .getResultNode();

            log.debugf("Getting current statistics flag for address [%s][%s]. Type=[%s]:%s", addr, attribName,
                    result.getType(), result.toJSONString(true));

            // here's the thing - some subsystems with statistics will enable the stats on the subsystem level
            // (like transactions) but others enable stats on each individual resource (like
            // datasources, where each datasource can have stats enabled or disabled individually).
            // Our StatisticsControlRequest turns stats on or off across the subsystem (so if you say turn
            // stats on for datasources, we set it across all datasources) to avoid having the
            // client bear the burden of specifying each individual resource for those subsystems that have
            // stats per resource (like datasources). But the problem there is we need to remember that it is possible
            // that a WildFly server had previously been configured to enable SOME resource stats but not ALL
            // resource stats (for example, maybe datasource "foo" has stats enabled but datasource "bar" has
            // them disabled). In this case, we can't tell the requestor about this (unless we change the
            // JSON response, but that puts more burden on the client to know what to do with that info).
            // We can only say if the datasource subsystem has them enabled or disabled. In this case
            // where one resource has them enabled and another disabled, we just look
            // at the first one we get - if its enabled, we'll say the subsystem has stats enabled, if its
            // disabled we'll say the subsystem is disabled. This is purely arbitrary, but its something we
            // have to deal with if we want to make it easy on the client. If they enabled or disable
            // stats using us (StatisticsControlRequest) this isn't a problem because we set them ALL or unset
            // them ALL. We never turn some on or some off. But there are cases where the enable flags will
            // be mixed. We could introduce a "MIXED" enum (in addition to ENABLED and DISABLED) but I'm
            // not sure that helps the user all that much anyway. But perhaps we can add that in the future.
            // For now we return null if mixed.

            if (result.getType() == ModelType.LIST) {
                // Go through the list of each resource's enabled flag:
                //   - If they are all enabled or all disabled, we indicate that.
                //   - If there is a mix, then to indicate that we return null.
                Boolean aggregate = null;
                for (ModelNode resourceNode : result.asList()) {
                    ModelNode resourceResult = JBossASClient.getResults(resourceNode);
                    boolean booleanFlag = resourceResult.asBoolean();
                    if (aggregate == null) {
                        aggregate = Boolean.valueOf(booleanFlag);
                    } else {
                        if (aggregate.booleanValue() != booleanFlag) {
                            return null; // MIXED! some were enabled, some were disabled
                        }
                    }
                }

                if (aggregate == null) {
                    return null; // there are no resources for this subsystem, so neither enabled nor disabled
                } else {
                    return (aggregate.booleanValue()) ? StatisticsSetting.ENABLED : StatisticsSetting.DISABLED;
                }
            } else {
                return (result.asBoolean()) ? StatisticsSetting.ENABLED : StatisticsSetting.DISABLED;
            }
        } catch (Throwable t) {
            return null; // we don't know
        }
    }

    private Optional<Boolean> getStatisticsEnabledFlag(StatisticsSetting setting) {
        if (setting == null) {
            return Optional.empty();
        }
        return Optional.of(setting == StatisticsSetting.ENABLED);
    }

    private List<String> getChildrenNames(PathAddress parentPath, String childType, ModelControllerClient mcc) {
        return OperationBuilder.readChildrenNames()
                .address(parentPath)
                .childType(childType)
                .execute(mcc)
                .assertSuccess()
                .getList();
    }

    @Override
    protected void success(BasicMessageWithExtraData<StatisticsControlRequest> envelope,
            StatisticsControlResponse response) {

        HashMap<StatisticsSetting, ArrayList<String>> settings = new HashMap<>();
        settings.put(null, new ArrayList<>());
        settings.put(StatisticsSetting.ENABLED, new ArrayList<>());
        settings.put(StatisticsSetting.DISABLED, new ArrayList<>());

        StatisticsControlRequest request = envelope.getBasicMessage();
        settings.get(request.getDatasources()).add("datasources");
        settings.get(request.getEjb3()).add("EJB3");
        settings.get(request.getInfinispan()).add("infinispan");
        settings.get(request.getMessaging()).add("messaging");
        settings.get(request.getTransactions()).add("transactions");
        settings.get(request.getWeb()).add("web");

        String msg = String.format(
                "Statistics for server [%s] have been "
                        + "enabled for subsystems %s, "
                        + "disabled for subsystems %s, "
                        + "and left as-is for subsystems %s",
                envelope.getBasicMessage().getResourcePath(),
                settings.get(StatisticsSetting.ENABLED),
                settings.get(StatisticsSetting.DISABLED),
                settings.get(null));

        response.setMessage(msg);
        response.setStatus(ResponseStatus.OK);
    }

    @Override
    protected void validate(String modelNodePath, BasicMessageWithExtraData<StatisticsControlRequest> envelope) {
    }

    @Override
    protected void validate(BasicMessageWithExtraData<StatisticsControlRequest> envelope,
            MonitoredEndpoint<? extends AbstractEndpointConfiguration> endpoint) {
    }

    @Override
    protected StatisticsControlResponse createResponse() {
        return new StatisticsControlResponse();
    }
}
