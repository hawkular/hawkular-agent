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
package org.hawkular.wildfly.agent.installer;

/**
 * This provides information when installing into the domain.xml file which is
 * the file that defines the central management policy of the domain.
 */
public class DomainTargetConfigInfo implements TargetConfigInfo {

    @Override
    public String getRootXPath() {
        return "/domain";
    }

    @Override
    public String getSecurityRealmsXPath() {
        return getRootXPath() + "/management/security-realms";
    }

    @Override
    public String getProfileXPath() {
        // notice we only support the <profile name="default">
        return getRootXPath() + "/profiles/profile[@name='default']";
    }

    @Override
    public String getManagedServerResourceTypeSets() {
        return new StringBuilder()
                .append("Domain Environment,")
                .append("Deployment,")
                .append("Web Component,")
                .append("EJB,")
                .append("Datasource,")
                .append("XA Datasource,")
                .append("JDBC Driver,")
                .append("Transaction Manager,")
                .append("Messaging,")
                .append("Hawkular")
                .toString();
    }
}
