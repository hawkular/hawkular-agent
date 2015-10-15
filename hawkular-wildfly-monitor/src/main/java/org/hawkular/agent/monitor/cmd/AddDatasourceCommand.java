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
package org.hawkular.agent.monitor.cmd;

import java.util.Map;
import java.util.Map.Entry;

import org.hawkular.agent.monitor.inventory.ManagedServer;
import org.hawkular.bus.common.BasicMessageWithExtraData;
import org.hawkular.cmdgw.api.AddDatasourceRequest;
import org.hawkular.cmdgw.api.AddDatasourceResponse;
import org.hawkular.cmdgw.api.ResponseStatus;
import org.hawkular.dmr.api.OperationBuilder;
import org.hawkular.dmr.api.OperationBuilder.CompositeOperationBuilder;
import org.hawkular.dmr.api.SubsystemDatasourceConstants;
import org.hawkular.dmr.api.SubsystemDatasourceConstants.DatasourceNodeConstants;
import org.hawkular.dmr.api.SubsystemDatasourceConstants.XaDatasourceNodeConstants;
import org.jboss.as.controller.client.ModelControllerClient;
import org.jboss.as.controller.descriptions.ModelDescriptionConstants;
import org.jboss.dmr.ModelNode;

/**
 * Adds a Datasource to an Application Server instance.
 */
public class AddDatasourceCommand extends AbstractResourcePathCommand<AddDatasourceRequest, AddDatasourceResponse>
        implements DatasourceNodeConstants, XaDatasourceNodeConstants, SubsystemDatasourceConstants {

    public static final Class<AddDatasourceRequest> REQUEST_CLASS = AddDatasourceRequest.class;

    public AddDatasourceCommand() {
        super("Add", "Datasource");
    }

    /** @see org.hawkular.agent.monitor.cmd.AbstractResourcePathCommand#createResponse() */
    @Override
    protected AddDatasourceResponse createResponse() {
        return new AddDatasourceResponse();
    }

    /**
     * @see org.hawkular.agent.monitor.cmd.AbstractResourcePathCommand#execute(org.hawkular.dmrclient.JBossASClient,
     *      org.hawkular.agent.monitor.inventory.ManagedServer, java.lang.String,
     *      org.hawkular.cmdgw.api.ResourcePathRequest, org.hawkular.cmdgw.api.ResourcePathResponse,
     *      org.hawkular.agent.monitor.cmd.CommandContext)
     */
    @Override
    protected void execute(ModelControllerClient controllerClient, ManagedServer managedServer, String modelNodePath,
            BasicMessageWithExtraData<AddDatasourceRequest> envelope, AddDatasourceResponse response,
            CommandContext context) throws Exception {
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
                    .parentBuilder();

        }
        if (!props.isEmpty()) {
            for (Entry<String, String> prop : props.entrySet()) {
                batch.add() //
                        .address().segments(dsAdr).segment(dsPropsDmrResourceType, prop.getKey()).parentBuilder() //
                        .attribute(ModelDescriptionConstants.VALUE, prop.getValue()).parentBuilder();
            }
        }

        batch.execute(controllerClient).assertSuccess();
        context.getDiscoveryService().discoverAllResourcesForAllManagedServers();

    }

    @Override
    protected void success(BasicMessageWithExtraData<AddDatasourceRequest> envelope, AddDatasourceResponse response) {
        response.setStatus(ResponseStatus.OK);
        response.setMessage(String.format("Added Datasource: %s", envelope.getBasicMessage().getDatasourceName()));
    }

    @Override
    protected void validate(BasicMessageWithExtraData<AddDatasourceRequest> envelope, String managedServerName,
            ManagedServer managedServer) {
        super.validate(envelope, managedServerName, managedServer);
        assertLocalOrRemoteServer(managedServer);
    }

    /**
     * @see org.hawkular.agent.monitor.cmd.AbstractResourcePathCommand#validate(java.lang.String,
     *      org.hawkular.cmdgw.api.ResourcePathRequest)
     */
    @Override
    protected void validate(String modelNodePath, BasicMessageWithExtraData<AddDatasourceRequest> envelope) {
    }

}
