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
package org.hawkular.agent.wildfly.cmd;

import java.util.HashMap;
import java.util.Map;

import org.hawkular.agent.monitor.cmd.AbstractDMRResourcePathCommand;
import org.hawkular.agent.monitor.cmd.CommandContext;
import org.hawkular.agent.monitor.config.AgentCoreEngineConfiguration.AbstractEndpointConfiguration;
import org.hawkular.agent.monitor.inventory.MonitoredEndpoint;
import org.hawkular.agent.monitor.log.AgentLoggers;
import org.hawkular.agent.monitor.log.MsgLogger;
import org.hawkular.agent.monitor.protocol.EndpointService;
import org.hawkular.agent.monitor.protocol.dmr.DMRNodeLocation;
import org.hawkular.agent.monitor.protocol.dmr.DMRSession;
import org.hawkular.agent.monitor.util.WildflyCompatibilityUtils;
import org.hawkular.bus.common.BasicMessageWithExtraData;
import org.hawkular.bus.common.BinaryData;
import org.hawkular.cmdgw.api.UpdateCollectionIntervalsRequest;
import org.hawkular.cmdgw.api.UpdateCollectionIntervalsResponse;
import org.hawkular.dmr.api.DmrApiException;
import org.hawkular.dmr.api.OperationBuilder;
import org.hawkular.dmr.api.OperationBuilder.CompositeOperationBuilder;
import org.hawkular.dmr.api.OperationBuilder.OperationResult;
import org.jboss.as.controller.PathAddress;
import org.jboss.as.controller.client.ModelControllerClient;

/**
 * Update the specified metric and avail type collection intervals.  Performs a bulk update to prevent
 * excessive agent restarts. Because metric types are not guaranteed to be consistent across agents, it is
 * not a failure if a requested metric type does not exist.
 *
 * @author Jay Shaughnessy
 */
public class UpdateCollectionIntervalsCommand
        extends AbstractDMRResourcePathCommand<UpdateCollectionIntervalsRequest, UpdateCollectionIntervalsResponse> {

    private static final MsgLogger log = AgentLoggers.getLogger(UpdateCollectionIntervalsCommand.class);
    public static final Class<UpdateCollectionIntervalsRequest> REQUEST_CLASS = UpdateCollectionIntervalsRequest.class;

    public UpdateCollectionIntervalsCommand() {
        super("Update Collection Intervals", "Agent[DMR]");
    }

    /** @see org.hawkular.agent.monitor.cmd.AbstractDMRResourcePathCommand#createResponse() */
    @Override
    protected UpdateCollectionIntervalsResponse createResponse() {
        return new UpdateCollectionIntervalsResponse();
    }

    @Override
    protected BinaryData execute(
            ModelControllerClient mcc,
            EndpointService<DMRNodeLocation, DMRSession> endpointService,
            String modelNodePath,
            BasicMessageWithExtraData<UpdateCollectionIntervalsRequest> envelope,
            UpdateCollectionIntervalsResponse response,
            CommandContext context,
            DMRSession dmrContext) throws Exception {

        PathAddress agentAddress = WildflyCompatibilityUtils.parseCLIStyleAddress(modelNodePath);

        UpdateCollectionIntervalsRequest request = envelope.getBasicMessage();
        Map<PathAddress, String> metricTypes = filterUpdates(mcc, request.getMetricTypes(), false, agentAddress);
        Map<PathAddress, String> availTypes = filterUpdates(mcc, request.getAvailTypes(), true, agentAddress);

        if (isEmpty(metricTypes) && isEmpty(availTypes)) {
            log.debug("Skipping collection interval update, no valid type updates provided.");
            return null;
        }

        CompositeOperationBuilder<?> composite = OperationBuilder.composite();
        addUpdates(composite, metricTypes);
        addUpdates(composite, availTypes);
        // Don't let the restart services hook kick in, instead do a deferred restart
        composite.allowResourceServiceRestart(false);
        // Now refresh the monitor service with the updated config. Delay the restart to allow time for the
        // command response to be returned.
        composite.byNameOperation("start")
                .address(agentAddress)
                .attribute("refresh", "true")
                .attribute("delay", "500") //  enough time to get the response sent back
                .parentBuilder();

        try {
            OperationResult<?> opResult = composite.execute(mcc).assertSuccess();
            setServerRefreshIndicator(opResult, response);

        } catch (DmrApiException e) {
            log.errorf("Failed to update collection intervals: %s", e.getMessage());
            throw e;
        }

        return null;
    }

    private Map<PathAddress, String> filterUpdates(ModelControllerClient mcc, Map<String, String> updates,
            boolean isAvail, PathAddress agentAddress) {

        if (isEmpty(updates)) {
            return null;
        }

        String agentPath = agentAddress.toCLIStyleString();
        Map<PathAddress, String> result = new HashMap<>();

        for (Map.Entry<String, String> entry : updates.entrySet()) {
            String typeId = entry.getKey();
            String interval = entry.getValue();

            PathAddress metricType = null;
            try {
                String path = isAvail ? toAvailPath(typeId, agentPath) : toMetricPath(typeId, agentPath);
                metricType = WildflyCompatibilityUtils.parseCLIStyleAddress(path);
            } catch (Exception e1) {
                log.warnf("Unable to update interval for invalid type [%s]", typeId);
                continue;
            }

            try {
                Integer.valueOf(interval);
            } catch (Exception e) {
                log.warnf("Unable to update interval for type [%s], invalid interval [%s]", typeId, interval);
                continue;
            }

            if (!isValidAddress(mcc, metricType)) {
                log.warnf("Skipping collection interval update on invalid metric type [%s]", typeId);
                continue;
            }

            result.put(metricType, interval);
        }

        return result;
    }

    private boolean isValidAddress(ModelControllerClient mcc, PathAddress address) {
        try {
            OperationBuilder.readAttribute()
                    .address(address)
                    .name("interval")
                    .includeDefaults()
                    .execute(mcc)
                    .assertSuccess();
            return true;

        } catch (Exception e) {
            return false;
        }
    }

    private void addUpdates(CompositeOperationBuilder<?> composite, Map<PathAddress, String> updates) {
        if (!isEmpty(updates)) {
            for (Map.Entry<PathAddress, String> entry : updates.entrySet()) {
                PathAddress metricType = entry.getKey();
                String interval = entry.getValue();

                composite.writeAttribute()
                        .address(metricType)
                        .attribute("interval", interval)
                        .allowResourceServiceRestart(false)
                        .parentBuilder();
                composite.writeAttribute()
                        .address(metricType)
                        .attribute("time-units", "seconds")
                        .allowResourceServiceRestart(false)
                        .parentBuilder();
            }
        }
    }

    private String toMetricPath(String metricTypeId, String agentPath) {
        String[] a = metricTypeId.split("~");
        if (a.length != 2) {
            throw new IllegalArgumentException("MetricTypeId must be of form MetricTypeSet~MetricTypeName");
        }
        return agentPath + (agentPath.endsWith("/") ? "" : "/") + "metric-set-dmr=" + a[0] + "/metric-dmr=" + a[1];
    }

    private String toAvailPath(String availTypeId, String agentPath) {
        String[] a = availTypeId.split("~");
        if (a.length != 2) {
            throw new IllegalArgumentException("AvailTypeId must be of form AvailTypeSet~AvailTypeName");
        }
        return agentPath + (agentPath.endsWith("/") ? "" : "/") + "avail-set-dmr=" + a[0] + "/avail-dmr=" + a[1];
    }

    private boolean isEmpty(Map<?, ?> c) {
        return null == c || c.isEmpty();
    }

    @Override
    protected void validate(BasicMessageWithExtraData<UpdateCollectionIntervalsRequest> envelope,
            MonitoredEndpoint<? extends AbstractEndpointConfiguration> endpoint) {
    }

    @Override
    protected void validate(String modelNodePath,
            BasicMessageWithExtraData<UpdateCollectionIntervalsRequest> envelope) {
    }
}
