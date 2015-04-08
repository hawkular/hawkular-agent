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
package org.hawkular.agent.monitor.storage;

import java.util.Set;

import org.hawkular.agent.monitor.api.MetricStorage;
import org.hawkular.agent.monitor.diagnostics.Diagnostics;
import org.hawkular.agent.monitor.scheduler.config.SchedulerConfiguration;
import org.hawkular.agent.monitor.service.ServerIdentifiers;

public interface StorageAdapter extends MetricStorage {
    /**
     * Stores the given collected metric data points.
     * @param datapoints the data to be stored
     */
    void store(Set<DataPoint> datapoints);

    /**
     * @return the configuration used by this adapter
     */
    SchedulerConfiguration getSchedulerConfiguration();

    /**
     * @param config the configuration of the scheduler to which this storage adapter is associated with
     */
    void setSchedulerConfiguration(SchedulerConfiguration config);

    /**
     * @param diag the object used to track internal diagnostic data for the storage adapter
     */
    void setDiagnostics(Diagnostics diag);

    /**
     * @param selfId helps identify where we are hosted
     */
    void setSelfIdentifiers(ServerIdentifiers selfId);
}
