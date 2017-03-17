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
package org.hawkular.agent.javaagent.cmd;

import java.util.Map;

import org.hawkular.agent.javaagent.JavaAgentEngine;
import org.hawkular.agent.javaagent.config.Configuration;
import org.hawkular.agent.javaagent.config.DMRAvail;
import org.hawkular.agent.javaagent.config.DMRAvailSet;
import org.hawkular.agent.javaagent.config.DMRMetric;
import org.hawkular.agent.javaagent.config.DMRMetricSet;
import org.hawkular.agent.javaagent.config.JMXAvail;
import org.hawkular.agent.javaagent.config.JMXAvailSet;
import org.hawkular.agent.javaagent.config.JMXMetric;
import org.hawkular.agent.javaagent.config.JMXMetricSet;
import org.hawkular.agent.javaagent.config.TimeUnits;
import org.hawkular.agent.javaagent.log.JavaAgentLoggers;
import org.hawkular.agent.javaagent.log.MsgLogger;
import org.hawkular.agent.monitor.cmd.AbstractJMXResourcePathCommand;
import org.hawkular.agent.monitor.cmd.CommandContext;
import org.hawkular.agent.monitor.cmd.CommandContext.ResponseSentListener;
import org.hawkular.agent.monitor.config.AgentCoreEngineConfiguration.AbstractEndpointConfiguration;
import org.hawkular.agent.monitor.inventory.MonitoredEndpoint;
import org.hawkular.agent.monitor.protocol.EndpointService;
import org.hawkular.agent.monitor.protocol.jmx.JMXNodeLocation;
import org.hawkular.agent.monitor.protocol.jmx.JMXSession;
import org.hawkular.bus.common.BasicMessage;
import org.hawkular.bus.common.BasicMessageWithExtraData;
import org.hawkular.bus.common.BinaryData;
import org.hawkular.cmdgw.api.UpdateCollectionIntervalsRequest;
import org.hawkular.cmdgw.api.UpdateCollectionIntervalsResponse;

/**
 * Update the specified metric and avail type collection intervals.
 * Because metric types are not guaranteed to be consistent across agents, it is
 * not a failure if a requested metric type does not exist.
 *
 * Note that this really doesn't involve JMX but it makes more sense to extend the JMX superclass
 * rather than extend the DMR one or to write a command from scratch.
 *
 * @author Jay Shaughnessy
 * @author John Mazzitelli
 */
public class UpdateCollectionIntervalsCommand
        extends AbstractJMXResourcePathCommand<UpdateCollectionIntervalsRequest, UpdateCollectionIntervalsResponse> {

    private static final MsgLogger log = JavaAgentLoggers.getLogger(UpdateCollectionIntervalsCommand.class);
    public static final Class<UpdateCollectionIntervalsRequest> REQUEST_CLASS = UpdateCollectionIntervalsRequest.class;

    public UpdateCollectionIntervalsCommand() {
        super("Update Collection Intervals", "Agent[JMX]");
    }

    @Override
    protected UpdateCollectionIntervalsResponse createResponse() {
        return new UpdateCollectionIntervalsResponse();
    }

    @Override
    protected BinaryData execute(
            EndpointService<JMXNodeLocation, JMXSession> endpointService,
            String resourceId,
            BasicMessageWithExtraData<UpdateCollectionIntervalsRequest> envelope,
            UpdateCollectionIntervalsResponse response,
            CommandContext context) throws Exception {

        // we can cast this because we know our command implementation is only ever installed in a JavaAgentEngine
        JavaAgentEngine javaAgent = (JavaAgentEngine) context.getAgentCoreEngine();
        Configuration javaAgentConfig = javaAgent.getConfigurationManager().getConfiguration();

        UpdateCollectionIntervalsRequest request = envelope.getBasicMessage();
        Map<String, String> metricTypes = request.getMetricTypes();
        Map<String, String> availTypes = request.getAvailTypes();

        boolean requireRestart = false;

        if (metricTypes != null && !metricTypes.isEmpty()) {
            NEXT_AVAIL: for (Map.Entry<String, String> entry : metricTypes.entrySet()) {
                String metricTypeId = entry.getKey();
                String[] names = parseMetricTypeId(metricTypeId);
                String metricSetName = names[0];
                String metricName = names[1];
                // find the metric and change its interval if found
                for (DMRMetricSet metricSet : javaAgentConfig.getDmrMetricSets()) {
                    if (metricSetName.equals(metricSet.getName())) {
                        for (DMRMetric metric : metricSet.getDmrMetrics()) {
                            if (metricName.equals(metric.getName())) {
                                metric.setInterval(Integer.valueOf(entry.getValue()));
                                metric.setTimeUnits(TimeUnits.seconds); // the command always assumes seconds
                                requireRestart = true;
                                continue NEXT_AVAIL;
                            }
                        }
                    }
                }
                for (JMXMetricSet metricSet : javaAgentConfig.getJmxMetricSets()) {
                    if (metricSetName.equals(metricSet.getName())) {
                        for (JMXMetric metric : metricSet.getJmxMetrics()) {
                            if (metricName.equals(metric.getName())) {
                                metric.setInterval(Integer.valueOf(entry.getValue()));
                                metric.setTimeUnits(TimeUnits.seconds); // the command always assumes seconds
                                requireRestart = true;
                                continue NEXT_AVAIL;
                            }
                        }
                    }
                }
            }
        }
        if (availTypes != null && !availTypes.isEmpty()) {
            NEXT_AVAIL: for (Map.Entry<String, String> entry : availTypes.entrySet()) {
                String availTypeId = entry.getKey();
                String[] names = parseAvailTypeId(availTypeId);
                String availSetName = names[0];
                String availName = names[1];
                // find the avail and change its interval if found
                for (DMRAvailSet availSet : javaAgentConfig.getDmrAvailSets()) {
                    if (availSetName.equals(availSet.getName())) {
                        for (DMRAvail avail : availSet.getDmrAvails()) {
                            if (availName.equals(avail.getName())) {
                                avail.setInterval(Integer.valueOf(entry.getValue()));
                                avail.setTimeUnits(TimeUnits.seconds); // the command always assumes seconds
                                requireRestart = true;
                                continue NEXT_AVAIL;
                            }
                        }
                    }
                }
                for (JMXAvailSet availSet : javaAgentConfig.getJmxAvailSets()) {
                    if (availSetName.equals(availSet.getName())) {
                        for (JMXAvail avail : availSet.getJmxAvails()) {
                            if (availName.equals(avail.getName())) {
                                avail.setInterval(Integer.valueOf(entry.getValue()));
                                avail.setTimeUnits(TimeUnits.seconds); // the command always assumes seconds
                                requireRestart = true;
                                continue NEXT_AVAIL;
                            }
                        }
                    }
                }
            }
        }

        if (requireRestart) {
            context.addResponseSentListener(new ResponseSentListener() {
                @Override
                public void onSend(BasicMessageWithExtraData<? extends BasicMessage> response, Exception sendError) {
                    log.info("Collection intervals updated. Persisting changes and restarting agent.");
                    javaAgent.stopHawkularAgent();
                    javaAgent.startHawkularAgent(javaAgentConfig);
                }
            });
        } else {
            log.debug("Skipping collection interval update, no valid type updates provided.");
        }

        return null;
    }

    @Override
    protected void validate(BasicMessageWithExtraData<UpdateCollectionIntervalsRequest> envelope,
            MonitoredEndpoint<? extends AbstractEndpointConfiguration> endpoint) {
        return; // no-op
    }

    private String[] parseMetricTypeId(String metricTypeId) {
        String[] names = metricTypeId.split("~");
        if (names.length != 2) {
            throw new IllegalArgumentException(
                    "MetricTypeId must be of form MetricTypeSetName~MetricTypeName: " + metricTypeId);
        }
        return names;
    }

    private String[] parseAvailTypeId(String availTypeId) {
        String[] names = availTypeId.split("~");
        if (names.length != 2) {
            throw new IllegalArgumentException(
                    "AvailTypeId must be of form AvailTypeSetName~AvailTypeName: " + availTypeId);
        }
        return names;
    }

}
