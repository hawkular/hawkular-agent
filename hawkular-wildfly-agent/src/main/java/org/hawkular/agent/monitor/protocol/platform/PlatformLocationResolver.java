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

import org.hawkular.agent.monitor.protocol.LocationResolver;
import org.hawkular.agent.monitor.protocol.platform.api.PlatformPath;

/**
 * @author <a href="https://github.com/ppalaga">Peter Palaga</a>
 * @see LocationResolver
 */
public class PlatformLocationResolver implements LocationResolver<PlatformNodeLocation> {

    @Override
    public PlatformNodeLocation absolutize(PlatformNodeLocation base, PlatformNodeLocation location) {
        PlatformPath basePath = base.getPlatformPath();
        if (basePath.equals(PlatformPath.empty())) {
            return location;
        } else {
            PlatformPath path = ((PlatformNodeLocation) location).getPlatformPath();
            if (path.equals(PlatformPath.empty())) {
                /* use base path */
                return base;
            } else {
                /* combine the two */
                PlatformPath absPath = PlatformPath.builder().segments(basePath).segments(path).build();
                return new PlatformNodeLocation(absPath);
            }
        }
    }

    @Override
    public boolean isParent(PlatformNodeLocation parent, PlatformNodeLocation child) {
        return parent.getPlatformPath().isParentOf(child.getPlatformPath());
    }

    @Override
    public boolean matches(PlatformNodeLocation query, PlatformNodeLocation location) {
        return query.getPlatformPath().apply(((PlatformNodeLocation) query).getPlatformPath());
    }

    @Override
    public String applyTemplate(String nameTemplate, PlatformNodeLocation location, String endpointName) {
        String name = location.getPlatformPath().getLastSegment().getName();
        return String.format(nameTemplate, name);
    }

}
