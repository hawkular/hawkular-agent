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
package org.hawkular.agent.monitor.service;

import org.junit.Assert;
import org.junit.Test;

public class ServiceStatusTest {

    @Test
    public void testMethods() {
        ServiceStatus initial = ServiceStatus.INITIAL;
        ServiceStatus starting = ServiceStatus.STARTING;
        ServiceStatus running = ServiceStatus.RUNNING;
        ServiceStatus stopping = ServiceStatus.STOPPING;
        ServiceStatus stopped = ServiceStatus.STOPPED;

        expectNoExceptionInitialOrStopped(initial);
        expectExceptionRunning(initial);

        expectExceptionInitialOrStopped(starting);
        expectExceptionRunning(starting);

        expectExceptionInitialOrStopped(running);
        expectNoExceptionRunning(running);

        expectExceptionInitialOrStopped(stopping);
        expectExceptionRunning(stopping);

        expectNoExceptionInitialOrStopped(stopped);
        expectExceptionRunning(stopped);
    }

    private void expectNoExceptionInitialOrStopped(ServiceStatus status) {
        status.assertInitialOrStopped(this.getClass(), "should be initial or stopped: " + status.name());
    }

    private void expectNoExceptionRunning(ServiceStatus status) {
        status.assertRunning(this.getClass(), "should be running : " + status.name());
    }

    private void expectExceptionInitialOrStopped(ServiceStatus status) {
        try {
            status.assertInitialOrStopped(this.getClass(), "should not be initial or stopped: " + status.name());
            Assert.fail("This should have thrown exception - status is not initial or stopped: " + status.name());
        } catch (IllegalStateException expected) {
        }
    }

    private void expectExceptionRunning(ServiceStatus status) {
        try {
            status.assertRunning(this.getClass(), "should not be running: " + status.name());
            Assert.fail("This should have thrown exception - status is not running: " + status.name());
        } catch (IllegalStateException expected) {
        }
    }
}
