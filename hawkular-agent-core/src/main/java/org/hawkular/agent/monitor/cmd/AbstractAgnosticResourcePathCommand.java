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
package org.hawkular.agent.monitor.cmd;

import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;

import org.hawkular.agent.monitor.config.AgentCoreEngineConfiguration.AbstractEndpointConfiguration;
import org.hawkular.agent.monitor.inventory.InventoryIdUtil;
import org.hawkular.agent.monitor.inventory.InventoryIdUtil.ResourceIdParts;
import org.hawkular.agent.monitor.inventory.MonitoredEndpoint;
import org.hawkular.agent.monitor.log.AgentLoggers;
import org.hawkular.agent.monitor.log.MsgLogger;
import org.hawkular.bus.common.BasicMessageWithExtraData;
import org.hawkular.cmdgw.api.MessageUtils;
import org.hawkular.cmdgw.api.ResourceRequest;
import org.hawkular.cmdgw.api.ResourceResponse;
import org.hawkular.cmdgw.api.ResponseStatus;

/**
 * A class that can execute either DMR or JMX {@link Command}s of type {@link ResourceRequest}.
 *
 * Note that this is agnostic as to whether the resource path refers to a JMX or DMR resource.
 * Subclasses will be able to build the necessary commands for the different types.
 */
public abstract class AbstractAgnosticResourcePathCommand //
<REQ extends ResourceRequest, RESP extends ResourceResponse>
        extends AbstractResourceCommand<REQ, RESP>
        implements Command<REQ, RESP> {

    private static final MsgLogger log = AgentLoggers.getLogger(AbstractAgnosticResourcePathCommand.class);

    public AbstractAgnosticResourcePathCommand(String operationName, String entityType) {
        super(operationName, entityType);
    }

    @Override
    public BasicMessageWithExtraData<RESP> execute(BasicMessageWithExtraData<REQ> envelope, CommandContext context)
            throws Exception {

        REQ request = envelope.getBasicMessage();
        String rawResourcePath = request.getResourceId();
        long timestampBeforeExecution = System.currentTimeMillis();

        try {
            validate(envelope);

            String resourceId = rawResourcePath;
            ResourceIdParts idParts = InventoryIdUtil.parseResourceId(resourceId);

            String managedServerName = idParts.getManagedServerName();
            boolean isDmr = context.getAgentCoreEngine()
                    .getProtocolServices()
                    .getDmrProtocolService()
                    .getEndpointServices()
                    .containsKey(managedServerName);
            if (isDmr) {
                return getDMRCommand().execute(envelope, context);
            } else {
                boolean isJmx = context.getAgentCoreEngine()
                        .getProtocolServices()
                        .getJmxProtocolService()
                        .getEndpointServices()
                        .containsKey(managedServerName);
                if (isJmx) {
                    return getJMXCommand().execute(envelope, context);
                }
            }

            throw new IllegalArgumentException(String.format(
                    "Cannot perform [%s] on a [%s] given by inventory path [%s]: unknown managed server [%s]",
                    this.getOperationName(envelope), this.getEntityType(envelope), idParts.getIdPart(),
                    managedServerName));

        } catch (Throwable t) {
            RESP response = createResponse();
            MessageUtils.prepareResourceResponse(request, response);
            response.setStatus(ResponseStatus.ERROR);
            String formattedTimestamp = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mmX").withZone(ZoneOffset.UTC)
                    .format(Instant.ofEpochMilli(timestampBeforeExecution));

            String msg = String.format(
                    "Could not perform [%s] on a [%s] given by inventory path [%s] requested on [%s]: %s",
                    this.getOperationName(envelope), this.getEntityType(envelope), rawResourcePath, formattedTimestamp,
                    t.toString());
            response.setMessage(msg);
            log.debug(msg, t);
            return new BasicMessageWithExtraData<>(response, null);
        }
    }

    @Override
    protected void validate(BasicMessageWithExtraData<REQ> envelope,
            MonitoredEndpoint<? extends AbstractEndpointConfiguration> endpoint) {
        throw new UnsupportedOperationException("This is not to be used - use one of the DMR or JMX command objects");
    }

    /**
     * Subclasses must provide a DMR command object via this method.
     * This command will be used when a DMR resource is to be targeted by the command.
     * @return DMR command instance
     */
    protected abstract AbstractDMRResourcePathCommand<REQ, RESP> getDMRCommand();

    /**
     * Subclasses must provide a JMX command object via this method.
     * This command will be used when a JMX resource is to be targeted by the command.
     * @return JMX command instance
     */
    protected abstract AbstractJMXResourcePathCommand<REQ, RESP> getJMXCommand();
}