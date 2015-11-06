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

import java.util.Set;

import org.hawkular.agent.monitor.protocol.platform.api.PlatformResourceAttributeName;

import oshi.hardware.Memory;

/**
 * @author <a href="https://github.com/ppalaga">Peter Palaga</a>
 */
public class OshiMemoryNode extends OshiPlatformResourceNode<Memory> {

    public OshiMemoryNode(Memory delegate) {
        super(delegate);
    }

    /** @see org.hawkular.agent.monitor.protocol.platform.api.PlatformResourceNode#getAttribute(String) */
    @Override
    public Object getAttribute(String attributeName) {
        switch (PlatformResourceAttributeName.MemoryAttribute.valueOfOrFail(attributeName)) {
            case total:
                return delegate.getTotal();
            case available:
                return delegate.getAvailable();
        }
        return super.getAttribute(attributeName);
    }

    /** @see org.hawkular.agent.monitor.protocol.platform.api.PlatformResourceNode#getAttributeNames() */
    @Override
    public Set<String> getAttributeNames() {
        return PlatformResourceAttributeName.MemoryAttribute.stringSet();
    }

}
