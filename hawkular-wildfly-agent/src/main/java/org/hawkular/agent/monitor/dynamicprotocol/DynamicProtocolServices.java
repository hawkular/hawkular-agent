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

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import javax.net.ssl.SSLContext;

import org.hawkular.agent.monitor.api.HawkularWildFlyAgentContext;
import org.hawkular.agent.monitor.dynamicprotocol.prometheus.PrometheusDynamicEndpointService;
import org.hawkular.agent.monitor.extension.MonitorServiceConfiguration.DynamicEndpointConfiguration;
import org.hawkular.agent.monitor.extension.MonitorServiceConfiguration.DynamicProtocolConfiguration;
import org.hawkular.agent.monitor.inventory.MonitoredEndpoint;
import org.hawkular.agent.monitor.inventory.Name;
import org.hawkular.agent.monitor.inventory.NameSet;
import org.hawkular.agent.monitor.log.AgentLoggers;
import org.hawkular.agent.monitor.log.MsgLogger;
import org.jboss.msc.value.InjectedValue;

/**
 * This object contains all the {@link DynamicProtocolService}s. These are protocols that have no real
 * notion of inventory (at least none that are understood). The services themselves are responsible
 * for taking care of inventory and collecting metrics themselves.
 *
 * This object will periodically trigger jobs on the dynamic protocol services so they can do their work.
 *
 * @author John Mazzitelli
 */
public class DynamicProtocolServices {
    public static class Builder {
        private final String feedId;
        private DynamicProtocolService prometheusProtocolService;
        private final Map<String, InjectedValue<SSLContext>> sslContexts;

        public Builder(String feedId, Map<String, InjectedValue<SSLContext>> sslContexts) {
            this.feedId = feedId;
            this.sslContexts = sslContexts;
        }

        public DynamicProtocolServices build() {
            return new DynamicProtocolServices(prometheusProtocolService);
        }

        public Builder prometheusDynamicProtocolService(DynamicProtocolConfiguration protocolConfig,
                HawkularWildFlyAgentContext hawkularStorage) {

            DynamicProtocolService.Builder builder = DynamicProtocolService.builder();

            for (DynamicEndpointConfiguration endpointConfig : protocolConfig.getEndpoints().values()) {
                if (!endpointConfig.isEnabled()) {
                    log.infoManagedServerDisabled(endpointConfig.getName().toString());
                } else {
                    final String securityRealm = endpointConfig.getSecurityRealm();
                    SSLContext sslContext = null;
                    if (securityRealm != null) {
                        InjectedValue<SSLContext> injectedValue = sslContexts.get(securityRealm);
                        if (injectedValue == null) {
                            throw new IllegalArgumentException("Unknown security realm: " + securityRealm);
                        }
                        sslContext = injectedValue.getOptionalValue();
                    }

                    final MonitoredEndpoint<DynamicEndpointConfiguration> endpoint = MonitoredEndpoint
                            .<DynamicEndpointConfiguration> of(endpointConfig, sslContext);

                    // get all the metric names that are to be collected, if any are specified
                    List<Name> metrics = new ArrayList<>();
                    for (Name metricSetName : endpointConfig.getMetricSets()) {
                        NameSet metricSet = protocolConfig.getMetrics().get(metricSetName);
                        if (metricSet != null && metricSet.isEnabled()) {
                            metrics.addAll(metricSet.getNameSet());
                        }
                    }

                    PrometheusDynamicEndpointService endpointService = new PrometheusDynamicEndpointService(
                            this.feedId, endpoint, hawkularStorage, metrics);
                    builder.endpointService(endpointService);

                    log.debugf("[%s] created", endpointService);
                }
            }

            this.prometheusProtocolService = builder.build();
            return this;
        }
    }

    private static final MsgLogger log = AgentLoggers.getLogger(DynamicProtocolServices.class);

    public static Builder builder(String feedId, Map<String, InjectedValue<SSLContext>> sslContexts) {
        return new Builder(feedId, sslContexts);
    }

    private final DynamicProtocolService prometheusProtocolService;
    private final List<DynamicProtocolService> services;

    public DynamicProtocolServices(
            DynamicProtocolService prometheusProtocolService) {
        this.prometheusProtocolService = prometheusProtocolService;
        this.services = Collections.unmodifiableList(Arrays.asList(prometheusProtocolService));
    }

    public void start() {
        for (DynamicProtocolService service : services) {
            service.start();
        }
    }

    public void stop() {
        for (DynamicProtocolService service : services) {
            service.stop();
        }
    }

    public DynamicProtocolService getPrometheusProtocolService() {
        return prometheusProtocolService;
    }

    public List<DynamicProtocolService> getServices() {
        return services;
    }
}
