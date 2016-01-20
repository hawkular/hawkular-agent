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
import java.util.Optional;

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
import org.hawkular.inventory.api.model.CanonicalPath;
import org.jboss.as.controller.PathAddress;
import org.jboss.as.controller.client.ModelControllerClient;

/**
 * Turns on or off statistics for several WildFly subsystems.
 */
public class StatisticsControlCommand
        extends AbstractResourcePathCommand<StatisticsControlRequest, StatisticsControlResponse> {
    @SuppressWarnings("unused")
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

        final CompositeOperationBuilder<?> batch = OperationBuilder.composite();

        if (datasources.isPresent()) {
            batch.writeAttribute()
                    .address(PathAddress.parseCLIStyleAddress("/subsystem=datasources/data-source=*"))
                    .attribute("statistics-enabled", datasources.get().toString())
                    .parentBuilder();
            batch.writeAttribute()
                    .address(PathAddress.parseCLIStyleAddress("/subsystem=datasources/xa-data-source=*"))
                    .attribute("statistics-enabled", datasources.get().toString())
                    .parentBuilder();
        }

        if (ejb3.isPresent()) {
            batch.writeAttribute()
                    .address(PathAddress.parseCLIStyleAddress("/subsystem=ejb3"))
                    .attribute("enable-statistics", ejb3.get().toString())
                    .parentBuilder();
        }

        if (infinispan.isPresent()) {
            batch.writeAttribute()
                    .address(PathAddress.parseCLIStyleAddress("/subsystem=infinispan/cache-container=*"))
                    .attribute("statistics-enabled", infinispan.get().toString())
                    .parentBuilder();
        }

        if (messaging.isPresent()) {
            batch.writeAttribute()
                    .address(PathAddress.parseCLIStyleAddress("/subsystem=messaging-activemq/server=*"))
                    .attribute("statistics-enabled", messaging.get().toString())
                    .parentBuilder();
        }

        if (transactions.isPresent()) {
            batch.writeAttribute()
                    .address(PathAddress.parseCLIStyleAddress("/subsystem=transactions"))
                    .attribute("enable-statistics", transactions.get().toString())
                    .parentBuilder();
        }

        if (web.isPresent()) {
            batch.writeAttribute()
                    .address(PathAddress.parseCLIStyleAddress("/subsystem=undertow"))
                    .attribute("statistics-enabled", web.get().toString())
                    .parentBuilder();
        }

        OperationResult<?> opResult = batch.execute(controllerClient).assertSuccess();
        setServerRefreshIndicator(opResult, response);

        return null;
    }

    private Optional<Boolean> getStatisticsEnabledFlag(StatisticsSetting setting) {
        if (setting == null) {
            return Optional.empty();
        }
        return Optional.of(setting == StatisticsSetting.ENABLED);
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
    protected void validate(BasicMessageWithExtraData<StatisticsControlRequest> envelope, MonitoredEndpoint endpoint) {
    }

    @Override
    protected StatisticsControlResponse createResponse() {
        return new StatisticsControlResponse();
    }
}
