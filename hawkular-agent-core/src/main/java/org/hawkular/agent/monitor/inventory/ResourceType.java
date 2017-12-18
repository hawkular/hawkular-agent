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
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.hawkular.client.api.NotificationType;

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
        private List<MetricType<L>> metricTypes = new ArrayList<>();
        private List<NotificationType> notificationTypes = new ArrayList<>();
        private List<Operation<L>> operations = new ArrayList<>();
        private List<ResourceConfigurationPropertyType<L>> resourceConfigurationPropertyTypes = new ArrayList<>();
        private Map<String, String> metricLabels = new HashMap<>();

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
                    Collections.unmodifiableList(metricTypes),
                    Collections.unmodifiableList(notificationTypes),
                    Collections.unmodifiableList(operations),
                    Collections.unmodifiableList(resourceConfigurationPropertyTypes),
                    Collections.unmodifiableMap(metricLabels));
        }

        @SuppressWarnings("unchecked")
        private This getThis() {
            return (This) this;
        }

        public This metricSetName(Name name) {
            this.metricSetNames.add(name);
            return getThis();
        }

        public This metricSetNames(Collection<Name> names) {
            this.metricSetNames.addAll(names);
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

        public This notificationType(NotificationType notificationType) {
            this.notificationTypes.add(notificationType);
            return getThis();
        }

        public This notificationTypes(Collection<NotificationType> notificationTypes) {
            this.notificationTypes.addAll(notificationTypes);
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

        public This metricLabels(Map<String, String> metricLabels) {
            if (metricLabels != null) {
                this.metricLabels.putAll(metricLabels);
            }
            return getThis();
        }

        public This metricLabel(String labelName, String labelValue) {
            this.metricLabels.put(labelName, labelValue);
            return getThis();
        }

        // immutable getters - these are needed when building up the resource type

        public List<Name> getMetricSetNames() {
            return Collections.unmodifiableList(metricSetNames);
        }
    }

    private final String resourceNameTemplate;
    private final Collection<Name> parents;
    private final Collection<Name> metricSetNames;
    private final Collection<MetricType<L>> metricTypes;
    private final Collection<NotificationType> notifications;
    private final Collection<Operation<L>> operations;
    private final Collection<ResourceConfigurationPropertyType<L>> resourceConfigurationPropertyTypes;
    private final Map<String, String> metricLabels;

    private ResourceType(
            ID id,
            Name name,
            L location,
            String resourceNameTemplate,
            Collection<Name> parents,
            Collection<Name> metricSetNames,
            Collection<MetricType<L>> metricTypes,
            Collection<NotificationType> notifications,
            Collection<Operation<L>> operations,
            Collection<ResourceConfigurationPropertyType<L>> resourceConfigurationPropertyTypes,
            Map<String, String> metricLabels) {
        super(id, name, location);
        this.resourceNameTemplate = resourceNameTemplate;
        this.parents = parents;
        this.metricSetNames = metricSetNames;
        this.metricTypes = metricTypes;
        this.notifications = notifications;
        this.operations = operations;
        this.resourceConfigurationPropertyTypes = resourceConfigurationPropertyTypes;
        this.metricLabels = metricLabels;
    }

    public String getResourceNameTemplate() {
        return resourceNameTemplate;
    }

    public Collection<Name> getParents() {
        return parents;
    }

    public Collection<NotificationType> getNotifications() {
        return notifications;
    }

    public Collection<Name> getMetricSets() {
        return metricSetNames;
    }

    public Collection<MetricType<L>> getMetricTypes() {
        return metricTypes;
    }

    public Collection<ResourceConfigurationPropertyType<L>> getResourceConfigurationPropertyTypes() {
        return resourceConfigurationPropertyTypes;
    }

    public Collection<Operation<L>> getOperations() {
        return operations;
    }

    public Map<String, String> getMetricLabels() {
        return metricLabels;
    }
}
