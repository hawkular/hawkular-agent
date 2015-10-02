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

    Name PLATFORM = new Name("Platform");

    Name OPERATING_SYSTEM = new Name("Operating System");

    Name FILE_STORE = new Name("File Store");
    Name FILE_STORE_USABLE_SPACE = new Name("Usable Space");
    Name FILE_STORE_TOTAL_SPACE = new Name("Total Space");

    Name MEMORY = new Name("Memory");
    Name MEMORY_AVAILABLE = new Name("Available Memory");
    Name MEMORY_TOTAL = new Name("Total Memory");

    Name PROCESSOR = new Name("Processor");
    Name PROCESSOR_CPU_USAGE = new Name("CPU Usage");

    Name POWER_SOURCE = new Name("Power Source");
    Name POWER_SOURCE_REMAINING_CAPACITY = new Name("Remaining Capacity");
    Name POWER_SOURCE_TIME_REMAINING = new Name("Time Remaining");
}
