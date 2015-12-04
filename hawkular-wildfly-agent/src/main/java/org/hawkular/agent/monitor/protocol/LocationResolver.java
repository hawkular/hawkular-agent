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

import org.hawkular.agent.monitor.inventory.AttributeLocation;
import org.hawkular.agent.monitor.inventory.NodeLocation;

/**
 * A bunch of methods to manipulate with protocol specific locations. Note that all of the provided operations should be
 * available offline.
 *
 * @author <a href="https://github.com/ppalaga">Peter Palaga</a>
 *
 * @param <L> the type of the protocol specific location, typically a subclass of {@link NodeLocation}
 */
public interface LocationResolver<L> {
    /**
     * Given a multi-target location (e.g. a location with a wildcard) and a single location,
     * this returns that portion of the single location that matches the multi-target wildcard.
     *
     * @param multiTargetLocation a query that was used to find the given singleLocation
     * @param singleLocation a single absolute location that has the multiTargetLocation wildcard resolved
     * @return the portion of the single location that matches the multi-target location wildcard
     * @throws ProtocolException if the single location doesn't have any matching portion
     */
    String findWildcardMatch(L multiTargetLocation, L singleLocation) throws ProtocolException;

    /**
     * Returns true if the location could refer to more than one target. For example,
     * this typically will return true if the location has a wildcard in it.
     * Note that just because this returns true does not mean there are multiple targets; there
     * could be 0 or 1 or more. It depends on what the wildcard matches.
     *
     * @param location the location to test
     *
     * @return <code>true</code> if location can refer to multiple targets,
     *         false if it refers to a single location
     */
    boolean isMultiTarget(L location);

    /**
     * Resolves the given {@code location} relative to the given {@code base} and returns the resolved location. Note
     * that this method can eventually return {@code location} or {@code base} if the resolution would yield a Location
     * equivalent to any of the two.
     *
     * @param base the base location to resolve {@code location} against
     * @param location the location to resolve
     * @return possibly a new resolved absolute location
     */
    L absolutize(L base, L location);

    default AttributeLocation<L> absolutize(L base, AttributeLocation<L> attributeLocation) {
        return attributeLocation.rebase(absolutize(base, attributeLocation.getLocation()));
    }

    boolean isParent(L parent, L child);

    boolean matches(L query, L location);

    String applyTemplate(String nameTemplate, L location, String endpointName);

}
