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
import org.hawkular.agent.monitor.protocol.EndpointService;
import org.hawkular.agent.monitor.protocol.dmr.DMRNodeLocation;
import org.hawkular.agent.monitor.protocol.dmr.DMRSession;
import org.hawkular.bus.common.BasicMessageWithExtraData;
import org.hawkular.bus.common.BinaryData;
import org.hawkular.cmdgw.api.ResourceRequest;
import org.hawkular.cmdgw.api.ResourceResponse;
import org.hawkular.dmr.api.DmrApiException;
import org.hawkular.dmr.api.OperationBuilder;
import org.hawkular.dmr.api.OperationBuilder.OperationResult;
import org.jboss.as.controller.client.ModelControllerClient;

/**
 * A base for {@link Command}s removing nodes from the DMR tree.
 *
 * @author <a href="https://github.com/ppalaga">Peter Palaga</a>
 */
public abstract class AbstractRemoveModelNodeCommand<Q extends ResourceRequest, S extends ResourceResponse>
        extends AbstractDMRResourcePathCommand<Q, S> {
    private static final MsgLogger log = AgentLoggers.getLogger(AbstractRemoveModelNodeCommand.class);

    public AbstractRemoveModelNodeCommand(String entityType) {
        super("Remove", entityType);
    }

    @Override
    protected BinaryData execute(
            ModelControllerClient controllerClient,
            EndpointService<DMRNodeLocation, DMRSession> endpointService,
            String modelNodePath,
            BasicMessageWithExtraData<Q> envelope,
            S response,
            CommandContext context,
            DMRSession dmrContext) throws Exception {

        OperationResult<?> opResult;

        try {
            opResult = OperationBuilder
                    .remove()
                    .address()
                    .segments(modelNodePath)
                    .parentBuilder()
                    .execute(controllerClient)
                    .assertSuccess();
        } catch (DmrApiException e) {
            /* A workaround for https://issues.jboss.org/browse/WFLY-5528 */
            log.warnf("Attempt #2 to remove resource [%s], see JIRA WFLY-5528", modelNodePath);
            opResult = OperationBuilder
                    .remove()
                    .address()
                    .segments(modelNodePath)
                    .parentBuilder()
                    .execute(controllerClient)
                    .assertSuccess();
        }

        setServerRefreshIndicator(opResult, response);

        DMRNodeLocation doomedLocation = DMRNodeLocation.of(modelNodePath);
        endpointService.removeResources(doomedLocation);

        // discover that the old resource has been removed so it is deleted from inventory
        endpointService.discoverAll();

        return null;
    }

}
