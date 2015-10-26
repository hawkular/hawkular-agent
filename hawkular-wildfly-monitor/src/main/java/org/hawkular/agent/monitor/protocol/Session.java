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
package org.hawkular.agent.monitor.protocol;

import java.io.Closeable;

import org.hawkular.agent.monitor.inventory.MonitoredEndpoint;
import org.hawkular.agent.monitor.inventory.ResourceTypeManager;

/**
 * A superclass for protocol specific session implementations. Note that {@link Session}s are closeable and that all
 * resources they provide may eventually be valid only within the scope of the present session.
 *
 * @author <a href="https://github.com/ppalaga">Peter Palaga</a>
 *
 * @param <L> the type of the protocol specific location, typically a subclass of {@link NodeLocation}
 * @param <E> the protocol specific {@link MonitoredEndpoint}
 */
public abstract class Session<L, E extends MonitoredEndpoint> implements Closeable {

    private final E endpoint;
    private final String feedId;
    private final Driver<L> driver;
    private final LocationResolver<L> locationResolver;

    private final ResourceTypeManager<L> resourceTypeManager;

    public Session(String feedId, E endpoint, ResourceTypeManager<L> resourceTypeManager,
            Driver<L> driver, LocationResolver<L> locationResolver) {
        super();
        this.feedId = feedId;
        this.endpoint = endpoint;
        this.resourceTypeManager = resourceTypeManager;
        this.driver = driver;
        this.locationResolver = locationResolver;
    }

    public E getEndpoint() {
        return endpoint;
    }

    public String getFeedId() {
        return feedId;
    }

    public Driver<L> getDriver() {
        return driver;
    }

    public ResourceTypeManager<L> getResourceTypeManager() {
        return resourceTypeManager;
    }

    public LocationResolver<L> getLocationResolver() {
        return locationResolver;
    }

}
