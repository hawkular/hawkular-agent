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

import java.net.URL;

/**
 * An insert item represents 1 edit action to be performed on target XML document.
 * Each 'insert' has {@link #select} attribute. Which denotes location
 * of content you wish to insert/replace. Then it must set either {@link #content} - path
 * to location of another XML file that will appear as a child
 * of elements evaluated by {@link #select} expression or {@link #xml} - which denotes
 * XML content as String. Optionally, {@link attribute} can be
 * defined to identify inserted/replaced content. If {@link attribute} is not defined,
 * content (defined either via {@link #xml} or {@link content}) is
 * loaded and xpath expression is created from root element's attributes and their values,
 * otherwise {@link attribute} is taken as the only one for
 * xpath expression.
 *
 * @author lzoubek@redhat.com
 *
 */
public class XmlEdit {

    private String select;
    private URL content;
    private String xml;
    private String attribute;

    public XmlEdit() {

    }

    public XmlEdit(String select, String xml) {
        this.select = select;
        this.xml = xml;
    }

    public XmlEdit(String select, URL content) {
        this.select = select;
        this.content = content;
    }

    public XmlEdit withAttribute(String attribute) {
        this.attribute = attribute;
        return this;
    }

    public String getAttribute() {
        return attribute;
    }

    public void setAttribute(String attribute) {
        this.attribute = attribute;
    }

    public void setXml(String xml) {
        this.xml = xml;
    }

    public String getXml() {
        return xml;
    }

    public String getSelect() {
        return select;
    }

    public void setSelect(String select) {
        this.select = select;
    }

    public URL getContent() {
        return content;
    }

    public void setContent(URL content) {
        this.content = content;
    }

    @Override
    public String toString() {
        return new StringBuilder(getClass().getName()+"[").append("select=" + this.select)
                .append(content == null ? "" : " content=" + content)
                .append(attribute == null ? "" : " attribute=" + attribute)
                .append(xml == null ? "" : " xml=" + this.xml).append("]").toString();
    }

}
