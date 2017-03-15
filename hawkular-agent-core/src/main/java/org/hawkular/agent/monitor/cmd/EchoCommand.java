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
import org.hawkular.cmdgw.api.EchoRequest;
import org.hawkular.cmdgw.api.EchoResponse;

public class EchoCommand implements Command<EchoRequest, EchoResponse> {
    public static final Class<EchoRequest> REQUEST_CLASS = EchoRequest.class;

    @Override
    public BasicMessageWithExtraData<EchoResponse> execute(BasicMessageWithExtraData<EchoRequest> envelope,
            CommandContext context) {
        String reply = String.format("WildFly Monitor Agent Echo: [%s]", envelope.getBasicMessage().getEchoMessage());

        // return the response
        EchoResponse echoResponse = new EchoResponse();
        echoResponse.setReply(reply);
        return new BasicMessageWithExtraData<>(echoResponse, envelope.getBinaryData());
    }
}
