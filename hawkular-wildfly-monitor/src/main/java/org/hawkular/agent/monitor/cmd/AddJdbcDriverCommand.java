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

import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;

import org.hawkular.agent.monitor.inventory.ManagedServer;
import org.hawkular.bus.common.BasicMessageWithExtraData;
import org.hawkular.cmdgw.api.AddJdbcDriverRequest;
import org.hawkular.cmdgw.api.AddJdbcDriverResponse;
import org.hawkular.cmdgw.api.ResponseStatus;
import org.hawkular.dmr.api.OperationBuilder;
import org.hawkular.dmr.api.SubsystemDatasourceConstants;
import org.hawkular.dmr.api.SubsystemDatasourceConstants.JdbcDriverNodeConstants;
import org.hawkular.dmrclient.modules.AddModuleRequest;
import org.hawkular.dmrclient.modules.AddModuleRequest.ModuleResource;
import org.hawkular.dmrclient.modules.Modules;
import org.jboss.as.controller.client.ModelControllerClient;

/**
 * Adds an JdbcDriver to an Application Server instance.
 */
public class AddJdbcDriverCommand extends AbstractResourcePathCommand<AddJdbcDriverRequest, AddJdbcDriverResponse>
        implements SubsystemDatasourceConstants, JdbcDriverNodeConstants {
    public static final Set<String> DEFAULT_DRIVER_MODULE_DEPENDENCIES = Collections
            .unmodifiableSet(new LinkedHashSet<>(Arrays.asList("javax.api", "javax.transaction.api")));

    public static final Class<AddJdbcDriverRequest> REQUEST_CLASS = AddJdbcDriverRequest.class;

    public AddJdbcDriverCommand() {
        super("Add", "JDBC Driver");
    }

    /** @see org.hawkular.agent.monitor.cmd.AbstractResourcePathCommand#createResponse() */
    @Override
    protected AddJdbcDriverResponse createResponse() {
        return new AddJdbcDriverResponse();
    }

    /**
     * @see org.hawkular.agent.monitor.cmd.AbstractResourcePathCommand#execute(org.hawkular.dmrclient.JBossASClient,
     *      org.hawkular.agent.monitor.inventory.ManagedServer, java.lang.String,
     *      org.hawkular.cmdgw.api.ResourcePathRequest, org.hawkular.cmdgw.api.ResourcePathResponse,
     *      org.hawkular.agent.monitor.cmd.CommandContext)
     */
    @Override
    protected void execute(ModelControllerClient controllerClient, ManagedServer managedServer, String modelNodePath,
            BasicMessageWithExtraData<AddJdbcDriverRequest> envelope, AddJdbcDriverResponse response,
            CommandContext context) throws Exception {
        AddJdbcDriverRequest request = envelope.getBasicMessage();
        response.setDriverName(request.getDriverName());

        ModuleResource jarResource = new AddModuleRequest.ModuleResource(envelope.getBinaryData(),
                request.getDriverJarName());

        AddModuleRequest addModuleRequest = new AddModuleRequest(request.getModuleName(), (String) null, (String) null,
                Collections.singleton(jarResource), DEFAULT_DRIVER_MODULE_DEPENDENCIES, null);
        new Modules(Modules.findModulesDir()).add(addModuleRequest);

        OperationBuilder.add() //
                .address().subsystemDatasources().segment(JDBC_DRIVER, request.getDriverName()).parentBuilder()
                .attribute(JdbcDriverNodeConstants.DRIVER_NAME, request.getDriverName())
                .attribute(JdbcDriverNodeConstants.DRIVER_MODULE_NAME, request.getModuleName())
                .attribute(JdbcDriverNodeConstants.DRIVER_CLASS_NAME, request.getDriverClass())
                .attribute(JdbcDriverNodeConstants.DRIVER_MAJOR_VERSION, request.getDriverMajorVersion())
                .attribute(JdbcDriverNodeConstants.DRIVER_MINOR_VERSION, request.getDriverMinorVersion())
                .execute(controllerClient) //
                .assertSuccess();
    }

    @Override
    protected void success(BasicMessageWithExtraData<AddJdbcDriverRequest> envelope, AddJdbcDriverResponse response) {
        response.setStatus(ResponseStatus.OK);
        response.setMessage(String.format("Added JDBC Driver: %s", envelope.getBasicMessage().getDriverName()));
    }

    /**
     * @see org.hawkular.agent.monitor.cmd.AbstractResourcePathCommand#validate(java.lang.String,
     *      org.hawkular.cmdgw.api.ResourcePathRequest)
     */
    @Override
    protected void validate(String modelNodePath, BasicMessageWithExtraData<AddJdbcDriverRequest> envelope) {
    }

}
