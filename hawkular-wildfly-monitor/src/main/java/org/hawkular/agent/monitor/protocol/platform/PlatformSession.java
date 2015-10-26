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
package org.hawkular.agent.monitor.protocol.platform;

import java.io.IOException;

import org.hawkular.agent.monitor.inventory.ResourceTypeManager;
import org.hawkular.agent.monitor.protocol.Driver;
import org.hawkular.agent.monitor.protocol.LocationResolver;
import org.hawkular.agent.monitor.protocol.Session;

/**
 * @author <a href="https://github.com/ppalaga">Peter Palaga</a>
 * @see Session
 */
public class PlatformSession
        extends Session<PlatformNodeLocation, PlatformEndpoint> {

    public PlatformSession(String feedId, PlatformEndpoint endpoint,
            ResourceTypeManager<PlatformNodeLocation> resourceTypeManager,
            Driver<PlatformNodeLocation> driver,
            LocationResolver<PlatformNodeLocation> locationResolver) {
        super(feedId, endpoint, resourceTypeManager, driver, locationResolver);
    }

    /** @see java.io.Closeable#close() */
    @Override
    public void close() throws IOException {
    }

}
