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

import org.hawkular.bus.common.BasicMessageWithExtraData;
import org.hawkular.cmdgw.api.ExecuteOperationRequest;
import org.hawkular.cmdgw.api.ExecuteOperationResponse;

/**
 * Execute an operation on a resource which can be either a DMR or JMX resource.
 */
public class ExecuteAgnosticOperationCommand extends
        AbstractAgnosticResourcePathCommand<ExecuteOperationRequest, ExecuteOperationResponse> {
    public static final Class<ExecuteOperationRequest> REQUEST_CLASS = ExecuteOperationRequest.class;

    public ExecuteAgnosticOperationCommand() {
        super("Execute Operation", "Any Resource");
    }

    @Override
    protected ExecuteOperationResponse createResponse() {
        return new ExecuteOperationResponse();
    }

    @Override
    protected AbstractDMRResourcePathCommand<ExecuteOperationRequest, ExecuteOperationResponse> getDMRCommand() {
        return new ExecuteDMROperationCommand();
    }

    @Override
    protected AbstractJMXResourcePathCommand<ExecuteOperationRequest, ExecuteOperationResponse> getJMXCommand() {
        return new ExecuteJMXOperationCommand();
    }

    @Override
    protected String getOperationName(BasicMessageWithExtraData<ExecuteOperationRequest> envelope) {
        return envelope.getBasicMessage().getOperationName();
    }
}
