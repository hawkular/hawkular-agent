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
import java.util.HashSet;

public abstract class ResourceType<MT extends MetricType, AT extends AvailType> extends NamedObject {
    public ResourceType(ID id, Name name) {
        super(id, name);
    }

    private String resourceNameTemplate;
    private Collection<Name> parents;
    private Collection<Name> metricSetNames = new HashSet<>();
    private Collection<Name> availSetNames = new HashSet<>();
    private Collection<MT> metricTypes = new HashSet<>();
    private Collection<AT> availTypes = new HashSet<>();

    public String getResourceNameTemplate() {
        return resourceNameTemplate;
    }

    public void setResourceNameTemplate(String resourceNameTemplate) {
        this.resourceNameTemplate = resourceNameTemplate;
    }

    public Collection<Name> getParents() {
        return parents;
    }

    public void setParents(Collection<Name> parents) {
        this.parents = parents;
    }

    public Collection<Name> getMetricSets() {
        return metricSetNames;
    }

    public void setMetricSets(Collection<Name> metricSets) {
        this.metricSetNames = metricSets;
    }

    public Collection<Name> getAvailSets() {
        return availSetNames;
    }

    public void setAvailSets(Collection<Name> availSets) {
        this.availSetNames = availSets;
    }

    public Collection<MT> getMetricTypes() {
        return metricTypes;
    }

    public Collection<AT> getAvailTypes() {
        return availTypes;
    }


}
