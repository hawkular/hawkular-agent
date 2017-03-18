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
package org.hawkular.agent.javaagent.config;

import java.util.Locale;

import org.jboss.as.controller.client.helpers.MeasurementUnit;

public class MeasurementUnitJsonProperty extends AbstractStringifiedProperty<MeasurementUnit> {
    public MeasurementUnitJsonProperty() {
        super();
    }

    public MeasurementUnitJsonProperty(MeasurementUnit initialValue) {
        super(initialValue);
    }

    public MeasurementUnitJsonProperty(String valueAsString) {
        super(valueAsString);
    }

    public MeasurementUnitJsonProperty(MeasurementUnitJsonProperty original) {
        super(original);
    }

    @Override
    protected MeasurementUnit deserialize(String valueAsString) {
        if (valueAsString != null) {
            return MeasurementUnit.valueOf(valueAsString.toUpperCase(Locale.ENGLISH));
        } else {
            throw new IllegalArgumentException("Measurement units not specified");
        }
    }

    @Override
    protected String serialize(MeasurementUnit value) {
        if (value != null) {
            return value.name().toLowerCase();
        } else {
            throw new IllegalArgumentException("Metric type is not specified");
        }
    }
}
