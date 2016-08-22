/*
 * Copyright 2015-2016 Red Hat, Inc. and/or its affiliates
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
package org.hawkular.agent.monitor.protocol.platform;

import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.stream.Collectors;

import org.hawkular.agent.monitor.inventory.ID;
import org.hawkular.agent.monitor.inventory.Name;

/**
 * Because the resource types, metrics, etc. are fixed for our native platform support,
 * these constants are here to define those fixed names of things.
 *
 * @author John Mazzitelli
 */
public interface Constants {
    // this is a special resource config property on the platform OS resource itself
    Name MACHINE_ID = new Name("Machine Id");

    enum PlatformResourceType {
        OPERATING_SYSTEM("Operating System"), //
        FILE_STORE("File Store"), //
        MEMORY("Memory"), //
        PROCESSOR("Processor"), //
        POWER_SOURCE("Power Source");

        private final ID resourceTypeId;
        private final Name resourceTypeName;
        private Collection<PlatformMetricType> metricTypes;
        private Collection<ID> metricTypeIds;

        PlatformResourceType(String name) {
            this.resourceTypeId = new ID("Platform_" + name);
            this.resourceTypeName = new Name(name);
        }

        public ID getResourceTypeId() {
            return resourceTypeId;
        }

        public Name getResourceTypeName() {
            return resourceTypeName;
        }

        public Collection<PlatformMetricType> getMetricTypes() {
            if (metricTypes == null) {
                metricTypes = PlatformMetricType.getPlatformMetricTypes(this);
            }
            return metricTypes;
        }

        public Collection<ID> getMetricTypeIds() {
            if (metricTypeIds == null) {
                metricTypeIds = Collections.unmodifiableList(getMetricTypes()
                        .stream()
                        .map(n -> n.getMetricTypeId())
                        .collect(Collectors.toList()));
            }
            return metricTypeIds;
        }
    }

    enum PlatformMetricType {
        // OPERATING SYSTEM METRICS
        OS_SYS_CPU_LOAD(PlatformResourceType.OPERATING_SYSTEM, "System CPU Load"), //
        OS_SYS_LOAD_AVG(PlatformResourceType.OPERATING_SYSTEM, "System Load Average"), //
        OS_PROCESS_COUNT(PlatformResourceType.OPERATING_SYSTEM, "Process Count"), //

        // FILE STORE METRICS
        FILE_STORE_USABLE_SPACE(PlatformResourceType.FILE_STORE, "Usable Space"), //
        FILE_STORE_TOTAL_SPACE(PlatformResourceType.FILE_STORE, "Total Space"), //

        // MEMORY METRICS
        MEMORY_AVAILABLE(PlatformResourceType.MEMORY, "Available Memory"), //
        MEMORY_TOTAL(PlatformResourceType.MEMORY, "Total Memory"), //

        // PROCESSOR METRICS
        PROCESSOR_CPU_USAGE(PlatformResourceType.PROCESSOR, "CPU Usage"), //

        // POWER SOURCE METRICS
        POWER_SOURCE_REMAINING_CAPACITY(PlatformResourceType.POWER_SOURCE, "Remaining Capacity"), //
        POWER_SOURCE_TIME_REMAINING(PlatformResourceType.POWER_SOURCE, "Time Remaining"); //

        private final PlatformResourceType resourceType;
        private final ID metricTypeId;
        private final Name metricTypeName;

        PlatformMetricType(PlatformResourceType resourceType, String name) {
            this.resourceType = resourceType;
            this.metricTypeId = new ID(resourceType.getResourceTypeId().getIDString() + "_" + name);
            this.metricTypeName = new Name(name);
        }

        public PlatformResourceType getResourceType() {
            return resourceType;
        }

        public ID getMetricTypeId() {
            return metricTypeId;
        }

        public Name getMetricTypeName() {
            return metricTypeName;
        }

        public static Collection<PlatformMetricType> getPlatformMetricTypes(PlatformResourceType type) {
            return Collections.unmodifiableList(Arrays.asList(PlatformMetricType.values())
                    .stream()
                    .filter(t -> t.getResourceType() == type)
                    .collect(Collectors.toList()));
        }
    }
}
