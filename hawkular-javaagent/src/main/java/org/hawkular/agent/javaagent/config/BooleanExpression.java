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

/**
 * The type of any YAML property that is a boolean
 * but whose YAML representation can include ${x} expressions.
 */
public class BooleanExpression extends AbstractExpression<Boolean> {
    public BooleanExpression() {
        super();
    }

    public BooleanExpression(Boolean initialValue) {
        super(initialValue);
    }

    public BooleanExpression(String expression) {
        super(expression);
    }

    // copy-constructor
    public BooleanExpression(BooleanExpression original) {
        super(original);
    }

    @Override
    protected Boolean deserialize(String valueAsString) {
        // make sure the value is either true or false - anything else is an error
        if (valueAsString == null) {
            throw new IllegalArgumentException("Boolean expression was null");
        } else if (valueAsString.equalsIgnoreCase("true")) {
            return Boolean.TRUE;
        } else if (valueAsString.equalsIgnoreCase("false")) {
            return Boolean.FALSE;
        } else {
            throw new IllegalArgumentException("Boolean expression was neither true nor false: " + valueAsString);
        }
    }
}
