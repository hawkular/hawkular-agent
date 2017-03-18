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

import com.fasterxml.jackson.annotation.JsonAutoDetect;
import com.fasterxml.jackson.annotation.JsonAutoDetect.Visibility;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonAutoDetect( //
        fieldVisibility = Visibility.NONE, //
        getterVisibility = Visibility.NONE, //
        setterVisibility = Visibility.NONE, //
        isGetterVisibility = Visibility.NONE)
public class ManagedServers implements Validatable {

    @JsonProperty("local-dmr")
    private LocalDMR localDmr;

    @JsonProperty("local-jmx")
    private LocalJMX localJmx;

    @JsonProperty("remote-dmr")
    private RemoteDMR[] remoteDmrs;

    @JsonProperty("remote-jmx")
    private RemoteJMX[] remoteJmxs;

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

    public LocalDMR getLocalDmr() {
        return localDmr;
    }

    public void setLocalDmr(LocalDMR localDmr) {
        this.localDmr = localDmr;
    }

    public LocalJMX getLocalJmx() {
        return localJmx;
    }

    public void setLocalJmx(LocalJMX localJmx) {
        this.localJmx = localJmx;
    }

    public RemoteDMR[] getRemoteDmrs() {
        return remoteDmrs;
    }

    public void setRemoteDmrs(RemoteDMR[] remoteDmrs) {
        this.remoteDmrs = remoteDmrs;
    }

    public RemoteJMX[] getRemoteJmxs() {
        return remoteJmxs;
    }

    public void setRemoteJmxs(RemoteJMX[] remoteJmxs) {
        this.remoteJmxs = remoteJmxs;
    }
}
