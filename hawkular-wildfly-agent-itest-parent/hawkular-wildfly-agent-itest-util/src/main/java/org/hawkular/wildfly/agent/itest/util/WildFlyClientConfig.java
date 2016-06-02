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

package org.hawkular.wildfly.agent.itest.util;

import java.io.IOException;

import org.hawkular.agent.monitor.protocol.dmr.DMREndpointService;
import org.jboss.as.controller.client.ModelControllerClient;

/**
 * Returns information about a plain wildfly that has been setup for the tests.
 */
public class WildFlyClientConfig {
    private final String wfHost;
    private final int wfManagementPort;
    private final String wfFeedId;

    public WildFlyClientConfig() {
        wfHost = System.getProperty("plain-wildfly.bind.address");
        wfManagementPort = Integer.parseInt(System.getProperty("plain-wildfly.management.http.port"));

        try (ModelControllerClient mcc = AbstractITest.newModelControllerClient(wfHost, wfManagementPort)) {
            wfFeedId = DMREndpointService.lookupServerIdentifier(mcc);
            System.out.println(String.format("Plain WF: %s:%d->%s", wfHost, wfManagementPort, wfFeedId));
        } catch (IOException e) {
            throw new RuntimeException("Could not get wfFeedId", e);
        }
    }

    public String getHost() {
        return wfHost;
    }

    public int getManagementPort() {
        return wfManagementPort;
    }

    public String getFeedId() {
        return wfFeedId;
    }
}
