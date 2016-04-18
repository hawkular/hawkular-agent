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

import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;

import org.hawkular.agent.monitor.extension.MonitorServiceConfiguration.AbstractEndpointConfiguration;
import org.hawkular.agent.monitor.inventory.MonitoredEndpoint;
import org.hawkular.agent.monitor.log.AgentLoggers;
import org.hawkular.agent.monitor.log.MsgLogger;
import org.hawkular.agent.monitor.protocol.EndpointService;
import org.hawkular.agent.monitor.protocol.dmr.DMRNodeLocation;
import org.hawkular.agent.monitor.protocol.dmr.DMRSession;
import org.hawkular.bus.common.BasicMessageWithExtraData;
import org.hawkular.bus.common.BinaryData;
import org.hawkular.cmdgw.api.UpdateDatasourceRequest;
import org.hawkular.cmdgw.api.UpdateDatasourceResponse;
import org.hawkular.dmr.api.DmrApiException;
import org.hawkular.dmr.api.OperationBuilder;
import org.hawkular.dmr.api.OperationBuilder.CompositeOperationBuilder;
import org.hawkular.dmr.api.OperationBuilder.OperationResult;
import org.hawkular.dmr.api.SubsystemDatasourceConstants;
import org.jboss.as.controller.client.ModelControllerClient;
import org.jboss.as.controller.descriptions.ModelDescriptionConstants;
import org.jboss.dmr.ModelNode;
import org.jboss.dmr.Property;

/**
 * Updates a Datasource to an Application Server instance.
 */
public class UpdateDatasourceCommand
        extends AbstractResourcePathCommand<UpdateDatasourceRequest, UpdateDatasourceResponse>
        implements SubsystemDatasourceConstants, SubsystemDatasourceConstants.DatasourceNodeConstants,
        SubsystemDatasourceConstants.XaDatasourceNodeConstants {
    private static final MsgLogger log = AgentLoggers.getLogger(UpdateDatasourceCommand.class);
    public static final Class<UpdateDatasourceRequest> REQUEST_CLASS = UpdateDatasourceRequest.class;

    public UpdateDatasourceCommand() {
        super("Update", "Datasource");
    }

    /** @see org.hawkular.agent.monitor.cmd.AbstractResourcePathCommand#createResponse() */
    @Override
    protected UpdateDatasourceResponse createResponse() {
        return new UpdateDatasourceResponse();
    }

    @Override
    protected BinaryData execute(
            ModelControllerClient controllerClient,
            EndpointService<DMRNodeLocation, DMRSession> endpointService,
            String modelNodePath,
            BasicMessageWithExtraData<UpdateDatasourceRequest> envelope,
            UpdateDatasourceResponse response,
            CommandContext context,
            DMRSession dmrContext) throws Exception {

        UpdateDatasourceRequest request = envelope.getBasicMessage();

        ModelNode adr = OperationBuilder.address().segments(modelNodePath).build();

        assertNotRename(adr, request.getDatasourceName());

        OperationResult<?> opResult;
        final CompositeOperationBuilder<?> batch;
        final boolean isXaDatasource = isXa(adr);
        /* there is also request.isXaDatasource() that we ignore here */
        if (isXaDatasource) {

            batch = OperationBuilder.composite() //
                    .writeAttribute().address(adr).attribute(JNDI_NAME, request.getJndiName()).parentBuilder() //
                    .writeAttribute().address(adr).attribute(DRIVER_NAME, request.getDriverName()).parentBuilder() //
                    .writeAttribute().address(adr).attribute(XA_DATASOURCE_CLASS, request.getXaDataSourceClass())
                    .parentBuilder() //
                    .writeAttribute().address(adr).attribute(USER_NAME, request.getUserName()).parentBuilder() //
                    .writeAttribute().address(adr).attribute(PASSWORD, request.getPassword()).parentBuilder() //
                    .writeAttribute().address(adr).attribute(SECURITY_DOMAIN, request.getConnectionUrl())
                    .parentBuilder();

            syncProps(controllerClient, adr, XA_DATASOURCE_PROPERTIES, request.getDatasourceProperties(), batch,
                    isXaDatasource);
            try {
                opResult = batch.execute(controllerClient).assertSuccess();
            } catch (DmrApiException e) {
                /* A workaround for https://issues.jboss.org/browse/WFLY-5527 */
                log.warn("Update attempt #2 for xa-datasource-properties, see JIRA WFLY-5527");
                opResult = batch.execute(controllerClient).assertSuccess();
            }
        } else {

            batch = OperationBuilder.composite() //
                    .writeAttribute().address(adr).attribute(JNDI_NAME, request.getJndiName()).parentBuilder() //
                    .writeAttribute().address(adr).attribute(DRIVER_NAME, request.getDriverName()).parentBuilder() //
                    .writeAttribute().address(adr).attribute(DRIVER_CLASS, request.getDriverClass()).parentBuilder() //
                    .writeAttribute().address(adr).attribute(CONNECTION_URL, request.getConnectionUrl())
                    .parentBuilder()
                    .writeAttribute().address(adr).attribute(USER_NAME, request.getUserName()).parentBuilder() //
                    .writeAttribute().address(adr).attribute(PASSWORD, request.getPassword()).parentBuilder() //
                    ;
            syncProps(controllerClient, adr, CONNECTION_PROPERTIES, request.getDatasourceProperties(), batch,
                    isXaDatasource);
            opResult = batch.execute(controllerClient).assertSuccess();
        }

        setServerRefreshIndicator(opResult, response);

        endpointService.discoverAll();
        return null;

    }

    /**
     * @param adr
     * @return
     */
    private boolean isXa(ModelNode adr) {
        List<Property> props = adr.asPropertyList();
        Property lastProp = props.get(props.size() - 1);
        switch (lastProp.getName()) {
            case XA_DATASOURCE:
                return true;
            case DATASOURCE:
                return false;
            default:
                String msg = String.format(
                        "DMR Address [%s] was supposed to be either a [%s] or [%s] address, which it is not",
                        adr.toString(), XA_DATASOURCE, DATASOURCE);
                throw new IllegalStateException(msg);
        }
    }

    private void syncProps(ModelControllerClient client, ModelNode parentAddress, String propType,
            Map<String, String> newProps, CompositeOperationBuilder<?> batch, boolean isXaDatasource) {

        List<ModelNode> availableProps = OperationBuilder.readChildrenResources().address(parentAddress)
                .childType(propType).execute(client).assertSuccess().getNodeList();
        if (newProps == null) {
            newProps = Collections.emptyMap();
        }

        Set<String> updatedPropNames = new HashSet<>();

        /* We must remove the keys and then re-create them, see https://issues.jboss.org/browse/AS7-6302 */
        for (ModelNode avialNode : availableProps) {
            Property prop = avialNode.asProperty();
            String availPropName = prop.getName();
            String availPropValue = prop.getValue().get(ModelDescriptionConstants.VALUE).asString();
            String newVal = newProps.get(availPropName);
            if (newVal == null) {
                /* no new val -> remove */
                // if (isXaDatasource) {
                // /* broken for XA */
                // throw new IllegalStateException("Cannot remove [" + propType + "=" + availPropName + "]");
                // } else {
                batch.remove() //
                        .address().segments(parentAddress).segment(propType, availPropName).parentBuilder() //
                        .parentBuilder();
                // }
            } else if (!newVal.equals(availPropValue)) {
                log.tracef("Modification of [%s=%s]: ![%s].equals([%s])", propType, availPropName, newVal,
                        availPropValue);
                /* a modification: remove old and add the new value */
                // if (isXaDatasource) {
                // /* broken for XA */
                // throw new IllegalStateException("Cannot modify [" + propType + "=" + availPropName + "]");
                // } else {
                batch.remove() //
                        .address().segments(parentAddress).segment(propType, availPropName).parentBuilder() //
                        .parentBuilder();

                batch.add() //
                        .address().segments(parentAddress).segment(propType, availPropName).parentBuilder() //
                        .valueAttribute(newVal).parentBuilder();
                // }

                updatedPropNames.add(availPropName);
            } else {
                /* no change */
                log.tracef("No change for [%s=%s]: [%s].equals([%s])", propType, availPropName, newVal,
                        availPropValue);
                updatedPropNames.add(availPropName);
            }
        }
        for (Entry<String, String> newProp : newProps.entrySet()) {
            String newPropName = newProp.getKey();
            if (!updatedPropNames.contains(newPropName)) {
                /* add key not available yet */
                batch.add() //
                        .address().segments(parentAddress).segment(propType, newPropName).parentBuilder() //
                        .valueAttribute(newProp.getValue()).parentBuilder();
            }
        }
    }

    @Override
    protected void validate(String modelNodePath, BasicMessageWithExtraData<UpdateDatasourceRequest> envelope) {
    }

    @Override
    protected void validate(BasicMessageWithExtraData<UpdateDatasourceRequest> envelope,
            MonitoredEndpoint<? extends AbstractEndpointConfiguration> endpoint) {
    }

}
