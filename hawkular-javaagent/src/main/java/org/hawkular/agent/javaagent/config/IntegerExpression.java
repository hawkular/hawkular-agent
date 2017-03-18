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
 * The type of any YAML property that is an integer
 * but whose YAML representation can include ${x} expressions.
 */
public class IntegerExpression extends AbstractExpression<Integer> {
    public IntegerExpression() {
        super();
    }

    public IntegerExpression(Integer initialValue) {
        super(initialValue);
    }

    public IntegerExpression(String expression) {
        super(expression);
    }

    // copy-constructor
    public IntegerExpression(IntegerExpression original) {
        super(original);
    }

    @Override
    protected Integer deserialize(String valueAsString) {
        try {
            return Integer.valueOf(valueAsString);
        } catch (Exception e) {
            throw new IllegalArgumentException("Integer expression could not be evaluated to a valid number", e);
        }
    }
}
