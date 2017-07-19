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
package org.hawkular.agent.monitor.inventory;

import java.util.HashMap;
import java.util.Map;

import org.hawkular.agent.monitor.api.Avail;

/**
 * Holds a map between resource ids and avails. Provides methods for retrieve data and update the data in the map.
 *
 * @author <a href="https://github.com/josejulio">Josejulio Martínez</a>
 */
public final class AvailManager<L> {

    public static class AddResult<L> {
        public enum Effect {
            MODIFIED, UNCHANGED, STARTING
        }
        private final MeasurementInstance<L, AvailType<L>> measurementInstance;
        private final Avail avail;
        private final Effect effect;

        public AddResult(MeasurementInstance<L, AvailType<L>> measurementInstance, Avail avail, Effect effect) {
            this.measurementInstance = measurementInstance;
            this.avail = avail;
            this.effect = effect;
        }

        public MeasurementInstance<L, AvailType<L>> getMeasurementInstance() {
            return measurementInstance;
        }

        public Avail getAvail() {
            return avail;
        }

        public Effect getEffect() {
            return effect;
        }
    }

    private Map<MeasurementInstance<L, AvailType<L>>, Avail> storage = new HashMap<>();

    public AddResult<L> addAvail(MeasurementInstance<L, AvailType<L>> measurementInstance, Avail avail) {
        Avail previousAvail = storage.get(measurementInstance);
        AddResult.Effect effect;
        if (previousAvail == null) {
            effect = AddResult.Effect.STARTING;
        } else if (avail == previousAvail) {
            effect = AddResult.Effect.UNCHANGED;
        } else {
            effect = AddResult.Effect.MODIFIED;
        }
        storage.put(measurementInstance, avail);
        return new AddResult<>(measurementInstance, avail, effect);
    }

}
