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
package org.hawkular.agent.monitor.protocol.dmr;

import java.io.IOException;

import org.hawkular.agent.monitor.inventory.MonitoredEndpoint;
import org.hawkular.agent.monitor.inventory.ResourceTypeManager;
import org.hawkular.agent.monitor.protocol.Driver;
import org.hawkular.agent.monitor.protocol.LocationResolver;
import org.hawkular.agent.monitor.protocol.Session;
import org.jboss.as.controller.client.ModelControllerClient;

/**
 * @author <a href="https://github.com/ppalaga">Peter Palaga</a>
 * @see Session
 */
public class DMRSession extends Session<DMRNodeLocation> {

    private final ModelControllerClient client;

    public DMRSession(String feedId,
            MonitoredEndpoint endpoint,
            ResourceTypeManager<DMRNodeLocation> resourceTypeManager,
            Driver<DMRNodeLocation> driver,
            LocationResolver<DMRNodeLocation> locationResolver,
            ModelControllerClient client) {
        super(feedId, endpoint, resourceTypeManager, driver, locationResolver);
        this.client = client;
    }

    /** @see java.io.Closeable#close() */
    @Override
    public void close() throws IOException {
        if (client != null) {
            client.close();
        }
    }

    /**
     * Returns a native client. Note that the returned client is valid only within the scope of this {@link DMRSession}
     * because it gets closed in {@link #close()}.
     *
     * @return a native client
     */
    public ModelControllerClient getClient() {
        return client;
    }
}
