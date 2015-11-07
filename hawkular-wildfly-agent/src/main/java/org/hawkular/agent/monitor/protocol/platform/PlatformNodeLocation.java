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

import org.hawkular.agent.monitor.inventory.NodeLocation;
import org.hawkular.agent.monitor.protocol.platform.api.PlatformPath;

/**
 * A {@link NodeLocation} based on {@link PlatformPath}.
 *
 * @author <a href="https://github.com/ppalaga">Peter Palaga</a>
 */
public class PlatformNodeLocation implements NodeLocation {

    private final PlatformPath platformPath;

    public PlatformNodeLocation(PlatformPath platformPath) {
        super();
        if (platformPath == null) {
            throw new IllegalArgumentException(
                    "Cannot create a new [" + getClass().getName() + "] with a null platformPath");
        }
        this.platformPath = platformPath;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj)
            return true;
        if (obj == null)
            return false;
        if (getClass() != obj.getClass())
            return false;
        PlatformNodeLocation other = (PlatformNodeLocation) obj;
        if (platformPath == null) {
            if (other.platformPath != null)
                return false;
        } else if (!platformPath.equals(other.platformPath))
            return false;
        return true;
    }

    public PlatformPath getPlatformPath() {
        return platformPath;
    }

    @Override
    public int hashCode() {
        return platformPath.hashCode();
    }

    @Override
    public String toString() {
        return platformPath.toString();
    }
}
