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

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.Map;
import java.util.Properties;

import org.apache.commons.cli.CommandLine;

/**
 * Installer values to be used for installation.
 *
 * @author lzoubek@redhat.com
 */
public class InstallerConfiguration {
    static final String OPTION_INSTALLER_CONFIG = "installer-config";
    static final String OPTION_WILDFLY_HOME = "wildfly-home";
    static final String OPTION_MODULE = "module";
    static final String OPTION_SERVER_CONFIG = "server-config";
    static final String OPTION_HAWKULAR_SERVER_URL = "hawkular-server-url";
    static final String OPTION_KEYSTORE_PATH = "keystore-path";
    static final String OPTION_KEYSTORE_PASSWORD = "keystore-password";
    static final String OPTION_KEY_PASSWORD = "key-password";
    static final String OPTION_KEY_ALIAS = "key-alias";
    static final String OPTION_HAWKULAR_USERNAME = "hawkular-username";
    static final String OPTION_HAWKULAR_PASSWORD = "hawkular-password";
    static final String OPTION_HAWKULAR_TOKEN = "hawkular-token";

    private final Properties properties;

    public InstallerConfiguration(CommandLine commandLine) throws IOException {
        this.properties = new Properties();

        // we allow the user to set system properties through the -D argument
        Properties sysprops = commandLine.getOptionProperties("D");
        for (Map.Entry<Object, Object> sysprop : sysprops.entrySet()) {
            System.setProperty(sysprop.getKey().toString(), sysprop.getValue().toString());
        }

        // seed our properties with the installer config properties file
        String installerConfig = commandLine.getOptionValue(OPTION_INSTALLER_CONFIG,
                "classpath:/hawkular-wildfly-monitor-installer.properties");

        if (installerConfig.startsWith("classpath:")) {
            installerConfig = installerConfig.substring(10);
            if (!installerConfig.startsWith("/")) {
                installerConfig = "/" + installerConfig;
            }
            properties.load(InstallerConfiguration.class.getResourceAsStream(installerConfig));
        } else {
            File installerConfigFile = new File(installerConfig);
            try (FileInputStream fis = new FileInputStream(installerConfigFile)) {
                properties.load(fis);
            }
        }

        // now we override the defaults with options the user provided us
        setProperty(properties, commandLine, OPTION_WILDFLY_HOME);
        setProperty(properties, commandLine, OPTION_MODULE);
        setProperty(properties, commandLine, OPTION_SERVER_CONFIG);
        setProperty(properties, commandLine, OPTION_HAWKULAR_SERVER_URL);
        setProperty(properties, commandLine, OPTION_KEYSTORE_PATH);
        setProperty(properties, commandLine, OPTION_KEYSTORE_PASSWORD);
        setProperty(properties, commandLine, OPTION_KEY_PASSWORD);
        setProperty(properties, commandLine, OPTION_KEY_ALIAS);
        setProperty(properties, commandLine, OPTION_HAWKULAR_USERNAME);
        setProperty(properties, commandLine, OPTION_HAWKULAR_PASSWORD);
        setProperty(properties, commandLine, OPTION_HAWKULAR_TOKEN);
    }

    private void setProperty(Properties props, CommandLine commandLine, String option) {
        String value = commandLine.getOptionValue(option);
        if (value != null) {
            properties.setProperty(option, value);
        }
    }

    public String getInstallerConfig() {
        return properties.getProperty(OPTION_INSTALLER_CONFIG);
    }

    public String getWildFlyHome() {
        return properties.getProperty(OPTION_WILDFLY_HOME);
    }

    public String getModule() {
        return properties.getProperty(OPTION_MODULE);
    }

    public String getServerConfig() {
        return properties.getProperty(OPTION_SERVER_CONFIG);
    }

    public String getHawkularServerUrl() {
        return properties.getProperty(OPTION_HAWKULAR_SERVER_URL);
    }

    public String getKeystorePath() {
        return properties.getProperty(OPTION_KEYSTORE_PATH);
    }

    public String getKeystorePassword() {
        return properties.getProperty(OPTION_KEYSTORE_PASSWORD);
    }

    public String getKeyPassword() {
        return properties.getProperty(OPTION_KEY_PASSWORD);
    }

    public String getKeyAlias() {
        return properties.getProperty(OPTION_KEY_ALIAS);
    }

    public String getHawkularUsername() {
        return properties.getProperty(OPTION_HAWKULAR_USERNAME);
    }

    public String getHawkularPassword() {
        return properties.getProperty(OPTION_HAWKULAR_PASSWORD);
    }

    public String getHawkularToken() {
        return properties.getProperty(OPTION_HAWKULAR_TOKEN);
    }
}