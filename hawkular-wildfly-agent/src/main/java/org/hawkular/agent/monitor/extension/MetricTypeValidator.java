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
package org.hawkular.agent.monitor.extension;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Locale;

import org.hawkular.metrics.client.common.MetricType;
import org.jboss.as.controller.OperationFailedException;
import org.jboss.as.controller.operations.validation.AllowedValuesValidator;
import org.jboss.as.controller.operations.validation.ModelTypeValidator;
import org.jboss.as.controller.operations.validation.ParameterValidator;
import org.jboss.dmr.ModelNode;
import org.jboss.dmr.ModelType;

/**
 * {@link ParameterValidator} that validates the value is a string matching one of the {@link MetricType} names.
 * This follows the design pattern of org.jboss.as.controller.operations.validation.TimeUnitValidator.
 */
public class MetricTypeValidator extends ModelTypeValidator implements AllowedValuesValidator {

    /** any value is valid, but an undefined value is not */
    public static final MetricTypeValidator ANY_REQUIRED = new MetricTypeValidator(false, true);
    /** any value is valid, as is an undefined value */
    public static final MetricTypeValidator ANY_OPTIONAL = new MetricTypeValidator(true, true);

    private final EnumSet<MetricType> allowedValues;

    public MetricTypeValidator(final boolean nullable, final MetricType... allowed) {
        this(nullable, true, allowed);
    }

    public MetricTypeValidator(final boolean nullable, final boolean allowExpressions) {
        super(ModelType.STRING, nullable, allowExpressions);
        allowedValues = EnumSet.allOf(MetricType.class);
    }

    public MetricTypeValidator(final boolean nullable, final boolean allowExpressions,
            final MetricType... allowed) {
        super(ModelType.STRING, nullable, allowExpressions);
        allowedValues = EnumSet.noneOf(MetricType.class);
        for (MetricType mt : allowed) {
            allowedValues.add(mt);
        }
    }

    @Override
    public void validateParameter(String parameterName, ModelNode value) throws OperationFailedException {
        super.validateParameter(parameterName, value);
        if (value.isDefined()) {
            String mtString = value.asString();
            MetricType mt = MetricType.valueOf(mtString.toUpperCase(Locale.ENGLISH));
            if (mt == null || !allowedValues.contains(mt)) {
                throw new OperationFailedException("Bad value [" + mtString + "] for param [" + parameterName + "]");
            }
        }
    }

    @Override
    public List<ModelNode> getAllowedValues() {
        List<ModelNode> result = new ArrayList<ModelNode>();
        for (MetricType mt : allowedValues) {
            result.add(new ModelNode().set(mt.name()));
        }
        return result;
    }
}
