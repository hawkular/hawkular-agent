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
import java.util.Collections;
import java.util.List;

import org.hawkular.agent.monitor.inventory.Name;

/**
 * Because the resource types, metrics, etc. are fixed for our native platform support,
 * these constants are here to define those fixed names of things.
 * @author John Mazzitelli
 */
public interface Constants {

    // these list the names of all the known resource types for platform resources
    // since OSHI only supports a fixed set of resources/metrics, we just hardwire them here
    enum PlatformResourceType {
        OPERATING_SYSTEM("Operating System",
                Arrays.asList(OPERATING_SYSTEM_SYS_CPU_LOAD, OPERATING_SYSTEM_SYS_LOAD_AVG,
                        OPERATING_SYSTEM_PROCESS_COUNT)),

        FILE_STORE("File Store",
                Arrays.asList(FILE_STORE_USABLE_SPACE, FILE_STORE_TOTAL_SPACE)),

        MEMORY("Memory",
                Arrays.asList(MEMORY_AVAILABLE, MEMORY_TOTAL)),

        PROCESSOR("Processor",
                Collections.singletonList(PROCESSOR_CPU_USAGE)),

        POWER_SOURCE("Power Source",
                Arrays.asList(POWER_SOURCE_REMAINING_CAPACITY, POWER_SOURCE_TIME_REMAINING))

        ;

        private final Name name;
        private final List<Name> metricNames;

        PlatformResourceType(String label, List<Name> metricNames) {
            this.name = new Name(label);
            this.metricNames = Collections.unmodifiableList(metricNames);
        }

        public Name getName() {
            return name;
        }

        public List<Name> getMetricNames() {
            return metricNames;
        }
    }

    Name PLATFORM = new Name("Platform");
    Name MACHINE_ID = new Name("Machine Id");

    // names of all known metrics of all known platform resource types

    Name OPERATING_SYSTEM_SYS_CPU_LOAD = new Name("System CPU Load");
    Name OPERATING_SYSTEM_SYS_LOAD_AVG = new Name("System Load Average");
    Name OPERATING_SYSTEM_PROCESS_COUNT = new Name("Process Count");

    Name FILE_STORE_USABLE_SPACE = new Name("Usable Space");
    Name FILE_STORE_TOTAL_SPACE = new Name("Total Space");

    Name MEMORY_AVAILABLE = new Name("Available Memory");
    Name MEMORY_TOTAL = new Name("Total Memory");

    Name PROCESSOR_CPU_USAGE = new Name("CPU Usage");

    Name POWER_SOURCE_REMAINING_CAPACITY = new Name("Remaining Capacity");
    Name POWER_SOURCE_TIME_REMAINING = new Name("Time Remaining");
}
