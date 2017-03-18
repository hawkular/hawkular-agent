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

import org.hawkular.metrics.client.common.MetricType;

public class MetricTypeJsonProperty extends AbstractStringifiedProperty<MetricType> {
    public MetricTypeJsonProperty() {
        super();
    }

    public MetricTypeJsonProperty(MetricType initialValue) {
        super(initialValue);
    }

    public MetricTypeJsonProperty(String valueAsString) {
        super(valueAsString);
    }

    public MetricTypeJsonProperty(MetricTypeJsonProperty original) {
        super(original);
    }

    @Override
    protected MetricType deserialize(String valueAsString) {
        if (valueAsString != null) {
            return assertIsSupportedMetricType(MetricType.valueOf(valueAsString.toUpperCase(Locale.ENGLISH)));
        } else {
            throw new IllegalArgumentException("Metric type is not specified");
        }
    }

    @Override
    protected String serialize(MetricType value) {
        if (value != null) {
            return assertIsSupportedMetricType(value).name().toLowerCase();
        } else {
            throw new IllegalArgumentException("Metric type is not specified");
        }
    }

    private MetricType assertIsSupportedMetricType(MetricType mt) {
        // TODO: in the future we may support STRING type so we'd add it here
        if (mt != MetricType.GAUGE && mt != MetricType.COUNTER) {
            throw new UnsupportedOperationException("Metric type [" + valueAsString + "] is not supported");
        }
        return mt;
    }
}
