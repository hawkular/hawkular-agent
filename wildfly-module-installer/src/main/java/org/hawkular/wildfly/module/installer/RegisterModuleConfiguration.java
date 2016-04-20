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
import java.net.URL;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * this class holds all inputs for changes to be made in standalone.xml or domain.xml or host.xml
 * @author lzoubek
 */
class RegisterModuleConfiguration {

    private File targetServerConfig;
    private File sourceServerConfig;
    private URL subsystem;
    private URL socketBinding;
    private Set<String> socketBindingGroups;
    private List<XmlEdit> xmlEdits;
    private String moduleId;
    private ConfigType configType = null; // null means we'll look at targetServerConfig filename to figure it out
    private Set<String> profiles;
    private boolean failNoMatch;

    public RegisterModuleConfiguration() {

    }

    @Override
    public String toString() {
        return new StringBuilder("RegisterOptions: [")
                .append("\n  serverConfig = " + targetServerConfig)
                .append("\n  configType = " + configType)
                .append("\n  serverConfigBackup = " + sourceServerConfig)
                .append("\n  moduleId = " + moduleId)
                .append("\n  subsystem = " + subsystem)
                .append("\n  socket-binding = " + socketBinding)
                .append("\n  socketBindingGroups = " + (socketBindingGroups == null? "null" :
                    Arrays.toString(socketBindingGroups.toArray())))
                .append("\n  edit = " + (xmlEdits == null ? "null" : Arrays.toString(xmlEdits.toArray())))
                .append("\n  failNoMatch = " + failNoMatch)
                .append("\n]")
                .toString();
    }

    /**
     * merge data from another instance to this instance, but do not overwrite non-null values with null values
     * @param configuration another instance
     * @return
     */
    public RegisterModuleConfiguration extend(RegisterModuleConfiguration configuration) {
        this.targetServerConfig = configuration.targetServerConfig == null ? this.targetServerConfig
                : configuration.targetServerConfig;
        this.sourceServerConfig = configuration.sourceServerConfig == null ? this.sourceServerConfig
                : configuration.sourceServerConfig;
        this.subsystem = configuration.subsystem == null ? this.subsystem : configuration.subsystem;
        this.socketBinding = configuration.socketBinding == null ? this.socketBinding : configuration.socketBinding;
        this.socketBindingGroups = configuration.socketBindingGroups == null ? this.socketBindingGroups
                : configuration.socketBindingGroups;
        this.xmlEdits = configuration.xmlEdits == null ? this.xmlEdits : configuration.xmlEdits;
        this.moduleId = configuration.moduleId == null ? this.moduleId : configuration.moduleId;
        this.failNoMatch = configuration.failNoMatch;
        this.configType = configuration.configType == null ? this.configType : configuration.configType;
        this.profiles = configuration.profiles == null ? this.profiles : configuration.profiles;
        return this;
    }

    public RegisterModuleConfiguration failNoMatch(boolean failNoMatch) {
        this.failNoMatch = failNoMatch;
        return this;
    }

    public RegisterModuleConfiguration withExtension(String moduleId) {
        this.moduleId = moduleId;
        return this;
    }

    public RegisterModuleConfiguration sourceServerConfig(File sourceServerConfig) {
        this.sourceServerConfig = sourceServerConfig;
        return this;
    }

    public RegisterModuleConfiguration targetServerConfig(File targetServerConfig) {
        this.targetServerConfig = targetServerConfig;

        // if we haven't been told what type of config it is, determine it by looking at the file name
        if (this.configType == null) {
            if (targetServerConfig.getName().matches(".*standalone[^/]*.xml")) {
                configType(ConfigType.STANDALONE);
            } else if (targetServerConfig.getName().matches(".*host[^/]*.xml")) {
                configType(ConfigType.HOST);
            } else if (targetServerConfig.getName().matches(".*domain[^/]*.xml")) {
                configType(ConfigType.DOMAIN);
            } else {
                configType(ConfigType.STANDALONE); // filename is weird, just assume standalone
            }
        }

        return this;
    }

    public RegisterModuleConfiguration configType(ConfigType configType) {
        this.configType = configType;
        return this;
    }

    public RegisterModuleConfiguration subsystem(URL subsystem) {
        this.subsystem = subsystem;
        return this;
    }

    public RegisterModuleConfiguration socketBinding(URL socketBinding) {
        this.socketBinding = socketBinding;
        return this;
    }

    public RegisterModuleConfiguration socketBindingGroups(Set<String> socketBindingGroups) {
        this.socketBindingGroups = socketBindingGroups;
        return this;
    }

    public RegisterModuleConfiguration xmlEdits(List<XmlEdit> inserts) {
        this.xmlEdits = inserts;
        return this;
    }

    public RegisterModuleConfiguration profiles(Set<String> profiles) {
        this.profiles = profiles;
        return this;
    }

    public List<XmlEdit> getXmlEdits() {
        if (xmlEdits == null) {
            xmlEdits = new ArrayList<>();
        }
        return xmlEdits;
    }

    public File getTargetServerConfig() {
        return targetServerConfig;
    }

    public URL getSocketBinding() {
        return socketBinding;
    }

    public URL getSubsystem() {
        return subsystem;
    }

    public Set<String> getSocketBindingGroups() {
        return socketBindingGroups;
    }

    public String getModuleId() {
        return moduleId;
    }

    public File getSourceServerConfig() {
        return sourceServerConfig;
    }

    public boolean isFailNoMatch() {
        return failNoMatch;
    }

    public ConfigType getConfigType() {
        return configType;
    }

    public Set<String> getProfiles() {
        if (profiles == null) {
            profiles = new HashSet<>();
        }
        return profiles;
    }

}
