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
package org.hawkular.agent.monitor.protocol.platform.oshi;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import org.hawkular.agent.monitor.protocol.platform.api.Platform;
import org.hawkular.agent.monitor.protocol.platform.api.PlatformPath;
import org.hawkular.agent.monitor.protocol.platform.api.PlatformPath.PathSegment;
import org.hawkular.agent.monitor.protocol.platform.api.PlatformResourceAttributeName;
import org.hawkular.agent.monitor.protocol.platform.api.PlatformResourceNode;
import org.hawkular.agent.monitor.protocol.platform.api.PlatformResourceTypeName;

import oshi.SystemInfo;
import oshi.hardware.PowerSource;
import oshi.hardware.Processor;
import oshi.software.os.OSFileStore;

/**
 * @author <a href="https://github.com/ppalaga">Peter Palaga</a>
 */
public class OshiPlatform extends OshiPlatformResourceNode<SystemInfo> implements Platform {

    public OshiPlatform(SystemInfo systemInfo) {
        super(systemInfo);
    }

    @Override
    public Object getAttribute(String attributeName) {
        switch (PlatformResourceAttributeName.PlatformAttribute.valueOfOrFail(attributeName)) {
            case description:
                return delegate.getOperatingSystem().toString();
        }
        return super.getAttribute(attributeName);
    }

    @Override
    public List<PlatformResourceNode> getChildren(PlatformResourceTypeName typeName) {
        if (typeName instanceof PlatformResourceTypeName.PlatformChildType) {
            PlatformResourceTypeName.PlatformChildType type = (PlatformResourceTypeName.PlatformChildType) typeName;
            List<PlatformResourceNode> result = new ArrayList<>();
            switch (type) {
                case processor:
                    for (Processor item : delegate.getHardware().getProcessors()) {
                        result.add(new OshiProcessorNode(item));
                    }
                    break;
                case memory:
                    result.add(new OshiMemoryNode(delegate.getHardware().getMemory()));
                    break;
                case fileStore:
                    for (OSFileStore item : delegate.getHardware().getFileStores()) {
                        result.add(new OshiFileStoreNode(item));
                    }
                    break;
                case powerSource:
                    for (PowerSource item : delegate.getHardware().getPowerSources()) {
                        result.add(new OshiPowerSourceNode(item));
                    }
                    break;
            }
            return Collections.unmodifiableList(result);
        }
        return super.getChildren(typeName);
    }

    @Override
    public Map<PlatformPath, PlatformResourceNode> getChildren(PlatformPath path) {
        PlatformResourceNode node = this;
        for (Iterator<PathSegment> it = path.getSegments().iterator(); it.hasNext();) {
            PathSegment segment = it.next();
            if (it.hasNext()) {
                node = node.getChild(segment.getType(), segment.getName());
            } else {
                /* the last segment */
                if (PlatformPath.ANY_NAME.equals(segment.getName())) {
                    Map<PlatformPath, PlatformResourceNode> result = new HashMap<>();
                    for (PlatformResourceNode n : node.getChildren(segment.getType())) {
                        List<PathSegment> segs = path.getSegments();
                        PathSegment lastSegment = path.getLastSegment();
                        PlatformPath p = PlatformPath.builder().segments(segs, 0, segs.size() - 1)
                                .segment(lastSegment.getType(), n.getName()).build();
                        result.put(p, n);
                    }
                    return Collections.unmodifiableMap(result);
                } else {
                    return Collections.singletonMap(path, node.getChild(segment.getType(), segment.getName()));
                }
            }
        }
        throw new IllegalArgumentException("This [" + getClass().getName() + "] does not have a child path ["
                + path + "]");
    }

}
