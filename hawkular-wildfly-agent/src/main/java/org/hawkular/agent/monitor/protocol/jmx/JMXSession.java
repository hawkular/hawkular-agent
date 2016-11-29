/*
 * Copyright 2015-2016 Red Hat, Inc. and/or its affiliates
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
package org.hawkular.agent.monitor.protocol.jmx;

import java.io.IOException;

import org.hawkular.agent.monitor.inventory.MonitoredEndpoint;
import org.hawkular.agent.monitor.inventory.ResourceTypeManager;
import org.hawkular.agent.monitor.protocol.Driver;
import org.hawkular.agent.monitor.protocol.LocationResolver;
import org.hawkular.agent.monitor.protocol.Session;
import org.jolokia.client.J4pClient;

/**
 * @author <a href="https://github.com/ppalaga">Peter Palaga</a>
 * @see Session
 */
public class JMXSession
        extends Session<JMXNodeLocation> {
    private final J4pClient client;

    public JMXSession(String feedId,
            MonitoredEndpoint endpoint,
            ResourceTypeManager<JMXNodeLocation> resourceTypeManager,
            Driver<JMXNodeLocation> driver,
            LocationResolver<JMXNodeLocation> locationResolver,
            J4pClient client) {
        super(feedId, endpoint, resourceTypeManager, driver, locationResolver);
        this.client = client;
    }

    /** @see java.io.Closeable#close() */
    @Override
    public void close() throws IOException {
        /* we could eventually close the client here if it was closeable */
    }

    /**
     * Returns a native client.
     *
     * @return a native client
     */
    public J4pClient getClient() {
        return client;
    }
}
