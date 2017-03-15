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
package org.hawkular.agent.monitor.protocol.jmx;

import java.lang.management.ManagementFactory;
import java.util.ArrayList;
import java.util.Map;

import javax.management.MBeanServer;
import javax.management.MBeanServerConnection;
import javax.management.MBeanServerFactory;

import org.hawkular.agent.monitor.config.AgentCoreEngineConfiguration.EndpointConfiguration;
import org.hawkular.agent.monitor.diagnostics.ProtocolDiagnostics;
import org.hawkular.agent.monitor.inventory.MonitoredEndpoint;
import org.hawkular.agent.monitor.inventory.ResourceTypeManager;
import org.hawkular.agent.monitor.protocol.Driver;
import org.hawkular.agent.monitor.protocol.EndpointService;
import org.jolokia.client.J4pClient;

/**
 * Endpoint service that can support both remote and local JMX servers.
 *
 * @see EndpointService
 */
public class JMXEndpointService
        extends EndpointService<JMXNodeLocation, JMXSession> {

    public static final String MBEAN_SERVER_NAME_KEY = "mbean-server-name";

    private final JolokiaClientFactory clientFactory;

    public JMXEndpointService(String feedId, MonitoredEndpoint<EndpointConfiguration> endpoint,
            ResourceTypeManager<JMXNodeLocation> resourceTypeManager, ProtocolDiagnostics diagnostics) {
        super(feedId, endpoint, resourceTypeManager, new JMXLocationResolver(), diagnostics);

        if (endpoint.getConnectionData() != null) {
            this.clientFactory = new JolokiaClientFactory(endpoint);
        } else {
            this.clientFactory = null;
        }
    }

    /** @see org.hawkular.agent.monitor.protocol.EndpointService#openSession() */
    @Override
    public JMXSession openSession() {
        Driver<JMXNodeLocation> driver;

        if (this.clientFactory != null) {
            // remote JMX access via Jolokia
            J4pClient client = clientFactory.createClient();
            driver = new JolokiaJMXDriver(getDiagnostics(), client);
        } else {
            // local JMX access via JMX API
            MBeanServerConnection mbs = getMBeanServerConnection();
            driver = new MBeanServerConnectionJMXDriver(getDiagnostics(), mbs);
        }

        return new JMXSession(getFeedId(), getMonitoredEndpoint(), getResourceTypeManager(), driver,
                getLocationResolver());
    }

    private MBeanServerConnection getMBeanServerConnection() {
        // Find out what the name of the MBeanServer is from our custom data in the endpoint config.
        String mbsName = null;
        Map<String, ? extends Object> customData = getMonitoredEndpoint().getEndpointConfiguration().getCustomData();
        if (customData != null) {
            Object nameObj = customData.get(MBEAN_SERVER_NAME_KEY);
            if (nameObj != null && !nameObj.toString().isEmpty()) {
                mbsName = nameObj.toString();
            }
        }

        // Find the MBeanServer from its "name" where the "name" is nothing more than its default domain.
        // If name is null, we will use the platform MBeanServer.
        MBeanServer mbs = null;

        if (mbsName != null) {
            ArrayList<MBeanServer> allMbs = MBeanServerFactory.findMBeanServer(null);

            for (MBeanServer curMbs : allMbs) {
                if ((curMbs != null) && mbsName.equals(curMbs.getDefaultDomain())) {
                    mbs = curMbs;
                    break;
                }
            }
        }

        if (mbs == null) {
            mbs = ManagementFactory.getPlatformMBeanServer();
        }

        return mbs;
    }
}
