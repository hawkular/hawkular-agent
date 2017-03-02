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

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

/**
 * @author John Mazzitelli
 *
 * @param <L> the type of the protocol specific location, typically a subclass of {@link NodeLocation}
 */
public final class ResourceType<L>
        extends NodeLocationProvider<L> {

    public static <L> Builder<Builder<?, L>, L> builder() {
        return new Builder<Builder<?, L>, L>();
    }

    public static class Builder<This extends Builder<?, L>, L>
            extends NodeLocationProvider.Builder<This, L> {

        private String resourceNameTemplate;
        private List<Name> parents = new ArrayList<>();
        private List<Name> metricSetNames = new ArrayList<>();
        private List<Name> availSetNames = new ArrayList<>();
        private List<MetricType<L>> metricTypes = new ArrayList<>();
        private List<AvailType<L>> availTypes = new ArrayList<>();
        private List<Operation<L>> operations = new ArrayList<>();
        private List<ResourceConfigurationPropertyType<L>> resourceConfigurationPropertyTypes = new ArrayList<>();

        private Builder() {
            super();
        }

        public ResourceType<L> build() {
            return new ResourceType<L>(id,
                    name,
                    location,
                    resourceNameTemplate,
                    Collections.unmodifiableList(parents),
                    Collections.unmodifiableList(metricSetNames),
                    Collections.unmodifiableList(availSetNames),
                    Collections.unmodifiableList(metricTypes),
                    Collections.unmodifiableList(availTypes),
                    Collections.unmodifiableList(operations),
                    Collections.unmodifiableList(resourceConfigurationPropertyTypes));
        }

        @SuppressWarnings("unchecked")
        private This getThis() {
            return (This) this;
        }

        public This availSetName(Name name) {
            this.availSetNames.add(name);
            return getThis();
        }

        public This availSetNames(Collection<Name> names) {
            this.availSetNames.addAll(names);
            return getThis();
        }

        public This metricSetName(Name name) {
            this.metricSetNames.add(name);
            return getThis();
        }

        public This metricSetNames(Collection<Name> names) {
            this.metricSetNames.addAll(names);
            return getThis();
        }

        public This availTypes(Collection<AvailType<L>> types) {
            this.availTypes.addAll(types);
            return getThis();
        }

        public This metricTypes(Collection<MetricType<L>> types) {
            this.metricTypes.addAll(types);
            return getThis();
        }

        public This operation(Operation<L> operation) {
            this.operations.add(operation);
            return getThis();
        }

        public This parent(Name name) {
            this.parents.add(name);
            return getThis();
        }

        public This parents(Collection<Name> names) {
            this.parents.addAll(names);
            return getThis();
        }

        public This resourceNameTemplate(String resourceNameTemplate) {
            this.resourceNameTemplate = resourceNameTemplate;
            return getThis();
        }

        public This resourceConfigurationPropertyType(
                ResourceConfigurationPropertyType<L> resourceConfigurationPropertyType) {
            this.resourceConfigurationPropertyTypes.add(resourceConfigurationPropertyType);
            return getThis();
        }

        // immutable getters - these are needed when building up the resource type

        public List<Name> getAvailSetNames() {
            return Collections.unmodifiableList(availSetNames);
        }

        public List<Name> getMetricSetNames() {
            return Collections.unmodifiableList(metricSetNames);
        }
    }

    private final String resourceNameTemplate;
    private final Collection<Name> parents;
    private final Collection<Name> metricSetNames;
    private final Collection<Name> availSetNames;
    private final Collection<MetricType<L>> metricTypes;
    private final Collection<AvailType<L>> availTypes;
    private final Collection<Operation<L>> operations;
    private final Collection<ResourceConfigurationPropertyType<L>> resourceConfigurationPropertyTypes;

    private ResourceType(ID id, Name name, L location, String resourceNameTemplate, Collection<Name> parents,
            Collection<Name> metricSetNames, Collection<Name> availSetNames, Collection<MetricType<L>> metricTypes,
            Collection<AvailType<L>> availTypes, Collection<Operation<L>> operations,
            Collection<ResourceConfigurationPropertyType<L>> resourceConfigurationPropertyTypes) {
        super(id, name, location);
        this.resourceNameTemplate = resourceNameTemplate;
        this.parents = parents;
        this.metricSetNames = metricSetNames;
        this.availSetNames = availSetNames;
        this.metricTypes = metricTypes;
        this.availTypes = availTypes;
        this.operations = operations;
        this.resourceConfigurationPropertyTypes = resourceConfigurationPropertyTypes;
    }

    public String getResourceNameTemplate() {
        return resourceNameTemplate;
    }

    public Collection<Name> getParents() {
        return parents;
    }

    public Collection<Name> getMetricSets() {
        return metricSetNames;
    }

    public Collection<Name> getAvailSets() {
        return availSetNames;
    }

    public Collection<MetricType<L>> getMetricTypes() {
        return metricTypes;
    }

    public Collection<AvailType<L>> getAvailTypes() {
        return availTypes;
    }

    public Collection<ResourceConfigurationPropertyType<L>> getResourceConfigurationPropertyTypes() {
        return resourceConfigurationPropertyTypes;
    }

    public Collection<Operation<L>> getOperations() {
        return operations;
    }

}
