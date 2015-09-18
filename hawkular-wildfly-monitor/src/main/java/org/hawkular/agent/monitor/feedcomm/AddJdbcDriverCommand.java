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
package org.hawkular.agent.monitor.feedcomm;

import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;

import org.hawkular.agent.monitor.extension.MonitorServiceConfiguration;
import org.hawkular.agent.monitor.inventory.ID;
import org.hawkular.agent.monitor.inventory.InventoryIdUtil;
import org.hawkular.agent.monitor.inventory.InventoryIdUtil.ResourceIdParts;
import org.hawkular.agent.monitor.inventory.ManagedServer;
import org.hawkular.agent.monitor.inventory.Name;
import org.hawkular.agent.monitor.inventory.ResourceManager;
import org.hawkular.agent.monitor.inventory.dmr.DMRInventoryManager;
import org.hawkular.agent.monitor.inventory.dmr.DMRResource;
import org.hawkular.agent.monitor.inventory.dmr.LocalDMRManagedServer;
import org.hawkular.agent.monitor.inventory.dmr.RemoteDMRManagedServer;
import org.hawkular.agent.monitor.log.MsgLogger;
import org.hawkular.bus.common.BasicMessageWithExtraData;
import org.hawkular.bus.common.BinaryData;
import org.hawkular.cmdgw.api.AddJdbcDriverRequest;
import org.hawkular.cmdgw.api.AddJdbcDriverResponse;
import org.hawkular.dmrclient.DatasourceJBossASClient;
import org.hawkular.dmrclient.modules.AddModuleRequest;
import org.hawkular.dmrclient.modules.AddModuleRequest.ModuleResource;
import org.hawkular.dmrclient.modules.Modules;
import org.hawkular.inventory.api.model.CanonicalPath;

/**
 * Adds an JdbcDriver on a resource.
 */
public class AddJdbcDriverCommand implements Command<AddJdbcDriverRequest, AddJdbcDriverResponse> {
    public static final Class<AddJdbcDriverRequest> REQUEST_CLASS = AddJdbcDriverRequest.class;
    public static final Set<String> DEFAULT_DRIVER_MODULE_DEPENDENCIES = Collections
            .unmodifiableSet(new LinkedHashSet(Arrays.asList("javax.api", "javax.transaction.api")));

    @Override
    public BasicMessageWithExtraData<AddJdbcDriverResponse> execute(AddJdbcDriverRequest request,
            BinaryData jdbcDriverContent, CommandContext context) throws Exception {

        MsgLogger.LOG.infof("Received request to add the JDBC Driver [%s] on resource [%s]", request.getModuleName(),
                request.getResourcePath());

        MonitorServiceConfiguration config = context.getMonitorServiceConfiguration();

        // Based on the resource ID we need to know which inventory manager is handling it.
        // From the inventory manager, we can get the actual resource.
        CanonicalPath canonicalPath = CanonicalPath.fromString(request.getResourcePath());
        String resourceId = canonicalPath.ids().getResourcePath().getSegment().getElementId();
        ResourceIdParts idParts = InventoryIdUtil.parseResourceId(resourceId);
        ManagedServer managedServer = config.managedServersMap.get(new Name(idParts.getManagedServerName()));

        if (managedServer == null) {
            throw new IllegalArgumentException(String.format("Cannot add JDBC Driver: unknown managed server [%s]",
                    idParts.getManagedServerName()));
        }

        if (managedServer instanceof LocalDMRManagedServer || managedServer instanceof RemoteDMRManagedServer) {
            return addLocal(resourceId, request, jdbcDriverContent, context, managedServer);
        } else {
            throw new IllegalStateException("Cannot add JDBC Driver: report this bug: " + managedServer.getClass());
        }
    }

    private BasicMessageWithExtraData<AddJdbcDriverResponse> addLocal(String resourceId, AddJdbcDriverRequest request,
            BinaryData jdbcDriverContent, CommandContext context, ManagedServer managedServer) throws Exception {

        DMRInventoryManager inventoryManager = context.getDiscoveryService().getDmrServerInventories()
                .get(managedServer);
        if (inventoryManager == null) {
            throw new IllegalArgumentException(
                    String.format("Cannot add JDBC Driver: missing inventory manager [%s]", managedServer));
        }

        ResourceManager<DMRResource> resourceManager = inventoryManager.getResourceManager();
        DMRResource resource = resourceManager.getResource(new ID(resourceId));
        if (resource == null) {
            throw new IllegalArgumentException(
                    String.format("Cannot add JDBC Driver: unknown resource [%s]", request.getResourcePath()));
        }

        AddJdbcDriverResponse response = new AddJdbcDriverResponse();
        response.setResourcePath(request.getResourcePath());

        try (DatasourceJBossASClient dsc = new DatasourceJBossASClient(
                inventoryManager.getModelControllerClientFactory().createClient())) {

            ModuleResource jarResource = new AddModuleRequest.ModuleResource(jdbcDriverContent,
                    request.getDriverJarName());

            AddModuleRequest addModuleRequest = new AddModuleRequest(request.getModuleName(), (String) null,
                    (String) null, Collections.singleton(jarResource), DEFAULT_DRIVER_MODULE_DEPENDENCIES, null);
            new Modules(Modules.findModulesDir()).add(addModuleRequest);
            dsc.addJdbcDriver(request.getDriverName(), request.getModuleName());
            response.setStatus("OK");
            response.setMessage(String.format("Added JDBC Driver: %s", request.getDriverName()));
        } catch (Exception e) {
            MsgLogger.LOG.errorFailedToExecuteCommand(e, this.getClass().getName(), request);
            response.setStatus("ERROR");
            response.setMessage(e.toString());
        }

        return new BasicMessageWithExtraData<>(response, null);
    }

}
