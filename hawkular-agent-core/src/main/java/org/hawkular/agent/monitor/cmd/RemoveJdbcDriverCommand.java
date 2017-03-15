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

import org.hawkular.agent.monitor.config.AgentCoreEngineConfiguration.AbstractEndpointConfiguration;
import org.hawkular.agent.monitor.inventory.MonitoredEndpoint;
import org.hawkular.bus.common.BasicMessageWithExtraData;
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

    @Override
    protected RemoveJdbcDriverResponse createResponse() {
        return new RemoveJdbcDriverResponse();
    }

    @Override
    protected void validate(String modelNodePath, BasicMessageWithExtraData<RemoveJdbcDriverRequest> envelope) {
        DatasourceJBossASClient.checkJdbcDriverPath(modelNodePath);
    }

    @Override
    protected void validate(BasicMessageWithExtraData<RemoveJdbcDriverRequest> envelope,
            MonitoredEndpoint<? extends AbstractEndpointConfiguration> endpoint) {
        assertLocalDMRServer(endpoint);
    }
}
