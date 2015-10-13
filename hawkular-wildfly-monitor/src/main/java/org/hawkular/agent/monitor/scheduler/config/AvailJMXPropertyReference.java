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

import org.hawkular.dmrclient.Address;

/**
 * A JMX resource reference that is to be checked for availability.
 */
public class AvailJMXPropertyReference extends JMXPropertyReference {

    private final String upRegex;

    public AvailJMXPropertyReference(final Address address, final String attribute, final Interval interval,
            final String upRegex) {
        super(address, attribute, interval);
        this.upRegex = upRegex;
    }

    public String getUpRegex() {
        return upRegex;
    }

    @Override
    public String toString() {
        return "AvailJMXPropertyReference[dmrPropRef=" + super.toString() + ", upRegex=" + upRegex + "]";
    }
}
