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
package org.hawkular.wildfly.monitor.installer;

import java.io.IOException;
import java.util.Properties;
/**
 * Installer defaults keep default values to be used for installation. Those values are loaded from
 * 'hawkular-wildfly-monitor-installer.properties' property file. Hawkular server can serve
 * hawkular-wildfly-monitor-installer and serve special version of installer
 * (with bundled module.zip) to each client. When serving it will probably generate
 * hawkular-wildfly-monitor-installer.jar on the fly and thus can generate content
 * of 'hawkular-wildfly-monitor-installer.properties'. In the end user may not need to
 * supply any command-line options because server will define all required defaults
 *  (like hawkular.server.url) upfront.
 * @author lzoubek@redhat.com
 */
public class InstallerDefaults {

    private final Properties properties;

    public InstallerDefaults() throws IOException {
        properties = new Properties();
        properties.load(InstallerDefaults.class.getResourceAsStream("/hawkular-wildfly-monitor-installer.properties"));
    }

    public String getHawkularServerUrl() {
        return properties.getProperty("hawkular.server.url");
    }

    public String getModule() {
        return properties.getProperty("module");
    }

    public String getHawkularUsername() {
        return properties.getProperty("hawkular.server.username", "jdoe");
    }

    public String getHawkularPassword() {
        return properties.getProperty("hawkular.server.password", "password");
    }
}
