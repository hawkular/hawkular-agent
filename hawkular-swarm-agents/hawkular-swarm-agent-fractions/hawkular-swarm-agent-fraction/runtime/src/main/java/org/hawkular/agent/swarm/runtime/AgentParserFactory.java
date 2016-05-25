/*
 * Copyright 2015-2016 Red Hat, Inc. and/or its affiliates
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
package org.hawkular.agent.swarm.runtime;

import java.util.List;
import java.util.Map;

import javax.xml.namespace.QName;

import org.hawkular.agent.monitor.extension.SubsystemExtension;
import org.jboss.dmr.ModelNode;
import org.jboss.staxmapper.XMLElementReader;
import org.wildfly.swarm.spi.runtime.AbstractParserFactory;

/**
 * @author Jay Shaughnessy
 * @author Bob McWhirter
 */
public class AgentParserFactory extends AbstractParserFactory {

    @Override
    public Map<QName, XMLElementReader<List<ModelNode>>> create() {

        ParsingContext ctx = new ParsingContext();
        SubsystemExtension ext = new SubsystemExtension();
        ext.initializeParsers(ctx);
        return ctx.getParser();
    }
}
