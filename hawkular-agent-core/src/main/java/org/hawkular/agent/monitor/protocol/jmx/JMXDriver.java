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
package org.hawkular.agent.monitor.protocol.jmx;

import javax.management.ObjectName;

import org.hawkular.agent.monitor.diagnostics.ProtocolDiagnostics;
import org.hawkular.agent.monitor.protocol.Driver;

/**
 * Abstract JMX driver that both local and remote JMX drivers extend.
 *
 * @see Driver
 */
public abstract class JMXDriver implements Driver<JMXNodeLocation> {

    private final ProtocolDiagnostics diagnostics;

    public JMXDriver(ProtocolDiagnostics diagnostics) {
        this.diagnostics = diagnostics;
    }

    public abstract Object executeOperation(ObjectName objName, String opName, Object[] args, Class<?>[] signature)
            throws Exception;

    protected ProtocolDiagnostics getDiagnostics() {
        return diagnostics;
    }
}
