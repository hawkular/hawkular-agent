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
package org.hawkular.agent.monitor.protocol.jmx;

import org.hawkular.agent.monitor.diagnostics.ProtocolDiagnostics;
import org.hawkular.agent.monitor.inventory.MonitoredEndpoint;
import org.hawkular.agent.monitor.inventory.ResourceTypeManager;
import org.hawkular.agent.monitor.protocol.Driver;
import org.hawkular.agent.monitor.protocol.EndpointService;
import org.jolokia.client.J4pClient;

/**
 * @author <a href="https://github.com/ppalaga">Peter Palaga</a>
 * @see EndpointService
 */
public class JMXEndpointService
        extends EndpointService<JMXNodeLocation, JMXSession> {

    private final JmxClientFactory clientFactory;
    public JMXEndpointService(String feedId, MonitoredEndpoint endpoint,
            ResourceTypeManager<JMXNodeLocation> resourceTypeManager, ProtocolDiagnostics diagnostics) {
        super(feedId, endpoint, resourceTypeManager, new JMXLocationResolver(), diagnostics);
        this.clientFactory = new JmxClientFactory(endpoint);
    }

    /** @see org.hawkular.agent.monitor.protocol.EndpointService#openSession() */
    @Override
    public JMXSession openSession() {
        J4pClient client = clientFactory.createClient();
        Driver<JMXNodeLocation> driver = new JMXDriver(client, diagnostics);
        return new JMXSession(feedId, endpoint, resourceTypeManager, driver, locationResolver, client);
    }

}
