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

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

import org.hawkular.agent.monitor.inventory.AttributeLocation;
import org.hawkular.agent.monitor.protocol.Driver;
import org.hawkular.agent.monitor.protocol.ProtocolException;
import org.hawkular.agent.monitor.protocol.platform.api.Platform;
import org.hawkular.agent.monitor.protocol.platform.api.PlatformPath;
import org.hawkular.agent.monitor.protocol.platform.api.PlatformResourceNode;

/**
 * @author <a href="https://github.com/ppalaga">Peter Palaga</a>
 * @see Driver
 */
public class PlatformDriver
        implements Driver<PlatformNodeLocation> {

    private final Platform platform;

    public PlatformDriver(Platform platform) {
        super();
        this.platform = platform;
    }

    @SuppressWarnings("unchecked")
    @Override
    public Map<PlatformNodeLocation, PlatformResourceNode> fetchNodes(PlatformNodeLocation location)
            throws ProtocolException {
        try {
            Map<PlatformPath, PlatformResourceNode> children = platform.getChildren(location.getPlatformPath());
            Map<PlatformNodeLocation, PlatformResourceNode> result = new HashMap<>();
            for (Entry<PlatformPath, PlatformResourceNode> en : children.entrySet()) {
                result.put(new PlatformNodeLocation(en.getKey()), en.getValue());
            }
            return Collections.unmodifiableMap(result);
        } catch (Exception e) {
            throw new ProtocolException(e);
        }
    }

    @Override
    public boolean attributeExists(AttributeLocation<PlatformNodeLocation> location) throws ProtocolException {
        Map<PlatformPath, PlatformResourceNode> nodes = platform.getChildren(location.getLocation().getPlatformPath());
        switch (nodes.size()) {
            case 0:
                return false;
            case 1:
                return nodes.values().iterator().next().getAttributeNames().contains(location.getAttribute());
            default:
                throw new ProtocolException(
                        "Platform Path [" + location.getLocation().getPlatformPath() + "] is not unique");
        }
    }

    @Override
    public Object fetchAttribute(AttributeLocation<PlatformNodeLocation> location) throws ProtocolException {
        Map<PlatformPath, PlatformResourceNode> nodes = platform.getChildren(location.getLocation().getPlatformPath());
        switch (nodes.size()) {
            case 0:
                return null;
            case 1:
                return nodes.values().iterator().next().getAttribute(location.getAttribute());
            default:
                List<Object> results = new ArrayList<>(nodes.size());
                for (PlatformResourceNode node : nodes.values()) {
                    results.add(node.getAttribute(location.getAttribute()));
                }
                return Collections.unmodifiableList(results);
        }
    }

}
