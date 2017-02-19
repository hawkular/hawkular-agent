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

import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.util.Collection;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;

import org.hawkular.agent.monitor.extension.MonitorServiceConfiguration.AbstractEndpointConfiguration;
import org.hawkular.agent.monitor.inventory.ID;
import org.hawkular.agent.monitor.inventory.MonitoredEndpoint;
import org.hawkular.agent.monitor.inventory.Operation;
import org.hawkular.agent.monitor.inventory.Resource;
import org.hawkular.agent.monitor.inventory.ResourceManager;
import org.hawkular.agent.monitor.log.AgentLoggers;
import org.hawkular.agent.monitor.log.MsgLogger;
import org.hawkular.agent.monitor.protocol.EndpointService;
import org.hawkular.agent.monitor.protocol.dmr.DMRNodeLocation;
import org.hawkular.agent.monitor.protocol.dmr.DMRSession;
import org.hawkular.bus.common.BasicMessageWithExtraData;
import org.hawkular.bus.common.BinaryData;
import org.hawkular.cmdgw.api.ExportJdrRequest;
import org.hawkular.cmdgw.api.ExportJdrResponse;
import org.hawkular.cmdgw.api.MessageUtils;
import org.hawkular.cmdgw.api.ResponseStatus;
import org.hawkular.dmr.api.OperationBuilder;
import org.hawkular.inventory.paths.CanonicalPath;
import org.jboss.as.controller.client.ModelControllerClient;
import org.jboss.dmr.ModelNode;

/**
 * @author Juraci Paixão Kröhling
 */
public class ExportJdrCommand extends AbstractResourcePathCommand<ExportJdrRequest, ExportJdrResponse> {
    private static final MsgLogger log = AgentLoggers.getLogger(ExportJdrCommand.class);
    public static final Class<ExportJdrRequest> REQUEST_CLASS = ExportJdrRequest.class;

    public ExportJdrCommand() {
        super("Export JDR", "WildFly Server");
    }

    @Override
    protected BinaryData execute(ModelControllerClient controllerClient,
            EndpointService<DMRNodeLocation, DMRSession> endpointService,
            String modelNodePath, BasicMessageWithExtraData<ExportJdrRequest> envelope, ExportJdrResponse response,
            CommandContext context, DMRSession dmrContext) throws Exception {
        ExportJdrRequest request = envelope.getBasicMessage();

        CanonicalPath canonicalPath = CanonicalPath.fromString(request.getResourcePath());
        String resourceId = canonicalPath.ids().getResourcePath().getSegment().getElementId();

        ResourceManager<DMRNodeLocation> resourceManager = endpointService.getResourceManager();
        Resource<DMRNodeLocation> resource = resourceManager.getResource(new ID(resourceId));
        if (resource == null) {
            throw new IllegalArgumentException(
                    String.format("Cannot export a DMR report: unknown resource [%s]", request.getResourcePath()));
        }

        // find the operation we need to execute - make sure it exists and get the address for the resource to invoke
        DMRNodeLocation opLocation = null;
        String actualOperationName = null;

        String requestedOpName = "JDR";
        Collection<Operation<DMRNodeLocation>> ops = resource.getResourceType().getOperations();
        log.tracef("Searching for operation [%s] among operations [%s] for resource [%s].", requestedOpName, ops,
                resource.getID());
        for (Operation<DMRNodeLocation> op : ops) {
            if (requestedOpName.equals(op.getID().getIDString())) {
                opLocation = dmrContext.getLocationResolver().absolutize(resource.getLocation(), op.getLocation());
                actualOperationName = op.getInternalName();
                break;
            }
        }

        if (opLocation == null) {
            throw new IllegalArgumentException(String.format(
                    "Cannot execute operation: unknown operation [%s] for resource [%s]", requestedOpName, resource));
        }

        MessageUtils.prepareResourcePathResponse(request, response);

        BinaryData binaryData = null;
        ModelNode resultNode = null;
        // Workaround for https://issues.jboss.org/browse/WFLY-8161 (part1)
        Map<String, String> pwPropCache = cachePasswordSysProps();

        try {
            resultNode = OperationBuilder.byName(actualOperationName) //
                    .address(opLocation.getPathAddress())
                    .execute(dmrContext.getClient()).assertSuccess() //
                    .getResultNode();
        } finally {
            // Workaround for https://issues.jboss.org/browse/WFLY-8161 (part2)
            restorePasswordSysProps(pwPropCache);
        }

        String reportLocation = resultNode.get("report-location").asString();

        File reportFile = new File(reportLocation);
        InputStream reportInputStream = new FileInputStream(reportFile);
        binaryData = new BinaryData(null, reportInputStream);

        response.setStatus(ResponseStatus.OK);
        response.setFileName(reportFile.getName());
        //response.setMessage(JBossASClient.getResults(opResp).asString());

        return binaryData;
    }

    @Override
    protected void validate(String modelNodePath, BasicMessageWithExtraData<ExportJdrRequest> envelope) {
    }

    @Override
    protected void validate(BasicMessageWithExtraData<ExportJdrRequest> envelope,
            MonitoredEndpoint<? extends AbstractEndpointConfiguration> endpoint) {
    }

    @Override
    protected boolean modifiesResource() {
        return false;
    }

    @Override
    protected ExportJdrResponse createResponse() {
        return new ExportJdrResponse();
    }

    private Map<String, String> cachePasswordSysProps() {
        Map<String, String> cache = new HashMap<>();

        Properties props = System.getProperties();
        Enumeration<?> names = props.propertyNames();
        while (names.hasMoreElements()) {
            String name = (String) names.nextElement();
            if (name.matches(".*password.*")) {
                cache.put(name, props.getProperty(name));
            }
        }
        return cache;
    }

    private void restorePasswordSysProps(Map<String, String> cache) {
        for (Map.Entry<String, String> entry : cache.entrySet()) {
            System.setProperty(entry.getKey(), entry.getValue());
        }
    }
}
