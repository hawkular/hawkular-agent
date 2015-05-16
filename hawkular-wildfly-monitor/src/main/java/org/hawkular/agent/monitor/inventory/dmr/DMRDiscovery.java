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
package org.hawkular.agent.monitor.inventory.dmr;

import java.util.Set;

import org.hawkular.agent.monitor.inventory.ResourceTypeManager;
import org.hawkular.agent.monitor.scheduler.ModelControllerClientFactory;
import org.hawkular.agent.monitor.scheduler.config.DMREndpoint;
import org.hawkular.dmrclient.Address;
import org.hawkular.dmrclient.CoreJBossASClient;
import org.jboss.as.controller.client.ModelControllerClient;
import org.jboss.dmr.ModelNode;
import org.jboss.logging.Logger;

public class DMRDiscovery {
    private static final Logger LOG = Logger.getLogger(DMRDiscovery.class);

    private final ResourceTypeManager<DMRResourceType, DMRResourceTypeSet> resourceTypeManager;
    private final DMREndpoint dmrEndpoint;
    private final ModelControllerClientFactory clientFactory;

    public DMRDiscovery(ResourceTypeManager<DMRResourceType, DMRResourceTypeSet> rtm,
            DMREndpoint dmrEndpoint, ModelControllerClientFactory clientFactory) {
        this.resourceTypeManager = rtm;
        this.dmrEndpoint = dmrEndpoint;
        this.clientFactory = clientFactory;

        LOG.debugf("Endpoint [%s] resource type graph -> %s", dmrEndpoint, rtm.getResourceTypesGraph());
    }

    public void discoveryRoots() {
        ModelControllerClient mcc = clientFactory.createClient();
        try (CoreJBossASClient client = new CoreJBossASClient(mcc)) {
            Set<DMRResourceType> roots = this.resourceTypeManager.getRootResourceTypes();
            for (DMRResourceType root : roots) {
                Address addr = new Address(root.getPath());
                ModelNode resources = client.readResource(addr);
                System.out.println(resources.toJSONString(false));
            }
        } catch (Exception e) {

        }
    }
}
