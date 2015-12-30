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

import java.util.Collections;
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

    private final SamplingService<L> samplingService;
    private final List<Resource<L>> payload;

    /**
     * Creates an inventory event.
     *
     * @param samplingService a service that provides details such as feed ID and endpoint information that helps
     *                        identify the resources in the event, plus has methods that can be used to monitor
     *                        the resources in the event.
     * @param payload the list of resources associated with this event
     */
    public InventoryEvent(SamplingService<L> samplingService, List<Resource<L>> payload) {
        if (samplingService == null) {
            throw new IllegalArgumentException("Sampling service cannot be null");
        }

        if (payload == null) {
            payload = Collections.emptyList();
        }

        this.samplingService = samplingService;
        this.payload = payload;
    }

    /**
     * @return the {@link MonitoredEndpoint} resources in payload come from
     */
    public MonitoredEndpoint getEndpoint() {
        MonitoredEndpoint me = this.samplingService.getEndpoint();
        if (me == null) {
            throw new IllegalStateException("Sampling service's endpoint is null");
        }
        return me;
    }

    /**
     * @return the {@code feedId} associated with the resources in payload
     */
    public String getFeedId() {
        String feedId = this.samplingService.getFeedId();
        if (feedId == null) {
            throw new IllegalStateException("Sampling service's feed ID is null");
        }
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
