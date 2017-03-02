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

import java.util.Map;
import java.util.Map.Entry;

import org.hawkular.agent.monitor.config.AgentCoreEngineConfiguration.AbstractEndpointConfiguration;
import org.hawkular.agent.monitor.inventory.MonitoredEndpoint;
import org.hawkular.agent.monitor.protocol.EndpointService;
import org.hawkular.agent.monitor.protocol.dmr.DMRNodeLocation;
import org.hawkular.agent.monitor.protocol.dmr.DMRSession;
import org.hawkular.bus.common.BasicMessageWithExtraData;
import org.hawkular.bus.common.BinaryData;
import org.hawkular.cmdgw.api.AddDatasourceRequest;
import org.hawkular.cmdgw.api.AddDatasourceResponse;
import org.hawkular.cmdgw.api.ResponseStatus;
import org.hawkular.dmr.api.OperationBuilder;
import org.hawkular.dmr.api.OperationBuilder.CompositeOperationBuilder;
import org.hawkular.dmr.api.OperationBuilder.OperationResult;
import org.hawkular.dmr.api.SubsystemDatasourceConstants;
import org.hawkular.dmr.api.SubsystemDatasourceConstants.DatasourceNodeConstants;
import org.hawkular.dmr.api.SubsystemDatasourceConstants.XaDatasourceNodeConstants;
import org.jboss.as.controller.client.ModelControllerClient;
import org.jboss.as.controller.descriptions.ModelDescriptionConstants;
import org.jboss.dmr.ModelNode;

/**
 * Adds a Datasource to an Application Server instance.
 */
public class AddDatasourceCommand extends AbstractDMRResourcePathCommand<AddDatasourceRequest, AddDatasourceResponse>
        implements DatasourceNodeConstants, XaDatasourceNodeConstants, SubsystemDatasourceConstants {

    public static final Class<AddDatasourceRequest> REQUEST_CLASS = AddDatasourceRequest.class;

    public AddDatasourceCommand() {
        super("Add", "Datasource");
    }

    @Override
    protected AddDatasourceResponse createResponse() {
        return new AddDatasourceResponse();
    }

    @Override
    protected BinaryData execute(
            ModelControllerClient controllerClient,
            EndpointService<DMRNodeLocation, DMRSession> endpointService,
            String modelNodePath,
            BasicMessageWithExtraData<AddDatasourceRequest> envelope,
            AddDatasourceResponse response,
            CommandContext context,
            DMRSession dmrContext) throws Exception {
        AddDatasourceRequest request = envelope.getBasicMessage();
        response.setDatasourceName(request.getDatasourceName());
        response.setXaDatasource(request.isXaDatasource());

        Map<String, String> props = request.getDatasourceProperties();
        final String dsDmrResourceType;
        final String dsPropsDmrResourceType;
        final ModelNode dsAdr;
        final CompositeOperationBuilder<?> batch;
        if (request.isXaDatasource()) {
            dsDmrResourceType = XA_DATASOURCE;
            dsPropsDmrResourceType = XA_DATASOURCE_PROPERTIES;
            dsAdr = OperationBuilder.address().subsystemDatasources()
                    .segment(dsDmrResourceType, request.getDatasourceName()).build();

            batch = OperationBuilder.composite() //
                    .add() //
                    .address(dsAdr) //
                    .attribute(JNDI_NAME, request.getJndiName()) //
                    .attribute(DRIVER_NAME, request.getDriverName()) //
                    .attribute(XA_DATASOURCE_CLASS, request.getXaDataSourceClass()) //
                    .attribute(USER_NAME, request.getUserName()) //
                    .attribute(PASSWORD, request.getPassword()) //
                    .attribute(STATISTICS_ENABLED, true) //
                    .parentBuilder();
        } else {
            dsDmrResourceType = DATASOURCE;
            dsPropsDmrResourceType = CONNECTION_PROPERTIES;
            dsAdr = OperationBuilder.address().subsystemDatasources()
                    .segment(dsDmrResourceType, request.getDatasourceName()).build();

            batch = OperationBuilder.composite() //
                    .add() //
                    .address(dsAdr) //
                    .attribute(JNDI_NAME, request.getJndiName()) //
                    .attribute(DRIVER_NAME, request.getDriverName()) //
                    .attribute(DRIVER_CLASS, request.getDriverClass()) //
                    .attribute(CONNECTION_URL, request.getConnectionUrl()) //
                    .attribute(USER_NAME, request.getUserName()) //
                    .attribute(PASSWORD, request.getPassword()) //
                    .attribute(STATISTICS_ENABLED, true) //
                    .parentBuilder();

        }
        if (props!=null && !props.isEmpty()) {
            for (Entry<String, String> prop : props.entrySet()) {
                batch.add() //
                        .address().segments(dsAdr).segment(dsPropsDmrResourceType, prop.getKey()).parentBuilder() //
                        .attribute(ModelDescriptionConstants.VALUE, prop.getValue()).parentBuilder();
            }
        }

        OperationResult<?> opResult = batch.execute(controllerClient).assertSuccess();
        setServerRefreshIndicator(opResult, response);

        // discover the new datasource so it gets placed into inventory
        endpointService.discoverAll();

        return null;
    }

    @Override
    protected void success(BasicMessageWithExtraData<AddDatasourceRequest> envelope, AddDatasourceResponse response) {
        response.setStatus(ResponseStatus.OK);
        response.setMessage(String.format("Added Datasource: %s", envelope.getBasicMessage().getDatasourceName()));
    }

    @Override
    protected void validate(BasicMessageWithExtraData<AddDatasourceRequest> envelope,
            MonitoredEndpoint<? extends AbstractEndpointConfiguration> endpoint) {
    }

    @Override
    protected void validate(String modelNodePath, BasicMessageWithExtraData<AddDatasourceRequest> envelope) {
    }

}
