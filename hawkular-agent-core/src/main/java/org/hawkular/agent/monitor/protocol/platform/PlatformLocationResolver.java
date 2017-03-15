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
package org.hawkular.agent.monitor.protocol.platform;

import java.util.List;

import org.hawkular.agent.monitor.protocol.LocationResolver;
import org.hawkular.agent.monitor.protocol.ProtocolException;
import org.hawkular.agent.monitor.protocol.platform.PlatformPath.PathSegment;

/**
 * @author <a href="https://github.com/ppalaga">Peter Palaga</a>
 * @see LocationResolver
 */
public class PlatformLocationResolver implements LocationResolver<PlatformNodeLocation> {

    @Override
    public String findWildcardMatch(PlatformNodeLocation multiTargetLocation, PlatformNodeLocation singleLocation)
            throws ProtocolException {
        List<PathSegment> multiTargetPaths = multiTargetLocation.getPlatformPath().getSegments();
        for (int i = 0; i < multiTargetPaths.size(); i++) {
            PathSegment multiTargetPathElement = multiTargetPaths.get(i);
            if (PlatformPath.ANY_NAME.equals(multiTargetPathElement.getName())) {
                PathSegment singleLocationPathElement;
                try {
                    singleLocationPathElement = singleLocation.getPlatformPath().getSegments().get(i);
                } catch (Exception e) {
                    throw new ProtocolException(String.format("[%s] doesn't have the same path size as [%s]",
                            singleLocation, multiTargetLocation));
                }

                // DMR wildcards are only in names, not types ("File Store=*" not "*=/usr")
                if (singleLocationPathElement.getType().equals(multiTargetPathElement.getType())) {
                    return singleLocationPathElement.getName();
                } else {
                    throw new ProtocolException(String.format("[%s] doesn't match the multi-target key in [%s]",
                            singleLocation, multiTargetLocation));
                }
            }
        }

        // nothing matched - single location must not have resulted from a query using the given multi-target location
        throw new ProtocolException(String.format("[%s] doesn't match the wildcard from [%s]", singleLocation,
                multiTargetLocation));
    }

    @Override
    public boolean isMultiTarget(PlatformNodeLocation location) {
        for (PathSegment segment : location.getPlatformPath().getSegments()) {
            if (PlatformPath.ANY_NAME.equals(segment.getName())) {
                return true;
            }
        }
        return false;
    }

    @Override
    public PlatformNodeLocation absolutize(PlatformNodeLocation base, PlatformNodeLocation location) {
        if (base == null || base.getPlatformPath().equals(PlatformPath.empty())) {
            return location;
        } else {
            PlatformPath path = ((PlatformNodeLocation) location).getPlatformPath();
            if (path.equals(PlatformPath.empty())) {
                /* use base path */
                return base;
            } else {
                /* combine the two */
                PlatformPath absPath = PlatformPath.builder().segments(base.getPlatformPath()).segments(path).build();
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
