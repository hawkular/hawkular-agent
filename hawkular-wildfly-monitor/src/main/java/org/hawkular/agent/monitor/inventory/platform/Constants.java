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
package org.hawkular.agent.monitor.inventory.platform;

import org.hawkular.agent.monitor.inventory.Name;

/**
 * Because the resource types, metrics, etc. are fixed for our native platform support,
 * these constants are here to define those fixed names of things.
 */
public interface Constants {

    // these list the names of all the known resource types for platform resources
    enum PlatformResourceType {
        OPERATING_SYSTEM("Operating System"),
        FILE_STORE("File Store"),
        MEMORY("Memory"),
        PROCESSOR("Processor"),
        POWER_SOURCE("Power Source");

        private final Name name;

        PlatformResourceType(String label) {
            this.name = new Name(label);
        }

        public Name getName() {
            return name;
        }

    }

    Name PLATFORM = new Name("Platform");

    // names of all known metrics of all known platform resource types

    Name OPERATING_SYSTEM_SYS_CPU_LOAD = new Name("System CPU Load");
    Name OPERATING_SYSTEM_SYS_LOAD_AVG = new Name("System Load Average");

    Name FILE_STORE_USABLE_SPACE = new Name("Usable Space");
    Name FILE_STORE_TOTAL_SPACE = new Name("Total Space");

    Name MEMORY_AVAILABLE = new Name("Available Memory");
    Name MEMORY_TOTAL = new Name("Total Memory");

    Name PROCESSOR_CPU_USAGE = new Name("CPU Usage");

    Name POWER_SOURCE_REMAINING_CAPACITY = new Name("Remaining Capacity");
    Name POWER_SOURCE_TIME_REMAINING = new Name("Time Remaining");
}
