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

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;

public abstract class ResourceType< //
MT extends MetricType, //
AT extends AvailType, //
O extends Operation<? extends ResourceType<?, ?, ?, ?>>, //
RCPT extends ResourceConfigurationPropertyType<? extends ResourceType<?, ?, ?, ?>>>
        extends NamedObject {

    public ResourceType(ID id, Name name) {
        super(id, name);
    }

    private String resourceNameTemplate;
    private Collection<Name> parents;
    private Collection<Name> metricSetNames = new HashSet<>();
    private Collection<Name> availSetNames = new HashSet<>();
    private Collection<MT> metricTypes = new HashSet<>();
    private Collection<AT> availTypes = new HashSet<>();
    private Collection<O> operations = new HashSet<>();
    private Collection<RCPT> resourceConfigurationPropertyTypes = new HashSet<>();

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

    public Collection<RCPT> getResourceConfigurationPropertyTypes() {
        return Collections.unmodifiableCollection(resourceConfigurationPropertyTypes);
    }

    public void addResourceConfigurationPropertyType(RCPT resConfigPropertyType) {
        resourceConfigurationPropertyTypes.add(resConfigPropertyType);

        // put it in our properties so it gets stored properly in inventory
        final String configsPropName = "resourceConfiguration";
        List<Map<String, Object>> configs = (List<Map<String, Object>>) getProperties().get(configsPropName);
        if (configs == null) {
            configs = new ArrayList<>();
            addProperty(configsPropName, configs);
        }
        configs.add(resConfigPropertyType.getProperties());
    }

    public Collection<O> getOperations() {
        return Collections.unmodifiableCollection(operations);
    }

    public void addOperation(O operation) {
        operations.add(operation);

        // put it in our properties so it gets stored properly in inventory
        final String opsPropName = "operations";
        List<Map<String, Object>> ops = (List<Map<String, Object>>) getProperties().get(opsPropName);
        if (ops == null) {
            ops = new ArrayList<>();
            addProperty(opsPropName, ops);
        }
        ops.add(operation.getProperties());
    }

}
