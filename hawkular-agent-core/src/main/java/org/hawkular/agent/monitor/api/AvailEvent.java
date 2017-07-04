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
package org.hawkular.agent.monitor.api;

import java.util.Map;

import org.hawkular.agent.monitor.inventory.AvailManager;
import org.hawkular.agent.monitor.inventory.AvailType;
import org.hawkular.agent.monitor.inventory.MeasurementInstance;

/**
 * Avail event for avail measurements.
 *
 * @author <a href="https://github.com/josejulio">Josejulio Martínez</a>
 */
public class AvailEvent<L> {

    private final SamplingService<L> samplingService;
    private final AvailManager<L> availManager;
    private final Map<MeasurementInstance<L, AvailType<L>>, Avail> changed;


    /**
     * Creates an avail event.
     * @param samplingService a service that provides details such as feed ID and endpoint information that helps
     *                        identify the resources in the event, plus has methods that can be used to monitor
     *                        the resources in the event.
     * @param availManager the avails associated with the event
     * @param changed map of changed avails
     */
    private AvailEvent(SamplingService<L> samplingService, AvailManager<L> availManager,
                      Map<MeasurementInstance<L, AvailType<L>>, Avail> changed) {

        if (samplingService == null) {
            throw new IllegalArgumentException("Sampling service cannot be null");
        }

        if (availManager == null) {
            throw new IllegalArgumentException("Avail manager cannot be null");
        }
        this.samplingService = samplingService;
        this.availManager = availManager;
        this.changed = changed;
    }

    public static <L> AvailEvent<L> availChanged(SamplingService<L> samplingService, AvailManager<L> availManager,
                               Map<MeasurementInstance<L, AvailType<L>>, Avail> changed) {
        return new AvailEvent<L>(samplingService, availManager, changed);
    }

    public SamplingService<L> getSamplingService() {
        return samplingService;
    }

    public AvailManager<L> getAvailManager() {
        return availManager;
    }

    public Map<MeasurementInstance<L, AvailType<L>>, Avail> getChanged() {
        return changed;
    }
}
