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
package org.hawkular.agent.monitor.protocol.dmr;

/**
 * Represents the endpoint to our local Wildfly instance (the one we are running inside of).
 *
 * @author John Mazzitelli
 */
public class LocalDMREndpoint extends DMREndpoint {
    private static final LocalDMREndpoint DEFAULT = new LocalDMREndpoint("self_");
    public static LocalDMREndpoint getDefault() {
        return DEFAULT;
    }
    /**
     * If the caller does not yet know the local WildFly's identification, it can use this
     * constructor which takes a client factory as an argument. That client factory will
     * be used when the identification needs to be determined (a client will be created
     * and queried for the server identification).
     *
     * @param name the name of the endpoint
     */
    public LocalDMREndpoint(String name) {
        super(name, null, 0, null, null, false, null);
    }
}
