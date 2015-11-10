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
package org.hawkular.agent.monitor.protocol;

import java.util.Collection;
import java.util.Map;
import java.util.Set;

import org.hawkular.agent.monitor.inventory.AttributeLocation;
import org.hawkular.agent.monitor.inventory.AvailType;
import org.hawkular.agent.monitor.inventory.ID;
import org.hawkular.agent.monitor.inventory.InventoryIdUtil;
import org.hawkular.agent.monitor.inventory.MeasurementInstance;
import org.hawkular.agent.monitor.inventory.MetricType;
import org.hawkular.agent.monitor.inventory.Name;
import org.hawkular.agent.monitor.inventory.NodeLocation;
import org.hawkular.agent.monitor.inventory.Resource;
import org.hawkular.agent.monitor.inventory.Resource.Builder;
import org.hawkular.agent.monitor.inventory.ResourceConfigurationPropertyInstance;
import org.hawkular.agent.monitor.inventory.ResourceConfigurationPropertyType;
import org.hawkular.agent.monitor.inventory.ResourceType;
import org.hawkular.agent.monitor.log.AgentLoggers;
import org.hawkular.agent.monitor.log.MsgLogger;
import org.hawkular.agent.monitor.util.Consumer;

/**
 * A bunch of discovery methods.
 *
 * @author <a href="https://github.com/ppalaga">Peter Palaga</a>
 *
 * @param <L> the type of the protocol specific location, typically a subclass of {@link NodeLocation}
 */
public final class Discovery<L> {

    private static final MsgLogger log = AgentLoggers.getLogger(Discovery.class);

    private <N> void addMetricAndAvailInstances(ID resourceId, ResourceType<L> type,
            L parentLocation, N baseNode,
            Resource.Builder<L> builder, Session<L> session) {

        for (MetricType<L> metricType : type.getMetricTypes()) {
            AttributeLocation<L> location = metricType.getAttributeLocation();
            try {
                final AttributeLocation<L> instanceLocation =
                        session.getLocationResolver().absolutize(parentLocation, location);
                if (session.getDriver().attributeExists(instanceLocation)) {
                    ID id = InventoryIdUtil.generateMetricInstanceId(resourceId, metricType);
                    Name name = metricType.getName();

                    MeasurementInstance<L, MetricType<L>> metricInstance = new MeasurementInstance<>(id, name,
                            instanceLocation, metricType);
                    builder.metric(metricInstance);
                }
            } catch (ProtocolException e) {
                log.warnFailedToLocate(e, metricType.getClass().getName(), String.valueOf(location),
                        String.valueOf(parentLocation));
            }
        }

        for (AvailType<L> availType : type.getAvailTypes()) {
            AttributeLocation<L> location = availType.getAttributeLocation();
            try {
                final AttributeLocation<L> instanceLocation =
                        session.getLocationResolver().absolutize(parentLocation, location);
                if (session.getDriver().attributeExists(instanceLocation)) {
                    ID id = InventoryIdUtil.generateAvailInstanceId(resourceId, availType);
                    Name name = availType.getName();

                    MeasurementInstance<L, AvailType<L>> availInstance = new MeasurementInstance<>(id, name,
                            instanceLocation, availType);
                    builder.avail(availInstance);
                }
            } catch (ProtocolException e) {
                log.warnFailedToLocate(e, availType.getClass().getName(), String.valueOf(location),
                        String.valueOf(parentLocation));
            }
        }
    }

    /**
     * Performs the discovery and stores the discovered inventory in this object's inventory manager. This discovers a
     * tree with parent resources at the top of the tree and children at the bottom (that is to say, a resource will
     * have an outgoing edge to its parent and incoming edges from its children).
     *
     * @param listener if not null, will be a listener that gets notified when resources are discovered
     *
     * @throws Exception if discovery failed
     */
    public void discoverAllResources(Session<L> session, Consumer<Resource<L>> resourceConsumer) {

        Set<ResourceType<L>> rootTypes = session.getResourceTypeManager().getRootResourceTypes();

        for (ResourceType<L> rootType : rootTypes) {
            discoverChildren(null, rootType, session, resourceConsumer);
        }

    }

    public <N> void discoverChildren(Resource<L> parent, ResourceType<L> childType, Session<L> session,
            Consumer<Resource<L>> resourceConsumer) {

        try {

            L parentLocation = parent != null ? parent.getLocation() : null;
            log.debugf("Discovering children of [%s] of type [%s]", parent, childType);
            final L childQuery = session.getLocationResolver().absolutize(parentLocation, childType.getLocation());
            Map<L, N> nativeResources = session.getDriver().fetchNodes(childQuery);

            for (Map.Entry<L, N> entry : nativeResources.entrySet()) {
                L location = entry.getKey(); // this is the unique DMR address for this resource
                String resourceName = session.getLocationResolver().applyTemplate(childType.getResourceNameTemplate(),
                        location, session.getEndpoint().getName());
                ID id = InventoryIdUtil.generateResourceId(
                        session.getFeedId(),
                        session.getEndpoint(),
                        location.toString());
                Builder<L> builder = Resource.<L> builder() //
                        .id(id) //
                        .name(new Name(resourceName))
                        .location(location)
                        .type(childType);

                if (parent != null) {
                    builder.parent(parent);
                }

                // get the configuration of the resource
                discoverResourceConfiguration(id, childType, location, entry.getValue(), builder, session);

                // populate the metrics/avails based on the resource's type
                addMetricAndAvailInstances(id, childType, location, entry.getValue(), builder, session);

                Resource<L> resource = builder.build();
                log.debugf("Discovered resource [%s]", resource);
                resourceConsumer.accept(resource);

                // recursively discover children of child types
                Set<ResourceType<L>> childTypes = session.getResourceTypeManager()
                        .getChildren(childType);
                for (ResourceType<L> nextLevelChildType : childTypes) {
                    discoverChildren(resource, nextLevelChildType, session, resourceConsumer);
                }

            }
        } catch (Exception e) {
            log.errorf(e, "Failed to discover resources in [%s]", session.getEndpoint());
        }
    }

    private <N> void discoverResourceConfiguration(ID resourceId, ResourceType<L> type,
            L parentAddress, N baseNode,
            Resource.Builder<L> builder, Session<L> session) {
        Collection<ResourceConfigurationPropertyType<L>> configPropTypes = type
                .getResourceConfigurationPropertyTypes();
        for (ResourceConfigurationPropertyType<L> configPropType : configPropTypes) {
            try {
                AttributeLocation<L> location = configPropType.getAttributeLocation();
                final AttributeLocation<L> instanceLocation =
                        session.getLocationResolver().absolutize(parentAddress, location);
                Object o = session.getDriver().fetchAttribute(instanceLocation);
                String val = o == null ? null : o.toString();

                ResourceConfigurationPropertyInstance<L> cpi = new ResourceConfigurationPropertyInstance<>(
                        ID.NULL_ID, configPropType.getName(), instanceLocation, configPropType, val);

                builder.resourceConfigurationProperty(cpi);
            } catch (Exception e) {
                log.warnf(e, "Failed to discover config [%s] for resource [%s]", configPropType, parentAddress);
            }
        }
    }

}
