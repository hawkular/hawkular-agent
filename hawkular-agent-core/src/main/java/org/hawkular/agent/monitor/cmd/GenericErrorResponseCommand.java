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

import org.hawkular.agent.monitor.log.AgentLoggers;
import org.hawkular.agent.monitor.log.MsgLogger;
import org.hawkular.bus.common.BasicMessage;
import org.hawkular.bus.common.BasicMessageWithExtraData;
import org.hawkular.cmdgw.api.GenericErrorResponse;

/**
 * The server is responding to our agent with some generic error message, probably due to some error condition that
 * caused the server not be able to service one of our requests.
 */
public class GenericErrorResponseCommand implements Command<GenericErrorResponse, BasicMessage> {
    private static final MsgLogger log = AgentLoggers.getLogger(GenericErrorResponseCommand.class);
    public static final Class<GenericErrorResponse> REQUEST_CLASS = GenericErrorResponse.class;

    @Override
    public BasicMessageWithExtraData<BasicMessage> execute(BasicMessageWithExtraData<GenericErrorResponse> envelope,
            CommandContext context) throws Exception {
        GenericErrorResponse errorResponse = envelope.getBasicMessage();
        String errorMessage = errorResponse.getErrorMessage();
        String stackTrace = errorResponse.getStackTrace();

        log.warnReceivedGenericErrorResponse(errorMessage, stackTrace);

        return null; // nothing to send back
    }
}
