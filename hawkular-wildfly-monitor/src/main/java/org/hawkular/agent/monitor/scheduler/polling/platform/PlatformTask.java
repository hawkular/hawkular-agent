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
package org.hawkular.agent.monitor.scheduler.polling.platform;

import org.hawkular.agent.monitor.scheduler.config.Interval;
import org.hawkular.agent.monitor.scheduler.config.PlatformEndpoint;
import org.hawkular.agent.monitor.scheduler.polling.Task;

/**
 * Represents a task that is to be executed on a platform resource.
 */
public abstract class PlatformTask implements Task {

    private final class PlatformTaskKind implements Kind {
        private final String id;

        private PlatformTaskKind(PlatformTask us) {
            id = "platform";
        }

        @Override
        public String getId() {
            return id;
        }
    }

    private final PlatformEndpoint endpoint;
    private final Type type;
    private final Interval interval;

    public PlatformTask(
            Type type,
            Interval interval,
            PlatformEndpoint endpoint) {

        if (type == null) {
            throw new IllegalArgumentException("type cannot be null");
        }

        if (interval == null) {
            throw new IllegalArgumentException("interval cannot be null");
        }

        if (endpoint == null) {
            throw new IllegalArgumentException("endpoint cannot be null");
        }

        this.type = type;
        this.interval = interval;
        this.endpoint = endpoint;
    }

    @Override
    public Type getType() {
        return type;
    }

    @Override
    public Interval getInterval() {
        return interval;
    }

    @Override
    public Kind getKind() {
        return new PlatformTaskKind(this);
    }

    public PlatformEndpoint getEndpoint() {
        return endpoint;
    }

    @Override
    public String toString() {
        StringBuilder str = new StringBuilder("PlatformTask: ");
        str.append("endpoint=[").append(endpoint).append("]");
        str.append(", type=[").append(type).append("]");
        str.append(", interval=[").append(interval).append("]");
        str.append(", kind=[").append(getKind().getId()).append("]");
        return str.toString();
    }
}
