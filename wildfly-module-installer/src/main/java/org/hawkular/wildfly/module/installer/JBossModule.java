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
package org.hawkular.wildfly.module.installer;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.xpath.XPath;
import javax.xml.xpath.XPathConstants;
import javax.xml.xpath.XPathFactory;

import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;
import org.jboss.logging.Logger;
import org.w3c.dom.Document;
import org.w3c.dom.NodeList;

class JBossModule {

    private final Logger log = Logger.getLogger(this.getClass());
    private URL root;
    private String moduleId;
    private List<String> resources = new ArrayList<String>();

    private JBossModule() {
    }

    public String getModuleId() {
        return moduleId;
    }

    public static JBossModule readFromURL(URL moduleZip) throws Exception {
        JBossModule m = new JBossModule();
        m.root = moduleZip;

        ZipInputStream zin = null;
        BufferedInputStream bin = null;
        boolean moduleXmlFound = false;
        try {
            bin = new BufferedInputStream(moduleZip.openStream());
            zin = new ZipInputStream(bin);
            ZipEntry ze = null;
            while ((ze = zin.getNextEntry()) != null) {
                if (ze.getName().endsWith("main/module.xml")) {
                    moduleXmlFound = true;
                    DocumentBuilderFactory dbFactory = DocumentBuilderFactory.newInstance();
                    DocumentBuilder dBuilder = dbFactory.newDocumentBuilder();
                    Document doc = dBuilder.parse(zin);
                    m.readModuleXmlInfo(doc);
                    break;
                }
            }
        } finally {
            try {
                // it should be enough to close the latest created stream in
                // case of chained (nested) streams, @see
                // http://www.javapractices.com/topic/TopicAction.do?Id=8
                if (zin != null) {
                    zin.close();
                }
            } catch (IOException ex) {
            }
        }
        if (!moduleXmlFound) {
            throw new FileNotFoundException("module.xml was not found in " + moduleZip);
        }
        return m;
    }

    private void readModuleXmlInfo(Document doc) throws Exception {
        this.moduleId = doc.getDocumentElement().getAttribute("name");
        XPath xPath = XPathFactory.newInstance().newXPath();
        NodeList nodeList = (NodeList) xPath.compile("//resources/resource-root")
                .evaluate(doc, XPathConstants.NODESET);
        for (int i = 0; i < nodeList.getLength(); i++) {
            this.resources.add(nodeList.item(i).getAttributes().getNamedItem("path").getTextContent());
        }
    }

    /**
     * uninstall (delete) JBossModule from given directory
     * @param modulesHome target directory
     */
    public void uninstallFrom(File modulesHome) throws Exception {
        log.info("Uninstalling module [" + this.root+ "] from [" + modulesHome.getAbsolutePath() + "]");
        ZipInputStream zin = null;
        try {
            zin = new ZipInputStream(this.root.openStream());
            ZipEntry ze = null;
            while ((ze = zin.getNextEntry()) != null) {
                String fileName = ze.getName();
                File newFile = new File(modulesHome + File.separator + fileName);
                if (!ze.isDirectory()) {
                    if (log.isDebugEnabled()) {
                        log.debug("Deleting "+newFile.getAbsolutePath());
                    }
                    FileUtils.deleteQuietly(newFile);
                }
            }
        } finally {
            try {
                // it should be enough to close the latest created stream in
                // case of chained (nested) streams, @see
                // http://www.javapractices.com/topic/TopicAction.do?Id=8
                if (zin != null) {
                    zin.close();
                }
            } catch (IOException ex) {
            }
        }
    }

    /**
     * installs JBossModule to given directory
     * @param modulesHome
     *            target directory to install module
     * @return list of installed files
     * @throws Exception
     */
    public List<File> installTo(File modulesHome) throws Exception {
        List<File> installedFiles = new ArrayList<File>();

        ZipInputStream zin = null;
        FileOutputStream fos;
        log.info("Extracting module [" + this.root+ "] to [" + modulesHome.getAbsolutePath() + "]");
        try {
            zin = new ZipInputStream(this.root.openStream());
            ZipEntry ze = null;
            while ((ze = zin.getNextEntry()) != null) {
                String fileName = ze.getName();
                File newFile = new File(modulesHome + File.separator + fileName);
                if (!ze.isDirectory()) {
                    log.debug("Writing " + newFile.getAbsolutePath());
                    File parent = newFile.getParentFile();
                    if (!parent.exists()) {
                        parent.mkdirs();
                    }
                    fos = new FileOutputStream(newFile);

                    IOUtils.copy(zin, fos);
                    IOUtils.closeQuietly(fos);
                    installedFiles.add(newFile);
                }
            }
        } finally {
            try {
                // it should be enough to close the latest created stream in
                // case of chained (nested) streams, @see
                // http://www.javapractices.com/topic/TopicAction.do?Id=8
                if (zin != null) {
                    zin.close();
                }
            } catch (IOException ex) {
            }
        }

        return installedFiles;
    }

}
