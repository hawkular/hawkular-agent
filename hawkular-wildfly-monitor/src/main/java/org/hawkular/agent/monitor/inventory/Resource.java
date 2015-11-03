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
package org.hawkular.agent.monitor.inventory;

import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;

import org.hawkular.agent.monitor.scheduler.config.MonitoredEndpoint;

public abstract class Resource< //
T extends ResourceType<?, ?, ?, ?>, //
E extends MonitoredEndpoint, //
M extends MetricInstance<?, ?, ?>, //
A extends AvailInstance<?, ?, ?>, //
C extends ResourceConfigurationPropertyInstance<?>> //
        extends NamedObject {

    private final T resourceType;
    private final Resource<?, ?, ?, ?, ?> parent;
    private final E endpoint;
    private final Collection<M> metrics = new HashSet<>();
    private final Collection<A> avails = new HashSet<>();
    private final Collection<C> resourceConfigurationProperties = new HashSet<>();

    public <P extends Resource<?, ?, ?, ?, ?>> Resource(ID id, Name name, E endpoint, T resourceType, P parent) {
        super(id, name);
        this.endpoint = endpoint;
        this.resourceType = resourceType;
        this.parent = parent;
    }

    public E getEndpoint() {
        return endpoint;
    }

    public T getResourceType() {
        return resourceType;
    }

    public <P extends Resource<?, ?, ?, ?, ?>> P getParent() {
        return (P) parent;
    }

    public Collection<M> getMetrics() {
        return metrics;
    }

    public Collection<A> getAvails() {
        return avails;
    }

    public Collection<C> getResourceConfigurationProperties() {
        return Collections.unmodifiableCollection(resourceConfigurationProperties);
    }

    public void addResourceConfigurationProperty(C configProperty) {
        resourceConfigurationProperties.add(configProperty);
    }

    @Override
    public String toString() {
        return String.format("%s=[type=%s][endpoint=%s]",
                super.toString(), this.resourceType, (this.endpoint != null) ? this.endpoint.getName() : "null");
    }
}
