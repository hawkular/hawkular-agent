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
package org.hawkular.agent.monitor.api;

public enum Avail {
    UP(0),
    DOWN(1),
    UNKNOWN(2);

    private final int numericValue;

    private Avail(int v) {
        numericValue = v;
    }

    public int getNumericValue() {
        return this.numericValue;
    }

    public static Avail fromNumericValue(int val) {
        switch (val) {
            case 0:
                return UP;
            case 1:
                return DOWN;
            case 2:
                return UNKNOWN;
            default:
                throw new IllegalArgumentException("No avail type with numeric value of [" + val + "]");
        }
    }
}
