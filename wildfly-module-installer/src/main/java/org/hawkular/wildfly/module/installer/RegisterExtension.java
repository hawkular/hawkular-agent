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
package org.hawkular.wildfly.module.installer;

import java.util.ArrayList;
import java.util.List;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.xpath.XPath;
import javax.xml.xpath.XPathConstants;
import javax.xml.xpath.XPathExpression;
import javax.xml.xpath.XPathFactory;

import org.jboss.logging.Logger;
import org.w3c.dom.Document;
import org.w3c.dom.NodeList;

class RegisterExtension {

    private final Logger log = Logger.getLogger(this.getClass());

    public RegisterExtension() {
    }

    /**
     * registers extension to standalone.xml or domain.xml
     * @param options
     * @throws Exception
     */
    public void register(RegisterModuleConfiguration options) throws Exception {
        switch (options.getConfigType()) {
            case DOMAIN:
                registerToDomain(options);
                break;
            case HOST:
                registerToHost(options);
                break;
            case STANDALONE:
                registerToStandalone(options);
                break;
            default:
                throw new IllegalArgumentException("unknown configuration type: " + options.getConfigType());
        }

        log.info("New serverConfig file written to [" + options.getTargetServerConfig().getAbsolutePath() + "]");
    }

    private void assertNotRegistered(RegisterModuleConfiguration options, String extensionExpr) throws Exception {
        final DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        final DocumentBuilder dBuilder = factory.newDocumentBuilder();
        final Document srcDoc = dBuilder.parse(options.getSourceServerConfig());
        final XPath xpath = XPathFactory.newInstance().newXPath();
        final XPathExpression expr = xpath.compile(extensionExpr);
        NodeList data = (NodeList) expr.evaluate(srcDoc, XPathConstants.NODESET);
        if (data != null && data.getLength() > 0) {
            throw new IllegalStateException("Extension [" + extensionExpr + "] is already installed. Aborting.");
        }
    }

    private void registerToStandalone(RegisterModuleConfiguration options) throws Exception {
        List<XmlEdit> inserts = new ArrayList<XmlEdit>();
        inserts.addAll(options.getXmlEdits());
        if (options.getModuleId() != null) {
            log.info("Register STANDALONE extension module=" + options.getModuleId());
            assertNotRegistered(options,
                    String.format("/server/extensions/extension[@module='%s']", options.getModuleId()));
            inserts.add(new XmlEdit("/server/extensions", "<extension module=\"" + options.getModuleId() + "\"/>"));
        }

        if (options.getSubsystem() != null) {
            inserts.add(new XmlEdit("/server/profile", options.getSubsystem()));
        }
        if (options.getSocketBindingGroups() != null && options.getSocketBinding() != null) {
            for (String group : options.getSocketBindingGroups()) {
                inserts.add(new XmlEdit("/server/socket-binding-group[@name='" + group + "']", options
                        .getSocketBinding()).withAttribute("name"));
            }
        }
        new XmlConfigBuilder(options.getSourceServerConfig(), options.getTargetServerConfig())
                .edits(inserts)
                .failNoMatch(options.isFailNoMatch()).build();
    }

    private void registerToHost(RegisterModuleConfiguration options) throws Exception {
        List<XmlEdit> inserts = new ArrayList<XmlEdit>();
        inserts.addAll(options.getXmlEdits());
        if (options.getModuleId() != null) {
            log.info("Register HOST extension module=" + options.getModuleId());
            assertNotRegistered(options,
                    String.format("/host/extensions/extension[@module='%s']", options.getModuleId()));
            inserts.add(new XmlEdit("/host/extensions", "<extension module=\"" + options.getModuleId() + "\"/>"));
        }

        if (options.getSubsystem() != null) {
            inserts.add(new XmlEdit("/host/profile", options.getSubsystem()));
        }
        if (options.getSocketBindingGroups() != null && options.getSocketBinding() != null) {
            for (String group : options.getSocketBindingGroups()) {
                inserts.add(new XmlEdit("/host",
                        "<socket-binding-group default-interface=\"public\" name=\"" + group + "\"/>"));
                inserts.add(new XmlEdit("/host/socket-binding-group[@name='" + group + "']", options
                        .getSocketBinding()).withAttribute("name"));
            }
        }
        new XmlConfigBuilder(options.getSourceServerConfig(), options.getTargetServerConfig())
                .edits(inserts)
                .failNoMatch(options.isFailNoMatch()).build();
    }

    private void registerToDomain(RegisterModuleConfiguration options) throws Exception {
        List<XmlEdit> inserts = new ArrayList<XmlEdit>();
        inserts.addAll(options.getXmlEdits());
        if (options.getModuleId() != null) {
            log.info("Register DOMAIN extension module=" + options.getModuleId());
            assertNotRegistered(options,
                    String.format("/domain/extensions/extension[@module='%s']", options.getModuleId()));
            inserts.add(new XmlEdit("/domain/extensions", "<extension module=\"" + options.getModuleId() + "\"/>"));
        }

        if (options.getSubsystem() != null) {
            for (String profile : options.getProfiles()) {
                inserts.add(new XmlEdit("/domain/profiles/profile[@name='" + profile + "']", options.getSubsystem()));
            }
        }
        if (options.getSocketBindingGroups() != null && options.getSocketBinding() != null) {
            for (String group : options.getSocketBindingGroups()) {
                inserts.add(new XmlEdit("/domain/socket-binding-groups/socket-binding-group[@name='" + group + "']",
                        options.getSocketBinding()).withAttribute("name"));
            }
        }
        new XmlConfigBuilder(options.getSourceServerConfig(), options.getTargetServerConfig())
                .edits(inserts)
                .failNoMatch(options.isFailNoMatch()).build();
    }

}
