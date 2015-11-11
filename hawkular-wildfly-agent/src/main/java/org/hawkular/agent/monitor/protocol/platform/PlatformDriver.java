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

import org.hawkular.agent.monitor.diagnostics.ProtocolDiagnostics;
import org.hawkular.agent.monitor.inventory.AttributeLocation;
import org.hawkular.agent.monitor.inventory.Name;
import org.hawkular.agent.monitor.protocol.Driver;
import org.hawkular.agent.monitor.protocol.ProtocolException;

import com.codahale.metrics.Timer.Context;

/**
 * @author <a href="https://github.com/ppalaga">Peter Palaga</a>
 * @see Driver
 */
public class PlatformDriver implements Driver<PlatformNodeLocation> {

    private final OshiPlatformCache platform;
    private final ProtocolDiagnostics diagnostics;

    public PlatformDriver(OshiPlatformCache platform, ProtocolDiagnostics diagnostics) {
        this.platform = platform;
        this.diagnostics = diagnostics;
    }

    @SuppressWarnings("unchecked")
    @Override
    public Map<PlatformNodeLocation, PlatformResourceNode> fetchNodes(PlatformNodeLocation location)
            throws ProtocolException {
        try {
            Map<PlatformPath, PlatformResourceNode> children = platform.discoverResources(location.getPlatformPath());
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
        Map<PlatformPath, PlatformResourceNode> nodes = platform
                .discoverResources(location.getLocation().getPlatformPath());
        switch (nodes.size()) {
            case 0:
                return false;
            case 1:
                return nodes.values().iterator().next().getType().getMetricNames()
                        .contains(new Name(location.getAttribute()));
            default:
                throw new ProtocolException(
                        "Platform Path [" + location.getLocation().getPlatformPath() + "] is not unique");
        }
    }

    @Override
    public Object fetchAttribute(AttributeLocation<PlatformNodeLocation> location) throws ProtocolException {
        try {
            Map<PlatformPath, PlatformResourceNode> nodes = null;
            Name metricToCollect = new Name(location.getAttribute()); // we know these are all metrics (no avails)
            try (Context timerContext = diagnostics.getRequestTimer().time()) {
                nodes = platform.discoverResources(location.getLocation().getPlatformPath());
                switch (nodes.size()) {
                    case 0:
                        return null;
                    case 1:
                        return platform.getMetric(nodes.values().iterator().next(), metricToCollect);
                    default:
                        List<Object> results = new ArrayList<>(nodes.size());
                        for (PlatformResourceNode node : nodes.values()) {
                            results.add(platform.getMetric(node, metricToCollect));
                        }
                        return Collections.unmodifiableList(results);
                }
            }
        } catch (Exception e) {
            diagnostics.getErrorRate().mark(1);
            throw new ProtocolException(e);
        }
    }

}
