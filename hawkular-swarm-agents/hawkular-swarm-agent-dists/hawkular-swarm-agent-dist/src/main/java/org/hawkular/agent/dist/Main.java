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
package org.hawkular.agent.dist;

import java.io.File;
import java.net.URL;

import org.hawkular.agent.swarm.AgentFraction;
import org.wildfly.swarm.container.Container;

/*
 * @author Jay Shaughnessy
 * @author Bob McWhirter
 */
public class Main {

    public static void main(String[] args) throws Exception {

        URL xmlConfig = null;
        if (args.length < 1) {
            ClassLoader cl = Main.class.getClassLoader();
            xmlConfig = cl.getResource("hawkular-swarm-agent-config.xml");
        } else {
            File xmlFile = new File(args[0]);
            if (xmlFile.canRead()) {
                xmlConfig = xmlFile.toURI().toURL();
            }
        }
        if (null == xmlConfig) {
            System.out.println("A configuration file must be supplied on the command line or " +
                    "hawkular-swarm-agent-config.xml must be on the classpath. Stopping...");
            return;
        }

        Container container = new Container(false).withXmlConfig(xmlConfig);
        container.fraction(new AgentFraction());
        container.start();
    }
}
