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
package org.hawkular.agent.monitor.protocol.dmr;

import org.hawkular.agent.monitor.inventory.NodeLocation;
import org.hawkular.agent.monitor.util.WildflyCompatibilityUtils;
import org.jboss.as.controller.PathAddress;
import org.jboss.dmr.ModelNode;

/**
 * A {@link NodeLocation} based on {@link PathAddress} from WildFly.
 *
 * @author <a href="https://github.com/ppalaga">Peter Palaga</a>
 */
public class DMRNodeLocation implements NodeLocation {
    private static final DMRNodeLocation EMPTY = new DMRNodeLocation(PathAddress.EMPTY_ADDRESS, true, true);

    public static DMRNodeLocation empty() {
        return EMPTY;
    }

    public static DMRNodeLocation of(ModelNode addressNode) {
        return of(addressNode, true, true);
    }

    public static DMRNodeLocation of(ModelNode addressNode, boolean resolveExpressions, boolean includeDefaults) {
        return new DMRNodeLocation(PathAddress.pathAddress(addressNode), resolveExpressions, includeDefaults);
    }

    public static DMRNodeLocation of(String path) {
        return of(path, true, true);
    }

    public static DMRNodeLocation of(String path, boolean resolveExpressions, boolean includeDefaults) {
        return new DMRNodeLocation(
                "/".equals(path) ? PathAddress.EMPTY_ADDRESS : WildflyCompatibilityUtils.parseCLIStyleAddress(path),
                resolveExpressions, includeDefaults);
    }

    private final PathAddress pathAddress;
    private final boolean resolveExpressions;
    private final boolean includeDefaults;

    public DMRNodeLocation(PathAddress pathAddress) {
        this(pathAddress, true, true);
    }

    public DMRNodeLocation(PathAddress pathAddress, boolean resolveExpressions, boolean includeDefaults) {
        super();
        if (pathAddress == null) {
            throw new IllegalArgumentException(
                    "Cannot create a new [" + getClass().getName() + "] with a null pathAddress");
        }
        this.pathAddress = pathAddress;
        this.resolveExpressions = resolveExpressions;
        this.includeDefaults = includeDefaults;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj)
            return true;
        if (obj == null)
            return false;
        if (getClass() != obj.getClass())
            return false;
        DMRNodeLocation other = (DMRNodeLocation) obj;
        if (pathAddress == null) {
            if (other.pathAddress != null)
                return false;
        } else if (!pathAddress.equals(other.pathAddress))
            return false;
        return true;
    }

    /**
     * @return a DMR path relative or absolute
     */
    public PathAddress getPathAddress() {
        return pathAddress;
    }

    @Override
    public int hashCode() {
        return pathAddress.hashCode();
    }

    @Override
    public String toString() {
        return pathAddress.toCLIStyleString();
    }

    public boolean getResolveExpressions() {
        return this.resolveExpressions;
    }

    public boolean getIncludeDefaults() {
        return this.includeDefaults;
    }
}
