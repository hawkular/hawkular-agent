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
package org.hawkular.agent.example;

import javax.annotation.PostConstruct;
import javax.annotation.Resource;
import javax.ejb.Singleton;

import org.hawkular.agent.monitor.api.HawkularWildFlyAgentContext;
import org.jboss.logging.Logger;

/**
 * A singleton that provides the agent API to allow others to create inventory and store metrics.
 */
@Singleton
public class HawkularWildFlyAgentProvider {
    private static final Logger log = Logger.getLogger(HawkularWildFlyAgentProvider.class);
    private static final String AGENT_JNDI = "java:global/hawkular/agent/api";

    @Resource(name = AGENT_JNDI)
    private HawkularWildFlyAgentContext hawkularAgent;

    private MyAppSamplingService myAppSamplingService;

    @PostConstruct
    public void postConstruct() {
        if (hawkularAgent == null) {
            log.debugf("The Hawkular WildFly Agent is either disabled or not deployed. It is unavailable for use.");
        }

        myAppSamplingService = new MyAppSamplingService();
    }

    public HawkularWildFlyAgentContext getHawkularWildFlyAgent() throws UnsupportedOperationException {
        if (hawkularAgent == null) {
            throw new UnsupportedOperationException(
                    "The Hawkular WildFly Agent is either disabled or not deployed "
                            + "and thus is not available for use.");
        }
        return hawkularAgent;
    }

    /**
     * This is the sampling service that the Hawkular WildFly Agent will use to request us
     * to perform metric collection and availability checking.
     *
     * @return sampling service
     */
    public MyAppSamplingService getSamplingService() {
        return this.myAppSamplingService;
    }
}
