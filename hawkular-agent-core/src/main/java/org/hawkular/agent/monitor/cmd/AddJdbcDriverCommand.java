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

import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;

import org.hawkular.agent.monitor.config.AgentCoreEngineConfiguration.AbstractEndpointConfiguration;
import org.hawkular.agent.monitor.inventory.MonitoredEndpoint;
import org.hawkular.agent.monitor.protocol.EndpointService;
import org.hawkular.agent.monitor.protocol.dmr.DMRNodeLocation;
import org.hawkular.agent.monitor.protocol.dmr.DMRSession;
import org.hawkular.bus.common.BasicMessageWithExtraData;
import org.hawkular.bus.common.BinaryData;
import org.hawkular.cmdgw.api.AddJdbcDriverRequest;
import org.hawkular.cmdgw.api.AddJdbcDriverResponse;
import org.hawkular.cmdgw.api.ResponseStatus;
import org.hawkular.dmr.api.OperationBuilder;
import org.hawkular.dmr.api.OperationBuilder.OperationResult;
import org.hawkular.dmr.api.SubsystemDatasourceConstants;
import org.hawkular.dmr.api.SubsystemDatasourceConstants.JdbcDriverNodeConstants;
import org.hawkular.dmrclient.modules.AddModuleRequest;
import org.hawkular.dmrclient.modules.AddModuleRequest.ModuleResource;
import org.hawkular.dmrclient.modules.Modules;
import org.jboss.as.controller.client.ModelControllerClient;

/**
 * Adds an JdbcDriver to an Application Server instance.
 */
public class AddJdbcDriverCommand extends AbstractDMRResourcePathCommand<AddJdbcDriverRequest, AddJdbcDriverResponse>
        implements SubsystemDatasourceConstants, JdbcDriverNodeConstants {
    public static final Set<String> DEFAULT_DRIVER_MODULE_DEPENDENCIES = Collections
            .unmodifiableSet(new LinkedHashSet<>(Arrays.asList("javax.api", "javax.transaction.api")));

    public static final Class<AddJdbcDriverRequest> REQUEST_CLASS = AddJdbcDriverRequest.class;

    public AddJdbcDriverCommand() {
        super("Add", "JDBC Driver");
    }

    /** @see org.hawkular.agent.monitor.cmd.AbstractDMRResourcePathCommand#createResponse() */
    @Override
    protected AddJdbcDriverResponse createResponse() {
        return new AddJdbcDriverResponse();
    }

    /**
     * @see org.hawkular.agent.monitor.cmd.AbstractDMRResourcePathCommand#execute(org.hawkular.dmrclient.JBossASClient,
     *      EndpointService, java.lang.String,
     *      org.hawkular.cmdgw.api.ResourcePathRequest, org.hawkular.cmdgw.api.ResourcePathResponse,
     *      org.hawkular.agent.monitor.cmd.CommandContext, DMRSession)
     */
    @Override
    protected BinaryData execute(ModelControllerClient controllerClient,
            EndpointService<DMRNodeLocation, DMRSession> endpointService,
            String modelNodePath,
            BasicMessageWithExtraData<AddJdbcDriverRequest> envelope, AddJdbcDriverResponse response,
            CommandContext context, DMRSession dmrContext) throws Exception {
        AddJdbcDriverRequest request = envelope.getBasicMessage();
        response.setDriverName(request.getDriverName());

        ModuleResource jarResource = new ModuleResource(envelope.getBinaryData(),
                request.getDriverJarName());

        AddModuleRequest addModuleRequest = new AddModuleRequest(request.getModuleName(), (String) null, (String) null,
                Collections.singleton(jarResource), DEFAULT_DRIVER_MODULE_DEPENDENCIES, null);
        new Modules(Modules.findModulesDir()).add(addModuleRequest);

        OperationResult<?> opResult = OperationBuilder.add()
                .address().subsystemDatasources().segment(JDBC_DRIVER, request.getDriverName()).parentBuilder()
                .attribute(JdbcDriverNodeConstants.DRIVER_NAME, request.getDriverName())
                .attribute(JdbcDriverNodeConstants.DRIVER_MODULE_NAME, request.getModuleName())
                .attribute(JdbcDriverNodeConstants.DRIVER_CLASS_NAME, request.getDriverClass())
                .attribute(JdbcDriverNodeConstants.DRIVER_MAJOR_VERSION, request.getDriverMajorVersion())
                .attribute(JdbcDriverNodeConstants.DRIVER_MINOR_VERSION, request.getDriverMinorVersion())
                .attribute(JdbcDriverNodeConstants.DRIVER_XA_DATASOURCE_CLASS_NAME,
                        request.getDriverXaDatasourceClassName())
                .attribute(JdbcDriverNodeConstants.JDBC_COMPLIANT, request.getJdbcCompliant())
                .execute(controllerClient)
                .assertSuccess();
        setServerRefreshIndicator(opResult, response);

        endpointService.discoverAll();

        return null;
    }

    @Override
    protected void success(BasicMessageWithExtraData<AddJdbcDriverRequest> envelope, AddJdbcDriverResponse response) {
        response.setStatus(ResponseStatus.OK);
        response.setMessage(String.format("Added JDBC Driver: %s", envelope.getBasicMessage().getDriverName()));
    }

    @Override
    protected void validate(String modelNodePath, BasicMessageWithExtraData<AddJdbcDriverRequest> envelope) {
    }

    @Override
    protected void validate(BasicMessageWithExtraData<AddJdbcDriverRequest> envelope,
            MonitoredEndpoint<? extends AbstractEndpointConfiguration> endpoint) {
        assertLocalDMRServer(endpoint);
    }

}
