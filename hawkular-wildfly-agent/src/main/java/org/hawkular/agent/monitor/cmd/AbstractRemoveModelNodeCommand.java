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

import org.hawkular.agent.monitor.log.AgentLoggers;
import org.hawkular.agent.monitor.log.MsgLogger;
import org.hawkular.agent.monitor.protocol.EndpointService;
import org.hawkular.agent.monitor.protocol.dmr.DMRNodeLocation;
import org.hawkular.agent.monitor.protocol.dmr.DMRSession;
import org.hawkular.bus.common.BasicMessageWithExtraData;
import org.hawkular.bus.common.BinaryData;
import org.hawkular.cmdgw.api.ResourcePathRequest;
import org.hawkular.cmdgw.api.ResourcePathResponse;
import org.hawkular.dmr.api.DmrApiException;
import org.hawkular.dmr.api.OperationBuilder;
import org.jboss.as.controller.client.ModelControllerClient;

/**
 * A base for {@link Command}s removing nodes from the DMR tree.
 *
 * @author <a href="https://github.com/ppalaga">Peter Palaga</a>
 */
public abstract class AbstractRemoveModelNodeCommand<REQ extends ResourcePathRequest, RESP extends ResourcePathResponse>
        extends AbstractResourcePathCommand<REQ, RESP> {
    private static final MsgLogger log = AgentLoggers.getLogger(AbstractRemoveModelNodeCommand.class);

    public AbstractRemoveModelNodeCommand(String entityType) {
        super("Remove", entityType);
    }

    @Override
    protected BinaryData execute(ModelControllerClient controllerClient,
            EndpointService<DMRNodeLocation, DMRSession> endpointService,
            String modelNodePath,
            BasicMessageWithExtraData<REQ> envelope, RESP response, CommandContext context,
            DMRSession dmrContext) throws Exception {
        try {
            OperationBuilder.remove().address().segments(modelNodePath).parentBuilder().execute(controllerClient)
                    .assertSuccess();
        } catch (DmrApiException e) {
            /* A workaround for https://issues.jboss.org/browse/WFLY-5528 */
            log.warnf("Trying to remove resource [%s] for second time, see https://issues.jboss.org/browse/WFLY-5528",
                    modelNodePath);
            OperationBuilder.remove().address().segments(modelNodePath).parentBuilder().execute(controllerClient)
                    .assertSuccess();
        }
        return null;
    }

}
