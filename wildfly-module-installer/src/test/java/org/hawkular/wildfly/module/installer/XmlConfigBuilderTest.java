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
package org.hawkular.wildfly.module.installer;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.StringReader;
import java.net.URL;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.xpath.XPath;
import javax.xml.xpath.XPathConstants;
import javax.xml.xpath.XPathExpression;
import javax.xml.xpath.XPathExpressionException;
import javax.xml.xpath.XPathFactory;

import org.apache.commons.io.IOUtils;
import org.junit.Assert;
import org.junit.Test;
import org.w3c.dom.Document;
import org.w3c.dom.NodeList;
import org.w3c.dom.ls.DOMImplementationLS;
import org.xml.sax.InputSource;

public class XmlConfigBuilderTest {

    final DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
    DocumentBuilder dBuilder;
    final XPath xpath = XPathFactory.newInstance().newXPath();

    public XmlConfigBuilderTest() {
        try {
            factory.setNamespaceAware(true);
            dBuilder = factory.newDocumentBuilder();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void printTempFile() {
        printFile(getTempFile());
    }

    private void printFile(File f) {
        try {
            System.out.println(IOUtils.toString(new FileReader(f)));
        } catch (Exception e) {
        }
    }

    private void printDocument(Document doc) {
        try {
            DOMImplementationLS domImplementation = (DOMImplementationLS) doc.getImplementation();
            System.out.println("XML DOCUMENT:\n" + domImplementation.createLSSerializer().writeToString(doc));
        } catch (Throwable t) {
            System.out.println("Can't print doc: " + t);
        }
    }
    private File getResourceFile(String name) {
        return new File("src/test/resources/" + name);
    }

    private URL getResourceURL(String name) throws Exception {
        return new File("src/test/resources/" + name).toURI().toURL();
    }

    private File getTempFile() {
        return new File(System.getProperty("java.io.tmpdir"), "test.xml");
    }

    private Document xml(String xml) throws Exception {
        InputSource is = new InputSource(new StringReader(xml));
        return dBuilder.parse(is);
    }

    private void assertXpath(String expression, Document doc, int expectedCount) throws Exception {
        XPathExpression expr = xpath.compile(expression);
        NodeList nl = (NodeList) expr.evaluate(doc, XPathConstants.NODESET);
        Assert.assertEquals(expectedCount, nl.getLength());
    }

    @Test
    public void findNamespaceFromXpath() {
        Assert.assertEquals("test", XmlConfigBuilder.findRecentNamespaceFromXpath("/*[namespace-uri()='test']/test"));
        Assert.assertEquals("test", XmlConfigBuilder
                .findRecentNamespaceFromXpath("/*[namespace-uri()='foo']/test/*[namespace-uri()='test']"));
    }

    @Test
    public void testXpath2Namespaced() {
        Assert.assertEquals("", XmlConfigBuilder.xpath2Namespaced("", ""));
        Assert.assertEquals("x:test/x:test1", XmlConfigBuilder.xpath2Namespaced("test/test1", "x"));
        Assert.assertEquals("/x:test[@a='x']/*[@b='y']",
                XmlConfigBuilder.xpath2Namespaced("/test[@a='x']/*[@b='y']", "x"));
    }

    @Test
    public void testElement2Xpath() throws Exception {
        // element without ns
        Assert.assertEquals("/test",
                XmlConfigBuilder.element2Xpath(xml("<test></test>").getDocumentElement(), "x", null));
        // element without ns default prefix passed
        Assert.assertEquals("/y:test",
                XmlConfigBuilder.element2Xpath(xml("<test></test>").getDocumentElement(), "x", "y"));
        // element with ns
        Assert.assertEquals("/x:test",
                XmlConfigBuilder.element2Xpath(xml("<test xmlns=\"foo\"></test>").getDocumentElement(), "x", null));
        // element with attributes
        Assert.assertEquals("/test[@attr1='val1' and @attr2='val2']",
                XmlConfigBuilder.element2Xpath(xml("<test attr1=\"val1\"  attr2=\"val2\"></test>")
                        .getDocumentElement(), "x", null));
        // element with ns and attributes
        Assert.assertEquals("/x:test[@attr1='val1' and @attr2='val2']",
                XmlConfigBuilder.element2Xpath(xml("<test attr1=\"val1\" xmlns=\"foo\"  attr2=\"val2\"></test>")
                        .getDocumentElement(), "x", null));
        // element with attributes, limit xpath to only look at one attribute
        Assert.assertEquals("/test[@attr1='val1']",
                XmlConfigBuilder.element2Xpath(xml("<test attr1=\"val1\"  attr2=\"val2\"></test>")
                        .getDocumentElement(), "x", null, "attr1"));
        // element with attributes, limit xpath to only look at one attribute and don't care about its value
        Assert.assertEquals("/test[@attr1]",
                XmlConfigBuilder.element2Xpath(xml("<test attr1=\"val1\"  attr2=\"val2\"></test>")
                        .getDocumentElement(), "x", null, "attr1", true));
    }

    @Test
    public void testRootAppend() throws Exception {
        XmlConfigBuilder builder = new XmlConfigBuilder(getResourceFile("root.xml"), getTempFile());
        builder.edit(new XmlEdit("/server", getResourceURL("content1Append.xml")));
        builder.build();
        Document doc = dBuilder.parse(builder.getTargetFile());
        assertXpath("/server/subsystem[@name='foobar']/child", doc, 2);
        assertXpath("/server/subsystem[@name='foo']/child", doc, 1); // subsystem
                                                                     // without
                                                                     // with
                                                                     // different
                                                                     // name
                                                                     // unchanged
    }

    @Test
    public void testIgnoreAttributeValue() throws Exception {
        // notice we are changing the one that does NOT have a namespace
        XmlEdit edit = new XmlEdit("/server",
                "<subsystem name=\"fooUPDATE\"><childUPDATE attrUPDATE=\"value2UPDATE\"></childUPDATE></subsystem>");
        edit.withAttribute("name");
        edit.withIsIgnoreAttributeValue(true);

        XmlConfigBuilder builder = new XmlConfigBuilder(getResourceFile("root.xml"), getTempFile());
        builder.edit(edit);
        builder.build();

        Document doc = dBuilder.parse(builder.getTargetFile());
        printDocument(doc);
        assertXpath("/server/subsystem[@name='fooUPDATE']/childUPDATE", doc, 1);
        assertXpath("/server/subsystem[@name='foo']", doc, 0);
        assertXpath("/server/subsystem[@name='foo']/child", doc, 0);
    }

    @Test
    public void testChangeAttributeValue() throws Exception {
        // notice we are changing the one that does NOT have a namespace
        XmlEdit edit = new XmlEdit("/server/subsystem[@name]", "fooUPDATE");
        edit.withAttribute("name");
        edit.withIsAttributeContent(true);

        XmlConfigBuilder builder = new XmlConfigBuilder(getResourceFile("root.xml"), getTempFile());
        builder.edit(edit);
        builder.build();

        Document doc = dBuilder.parse(builder.getTargetFile());
        // this first test is just to make sure we started with a subsystem with name=bar
        assertXpath("/server/subsystem[@name='foo']/child", dBuilder.parse(getResourceFile("root.xml")), 1);
        assertXpath("/server/subsystem[@name='fooUPDATE']/child", doc, 1);
        assertXpath("/server/subsystem[@name='foo']/child", doc, 0);
    }

    @Test
    public void testChangeAttributeValueWithNamespace() throws Exception {
        XmlEdit edit = new XmlEdit("/server/*[namespace-uri()='foo'][@name]", "barUPDATE");
        edit.withAttribute("name");
        edit.withIsAttributeContent(true);

        XmlConfigBuilder builder = new XmlConfigBuilder(getResourceFile("root.xml"), getTempFile());
        builder.edit(edit);
        builder.build();

        Document doc = dBuilder.parse(builder.getTargetFile());
        System.out.println("testChangeAttributeValueWithNamespace");
        printFile(getResourceFile("root.xml"));
        printFile(builder.getTargetFile());
        printDocument(doc);

        // this first test is just to make sure we started with a subsystem under namespace foo with name=bar
        assertXpath("/server/foo:subsystem[@name='bar']/foo:child",
                dBuilder.parse(getResourceFile("root.xml")), 1);
        assertXpath("/server/foo:subsystem[@name='bar']/foo:child", doc, 0);
        assertXpath("/server/foo:subsystem[@name='barUPDATE']/foo:child", doc, 1);
    }

    @Test
    public void testRootAppendKeepNestedNS() throws Exception {
        XmlConfigBuilder builder = new XmlConfigBuilder(getResourceFile("root.xml"), getTempFile());
        builder.edit(new XmlEdit("/server", getResourceURL("content1AppendNestedNS.xml")));
        builder.build();
        Document doc = dBuilder.parse(builder.getTargetFile());
        xpath.setNamespaceContext(new NamespaceContextImpl().mapping("x", "keepme"));
        assertXpath("/server/subsystem[@name='foobar']/child", doc, 2);
        assertXpath("/server/subsystem[@name='foo']/child", doc, 1); // subsystem
                                                                     // without
                                                                     // with
                                                                     // different
                                                                     // name
                                                                     // unchanged
        assertXpath("/server/subsystem[@name='foobar']/x:child2/x:keepme", doc, 1); // verify
                                                                                    // we
                                                                                    // did
                                                                                    // not
                                                                                    // overwrite
                                                                                    // nested
                                                                                    // namespace
    }

    @Test
    public void testRootReplace() throws Exception {
        XmlConfigBuilder builder = new XmlConfigBuilder(getResourceFile("root.xml"), getTempFile());
        builder.edit(new XmlEdit("/server", getResourceURL("content1Replace.xml")));
        builder.build();
        Document doc = dBuilder.parse(builder.getTargetFile());
        assertXpath("/server/subsystem[@name='foo']/child", doc, 2); // subsystem
                                                                     // modified
    }

    @Test
    public void testNSRrootAppend() throws Exception {
        XmlConfigBuilder builder = new XmlConfigBuilder(getResourceFile("rootNS.xml"), getTempFile());
        builder.edit(new XmlEdit("/server", getResourceURL("content1Append.xml")));
        builder.build();
        Document doc = dBuilder.parse(builder.getTargetFile());
        String xmlns = doc.getDocumentElement().getAttribute("xmlns");
        xpath.setNamespaceContext(new NamespaceContextImpl().mapping("x", xmlns));
        assertXpath("/x:server/x:subsystem[@name='foobar']/x:child", doc, 2);
        assertXpath("/x:server/x:subsystem[@name='foo']/x:child", doc, 1); // subsystem
                                                                           // without
                                                                           // with
                                                                           // different
                                                                           // name
                                                                           // unchanged
    }

    @Test
    public void testNSRootReplace() throws Exception {
        XmlConfigBuilder builder = new XmlConfigBuilder(getResourceFile("rootNS.xml"), getTempFile());
        builder.edit(new XmlEdit("/server", getResourceURL("content1Replace.xml")));
        builder.build();
        Document doc = dBuilder.parse(builder.getTargetFile());
        String xmlns = doc.getDocumentElement().getAttribute("xmlns");
        xpath.setNamespaceContext(new NamespaceContextImpl().mapping("x", xmlns).mapping("ns", "foo"));
        assertXpath("/x:server/ns:subsystem/ns:child", doc, 5); // subsystem
                                                                // without name
                                                                // attribute
                                                                // unchaned (4
                                                                // +1 already
                                                                // present)
        assertXpath("/x:server/x:subsystem[@name='foo']/x:child", doc, 2); // subsystem
                                                                           // without
                                                                           // ns
                                                                           // replaced
    }

    @Test
    public void testNSRootNSAppend() throws Exception {
        XmlConfigBuilder builder = new XmlConfigBuilder(getResourceFile("rootNS.xml"), getTempFile());
        builder.edit(new XmlEdit("/server", getResourceURL("content1NSAppend.xml")));
        builder.build();
        Document doc = dBuilder.parse(builder.getTargetFile());
        String xmlns = doc.getDocumentElement().getAttribute("xmlns");
        xpath.setNamespaceContext(new NamespaceContextImpl().mapping("x", xmlns).mapping("ns", "something"));
        assertXpath(
                "/*[local-name()='server']/*[local-name()='subsystem'"
                + " and namespace-uri()='something']/*[local-name()='child']",
                doc, 2);
        // query using namespaces
        assertXpath("/x:server/ns:subsystem/ns:child", doc, 2);
        assertXpath("/x:server/x:subsystem[@name='foo']/x:child", doc, 1); // subsystem
                                                                           // without
                                                                           // ns
                                                                           // unchanged
    }

    @Test
    public void testNSRootNSReplace() throws Exception {
        XmlConfigBuilder builder = new XmlConfigBuilder(getResourceFile("rootNS.xml"), getTempFile());
        builder.edit(new XmlEdit("/server", getResourceURL("content1NSReplace.xml")));
        builder.build();
        Document doc = dBuilder.parse(builder.getTargetFile());
        String xmlns = doc.getDocumentElement().getAttribute("xmlns");
        xpath.setNamespaceContext(new NamespaceContextImpl().mapping("x", xmlns).mapping("ns", "foo"));
        assertXpath(
                "/*[local-name()='server']/*[local-name()='subsystem'"
                + " and namespace-uri()='foo' and @name='bar']/*[local-name()='child']",
                doc, 2);
        // query using namespaces
        assertXpath("/x:server/ns:subsystem[@name='bar']/ns:child", doc, 2); // replaced
        assertXpath("/x:server/ns:subsystem/ns:child", doc, 6); // subsystem
                                                                // without name
                                                                // attribute
                                                                // unchaned (4
                                                                // already
                                                                // present + 2
                                                                // added)
        assertXpath("/x:server/x:subsystem[@name='foo']/x:child", doc, 1); // subsystem
                                                                           // without
                                                                           // ns
                                                                           // unchanged
    }

    @Test
    public void testNSRootAppendUnderNS() throws Exception {
        XmlConfigBuilder builder = new XmlConfigBuilder(getResourceFile("rootNS.xml"), getTempFile());
        builder.edit(new XmlEdit("/server/*[local-name()='subsystem' and namespace-uri()='foo' and @name='bar']",
                getResourceURL("content1Append.xml")));
        builder.build();
        Document doc = dBuilder.parse(builder.getTargetFile());
        String xmlns = doc.getDocumentElement().getAttribute("xmlns");
        xpath.setNamespaceContext(new NamespaceContextImpl().mapping("x", xmlns).mapping("ns", "foo"));
        assertXpath("/x:server/ns:subsystem[@name='bar']/ns:subsystem[@name='foobar']", doc, 1);
        assertXpath("/x:server/ns:subsystem[@name='bar']/ns:subsystem[@name='foobar']/ns:child", doc, 2);
    }

    @Test(expected = XPathExpressionException.class)
    public void invalidSelect() throws Exception {
        XmlConfigBuilder builder = new XmlConfigBuilder(getResourceFile("rotNS.xml"), getTempFile());
        builder.edit(new XmlEdit("/serv hello?", getResourceURL("content1Append.xml")));
        builder.build();
    }

    @Test(expected = FileNotFoundException.class)
    public void contentDoesNotExist() throws Exception {
        XmlConfigBuilder builder = new XmlConfigBuilder(getResourceFile("rotNS.xml"), getTempFile());
        builder.edit(new XmlEdit("/server", getResourceURL("foo.xml")));
        builder.build();
    }

    // @Test()
    public void invalidSelectNoNodeset() throws Exception {
        // TODO expect exception
        XmlConfigBuilder builder = new XmlConfigBuilder(getResourceFile("rootNS.xml"), getTempFile());
        builder.edit(new XmlEdit("/server/@attr", getResourceURL("content1.xml")));
        builder.build();
    }
}
