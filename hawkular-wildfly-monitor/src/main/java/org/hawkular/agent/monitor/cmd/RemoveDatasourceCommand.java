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

import org.hawkular.agent.monitor.protocol.dmr.DMREndpoint;
import org.hawkular.bus.common.BasicMessageWithExtraData;
import org.hawkular.cmdgw.api.RemoveDatasourceRequest;
import org.hawkular.cmdgw.api.RemoveDatasourceResponse;
import org.hawkular.dmrclient.DatasourceJBossASClient;

/**
 * @author <a href="https://github.com/ppalaga">Peter Palaga</a>
 */
public class RemoveDatasourceCommand
        extends AbstractRemoveModelNodeCommand<RemoveDatasourceRequest, RemoveDatasourceResponse> {

    private static final String DATASOURCE_ENTITY_TYPE = "Datasource";
    public static final Class<RemoveDatasourceRequest> REQUEST_CLASS = RemoveDatasourceRequest.class;

    public RemoveDatasourceCommand() {
        super(DATASOURCE_ENTITY_TYPE);
    }

    @Override
    protected RemoveDatasourceResponse createResponse() {
        return new RemoveDatasourceResponse();
    }

    @Override
    protected void validate(String modelNodePath, BasicMessageWithExtraData<RemoveDatasourceRequest> envelope) {
        DatasourceJBossASClient.checkDatasourcePath(modelNodePath);
    }

    @Override
    protected void validate(BasicMessageWithExtraData<RemoveDatasourceRequest> envelope, DMREndpoint dmrEndpoint) {
    }


}
