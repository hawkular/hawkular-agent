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
package org.hawkular.agent.monitor.service;

import java.util.Map;

import org.hawkular.agent.monitor.inventory.ManagedServer;
import org.hawkular.agent.monitor.inventory.dmr.DMRInventoryManager;

/**
 * A simple interface that provides methods used to discover resources.
 */
public interface DiscoveryService {
    /**
     * This will build inventory managers for every configured managed
     * and discover all resources in those managed servers, populating
     * the inventory managers with the discovered resources.
     */
    void discoverAllResourcesForAllManagedServers();

    /**
     * @return the discovered inventories of all managed servers
     */
    Map<ManagedServer, DMRInventoryManager> getDmrServerInventories();
}
