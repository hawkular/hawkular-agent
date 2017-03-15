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
package org.hawkular.agent.javaagent.config;

import org.hawkular.agent.javaagent.Util;

import com.fasterxml.jackson.annotation.JsonProperty;

public class ManagedServers implements Validatable {

    @JsonProperty("local-dmr")
    public LocalDMR localDmr;

    @JsonProperty("local-jmx")
    public LocalJMX localJmx;

    @JsonProperty("remote-dmr")
    public RemoteDMR[] remoteDmrs;

    @JsonProperty("remote-jmx")
    public RemoteJMX[] remoteJmxs;

    public ManagedServers() {
    }

    public ManagedServers(ManagedServers original) {
        this.localDmr = original.localDmr == null ? null : new LocalDMR(original.localDmr);
        this.localJmx = original.localJmx == null ? null : new LocalJMX(original.localJmx);
        this.remoteDmrs = Util.cloneArray(original.remoteDmrs);
        this.remoteJmxs = Util.cloneArray(original.remoteJmxs);
    }

    @Override
    public void validate() throws Exception {
        if (localDmr != null) {
            localDmr.validate();
        }
        if (localJmx != null) {
            localJmx.validate();
        }
        if (remoteDmrs != null) {
            for (RemoteDMR remoteDmr : remoteDmrs) {
                remoteDmr.validate();
            }
        }
        if (remoteJmxs != null) {
            for (RemoteJMX remoteJmx : remoteJmxs) {
                remoteJmx.validate();
            }
        }
    }
}
