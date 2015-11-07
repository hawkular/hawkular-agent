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

import java.util.Hashtable;
import java.util.Iterator;
import java.util.Map;

import javax.management.MalformedObjectNameException;
import javax.management.ObjectName;

import org.hawkular.agent.monitor.protocol.LocationResolver;

/**
 * @author <a href="https://github.com/ppalaga">Peter Palaga</a>
 * @see LocationResolver
 */
public class JMXLocationResolver implements LocationResolver<JMXNodeLocation> {

    @Override
    public JMXNodeLocation absolutize(JMXNodeLocation base, JMXNodeLocation location) {
        return location;
    }

    @Override
    public boolean isParent(JMXNodeLocation parent, JMXNodeLocation child) {

        if (child == null) {
            throw new IllegalArgumentException(
                    "Cannot compute [" + getClass().getName() + "].isParentOf() with a null child argument");
        }
        if (parent.getCanonicalKeys().size() > child.getCanonicalKeys().size()) {
            /* this cannot be a patternt if it is longer than the child */
            return false;
        }


        int prefixLength = parent.getCanonicalKeys().size();
        ObjectName childObjectName = child.getObjectName();
        if (prefixLength == child.getCanonicalKeys().size()) {
            /* simple match for the same size */
            return parent.getObjectName().apply(childObjectName);
        }

        /* child is longer than this: let's cut the prefix out of child and match against this  */
        Hashtable<String, String> prefixProps = new Hashtable<>(prefixLength + prefixLength / 2);
        int i = 0;
        Iterator<String> it = child.getCanonicalKeys().iterator();
        while (i < prefixLength && it.hasNext()) {
            String key = it.next();
            prefixProps.put(key, childObjectName.getKeyProperty(key));
            i++;
        }
        try {
            ObjectName prefixObjectName = new ObjectName(childObjectName.getDomain(), prefixProps);
            return parent.getObjectName().apply(prefixObjectName);
        } catch (MalformedObjectNameException e) {
            throw new RuntimeException(e);
        }
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
