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

import java.util.ArrayList;
import java.util.List;

import org.hawkular.agent.monitor.protocol.LocationResolver;
import org.hawkular.agent.monitor.protocol.ProtocolException;
import org.jboss.as.controller.PathAddress;
import org.jboss.as.controller.PathElement;
import org.jboss.dmr.ModelNode;
import org.jboss.dmr.Property;

/**
 * @author <a href="https://github.com/ppalaga">Peter Palaga</a>
 * @see LocationResolver
 */
public class DMRLocationResolver implements LocationResolver<DMRNodeLocation> {
    private static boolean matches(int length, PathAddress pattern, PathAddress address) {
        for (int i = 0; i < length; i++) {
            PathElement otherElem = address.getElement(i);
            Property prop = new Property(otherElem.getKey(), new ModelNode(otherElem.getValue()));
            if (!pattern.getElement(i).matches(prop)) {
                return false;
            }
        }
        return true;
    }

    @Override
    public DMRNodeLocation buildLocation(String path) throws Exception {
        return DMRNodeLocation.of(path);
    }

    @Override
    public String findWildcardMatch(DMRNodeLocation multiTargetLocation, DMRNodeLocation singleLocation)
            throws ProtocolException {

        if (multiTargetLocation == null) {
            throw new ProtocolException("multiTargetLocation is null");
        }

        PathAddress multiTargetPaths = multiTargetLocation.getPathAddress();
        for (int i = 0; i < multiTargetPaths.size(); i++) {
            PathElement multiTargetPathElement = multiTargetPaths.getElement(i);
            if (multiTargetPathElement.isWildcard()) {
                PathElement singleLocationPathElement;
                try {
                    singleLocationPathElement = singleLocation.getPathAddress().getElement(i);
                } catch (Exception e) {
                    throw new ProtocolException(String.format("[%s] doesn't have the same path size as [%s]",
                            singleLocation, multiTargetLocation));
                }

                // DMR wildcards are only in values ("/name=*" not "/*=value")
                if (singleLocationPathElement.getKey().equals(multiTargetPathElement.getKey())) {
                    return singleLocationPathElement.getValue();
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
    public boolean isMultiTarget(DMRNodeLocation location) {
        return location.getPathAddress().isMultiTarget();
    }

    @Override
    public DMRNodeLocation absolutize(DMRNodeLocation base, DMRNodeLocation location) {
        if (base == null || base.getPathAddress().equals(PathAddress.EMPTY_ADDRESS)) {
            return location;
        } else if (location == null) {
            return base;
        } else {
            PathAddress basePath = base.getPathAddress();
            PathAddress path = ((DMRNodeLocation) location).getPathAddress();
            if (path.equals(PathAddress.EMPTY_ADDRESS)) {
                // use base path, but retain the location's resolve-expressions/include-defaults settings
                return new DMRNodeLocation(base.getPathAddress(),
                        location.getResolveExpressions(), location.getIncludeDefaults());
            } else {
                // combine the two - retaining the location's resolve-expressions/include-defaults settings
                List<PathElement> segments = new ArrayList<>(basePath.size() + path.size());
                for (PathElement segment : basePath) {
                    segments.add(segment);
                }
                for (PathElement segment : path) {
                    segments.add(segment);
                }
                PathAddress absPath = PathAddress.pathAddress(segments);
                return new DMRNodeLocation(absPath,
                        location.getResolveExpressions(), location.getIncludeDefaults());
            }
        }
    }

    @Override
    public boolean isParent(DMRNodeLocation parent, DMRNodeLocation child) {
        if (parent == null) {
            throw new IllegalArgumentException(
                    "Cannot compute [" + getClass().getName() + "].isParent() with a null parent argument");
        }

        if (child == null) {
            throw new IllegalArgumentException(
                    "Cannot compute [" + getClass().getName() + "].isParent() with a null child argument");
        }

        PathAddress parentPath = parent.getPathAddress();
        PathAddress childPath = child.getPathAddress();
        int parentLength = parentPath.size();
        if (parentLength < childPath.size()) {
            return matches(parentLength, parentPath, childPath);
        } else {
            return false;
        }
    }

    @Override
    public boolean matches(DMRNodeLocation query, DMRNodeLocation location) {
        if (query == null) {
            throw new IllegalArgumentException(
                    "Cannot compute [" + getClass().getName() + "].matches() with a null query argument");
        }
        PathAddress queryPath = query.getPathAddress();
        PathAddress path = location.getPathAddress();

        int queryLength = queryPath.size();
        if (queryLength == path.size()) {
            return matches(queryLength, queryPath, path);
        } else {
            return false;
        }
    }

    @Override
    public String applyTemplate(String nameTemplate, DMRNodeLocation location, String endpointName) {

        // The name template can have %# where # is the index number of the address part that should be substituted.
        // For example, suppose a resource has an address of "/hello=world/foo=bar" and the template is "Name [%2]".
        // The %2 will get substituted with the second address part (which is "world" - indices start at 1).
        // The name template can have %key% where key is the key of an address whose value is used to replace the
        // token. For example, "Name [%foo%]" will end up being "Name [bar]".
        //
        // String.format() requires "$s" after the "%#" to denote the type of value is a string (all our address
        // parts are strings, so we know "$s" is what we want).
        // This replaceAll just replaces all occurrances of "%#" with "%#$s" so String.format will work.
        // We also allow for the special %- notation to mean "the last address part" since that's usually the one we
        // want and sometimes you can't know its positional value.
        // We also support %ManagedServerName which can help distinguish similar resources running in different servers.
        List<String> args = new ArrayList<>();
        for (PathElement segment : location.getPathAddress()) {
            args.add(segment.getKey());
            args.add(segment.getValue());
            nameTemplate = nameTemplate.replace("%" + segment.getKey() + "%", segment.getValue());
        }
        nameTemplate = nameTemplate.replaceAll("%(\\d+)", "%$1\\$s");
        nameTemplate = nameTemplate.replaceAll("%(-)", "%" + args.size() + "\\$s");
        nameTemplate = nameTemplate.replaceAll("%ManagedServerName", endpointName);
        String nameStr = String.format(nameTemplate, args.toArray());
        return nameStr;
    }
}
