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

import java.util.regex.Pattern;

public final class AvailType<L> extends MeasurementType<L> {

    private static final Pattern DEFAULT_UP_PATTERN = Pattern.compile("(?i)(UP|OK)");

    public static Pattern getDefaultUpPattern() {
        return DEFAULT_UP_PATTERN;
    }

    private final Pattern upPattern;

    public AvailType(ID id, Name name, AttributeLocation<L> location, Interval interval, Pattern upPattern) {
        super(id, name, location, interval);
        this.upPattern = upPattern;
    }

    public Pattern getUpPattern() {
        return upPattern;
    }

}
