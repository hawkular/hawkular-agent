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

import java.util.Collections;
import java.util.List;
import java.util.Set;

import org.hawkular.agent.monitor.protocol.platform.api.PlatformResourceAttributeName;
import org.hawkular.agent.monitor.protocol.platform.api.PlatformResourceNode;
import org.hawkular.agent.monitor.protocol.platform.api.PlatformResourceTypeName;

/**
 * @author <a href="https://github.com/ppalaga">Peter Palaga</a>
 */
public abstract class OshiPlatformResourceNode<T> implements PlatformResourceNode {

    protected final T delegate;

    public OshiPlatformResourceNode(T delegate) {
        super();
        this.delegate = delegate;
    }

    public Object getAttribute(String attributeName) {
        throw new IllegalArgumentException(
                "[" + getClass().getName() + "] does not have attribute [" + attributeName + "]");
    }

    public Set<String> getAttributeNames() {
        return Collections.emptySet();
    }

    public List<PlatformResourceNode> getChildren(PlatformResourceTypeName typeName) {
        throw new IllegalArgumentException(
                "[" + getClass().getName() + "] does not have children of type [" + typeName + "]");
    }

    public Set<PlatformResourceTypeName> getChildTypeNames() {
        return Collections.emptySet();
    }

    @Override
    public String getName() {
        return String.valueOf(getAttribute(PlatformResourceAttributeName.NAME));
    }

    @Override
    public PlatformResourceNode getChild(PlatformResourceTypeName typeName, String name) {
        List<PlatformResourceNode> children = getChildren(typeName);
        for (PlatformResourceNode ch : children) {
            if (name.equals(ch.getName())) {
                return ch;
            }
        }
        throw new IllegalArgumentException("This [" + getClass().getName() + "] does not have a child of type ["
                + typeName + "] with name [" + name + "]");
    }

}
