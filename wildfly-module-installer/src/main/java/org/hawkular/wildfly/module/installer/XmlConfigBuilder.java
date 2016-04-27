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

import java.io.ByteArrayInputStream;
import java.io.File;
import java.net.URL;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import javax.xml.xpath.XPath;
import javax.xml.xpath.XPathConstants;
import javax.xml.xpath.XPathExpression;
import javax.xml.xpath.XPathExpressionException;
import javax.xml.xpath.XPathFactory;

import org.jboss.logging.Logger;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

class XmlConfigBuilder {

    private static final String PREFIX = "x";
    private static final String PREFIX_CONTENT = "ns";
    private static final Pattern NS_IN_XPATH = Pattern.compile("namespace-uri\\(\\)[^\']+\'([^\']+)");
    private List<XmlEdit> edits;
    private boolean failNoMatch;
    private final File targetFile;
    private final File sourceFile;
    final DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
    final XPath xpath = XPathFactory.newInstance().newXPath();
    final NamespaceContextImpl namespaceContext = new NamespaceContextImpl();
    private final Logger log = Logger.getLogger(this.getClass());

    public XmlConfigBuilder(File sourceFile, File targetFile) {
        factory.setNamespaceAware(true);
        xpath.setNamespaceContext(namespaceContext);
        this.sourceFile = sourceFile;
        this.targetFile = targetFile;
    }

    public File getSourceFile() {
        return sourceFile;
    }

    public File getTargetFile() {
        return targetFile;
    }

    private void debug(String message) {
        if (log.isDebugEnabled()) {
            log.debug(message);
        }
    }

    private void warning(String message) {
        log.warn(message);
    }

    public void build() throws Exception {
        DocumentBuilder dBuilder = factory.newDocumentBuilder();
        Document srcDoc = dBuilder.parse(sourceFile);

        // sort inserts by the shortest
        Collections.sort(getXmlEdits(), new Comparator<XmlEdit>() {

            public int compare(XmlEdit o1, XmlEdit o2) {
                Integer o1Size = o1.getSelect().split("/").length;
                Integer o2Size = o2.getSelect().split("/").length;
                return o1Size.compareTo(o2Size);
            }
        });
        debug("Building [" + this.sourceFile + "] ");
        // if our target document defines namespace in root element, let's read
        // it
        String namespace = getNameSpace(srcDoc);
        if (namespace != null) {
            namespaceContext.mapping(PREFIX, namespace);
        }

        for (XmlEdit xmlEdit : getXmlEdits()) {
            debug("Applying " + xmlEdit);
            String expression = xmlEdit.getSelect();

            if (namespace != null) {
                // enhance given xpath to use namespaces
                expression = xpath2Namespaced(expression, PREFIX);
                debug("Expression " + expression);
            }
            XPathExpression expr = xpath.compile(expression);
            try {
                NodeList nl = (NodeList) expr.evaluate(srcDoc, XPathConstants.NODESET);
                if (nl.getLength() == 0) {
                    if (failNoMatch) {
                        throw new Exception("Failed to update [" + targetFile.getAbsolutePath() + "] " + xmlEdit
                                + " does not select any element");
                    }
                    warning(xmlEdit + " does not select any element");
                    continue;
                }
                debug("Expression evaluated to " + nl.getLength() + " nodes");
                if (xmlEdit.isAttributeContent()) {
                    String attribValue = xmlEdit.getXml(); // we only support XML. Stream content in future if we need
                    for (int i = 0; i < nl.getLength(); i++) {
                        Node node = nl.item(i);
                        if (node instanceof Element) {
                            Element element = (Element) node;
                            element.setAttribute(xmlEdit.getAttribute(), attribValue);
                            break;
                        }
                    }
                } else {
                    Document contentDoc = null;
                    if (xmlEdit.getContent() != null) {
                        debug("Loading content XML from file " + xmlEdit.getContent());
                        contentDoc = dBuilder.parse(xmlEdit.getContent().openStream());
                    } else {
                        debug("Loading content XML from string");
                        contentDoc = dBuilder.parse(new ByteArrayInputStream(xmlEdit.getXml().getBytes()));
                    }

                    for (int i = 0; i < nl.getLength(); i++) {
                        Node node = nl.item(i);
                        if (node instanceof Element) {
                            Element element = (Element) node;
                            Node inserting = contentDoc.getDocumentElement().cloneNode(true);
                            srcDoc.adoptNode(inserting);
                            String recentNs = findRecentNamespaceFromXpath(expression);
                            // is the root node of inserting content already present?
                            XPathExpression contentExpr = createContentRootExpression(contentDoc, recentNs, namespace,
                                    xmlEdit.getAttribute(), xmlEdit.isIgnoreAttributeValue());
                            NodeList existingNodes = (NodeList) contentExpr.evaluate(element, XPathConstants.NODESET);
                            if (existingNodes.getLength() > 0) {
                                // we need to remove those? (could be many)... we'll just replace the last guy
                                element.replaceChild(inserting, existingNodes.item(existingNodes.getLength() - 1));
                            } else {
                                element.appendChild(inserting);
                            }

                            String contentNs = getNameSpace(contentDoc);
                            // find most recent NS from inserted node back to root
                            // node and assign to it
                            recentNs = findRecentNamespace(srcDoc, inserting);
                            if (contentNs == null && recentNs != null) {
                                // content document does not have namespace, let's
                                // rename it to our namespace
                                renameNamespaceRecursive(srcDoc, inserting, recentNs);
                            }
                        }
                    }
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
        writeTargetDcoument(srcDoc);
    }

    /**
     * looks for most recent namespace query in xpath (denoted by namespace-uri()='')
     * @param expression
     * @return
     */
    public static String findRecentNamespaceFromXpath(String expression) {
        Matcher m = NS_IN_XPATH.matcher(expression);
        String ns = null;
        while (m.find()) {
            ns = m.group(1);
        }
        return ns;
    }

    private String findRecentNamespace(Document doc, Node node) {
        Node parent = null;
        while ((parent = node.getParentNode()) != null) {
            Element parentEl = (Element) parent;
            String ns = parentEl.getAttribute("xmlns");
            if (!ns.isEmpty()) {
                return ns;
            }
            if (parent.isSameNode(doc.getDocumentElement())) {
                return null;
            }
            if (parent.isEqualNode(node)) {
                return null;
            }
            node = parent;
        }
        return null;
    }

    private void writeTargetDcoument(Document doc) throws Exception {
        debug("Writing target file..");
        TransformerFactory transformerFactory = TransformerFactory.newInstance();
        Transformer transformer = transformerFactory.newTransformer();
        transformer.setOutputProperty(OutputKeys.INDENT, "yes");
        transformer.setOutputProperty(OutputKeys.METHOD, "xml");
        transformer.setOutputProperty("{http://xml.apache.org/xslt}indent-amount", "4");

        // remove empty/text nodes in order to "properly" format it
        XPathExpression xpathExp = xpath.compile(
                "//text()[normalize-space(.) = '']");
            NodeList emptyTextNodes = (NodeList) xpathExp.evaluate(doc, XPathConstants.NODESET);

            // Remove each empty text node from document.
            for (int i = 0; i < emptyTextNodes.getLength(); i++) {
                Node emptyTextNode = emptyTextNodes.item(i);
                emptyTextNode.getParentNode().removeChild(emptyTextNode);
            }

        DOMSource source = new DOMSource(doc);
        StreamResult result = new StreamResult(targetFile);

        transformer.transform(source, result);
    }

    // to help debugging
    /*
    private void printDocument(Document doc) {
        try {
            DOMImplementationLS domImplementation = (DOMImplementationLS) doc.getImplementation();
            log.fatal("XML DOCUMENT:\n" + domImplementation.createLSSerializer().writeToString(doc));
        } catch (Throwable t) {
            log.warn("Can't print doc: " + t);
        }
    }
    */

    public static String xpath2Namespaced(String expression, String prefix) {
        String[] components = expression.split("/");
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < components.length; i++) {
            String piece = components[i];
            if (piece.matches("^\\w+.*")) {
                piece = prefix + ":" + piece;
            }
            sb.append(piece + "/");
        }
        if (sb.length() > 0) {
            sb.deleteCharAt(sb.length() - 1);
        }
        return sb.toString();
    }

    public static String element2Xpath(Element element, String prefix, String defaultPrefix) {
        return element2Xpath(element, prefix, defaultPrefix, null);
    }

    public static String element2Xpath(Element element, String prefix, String defaultPrefix, String identityAttribute) {
        return element2Xpath(element, prefix, defaultPrefix, identityAttribute, false);
    }

    public static String element2Xpath(Element element, String prefix, String defaultPrefix, String identityAttribute,
            boolean ignoreAttributeValue) {
        StringBuilder sb = new StringBuilder("/");
        String ns = element.getAttribute("xmlns");
        String pref = "";
        if (!ns.isEmpty()) {
            pref = prefix + ":";
        } else if (defaultPrefix != null) {
            pref = defaultPrefix + ":";
        }
        sb.append(pref + element.getLocalName());
        if (identityAttribute != null && !identityAttribute.isEmpty()) {
            if (ignoreAttributeValue) {
                sb.append("[@" + identityAttribute + "]");
            } else {
                sb.append("[@" + identityAttribute + "='" + element.getAttribute(identityAttribute) + "']");
            }
        } else {
            // use all attributes
            NamedNodeMap attributes = element.getAttributes();
            if (attributes.getLength() > 0) {
                StringBuilder sbAttributes = new StringBuilder();
                sbAttributes.append("[");
                int validAttributes = 0;
                for (int i = 0; i < attributes.getLength(); i++) {
                    Node node = attributes.item(i);
                    if ("xmlns".equals(node.getNodeName())) {
                        // ignore this guy as it's handled with prefixing
                        continue;
                    }
                    validAttributes++;
                    sbAttributes.append("@" + node.getNodeName() + "='" + node.getNodeValue() + "' and ");
                }
                if (validAttributes > 0) {
                    sbAttributes.delete(sbAttributes.length() - 5, sbAttributes.length()); // delete last ' and '
                    sbAttributes.append("]");
                    sb.append(sbAttributes.toString());
                }
            }
        }

        return sb.toString();
    }

    private XPathExpression createContentRootExpression(Document contentDoc, String contentNamespace,
            String rootNamespace, String identityAttribute, boolean ignoreAttributeValue)
            throws Exception {
        String expression = null;

        if (contentNamespace != null) {
            expression = element2Xpath(contentDoc.getDocumentElement(), PREFIX_CONTENT, PREFIX_CONTENT,
                    identityAttribute, ignoreAttributeValue);
            namespaceContext.mapping(PREFIX_CONTENT, contentNamespace);
        } else {
            String ns = getNameSpace(contentDoc);
            if (ns != null) {
                expression = element2Xpath(contentDoc.getDocumentElement(), PREFIX_CONTENT, null, identityAttribute,
                        ignoreAttributeValue);
                namespaceContext.mapping(PREFIX_CONTENT, ns);
            } else {
                expression = element2Xpath(contentDoc.getDocumentElement(), PREFIX_CONTENT,
                        rootNamespace == null ? null : PREFIX, identityAttribute, ignoreAttributeValue);
            }
        }

        debug("Content expression " + expression);
        // our expression always starts with /, but we'll be evaluating it in
        // context of some other node, thus it needs to be relative
        return xpath.compile(expression.substring(1));
    }

    public XmlConfigBuilder failNoMatch(boolean failNoMatch) throws Exception {
        this.failNoMatch = failNoMatch;
        return this;
    }

    public XmlConfigBuilder edit(XmlEdit insert) throws Exception {
        validateInsert(insert);
        getXmlEdits().add(insert);
        return this;
    }

    public XmlConfigBuilder edits(List<XmlEdit> inserts) throws Exception {
        for (XmlEdit i : inserts) {
            validateInsert(i);
            getXmlEdits().add(i);
        }
        return this;
    }

    private List<XmlEdit> getXmlEdits() {
        if (edits == null) {
            edits = new ArrayList<XmlEdit>();
        }
        return edits;
    }

    private void validateInsert(XmlEdit insert) throws IllegalArgumentException, XPathExpressionException {
        URL f = insert.getContent();
        if (f == null && insert.getXml() == null) {
            throw new IllegalArgumentException("Either content or xml must be specified");
        }
        String expression = insert.getSelect();
        if (expression.length() > 1 && expression.endsWith("/")) { // remove
                                                                   // trailing
                                                                   // slash
            insert.setSelect(expression.substring(0, expression.length() - 1));
        }
        try {
            xpath.compile(insert.getSelect());
        } catch (XPathExpressionException xee) {
            throw new XPathExpressionException(insert.getSelect() + " is not a valid xpath : " + xee.getMessage());
        }

        // TODO validate expression must eval to NODELIST
    }

    /**
     * return XML namespace for root of given document
     * @param doc
     * @return null if namespace is not defined
     */
    private static String getNameSpace(Document doc) {
        if (doc == null) {
            return null;
        }
        String ns = doc.getDocumentElement().getAttribute("xmlns");
        if (ns.isEmpty()) {
            return null;
        }
        return ns;
    }

    private static void renameNamespaceRecursive(Document doc, Node node, String namespace) {
        if (node.getNodeType() == Node.ELEMENT_NODE) {
            Element el = (Element) node;
            if (el.getAttribute("xmlns").isEmpty()) {
                doc.renameNode(node, namespace, node.getNodeName());
            } else {
                return; // stop renaming ns here, since all children of this
                        // node belong to it's ns
            }

        }

        NodeList list = node.getChildNodes();
        for (int i = 0; i < list.getLength(); ++i) {
            renameNamespaceRecursive(doc, list.item(i), namespace);
        }
    }
}
