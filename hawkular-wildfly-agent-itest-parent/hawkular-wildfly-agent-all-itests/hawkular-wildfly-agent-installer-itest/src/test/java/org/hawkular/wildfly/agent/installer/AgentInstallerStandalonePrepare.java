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
package org.hawkular.wildfly.agent.installer;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;
import java.io.UnsupportedEncodingException;

import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.xpath.XPath;
import javax.xml.xpath.XPathConstants;
import javax.xml.xpath.XPathExpressionException;
import javax.xml.xpath.XPathFactory;

import org.hawkular.wildfly.agent.itest.util.AbstractITest;
import org.testng.Assert;
import org.testng.annotations.Test;
import org.w3c.dom.Document;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;

/**
 * @author <a href="https://github.com/ppalaga">Peter Palaga</a>
 *
 */
public class AgentInstallerStandalonePrepare extends AbstractITest {

    @Test
    public void hawkularInitialized() throws Throwable {
        waitForAccountsAndInventory();
    }

    @Test
    public void installAgent() throws Throwable {

        assertAgentInStandalone(false, wfHome);
        File agentModuleXml = new File(wfHome,
                "modules/system/add-ons/hawkular-agent/org/hawkular/agent/main/module.xml");
        Assert.assertFalse(agentModuleXml.exists(), "[" + agentModuleXml.getAbsolutePath() + "] should not exist");

        String agentExtensionZipPath = System.getProperty("hawkular-wildfly-agent-wf-extension.zip.path");
        Assert.assertTrue(new File(agentExtensionZipPath).exists(),
                "${hawkular-wildfly-agent-wf-extension.zip.path} [" + agentExtensionZipPath + "] does not exist");

        AgentInstaller.main(new String[] {
                "--target-location=" + wfHome.getAbsolutePath(),
                "--module-dist=" + agentExtensionZipPath,
                "--server-url=http://" + hawkularHost + ":" + hawkularHttpPort,
                "--username=" + testUser,
                "--password=" + testPasword,
                "--tenant-id=" + testHelper.getTenantId(),
                "--enabled=true"
        });

        Assert.assertTrue(agentModuleXml.exists(), "[" + agentModuleXml.getAbsolutePath() + "] should exist");
        assertAgentInStandalone(true, wfHome);

    }

    @Test
    public void installAgentConfigOnly() throws Throwable {

        String agentExtensionZipPath = System.getProperty("hawkular-wildfly-agent-wf-extension.zip.path");
        Assert.assertTrue(new File(agentExtensionZipPath).exists(),
                "${hawkular-wildfly-agent-wf-extension.zip.path} [" + agentExtensionZipPath + "] does not exist");

        String wfHomeProperty = System.getProperty("wildfly-for-config-only-install-tests.home.dir");
        Assert.assertNotNull(wfHomeProperty);
        File wfHome = new File(wfHomeProperty);

        File agentModuleXml = new File(wfHome,
                "modules/system/add-ons/hawkular-agent/org/hawkular/agent/main/module.xml");
        Assert.assertTrue(agentModuleXml.exists(), "[" + agentModuleXml.getAbsolutePath() + "] should not exist");

        AgentInstaller.main(new String[] {
                "--target-location=" + wfHome.getAbsolutePath(),
                "--server-url=http://" + hawkularHost + ":" + hawkularHttpPort,
                "--username=" + testUser,
                "--password=" + testPasword,
                "--config-only"
        });

        assertAgentInStandalone(true, wfHome);

    }

    private void assertAgentInStandalone(boolean agentAvailableExpected, File wfHome) throws UnsupportedEncodingException,
            FileNotFoundException, IOException, SAXException, ParserConfigurationException, XPathExpressionException {
        XPath xPath = XPathFactory.newInstance().newXPath();
        File standaloneXml = new File(wfHome, "standalone/configuration/standalone.xml");

        try (Reader r = new InputStreamReader(new FileInputStream(standaloneXml), "utf-8")) {
            DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
            dbf.setNamespaceAware(true);
            Document standaloneDom = dbf.newDocumentBuilder().parse(new InputSource(r));

            boolean foundExtensionModule = (boolean) xPath.evaluate(
                    "/*[local-name()='server']/*[local-name()='extensions']" +
                            "/*[local-name()='extension' and @module='org.hawkular.agent']",
                    standaloneDom, XPathConstants.BOOLEAN);

            Assert.assertEquals(foundExtensionModule, agentAvailableExpected,
                    "/server/extensions/extension[@module='org.hawkular.agent'] should "
                            + (agentAvailableExpected ? "" : "not") + " exist in [" + standaloneXml.getAbsolutePath()
                            + "]");

            boolean foundSubsystem = (boolean) xPath.evaluate(
                    "/*[local-name()='server']/*[local-name()='profile']" +
                            "/*[local-name()='subsystem' and namespace-uri()='urn:org.hawkular.agent:agent:1.0']",
                    standaloneDom, XPathConstants.BOOLEAN);

            Assert.assertEquals(foundSubsystem, agentAvailableExpected,
                    "/server/profile/subsystem[@xmlns='urn:org.hawkular.agent:agent:1.0'] should "
                            + (agentAvailableExpected ? "" : "not ") + "exist in [" + standaloneXml.getAbsolutePath()
                            + "]");

        }
    }

}
