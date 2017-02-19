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

/**
 * @author <a href="https://github.com/ppalaga">Peter Palaga</a>
 */
public enum ServiceStatus {
    INITIAL, STARTING, RUNNING, STOPPING, STOPPED;

    public void assertInitialOrStopped(Class<?> cl, String action) throws IllegalStateException {
        if (this != INITIAL && this != STOPPED) {
            throw new IllegalStateException("[" + cl.getName() + "] must be in state [" + INITIAL + "] or ["
                    + STOPPED + "] rather than [" + this + "] to perform [" + action + "]");
        }
    }

    public void assertRunning(Class<?> cl, String action) throws IllegalStateException {
        if (this != RUNNING) {
            throw new IllegalStateException("[" + cl.getName() + "] must be in state [" + RUNNING + "] rather than ["
                    + this + "] to perform [" + action + "]");
        }
    }

    /**
     * @return true if the service is stopped or will be stopped soon. Initial mode is considered stopped.
     */
    public boolean isStoppingOrStopped() {
        return this == STOPPING || this == STOPPED || this == INITIAL;
    }
}
