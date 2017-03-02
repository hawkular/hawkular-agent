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

package org.hawkular.javaagent.itest.util;

/**
 * Returns information about a plain wildfly that has been setup for the tests.
 */
public class WildFlyClientConfig {
    private final String wfHost;
    private final int wfManagementPort;

    public WildFlyClientConfig() {
        wfHost = System.getProperty("plain-wildfly.bind.address");
        wfManagementPort = Integer.parseInt(System.getProperty("plain-wildfly.management.http.port"));
        if (wfHost == null) {
            throw new RuntimeException("Plain WildFly Server system properties are not set");
        }
    }

    public String getHost() {
        return wfHost;
    }

    public int getManagementPort() {
        return wfManagementPort;
    }
}
