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
package org.hawkular.agent.monitor.api;

import java.util.List;

import org.hawkular.agent.monitor.inventory.MonitoredEndpoint;
import org.hawkular.agent.monitor.inventory.Resource;

/**
 * A event for changes in the inventory of resources.
 *
 * @author <a href="https://github.com/ppalaga">Peter Palaga</a>
 *
 * @param <T> the type of {@link #payload}
 *
 * @see InventoryListener
 */
public class InventoryEvent<L> {

    private final String feedId;
    private final MonitoredEndpoint endpoint;
    private final SamplingService<L> samplingService;
    private final List<Resource<L>> payload;


    public InventoryEvent(String feedId, MonitoredEndpoint endpoint, SamplingService<L> samplingService,
            List<Resource<L>> payload) {
        super();
        this.feedId = feedId;
        this.endpoint = endpoint;
        this.samplingService = samplingService;
        this.payload = payload;
    }

    /**
     * @return the {@link MonitoredEndpoint} resources in payload come from
     */
    public MonitoredEndpoint getEndpoint() {
        return endpoint;
    }

    /**
     * @return the {@code feedId} associated with the resources in payload
     */
    public String getFeedId() {
        return feedId;
    }

    /**
     * @return the resources related to this event
     */
    public List<Resource<L>> getPayload() {
        return payload;
    }

    /**
     * @return the sampling service able to handle the resources in payload
     */
    public SamplingService<L> getSamplingService() {
        return samplingService;
    }

}
