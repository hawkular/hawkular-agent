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

import org.hawkular.agent.javaagent.config.StringExpression.StringValue;

/**
 * The type of any YAML property that is a string
 * but whose YAML representation can include ${x} expressions.
 */
public class StringExpression extends AbstractExpression<StringValue> {
    public StringExpression() {
        super();
    }

    public StringExpression(StringValue initialValue) {
        super(initialValue);
    }

    public StringExpression(String expression) {
        super(expression);
    }

    // copy-constructor
    public StringExpression(StringExpression original) {
        super(original);
    }

    @Override
    protected StringValue deserialize(String valueAsString) {
        return new StringValue(valueAsString);
    }

    // we need a type to distinguish from String so our constructor signatures don't clash
    public static class StringValue {
        private final String value;

        public StringValue(String value) {
            this.value = value;
        }

        public String toString() {
            return this.value;
        }
    }
}
