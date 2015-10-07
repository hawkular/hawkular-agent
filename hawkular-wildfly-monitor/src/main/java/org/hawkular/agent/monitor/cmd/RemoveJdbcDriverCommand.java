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

import org.hawkular.agent.monitor.inventory.ManagedServer;
import org.hawkular.agent.monitor.inventory.dmr.LocalDMRManagedServer;
import org.hawkular.cmdgw.api.RemoveJdbcDriverRequest;
import org.hawkular.cmdgw.api.RemoveJdbcDriverResponse;
import org.hawkular.dmrclient.DatasourceJBossASClient;

/**
 * @author <a href="https://github.com/ppalaga">Peter Palaga</a>
 */
public class RemoveJdbcDriverCommand
        extends AbstractRemoveModelNodeCommand<RemoveJdbcDriverRequest, RemoveJdbcDriverResponse> {

    private static final String JDBC_DRIVER_ENTITY_TYPE = "JDBC Driver";
    public static final Class<RemoveJdbcDriverRequest> REQUEST_CLASS = RemoveJdbcDriverRequest.class;

    public RemoveJdbcDriverCommand() {
        super(JDBC_DRIVER_ENTITY_TYPE);
    }

    /** @see org.hawkular.agent.monitor.cmd.AbstractRemoveModelNodeCommand#createResponse() */
    @Override
    protected RemoveJdbcDriverResponse createResponse() {
        return new RemoveJdbcDriverResponse();
    }

    @Override
    protected void validate(String modelNodePath, RemoveJdbcDriverRequest request) {
        DatasourceJBossASClient.checkJdbcDriverPath(modelNodePath);
    }

    @Override
    protected void validate(RemoveJdbcDriverRequest request, String managedServerName, ManagedServer managedServer) {
        super.validate(request, managedServerName, managedServer);
        if (!(managedServer instanceof LocalDMRManagedServer)) {
            throw new IllegalStateException(String.format("Cannot remove [%s] from [%s]. Only [%s] is supported",
                    entityType, managedServer.getClass().getName(), LocalDMRManagedServer.class.getName()));
        }
    }

}
