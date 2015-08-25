/*
 * Copyright 2015 Red Hat, Inc. and/or its affiliates
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
package org.hawkular.agent.monitor.modules;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.Stack;

import javax.xml.namespace.NamespaceContext;
import javax.xml.stream.XMLOutputFactory;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamWriter;

import org.hawkular.agent.monitor.feedcomm.InvalidCommandRequestException;

/**
 * Operations related to JBoss modules. Inspired by {@code org.jboss.as.cli.handlers.module.ASModuleHandler} from
 * {@code org.wildfly.core:wildfly-cli}.
 *
 * @author <a href="https://github.com/ppalaga">Peter Palaga</a>
 */
public class Modules {
    /**
     * Element and attribute names and values for module.xml files.
     */
    public enum Name {
        dependencies, main_class {
            public String toString() {
                return "main-class";
            }
        },
        module, module_ns {
            public String toString() {
                return "urn:jboss:module:1.1";
            }
        },
        name, path, properties, property, resource_root {
            public String toString() {
                return "resource-root";
            }
        },
        resources, slot, value
    }

    private static class PrettyWriter implements XMLStreamWriter {
        private static final int INDENT_SIZE = 2;
        private static final char[] NEW_LINE = new char[] { '\n' };
        private static final char[] SPACE = new char[] { ' ' };
        private final XMLStreamWriter delegate;

        private int indent = 0;

        /** Stores {@link Boolean#TRUE} if the element has children, or {@link Boolean#FALSE} otherwise */
        private final Stack<Boolean> stack = new Stack<>();

        public PrettyWriter(XMLStreamWriter delegate) {
            super();
            this.delegate = delegate;
        }

        public void close() throws XMLStreamException {
            delegate.close();
        }

        public void flush() throws XMLStreamException {
            delegate.flush();
        }

        public NamespaceContext getNamespaceContext() {
            return delegate.getNamespaceContext();
        }

        public String getPrefix(String uri) throws XMLStreamException {
            return delegate.getPrefix(uri);
        }

        public Object getProperty(String name) throws IllegalArgumentException {
            return delegate.getProperty(name);
        }

        private void indent() throws XMLStreamException {
            delegate.writeCharacters(NEW_LINE, 0, 1);
            for (int i = 0; i < indent; i++) {
                delegate.writeCharacters(SPACE, 0, 1);
            }
            indent += INDENT_SIZE;

            if (!stack.empty()) {
                stack.set(stack.size() - 1, Boolean.TRUE);
            }

            stack.push(Boolean.FALSE);
        }

        public void setDefaultNamespace(String uri) throws XMLStreamException {
            delegate.setDefaultNamespace(uri);
        }

        public void setNamespaceContext(NamespaceContext context) throws XMLStreamException {
            delegate.setNamespaceContext(context);
        }

        public void setPrefix(String prefix, String uri) throws XMLStreamException {
            delegate.setPrefix(prefix, uri);
        }

        private void unindent() throws XMLStreamException {
            indent -= INDENT_SIZE;

            if (stack.pop().booleanValue()) {
                delegate.writeCharacters(NEW_LINE, 0, 1);
                for (int i = 0; i < indent; i++) {
                    delegate.writeCharacters(SPACE, 0, 1);
                }
            }
        }

        public void writeAttribute(String localName, String value) throws XMLStreamException {
            delegate.writeAttribute(localName, value);
        }

        public void writeAttribute(String namespaceURI, String localName, String value) throws XMLStreamException {
            delegate.writeAttribute(namespaceURI, localName, value);
        }

        public void writeAttribute(String prefix, String namespaceURI, String localName, String value)
                throws XMLStreamException {
            delegate.writeAttribute(prefix, namespaceURI, localName, value);
        }

        public void writeCData(String data) throws XMLStreamException {
            delegate.writeCData(data);
        }

        public void writeCharacters(char[] text, int start, int len) throws XMLStreamException {
            delegate.writeCharacters(text, start, len);
        }

        public void writeCharacters(String text) throws XMLStreamException {
            delegate.writeCharacters(text);
        }

        public void writeComment(String data) throws XMLStreamException {
            delegate.writeComment(data);
        }

        public void writeDefaultNamespace(String namespaceURI) throws XMLStreamException {
            delegate.writeDefaultNamespace(namespaceURI);
        }

        public void writeDTD(String dtd) throws XMLStreamException {
            delegate.writeDTD(dtd);
        }

        public void writeEmptyElement(String localName) throws XMLStreamException {
            indent();
            delegate.writeEmptyElement(localName);
            unindent();
        }

        public void writeEmptyElement(String namespaceURI, String localName) throws XMLStreamException {
            indent();
            delegate.writeEmptyElement(namespaceURI, localName);
            unindent();
        }

        public void writeEmptyElement(String prefix, String localName, String namespaceURI) throws XMLStreamException {
            indent();
            delegate.writeEmptyElement(prefix, localName, namespaceURI);
            unindent();
        }

        public void writeEndDocument() throws XMLStreamException {
            delegate.writeEndDocument();
        }

        public void writeEndElement() throws XMLStreamException {
            unindent();
            delegate.writeEndElement();
        }

        public void writeEntityRef(String name) throws XMLStreamException {
            delegate.writeEntityRef(name);
        }

        public void writeNamespace(String prefix, String namespaceURI) throws XMLStreamException {
            delegate.writeNamespace(prefix, namespaceURI);
        }

        public void writeProcessingInstruction(String target) throws XMLStreamException {
            delegate.writeProcessingInstruction(target);
        }

        public void writeProcessingInstruction(String target, String data) throws XMLStreamException {
            delegate.writeProcessingInstruction(target, data);
        }

        public void writeStartDocument() throws XMLStreamException {
            delegate.writeStartDocument();
        }

        public void writeStartDocument(String version) throws XMLStreamException {
            delegate.writeStartDocument(version);
        }

        public void writeStartDocument(String encoding, String version) throws XMLStreamException {
            delegate.writeStartDocument(encoding, version);
        }

        public void writeStartElement(String localName) throws XMLStreamException {
            indent();
            delegate.writeStartElement(localName);
        }

        public void writeStartElement(String namespaceURI, String localName) throws XMLStreamException {
            indent();
            delegate.writeStartElement(namespaceURI, localName);
        }
        public void writeStartElement(String prefix, String localName, String namespaceURI) throws XMLStreamException {
            indent();
            delegate.writeStartElement(prefix, localName, namespaceURI);
        }

    }

    static final String JBOSS_HOME_ENV_VAR = "JBOSS_HOME_ENV_VAR";
    static final String JBOSS_HOME_PROPERTY = "jboss.home.dir";

    public static File findModulesDir() {
        String jbossHomeStr = System.getenv(JBOSS_HOME_ENV_VAR);
        if (jbossHomeStr == null) {
            // Not found, check the system property, this may be set from a client using the CLI API to execute commands
            jbossHomeStr = System.getProperty(JBOSS_HOME_PROPERTY, null);
        }
        if (jbossHomeStr == null) {
            throw new IllegalStateException(JBOSS_HOME_ENV_VAR + " environment variable is not set.");
        }
        File modulesDir = new File(jbossHomeStr, "modules");
        if (!modulesDir.exists()) {
            throw new IllegalStateException(
                    "Failed to locate the modules dir on the filesystem: " + modulesDir.getAbsolutePath());
        }
        return modulesDir;
    }

    private final File modulesDir;

    public Modules(File modulesDir) {
        super();
        this.modulesDir = modulesDir;
    }

    public void add(AddModuleRequest addModuleRequest) throws Exception {
        validate(addModuleRequest);

        final String moduleName = addModuleRequest.getModuleName();
        final File moduleDir = getModulePath(moduleName, addModuleRequest.getSlot());
        if (!moduleDir.mkdirs()) {
            throw new IllegalStateException("Failed to create directory [" + moduleDir.getAbsolutePath() + "]");
        }

        File moduleXmlPath = new File(moduleDir, "module.xml");
        try (Writer w = new OutputStreamWriter(new FileOutputStream(moduleXmlPath), "utf-8")) {
            writeModuleXml(w, addModuleRequest);
        }

        copyResources(addModuleRequest, moduleDir);
    }

    /**
     * @param addModuleRequest
     * @throws IOException
     */
    void copyResources(AddModuleRequest addModuleRequest, File moduleDir) throws IOException {
        for (String srcPath : addModuleRequest.getResources()) {
            File srcFile = new File(srcPath);
            File destFile = new File(moduleDir, srcFile.getName());
            Files.copy(srcFile.toPath(), destFile.toPath(), StandardCopyOption.COPY_ATTRIBUTES);
        }
    }

    File getModulePath(final String moduleName, String slot) {
        return new File(modulesDir,
                moduleName.replace('.', File.separatorChar) + File.separatorChar + (slot == null ? "main" : slot));
    }

    void validate(AddModuleRequest addModuleRequest) throws InvalidCommandRequestException {
        if (!modulesDir.exists()) {
            throw new InvalidCommandRequestException("The $JBOSS_HOME/modules director [" + modulesDir.getAbsolutePath()
                    + "] must exist to be able to create a new module.");
        }
        final String moduleName = addModuleRequest.getModuleName();
        Objects.requireNonNull(moduleName, AddModuleRequest.class.getName() + ".moduleName cannot be null");
        final File moduleDir = getModulePath(moduleName, addModuleRequest.getSlot());
        if (moduleDir.exists()) {
            throw new InvalidCommandRequestException(
                    "[" + moduleName + "] already exists at [" + moduleDir.getAbsolutePath() + "]");
        }

        for (String srcPath : addModuleRequest.getResources()) {
            File srcFile = new File(srcPath);
            if (!srcFile.exists()) {
                throw new InvalidCommandRequestException("File [" + srcFile.getAbsolutePath()
                        + "] to copy to a new module [" + addModuleRequest.getModuleName() + "] does not exist.");
            }
        }

    }

    void writeModuleXml(Writer out, AddModuleRequest addModuleRequest) throws XMLStreamException {

        XMLStreamWriter writer = new PrettyWriter(XMLOutputFactory.newInstance().createXMLStreamWriter(out));
        writer.writeStartDocument();
        writer.writeStartElement(Name.module.toString());
        writer.writeDefaultNamespace(Name.module_ns.toString());

        writer.writeAttribute(Name.name.toString(), addModuleRequest.getModuleName());

        final String slot = addModuleRequest.getSlot();
        if (slot != null) {
            writer.writeAttribute(Name.slot.toString(), slot);
        }

        final Map<String, String> properties = addModuleRequest.getProperties();
        if (properties != null && !properties.isEmpty()) {
            writer.writeStartElement(Name.properties.toString());
            for (Map.Entry<String, String> entry : properties.entrySet()) {
                writer.writeEmptyElement(Name.property.toString());
                writer.writeAttribute(Name.name.toString(), entry.getKey());
                writer.writeAttribute(Name.value.toString(), entry.getValue());
            }
            writer.writeEndElement();
        }

        final String mainClass = addModuleRequest.getMainClass();
        if (mainClass != null) {
            writer.writeEmptyElement(Name.main_class.toString());
            writer.writeAttribute(Name.value.toString(), mainClass);
        }

        final Set<String> resources = addModuleRequest.getResources();
        if (resources != null && !resources.isEmpty()) {
            writer.writeStartElement(Name.resources.toString());
            for (String resPath : resources) {
                writer.writeEmptyElement(Name.resource_root.toString());
                writer.writeAttribute(Name.path.toString(), new File(resPath).getName());
            }
            writer.writeEndElement();
        }

        final Set<String> dependencies = addModuleRequest.getDependencies();
        if (dependencies != null && !dependencies.isEmpty()) {
            writer.writeStartElement(Name.dependencies.toString());
            for (String dep : dependencies) {
                writer.writeEmptyElement(Name.module.toString());
                writer.writeAttribute(Name.name.toString(), dep);
            }
            writer.writeEndElement();
        }

        writer.writeEndElement();
        writer.writeEndDocument();
    }
}
