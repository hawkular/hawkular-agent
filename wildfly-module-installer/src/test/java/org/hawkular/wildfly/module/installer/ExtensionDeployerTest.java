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

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Paths;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.xpath.XPath;
import javax.xml.xpath.XPathConstants;
import javax.xml.xpath.XPathExpression;
import javax.xml.xpath.XPathFactory;

import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;
import org.junit.Assert;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.w3c.dom.Document;
import org.w3c.dom.NodeList;
import org.w3c.dom.ls.DOMImplementationLS;

public class ExtensionDeployerTest {

    static final File widlflyHome = Paths.get("target", "fake-wildfly").toFile();
    static final File standaloneXml = Paths.get(widlflyHome.getAbsolutePath(), "standalone",
            "configuration", "standalone.xml").toFile();

    static final File domainXml = Paths.get(widlflyHome.getAbsolutePath(), "domain",
            "configuration", "domain.xml").toFile();

    static final File hostXml = Paths.get(widlflyHome.getAbsolutePath(), "domain",
            "configuration", "host.xml").toFile();

    static final File modulesHome = Paths.get(widlflyHome.getAbsolutePath(), "modules",
            "system", "layers", "base").toFile();
    private File moduleZip = Paths.get("target", "test-module.zip").toFile();

    final DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
    DocumentBuilder dBuilder;
    final XPath xpath = XPathFactory.newInstance().newXPath();

    public ExtensionDeployerTest() {
        try {
            factory.setNamespaceAware(true);
            dBuilder = factory.newDocumentBuilder();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private static File getResourceFile(String name) {
        return new File("src/test/resources/" + name);
    }

    private void createModuleZip(String... resources) throws Exception {
        FileOutputStream fout = new FileOutputStream(moduleZip);
        ZipOutputStream zout = new ZipOutputStream(fout);
        // nest to some subdir
        for (String res : resources) {
            ZipEntry ze = new ZipEntry("fake-module/main/" + res);
            zout.putNextEntry(ze);
            zout.write(IOUtils.toByteArray(getClass().getResourceAsStream("/module/" + res)));
            zout.closeEntry();
        }
        zout.close();

    }

    // to help debugging the tests themselves
    private void printDocument(String str, Document doc) {
        try {
            DOMImplementationLS domImplementation = (DOMImplementationLS) doc.getImplementation();
            System.out.println(
                    "XML DOCUMENT for [" + str + "]:\n" + domImplementation.createLSSerializer().writeToString(doc));
        } catch (Throwable t) {
            System.out.println("Can't print doc: " + t);
        }
    }

    @BeforeClass
    public static void prepareTargetWildfly() throws Exception {
        modulesHome.mkdirs();
        standaloneXml.getParentFile().mkdirs();
        domainXml.getParentFile().mkdirs();
        hostXml.getParentFile().mkdirs();
    }

    @Before
    public void beforeTest() throws IOException {
        if (moduleZip != null) {
            moduleZip.delete();
        }
        // clean up deployed module in fake wildfly
        FileUtils.deleteDirectory(new File(modulesHome, "fake-module"));
        // copy minimal configs
        FileUtils.copyFile(getResourceFile("standalone-minimal.xml"), standaloneXml);
        FileUtils.copyFile(getResourceFile("domain-minimal.xml"), domainXml);
        FileUtils.copyFile(getResourceFile("host-minimal.xml"), hostXml);
    }

    @Test
    public void deployEmptyModule() throws Exception {
        createModuleZip("module.xml");
        DeploymentConfiguration configuration = DeploymentConfiguration.builder()
                .jbossHome(widlflyHome)
                .module(moduleZip.toURI().toURL())
                .build();
        new ExtensionDeployer().install(configuration);
        File deployedModuleXml = Paths.get(modulesHome.getAbsolutePath(), "fake-module", "main", "module.xml")
                .toFile();
        Assert.assertTrue("Deployed module.xml exists", deployedModuleXml.exists());
    }

    @Test
    public void deployModuleWithSnippetsIncluded() throws Exception {
        createModuleZip("module.xml", "subsystem-snippet.xml", "socket-binding-snippet.xml");
        DeploymentConfiguration configuration = DeploymentConfiguration.builder()
                .jbossHome(widlflyHome)
                .module(moduleZip.toURI().toURL())
                .build();
        new ExtensionDeployer().install(configuration);
        File deployedModuleXml = Paths.get(modulesHome.getAbsolutePath(), "fake-module", "main", "module.xml")
                .toFile();
        Assert.assertTrue("Deployed module.xml exists", deployedModuleXml.exists());
        Document doc = dBuilder.parse(standaloneXml);
        String xmlns = doc.getDocumentElement().getAttribute("xmlns");
        xpath.setNamespaceContext(new NamespaceContextImpl().mapping("x", xmlns).mapping("foo", "foo"));

        printDocument("Standalone", doc);

        // verify extension was installed
        assertXpath("/x:server/x:extensions/x:extension[@module='org.hawkular.agent.monitor']", doc, 1);
        // verify subsystem is defined, in module.xml we have "org.hawkular.agent.monitor"
        assertXpath("/x:server/x:profile/foo:subsystem", doc, 1);
        // verify socket binding was installed
        assertXpath("/x:server/x:socket-binding-group[@name='standard-sockets']"
                + "/x:outbound-socket-binding[@name='hawkular']", doc, 1);

        try {
            new ExtensionDeployer().install(configuration);
            Assert.fail("Should not be able to install server extension when it already is installed");
        } catch (Exception expected) {
        }
    }

    @Test
    public void deployModuleWithDefaultSocketBinding() throws Exception {
        createModuleZip("module.xml");

        DeploymentConfiguration configuration = DeploymentConfiguration.builder()
                .jbossHome(widlflyHome)
                .module(moduleZip.toURI().toURL())
                .socketBinding(getClass().getResource("/module/socket-binding-snippet.xml"))
                .build();
        new ExtensionDeployer().install(configuration);
        File deployedModuleXml = Paths.get(modulesHome.getAbsolutePath(), "fake-module", "main", "module.xml")
                .toFile();
        Assert.assertTrue("Deployed module.xml exists", deployedModuleXml.exists());
        Document doc = dBuilder.parse(standaloneXml);
        String xmlns = doc.getDocumentElement().getAttribute("xmlns");
        xpath.setNamespaceContext(new NamespaceContextImpl().mapping("x", xmlns).mapping("foo", "foo"));

        // verify extension was installed
        assertXpath("/x:server/x:extensions/x:extension[@module='org.hawkular.agent.monitor']", doc, 1);
        // verify socket binding was installed
        assertXpath("/x:server/x:socket-binding-group[@name='standard-sockets']"
                + "/x:outbound-socket-binding[@name='hawkular']", doc, 1);
    }

    @Test
    public void deployToDomainDefaultProfile() throws Exception {
        createModuleZip("module.xml", "subsystem-snippet.xml");
        DeploymentConfiguration configuration = DeploymentConfiguration.builder()
                .jbossHome(widlflyHome)
                .module(moduleZip.toURI().toURL())
                .serverConfig("domain/configuration/domain.xml")
                .socketBinding(getClass().getResource("/module/socket-binding-snippet.xml"))
                .build();
        new ExtensionDeployer().install(configuration);
        File deployedModuleXml = Paths.get(modulesHome.getAbsolutePath(), "fake-module", "main", "module.xml")
                .toFile();
        Assert.assertTrue("Deployed module.xml exists", deployedModuleXml.exists());

        Document doc = dBuilder.parse(domainXml);
        String xmlns = doc.getDocumentElement().getAttribute("xmlns");
        xpath.setNamespaceContext(new NamespaceContextImpl().mapping("x", xmlns).mapping("foo", "foo"));

        printDocument("Domain (default)", doc);

        // verify extension was installed
        assertXpath("/x:domain/x:extensions/x:extension[@module='org.hawkular.agent.monitor']", doc, 1);

        // verify subsystem was setup in default profile only
        assertXpath("/x:domain/x:profiles/x:profile[@name='default']/foo:subsystem", doc, 1);
        assertXpath("/x:domain/x:profiles/x:profile[@name!='default']/foo:subsystem", doc, 0);

        // verify socket binding was installed
        assertXpath("/x:domain/x:socket-binding-groups/x:socket-binding-group[@name='standard-sockets']"
                + "/x:outbound-socket-binding[@name='hawkular']", doc, 1);
    }

    @Test
    public void deployToDomain() throws Exception {
        createModuleZip("module.xml", "subsystem-snippet.xml");
        DeploymentConfiguration configuration = DeploymentConfiguration.builder()
                .jbossHome(widlflyHome)
                .module(moduleZip.toURI().toURL())
                .configType(ConfigType.DOMAIN)
                .socketBinding(getClass().getResource("/module/socket-binding-snippet.xml"))
                .addProfile("ha")
                .addProfile("full-ha")
                .serverConfig("domain/configuration/domain.xml")
                .build();
        new ExtensionDeployer().install(configuration);
        File deployedModuleXml = Paths.get(modulesHome.getAbsolutePath(), "fake-module", "main", "module.xml")
                .toFile();
        Assert.assertTrue("Deployed module.xml exists", deployedModuleXml.exists());

        Document doc = dBuilder.parse(domainXml);
        String xmlns = doc.getDocumentElement().getAttribute("xmlns");
        xpath.setNamespaceContext(new NamespaceContextImpl().mapping("x", xmlns).mapping("foo", "foo"));

        printDocument("Domain", doc);

        // verify extension was installed
        assertXpath("/x:domain/x:extensions/x:extension[@module='org.hawkular.agent.monitor']", doc, 1);

        // verify subsystem was setup in selected profiles
        assertXpath("/x:domain/x:profiles/x:profile[@name='ha']/foo:subsystem", doc, 1);
        assertXpath("/x:domain/x:profiles/x:profile[@name='full-ha']/foo:subsystem", doc, 1);
        assertXpath("/x:domain/x:profiles/x:profile[@name!='full-ha' and @name!='ha']/foo:subsystem", doc, 0);

        // verify socket binding was installed
        assertXpath("/x:domain/x:socket-binding-groups/x:socket-binding-group[@name='standard-sockets']"
                + "/x:outbound-socket-binding[@name='hawkular']", doc, 1);

        try {
            new ExtensionDeployer().install(configuration);
            Assert.fail("Should not be able to install domain extension when it already is installed");
        } catch (Exception expected) {
        }
    }

    @Test
    public void deployToHost() throws Exception {
        createModuleZip("module.xml", "subsystem-snippet.xml", "socket-binding-snippet.xml");
        DeploymentConfiguration configuration = DeploymentConfiguration.builder()
                .jbossHome(widlflyHome)
                .serverConfig("domain/configuration/host.xml") // should set configType for us, too
                .module(moduleZip.toURI().toURL())
                .build();
        new ExtensionDeployer().install(configuration);
        File deployedModuleXml = Paths.get(modulesHome.getAbsolutePath(), "fake-module", "main", "module.xml")
                .toFile();
        Assert.assertTrue("Deployed module.xml exists", deployedModuleXml.exists());
        Document doc = dBuilder.parse(hostXml);
        String xmlns = doc.getDocumentElement().getAttribute("xmlns");
        xpath.setNamespaceContext(new NamespaceContextImpl().mapping("x", xmlns).mapping("foo", "foo"));

        printDocument("Host", doc);

        // verify extension was installed
        assertXpath("/x:host/x:extensions/x:extension[@module='org.hawkular.agent.monitor']", doc, 1);
        // verify subsystem is defined, in module.xml we have "org.hawkular.agent.monitor"
        assertXpath("/x:host/x:profile/foo:subsystem", doc, 1);
        // verify socket binding was installed
        assertXpath("/x:host/x:socket-binding-group[@name='standard-sockets']"
                + "/x:outbound-socket-binding[@name='hawkular']", doc, 1);

        try {
            new ExtensionDeployer().install(configuration);
            Assert.fail("Should not be able to install host extension when it already is installed");
        } catch (Exception expected) {
        }
    }

    private void assertXpath(String expression, Document doc, int expectedCount) throws Exception {
        XPathExpression expr = xpath.compile(expression);
        NodeList nl = (NodeList) expr.evaluate(doc, XPathConstants.NODESET);
        Assert.assertEquals(expectedCount, nl.getLength());
    }
}
