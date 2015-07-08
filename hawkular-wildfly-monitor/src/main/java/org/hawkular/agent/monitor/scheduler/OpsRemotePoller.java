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
package org.hawkular.agent.monitor.scheduler;

import java.util.concurrent.TimeUnit;

import org.hawkular.agent.monitor.scheduler.config.Interval;
import org.hawkular.agent.monitor.scheduler.polling.KeyGenerator;
import org.hawkular.agent.monitor.scheduler.polling.Task;
import org.hawkular.agent.monitor.scheduler.polling.Task.Kind;
import org.hawkular.agent.monitor.scheduler.polling.Task.Type;

/**
 * TODO document me
 *
 * @author Heiko W. Rupp
 */
public class OpsRemotePoller implements Task {

    public OpsRemotePoller() {

    }

    private final class OpsKind implements Kind {
        private final String id;

        public OpsKind(OpsRemotePoller opsRemotePoller) {
            this.id = "OpsTaskId-" + opsRemotePoller.hashCode();
        }

        @Override
        public String getId() {
            return this.id;
        }
    }

    private final class OpsKeyGen implements KeyGenerator {
        @Override
        public String generateKey(Task task) {
            return "123-key"; // TODO
        }
    }

    @Override
    public Type getType() {
        return Type.OPS;
    }

    @Override
    public Kind getKind() {
        return new OpsKind(this);
    }

    @Override
    public Interval getInterval() {
        return new Interval(20, TimeUnit.SECONDS);
    }

    @Override
    public KeyGenerator getKeyGenerator() {
        return new OpsKeyGen();
    }
}
