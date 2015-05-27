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
package org.hawkular.agent.monitor.scheduler.config;

import org.hawkular.agent.monitor.scheduler.ModelControllerClientFactory;
import org.hawkular.agent.monitor.service.ServerIdentifiers;


/**
 * Represent the endpoint to our local Wildfly instance (the one we are running inside of).
 */
public class LocalDMREndpoint extends DMREndpoint {
    private final ModelControllerClientFactory localClientFactory;
    private final ServerIdentifiers localId;

    /**
     * If the caller does not yet know the local WildFly's identification, it can use this
     * constructor which takes a client factory as an argument. That client factory will
     * be used when the identification needs to be determined (a client will be created
     * and queried for the server identification).
     *
     * @param name the name of the endpoint
     * @param localClientFactory creates clients that will talk to the WildFly instance we are running in
     */
    public LocalDMREndpoint(String name, ModelControllerClientFactory localClientFactory) {
        super(name, null, 0, null, null);
        this.localClientFactory = localClientFactory;
        this.localId = null;
    }

    /**
     * If the caller already knows the local WildFly's identification, it can use this
     * constructor which takes the known ServerIdentifiers object thus helping this
     * object avoid having to contact the management endpoint of the local WildFly
     * to obtain that information.
     *
     * @param name the name of the endpoint
     * @param selfId the known identification of the WildFly instance we are running in
     */
    public LocalDMREndpoint(String name, ServerIdentifiers selfId) {
        super(name, null, 0, null, null);
        this.localClientFactory = null;
        this.localId = selfId;
    }

    @Override
    public ServerIdentifiers getServerIdentifiers() {
        if (localId != null) {
            return localId;
        }
        return super.getServerIdentifiers();
    }

    @Override
    protected ModelControllerClientFactory getModelControllerClientFactory() {
        return localClientFactory;
    }
}
