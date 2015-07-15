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
package org.hawkular.agent.monitor.scheduler.polling;

import org.hawkular.agent.monitor.scheduler.config.Interval;

/**
 * A task that can define an interval for periodic execution.
 */
public interface Task {

    enum Type {
        /** The task is for collecting a metric data. */
        METRIC,

        /** The task is for collecting availability data. */
        AVAIL,
    }

    interface Kind {
        String getId();

        default boolean isSameKind(Task compareTo) {
            if (compareTo == null) {
                return false;
            }
            return getId().equals(compareTo.getKind().getId());
        }
    }

    /**
     * @return indicates the purpose of the task
     */
    Type getType();

    /**
     * @return a comparable that indicates the kind of task this is.
     */
    Kind getKind();

    /**
     * @return how often the task should be executed.
     */
    Interval getInterval();

    /**
     * @return the object that generates the key for the tasks collected data.
     */
    KeyGenerator getKeyGenerator();

}
