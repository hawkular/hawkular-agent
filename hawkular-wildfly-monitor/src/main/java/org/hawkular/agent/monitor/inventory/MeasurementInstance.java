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

import org.hawkular.agent.monitor.scheduler.config.MonitoredPropertyReference;

public abstract class MeasurementInstance< //
R extends Resource<?, ?, ?, ?>, //
M extends MeasurementType, //
P extends MonitoredPropertyReference> //
        extends NamedObject {

    private final R resource;
    private final M measurementType;
    private final P property;

    public MeasurementInstance(ID id, Name name, R resource, M measurementType, P property) {
        super(id, name);
        this.resource = resource;
        this.measurementType = measurementType;
        this.property = property;
    }

    /**
     * @return the resource that this measurement instance belongs to
     */
    public R getResource() {
        return resource;
    }

    public M getMeasurementType() {
        return measurementType;
    }

    public P getProperty() {
        return property;
    }
}
