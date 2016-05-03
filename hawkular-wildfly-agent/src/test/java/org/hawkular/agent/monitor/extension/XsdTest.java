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
package org.hawkular.agent.monitor.extension;

import java.io.File;

import javax.xml.XMLConstants;
import javax.xml.transform.Source;
import javax.xml.transform.stream.StreamSource;
import javax.xml.validation.Schema;
import javax.xml.validation.SchemaFactory;
import javax.xml.validation.Validator;

import org.junit.Assert;
import org.junit.Test;

public class XsdTest {

    /**
     * Tests that the xml validates against the XSD and is parsed
     */
    @Test
    public void testValidateSubsystem() throws Exception {
        SchemaFactory schemaFactory = SchemaFactory.newInstance(XMLConstants.W3C_XML_SCHEMA_NS_URI);
        Schema schema = schemaFactory.newSchema(new File(
                "src/main/resources/schema/hawkular-agent-monitor-subsystem.xsd"));
        Validator validator = schema.newValidator();
        for (File f : new File("src/test/resources/org/hawkular/agent/monitor/extension").listFiles()) {
            Source xmlFile = new StreamSource(f);
            try {
                System.out.println("Validating " + xmlFile.getSystemId());
                validator.validate(xmlFile);
            } catch (Exception e) {
                Assert.fail(e.getLocalizedMessage());
            }
        }
    }
}
