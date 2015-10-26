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
package org.hawkular.agent.monitor.protocol.platform.api;

import java.util.Map;
import java.util.Set;

/**
 * @author <a href="https://github.com/ppalaga">Peter Palaga</a>
 */
public interface Platform extends PlatformResourceNode {

    @Override
    default Set<String> getAttributeNames() {
        return PlatformResourceAttributeName.PlatformAttribute.stringSet();
    }

    @Override
    default Set<PlatformResourceTypeName> getChildTypeNames() {
        return PlatformResourceTypeName.PlatformChildType.valueSet();
    }

    Map<PlatformPath, PlatformResourceNode> getChildren(PlatformPath path);
}
