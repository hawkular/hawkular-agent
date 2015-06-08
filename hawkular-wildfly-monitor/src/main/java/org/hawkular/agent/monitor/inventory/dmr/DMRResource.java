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
package org.hawkular.agent.monitor.inventory.dmr;

import org.hawkular.agent.monitor.inventory.ID;
import org.hawkular.agent.monitor.inventory.Name;
import org.hawkular.agent.monitor.inventory.Resource;
import org.hawkular.agent.monitor.scheduler.config.DMREndpoint;
import org.hawkular.dmrclient.Address;
import org.jboss.dmr.ModelNode;

public class DMRResource extends Resource
        <DMRResourceType,
        DMREndpoint,
        DMRMetricInstance,
        DMRAvailInstance,
        DMRResourceConfigurationPropertyInstance> {

    private final Address address;
    private final ModelNode modelNode;

    public DMRResource(ID id, Name name, DMREndpoint endpoint, DMRResourceType type, DMRResource parent,
            Address address, ModelNode modelNode) {
        super(id, name, endpoint, type, parent);
        this.address = address;
        this.modelNode = modelNode;
    }

    public Address getAddress() {
        return address;
    }

    public ModelNode getModelNode() {
        return modelNode;
    }

    @Override
    public String toString() {
        return String.format("%s[address=%s]", super.toString(), this.address);
    }
}
