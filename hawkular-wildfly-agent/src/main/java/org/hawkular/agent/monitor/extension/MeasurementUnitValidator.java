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

import org.jboss.as.controller.OperationFailedException;
import org.jboss.as.controller.client.helpers.MeasurementUnit;
import org.jboss.as.controller.operations.validation.AllowedValuesValidator;
import org.jboss.as.controller.operations.validation.ModelTypeValidator;
import org.jboss.as.controller.operations.validation.ParameterValidator;
import org.jboss.dmr.ModelNode;
import org.jboss.dmr.ModelType;

/**
 * {@link ParameterValidator} that validates the value is a string matching one of the {@link MeasurementUnit} names.
 * This follows the design pattern of org.jboss.as.controller.operations.validation.TimeUnitValidator.
 */
public class MeasurementUnitValidator extends ModelTypeValidator implements AllowedValuesValidator {

    /** MeasurementUnitValidator where any MeasurementUnit is valid, but an undefined value is not */
    public static final MeasurementUnitValidator ANY_REQUIRED = new MeasurementUnitValidator(false, true);
    /** MeasurementUnitValidator where any MeasurementUnit is valid, as is an undefined value */
    public static final MeasurementUnitValidator ANY_OPTIONAL = new MeasurementUnitValidator(true, true);

    private final EnumSet<MeasurementUnit> allowedValues;

    public MeasurementUnitValidator(final boolean nullable, final MeasurementUnit... allowed) {
        this(nullable, true, allowed);
    }

    public MeasurementUnitValidator(final boolean nullable, final boolean allowExpressions) {
        super(ModelType.STRING, nullable, allowExpressions);
        allowedValues = EnumSet.allOf(MeasurementUnit.class);
    }

    public MeasurementUnitValidator(final boolean nullable, final boolean allowExpressions,
            final MeasurementUnit... allowed) {
        super(ModelType.STRING, nullable, allowExpressions);
        allowedValues = EnumSet.noneOf(MeasurementUnit.class);
        for (MeasurementUnit tu : allowed) {
            allowedValues.add(tu);
        }
    }

    @Override
    public void validateParameter(String parameterName, ModelNode value) throws OperationFailedException {
        super.validateParameter(parameterName, value);
        if (value.isDefined()) {
            String muString = value.asString();
            MeasurementUnit mu = MeasurementUnit.valueOf(muString.toUpperCase(Locale.ENGLISH));
            if (mu == null || !allowedValues.contains(mu)) {
                throw new OperationFailedException("Bad value [" + muString + "] for param [" + parameterName + "]");
            }
        }
    }

    @Override
    public List<ModelNode> getAllowedValues() {
        List<ModelNode> result = new ArrayList<ModelNode>();
        for (MeasurementUnit mu : allowedValues) {
            result.add(new ModelNode().set(mu.name()));
        }
        return result;
    }
}
