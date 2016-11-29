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
package org.hawkular.agent.monitor.protocol.jmx;

import java.util.Map;

import javax.management.ObjectName;

import org.hawkular.agent.monitor.protocol.LocationResolver;
import org.hawkular.agent.monitor.protocol.ProtocolException;

/**
 * @author <a href="https://github.com/ppalaga">Peter Palaga</a>
 * @see LocationResolver
 */
public class JMXLocationResolver implements LocationResolver<JMXNodeLocation> {

    @Override
    public String findWildcardMatch(JMXNodeLocation multiTargetLocation, JMXNodeLocation singleLocation)
            throws ProtocolException {

        if (multiTargetLocation == null) {
            throw new ProtocolException("multiTargetLocation is null");
        }

        for (String multiTargetPathKey : multiTargetLocation.getCanonicalKeys()) {
            String multiTargetPathValue = multiTargetLocation.getObjectName().getKeyProperty(multiTargetPathKey);
            if ("*".equals(multiTargetPathValue)) {
                String singleLocationPathValue = singleLocation.getObjectName().getKeyProperty(multiTargetPathKey);
                if (singleLocationPathValue != null) {
                    return singleLocationPathValue;
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
    public boolean isMultiTarget(JMXNodeLocation location) {
        return location.getObjectName().isPattern();
    }

    @Override
    public JMXNodeLocation absolutize(JMXNodeLocation base, JMXNodeLocation location) {
        return (location != null && location.getObjectName() != null) ? location : base;
    }

    @Override
    public boolean isParent(JMXNodeLocation parent, JMXNodeLocation child) {

        if (parent == null) {
            throw new IllegalArgumentException(
                    "Cannot compute [" + getClass().getName() + "].isParent() with a null parent argument");
        }

        if (child == null) {
            throw new IllegalArgumentException(
                    "Cannot compute [" + getClass().getName() + "].isParent() with a null child argument");
        }

        ObjectName parentObjectName = parent.getObjectName();
        ObjectName childObjectName = child.getObjectName();

        // no sense continuing if they aren't even in the same JMX domain
        if (!parentObjectName.getDomain().equals(childObjectName.getDomain())) {
            return false;
        }

        int parentKeyCount = parent.getCanonicalKeys().size();
        int childKeyCount = child.getCanonicalKeys().size();

        // if the number of parent keys are greater than the number of child keys, it can't be the child's parent
        if (parentKeyCount >= childKeyCount) {
            return false;
        }

        // if the child has all the parent keys and matches the parent's key values, its a child
        for (String parentKey : parent.getCanonicalKeys()) {
            String parentKeyValue = parentObjectName.getKeyProperty(parentKey);
            if (!parentKeyValue.equals(childObjectName.getKeyProperty(parentKey))) {
                return false; // it can't possibly be a child since it doesn't match
            }
        }

        return true;
     }

    @Override
    public boolean matches(JMXNodeLocation query, JMXNodeLocation location) {
        return query.getObjectName().apply(location.getObjectName());
    }

    @Override
    public String applyTemplate(String nameTemplate, JMXNodeLocation location, String endpointName) {
        // The name template can have %X% where X is a key in the object name.
        // This will be substituted with the value of that key in the resource name.
        // For example, suppose a resource has an object name of "domain:abc=xyz" and the template is "Name [%abc%]".
        // The %abc% will get substituted with the value of that "abc" key from the object name
        // (in this case, the value is "xyz") so the resource name would be generated as "Name [xyz]".
        // Also supported is the substitution key "%_ManagedServerName%" which can help distinguish similar
        // resources running in different servers.
        nameTemplate = nameTemplate.replace("%_ManagedServerName%", endpointName);
        for (Map.Entry<String, String> entry : location.getObjectName().getKeyPropertyList().entrySet()) {
            if (nameTemplate.indexOf("%") == -1) {
                break; // no sense continuing if the nameTemplate doesn't have any tokens left
            }
            String key = entry.getKey();
            String value = entry.getValue();
            nameTemplate = nameTemplate.replace("%" + key + "%", value);
        }
        return nameTemplate;
    }

}
