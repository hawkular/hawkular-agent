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
package org.hawkular.agent.monitor.dynamicprotocol;

import org.hawkular.agent.monitor.api.HawkularWildFlyAgentContext;
import org.hawkular.agent.monitor.extension.MonitorServiceConfiguration.DynamicEndpointConfiguration;
import org.hawkular.agent.monitor.inventory.MonitoredEndpoint;
import org.hawkular.agent.monitor.log.AgentLoggers;
import org.hawkular.agent.monitor.log.MsgLogger;
import org.hawkular.agent.monitor.service.ServiceStatus;

/**
 * A service to dynamically monitor a single {@link MonitoredEndpoint}. This service is responsible
 * for everything about the monitoring of the remote endpoint.
 */
public abstract class DynamicEndpointService implements Runnable {
    private static final MsgLogger LOG = AgentLoggers.getLogger(DynamicEndpointService.class);

    private final MonitoredEndpoint<DynamicEndpointConfiguration> endpoint;
    private final String feedId;
    private final HawkularWildFlyAgentContext hawkularStorage;

    protected volatile ServiceStatus status = ServiceStatus.INITIAL;


    public DynamicEndpointService(String feedId, MonitoredEndpoint<DynamicEndpointConfiguration> endpoint,
            HawkularWildFlyAgentContext hawkularStorage) {
        super();
        this.feedId = feedId;
        this.endpoint = endpoint;
        this.hawkularStorage = hawkularStorage;
    }

    public String getFeedId() {
        return feedId;
    }

    public MonitoredEndpoint<DynamicEndpointConfiguration> getMonitoredEndpoint() {
        return endpoint;
    }

    public HawkularWildFlyAgentContext getHawkularStorage() {
        return hawkularStorage;
    }

    public void start() {
        status.assertInitialOrStopped(getClass(), "start()");
        status = ServiceStatus.STARTING;
        // nothing to do
        status = ServiceStatus.RUNNING;

        LOG.debugf("Started [%s]", toString());
    }

    public void stop() {
        status.assertRunning(getClass(), "stop()");
        status = ServiceStatus.STOPPING;
        // nothing to do
        status = ServiceStatus.STOPPED;

        LOG.debugf("Stopped [%s]", toString());
    }

    /**
     * This is called when the dynamic protocol service is due to perform its work.
     */
    @Override
    public abstract void run();

    @Override
    public String toString() {
        return String.format("%s[%s]", getClass().getSimpleName(), getMonitoredEndpoint());
    }

    @Override
    public int hashCode() {
        int result = 31 + ((endpoint == null) ? 0 : endpoint.hashCode());
        return result;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (!(obj instanceof DynamicEndpointService)) {
            return false;
        }
        DynamicEndpointService other = (DynamicEndpointService) obj;
        if (this.endpoint == null) {
            if (other.endpoint != null) {
                return false;
            }
        } else if (!this.endpoint.equals(other.endpoint)) {
            return false;
        }
        return true;
    }
}
