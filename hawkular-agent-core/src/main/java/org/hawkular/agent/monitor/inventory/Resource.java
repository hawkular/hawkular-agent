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
package org.hawkular.agent.monitor.inventory;

import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

/**
 * Identifies a managed resource and contains all of its related data.
 *
 * @author John Mazzitelli
 *
 * @param <L> the type of the protocol specific location, typically a subclass of {@link NodeLocation}
 */
public final class Resource<L> extends NodeLocationProvider<L> {

    public static class Builder<L>
            extends NodeLocationProvider.Builder<Builder<L>, L> {
        private ResourceType<L> resourceType;
        private Resource<L> parent;
        private Set<MeasurementInstance<L, MetricType<L>>> metrics = new HashSet<>();
        private Set<MeasurementInstance<L, AvailType<L>>> avails = new HashSet<>();
        private Set<ResourceConfigurationPropertyInstance<L>> resourceConfigurationProperties = new HashSet<>();

        private Builder() {
            super();
        }

        public Builder(Resource<L> template) {
            super(template);
            parent(template.getParent());
            type(template.getResourceType());
            for (MeasurementInstance<L, MetricType<L>> m : template.getMetrics()) {
                metric(new MeasurementInstance<>(m, true));
            }
            for (MeasurementInstance<L, AvailType<L>> a : template.getAvails()) {
                avail(new MeasurementInstance<>(a, true));
            }
            for (ResourceConfigurationPropertyInstance<L> r : template.getResourceConfigurationProperties()) {
                resourceConfigurationProperty(new ResourceConfigurationPropertyInstance<>(r, true));
            }
        }

        public Builder<L> parent(Resource<L> parent) {
            this.parent = parent;
            return this;
        }

        public Builder<L> type(ResourceType<L> type) {
            this.resourceType = type;
            return this;
        }

        public Builder<L> metric(MeasurementInstance<L, MetricType<L>> metric) {
            metrics.add(metric);
            return this;
        }

        public Builder<L> avail(MeasurementInstance<L, AvailType<L>> avail) {
            avails.add(avail);
            return this;
        }

        public Builder<L> resourceConfigurationProperty(
                ResourceConfigurationPropertyInstance<L> //
                resourceConfigurationProperty) {
            resourceConfigurationProperties.add(resourceConfigurationProperty);
            return this;
        }

        public Resource<L> build() {
            return new Resource<L>(id, name, location, resourceType, parent, Collections.unmodifiableSet(metrics),
                    Collections.unmodifiableSet(avails), Collections.unmodifiableSet(resourceConfigurationProperties));
        }
    }

    public static <L> Builder<L> builder() {
        return new Builder<L>();
    }

    /**
     * Creates a builder with the given resource as a starting template.
     * You can use this to clone a resource as well as build a resource
     * that looks similar to the given template resource.
     *
     * @param template start with the data found in the given template resource
     */
    public static <L> Builder<L> builder(Resource<L> template) {
        return new Builder<L>(template);
    }

    private final ResourceType<L> resourceType;
    private final Resource<L> parent;
    private final Set<MeasurementInstance<L, MetricType<L>>> metrics;
    private final Set<MeasurementInstance<L, AvailType<L>>> avails;
    private final Set<ResourceConfigurationPropertyInstance<L>> resourceConfigurationProperties;

    private Resource(ID id,
            Name name,
            L location,
            ResourceType<L> resourceType,
            Resource<L> parent,
            Set<MeasurementInstance<L, MetricType<L>>> metrics,
            Set<MeasurementInstance<L, AvailType<L>>> avails,
            Set<ResourceConfigurationPropertyInstance<L>> resourceConfigurationProperties) {
        super(id, name, location);
        this.resourceType = resourceType;
        this.parent = parent;
        this.metrics = metrics;
        this.avails = avails;
        this.resourceConfigurationProperties = resourceConfigurationProperties;
        assignToThisResource(this.metrics);
        assignToThisResource(this.avails);
        assignToThisResource(this.resourceConfigurationProperties);
    }

    private void assignToThisResource(Collection<? extends Instance<L, ?>> instances) {
        if (instances != null && !instances.isEmpty()) {
            for (Instance<L, ?> instance : instances) {
                instance.setResource(this);
            }
        }
    }

    public ResourceType<L> getResourceType() {
        return resourceType;
    }

    public Resource<L> getParent() {
        return parent;
    }

    public Collection<MeasurementInstance<L, MetricType<L>>> getMetrics() {
        return metrics;
    }

    public Collection<MeasurementInstance<L, AvailType<L>>> getAvails() {
        return avails;
    }

    public Collection<ResourceConfigurationPropertyInstance<L>> getResourceConfigurationProperties() {
        return resourceConfigurationProperties;
    }

    @Override
    public String toString() {
        return String.format("%s=[type=%s]", super.toString(), this.resourceType);
    }
}
