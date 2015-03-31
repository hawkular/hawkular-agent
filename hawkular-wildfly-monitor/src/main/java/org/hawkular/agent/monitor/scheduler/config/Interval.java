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
package org.hawkular.agent.monitor.scheduler.config;

import java.util.concurrent.TimeUnit;

public class Interval{
    private final int val;
    private final TimeUnit unit;

    public Interval(int val, TimeUnit unit) {
        if (unit == null) {
            throw new IllegalArgumentException("Missing units");
        }

        this.val = val;
        this.unit = unit;
    }

    public long millis() {
        return TimeUnit.MILLISECONDS.convert(val, unit);
    }

    public long seconds() {
        return TimeUnit.SECONDS.convert(val, unit);
    }

    public int getVal() {
        return val;
    }

    public TimeUnit getUnit() {
        return unit;
    }

    @Override
    public String toString() {
        return "Interval[" + this.val + " " + this.unit.name() + "]";
    }
}
