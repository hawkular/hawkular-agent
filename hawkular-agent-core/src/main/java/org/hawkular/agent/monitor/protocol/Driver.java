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
package org.hawkular.agent.monitor.protocol;

import java.util.List;
import java.util.Map;

import org.hawkular.agent.monitor.inventory.AttributeLocation;
import org.hawkular.agent.monitor.inventory.MonitoredEndpoint;
import org.hawkular.agent.monitor.inventory.NodeLocation;

/**
 * An interface to encapsulate a protocol specific access to resources.
 *
 * @author <a href="https://github.com/ppalaga">Peter Palaga</a>
 *
 * @param <L> the type of the protocol specific location, typically a subclass of {@link NodeLocation}
 */
public interface Driver<L> {

    /**
     * Fetches nodes that match the given {@code query} from a {@link MonitoredEndpoint} and returns them as an
     * immutable {@link Map}. In a given entry in the returned map, the key is the location of the protocol specific
     * node object that is stored in the value of the entry.
     *
     * @param query a pattern location to query for native resource nodes
     * @return an immutable {@link Map} from locations to native resource nodes
     * @throws ProtocolException on any problems related to the retrieval
     */
    <N> Map<L, N> fetchNodes(L query) throws ProtocolException;

    /**
     * Fetches the attribute value specified by the given {@code attributeLocation} from a {@link MonitoredEndpoint} and
     * returns it. If {@link AttributeLocation#getLocation()} is a path pattern containing wildcards, then this method
     * MAY return a {@link List} of objects that contains the attribute values of matching attributes.
     *
     * @param attributeLocation the attribute to retrieve
     * @return the attribute value or null if there is no such attribute or if the attribute is unset.
     * @throws ProtocolException on any problems related to the retrieval
     */
    Object fetchAttribute(AttributeLocation<L> attributeLocation) throws ProtocolException;

    /**
     * Fetches the attribute value specified by the given {@code attributeLocation} from a {@link MonitoredEndpoint} and
     * returns it as the values in the given map.
     *
     * Assumes the {@link AttributeLocation#getLocation()} is a multi-target path (e.g. it contains wildcards)
     * and returns a map of all attribute values keyed on the resource location. If attributeLocation is not
     * multi-targeted the map will just contain a single entry.
     *
     * Use this method (as opposed to {@link #fetchAttribute(AttributeLocation)}) if you need to know which
     * resource locations returned which attribute values. The values returned are the same in both methods, this
     * just allows you to correlate which location returned which value.
     *
     * @param attributeLocation the attribute to retrieve
     * @return the attribute value or null if there is no such attribute or if the attribute is unset.
     * @throws ProtocolException on any problems related to the retrieval
     */
    Map<L, Object> fetchAttributeAsMap(AttributeLocation<L> attributeLocation) throws ProtocolException;

    /**
     * Returns {@code true} if the given {@code attributeLocation} exists on a {@link MonitoredEndpoint} or
     * {@code false} otherwise. This method should return {@code true} for attributes that exist but are unset.
     *
     * @param attributeLocation the {@link AttributeLocation} to check
     * @return {@code true} or {@code false}
     * @throws ProtocolException on any problems related to the retrieval
     */
    boolean attributeExists(AttributeLocation<L> attributeLocation) throws ProtocolException;

}
