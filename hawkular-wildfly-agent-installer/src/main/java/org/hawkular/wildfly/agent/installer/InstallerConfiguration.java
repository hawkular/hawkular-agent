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

import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.net.URL;
import java.util.Map;
import java.util.Properties;

import org.jboss.aesh.cl.CommandLine;
import org.jboss.aesh.cl.internal.OptionType;
import org.jboss.aesh.cl.internal.ProcessedCommand;
import org.jboss.aesh.cl.internal.ProcessedCommandBuilder;
import org.jboss.aesh.cl.internal.ProcessedOptionBuilder;
import org.jboss.logging.Logger;

/**
 * Installer values to be used for installation.
 */
public class InstallerConfiguration {
    private static final Logger log = Logger.getLogger(AgentInstaller.class);

    // command name
    static final String COMMAND_NAME = "hawkular-wildfly-agent-installer";

    // these are standalone command line options that are *not* found in the config .properties file
    static final String OPTION_INSTALLER_CONFIG = "installer-config";
    static final String OPTION_ENCRYPTION_KEY = "encryption-key";
    static final String OPTION_ENCRYPTION_SALT = "encryption-salt";

    // these are command line options that can also be defined in the config .properties file
    static final String OPTION_ENABLED = "enabled";
    static final String OPTION_TARGET_LOCATION = "target-location";
    static final String OPTION_MODULE_DISTRIBUTION = "module-dist";
    static final String OPTION_TARGET_CONFIG = "target-config";
    static final String OPTION_SUBSYSTEM_SNIPPET = "subsystem-snippet";
    static final String OPTION_SERVER_URL = "server-url";
    static final String OPTION_KEYSTORE_PATH = "keystore-path";
    static final String OPTION_KEYSTORE_PASSWORD = "keystore-password";
    static final String OPTION_KEY_PASSWORD = "key-password";
    static final String OPTION_KEY_ALIAS = "key-alias";
    static final String OPTION_USERNAME = "username";
    static final String OPTION_PASSWORD = "password";
    static final String OPTION_MANAGED_SERVER_NAME = "managed-server-name";
    static final String OPTION_FEED_ID = "feed-id";
    static final String OPTION_TENANT_ID = "tenant-id";
    static final String OPTION_METRICS_ONLY_MODE = "metrics-only";
    static final String OPTION_MANAGED_RESOURCE_TYPE_SETS = "managed-server-resource-type-sets";

    static ProcessedCommand<?> buildCommandLineOptions() throws Exception {
        ProcessedCommandBuilder cmd = new ProcessedCommandBuilder();

        cmd.name(COMMAND_NAME);

        cmd.addOption(new ProcessedOptionBuilder()
                .name("D")
                .shortName('D')
                .optionType(OptionType.GROUP)
                .type(String.class)
                .valueSeparator('=')
                .description("Defines system properties to set")
                .create());
        cmd.addOption(new ProcessedOptionBuilder()
                .name(InstallerConfiguration.OPTION_INSTALLER_CONFIG)
                .optionType(OptionType.NORMAL)
                .type(String.class)
                .description("Installer .properties configuration file")
                .create());
        cmd.addOption(new ProcessedOptionBuilder()
                .name(InstallerConfiguration.OPTION_ENCRYPTION_KEY)
                .optionType(OptionType.NORMAL)
                .type(String.class)
                .addDefaultValue("")
                .description("If specified, this is used to decode the properties that were encrypted. "
                        + "If you do not provide a value with the option, you will be prompted for one.")
                .create());
        cmd.addOption(new ProcessedOptionBuilder()
                .name(InstallerConfiguration.OPTION_ENCRYPTION_SALT)
                .optionType(OptionType.NORMAL)
                .type(String.class)
                .addDefaultValue("")
                .description("The salt used for generating the key. Recommended, if encryption is used. "
                        + "If not specified, the same value as the key will be used.")
                .create());

        // the following are those options that can also be in the config file

        cmd.addOption(new ProcessedOptionBuilder()
                .name(InstallerConfiguration.OPTION_ENABLED)
                .optionType(OptionType.NORMAL)
                .type(String.class)
                .description("Indicates if the agent should be enabled at startup")
                .create());
        cmd.addOption(new ProcessedOptionBuilder()
                .name(InstallerConfiguration.OPTION_TARGET_LOCATION)
                .optionType(OptionType.NORMAL)
                .type(String.class)
                .description("Target home directory of the application server where the agent is to be installed")
                .create());
        cmd.addOption(new ProcessedOptionBuilder()
                .name(InstallerConfiguration.OPTION_MODULE_DISTRIBUTION)
                .optionType(OptionType.NORMAL)
                .type(String.class)
                .description("Hawkular WildFly Agent Module distribution zip file - can be a file path or URL")
                .create());
        cmd.addOption(new ProcessedOptionBuilder()
                .name(InstallerConfiguration.OPTION_SERVER_URL)
                .optionType(OptionType.NORMAL)
                .type(String.class)
                .description("Server URL where the agent will send its monitoring data")
                .create());
        cmd.addOption(new ProcessedOptionBuilder()
                .name(InstallerConfiguration.OPTION_TARGET_CONFIG)
                .optionType(OptionType.NORMAL)
                .type(String.class)
                .description("The target configuration file to write to. Can be either absolute path or relative to "
                        + InstallerConfiguration.OPTION_TARGET_LOCATION)
                .create());
        cmd.addOption(new ProcessedOptionBuilder()
                .name(InstallerConfiguration.OPTION_SUBSYSTEM_SNIPPET)
                .optionType(OptionType.NORMAL)
                .type(String.class)
                .description("Customized subsystem XML content that overrides the default subsystem configuration")
                .create());
        cmd.addOption(new ProcessedOptionBuilder()
                .name(InstallerConfiguration.OPTION_MANAGED_SERVER_NAME)
                .optionType(OptionType.NORMAL)
                .type(String.class)
                .description("The agent will use this name to refer to the server where it is deployed "
                        + "and locally managing.")
                .create());
        cmd.addOption(new ProcessedOptionBuilder()
                .name(InstallerConfiguration.OPTION_FEED_ID)
                .optionType(OptionType.NORMAL)
                .type(String.class)
                .description("The feed ID that the agent will use to identify its data.")
                .create());
        cmd.addOption(new ProcessedOptionBuilder()
                .name(InstallerConfiguration.OPTION_TENANT_ID)
                .optionType(OptionType.NORMAL)
                .type(String.class)
                .description("The tenant ID that the agent will ask to be used. "
                        + "Usually only used when in metrics-only mode.")
                .create());
        cmd.addOption(new ProcessedOptionBuilder()
                .name(InstallerConfiguration.OPTION_METRICS_ONLY_MODE)
                .optionType(OptionType.NORMAL)
                .type(String.class)
                .description("If true, the agent will be configured to run in metrics-only mode "
                        + "(inventory will not be stored and no websocket connection to a Hawkular "
                        + "Server will be made.) If true, you must specify a tenant-id.")
                .create());
        cmd.addOption(new ProcessedOptionBuilder()
                .name(InstallerConfiguration.OPTION_MANAGED_RESOURCE_TYPE_SETS)
                .optionType(OptionType.NORMAL)
                .type(String.class)
                .description("If true, the agent will be configured to monitor these resource type sets. "
                        + "If not provided, a default set will be used based on the type of application "
                        + "server where the agent is being installed into (standalone or domain).")
                .create());

        // SSL/security related config options

        cmd.addOption(new ProcessedOptionBuilder()
                .name(InstallerConfiguration.OPTION_KEYSTORE_PATH)
                .optionType(OptionType.NORMAL)
                .type(String.class)
                .description("Keystore file. Required when " + InstallerConfiguration.OPTION_SERVER_URL
                        + " protocol is https")
                .create());
        cmd.addOption(new ProcessedOptionBuilder()
                .name(InstallerConfiguration.OPTION_KEYSTORE_PASSWORD)
                .optionType(OptionType.NORMAL)
                .type(String.class)
                .description("Keystore password. When " + InstallerConfiguration.OPTION_SERVER_URL
                        + " protocol is https and this option is not passed, installer will ask for password")
                .create());
        cmd.addOption(new ProcessedOptionBuilder()
                .name(InstallerConfiguration.OPTION_KEY_PASSWORD)
                .optionType(OptionType.NORMAL)
                .type(String.class)
                .description("Key password. When " + InstallerConfiguration.OPTION_SERVER_URL
                        + " protocol is https and this option is not passed, installer will ask for password")
                .create());
        cmd.addOption(new ProcessedOptionBuilder()
                .name(InstallerConfiguration.OPTION_KEY_ALIAS)
                .optionType(OptionType.NORMAL)
                .type(String.class)
                .description("Key alias. Required when " + InstallerConfiguration.OPTION_SERVER_URL
                        + " protocol is https")
                .create());
        cmd.addOption(new ProcessedOptionBuilder()
                .name(InstallerConfiguration.OPTION_USERNAME)
                .optionType(OptionType.NORMAL)
                .type(String.class)
                .description("User the agent will use when connecting to Hawkular Server.")
                .create());
        cmd.addOption(new ProcessedOptionBuilder()
                .name(InstallerConfiguration.OPTION_PASSWORD)
                .optionType(OptionType.NORMAL)
                .type(String.class)
                .description("Credentials agent will use when connecting to Hawkular Server.")
                .create());

        return cmd.create();
    }

    private final Properties properties;

    public InstallerConfiguration(CommandLine<?> commandLine) throws Exception {
        if (commandLine.getParserException() != null) {
            throw commandLine.getParserException();
        }

        this.properties = new Properties();

        // we allow the user to set system properties through the -D argument
        Map<String, String> sysprops = commandLine.getOptionProperties("D");
        for (Map.Entry<String, String> sysprop : sysprops.entrySet()) {
            System.setProperty(sysprop.getKey(), sysprop.getValue());
        }

        // seed our properties with the installer config properties file
        String installerConfig = commandLine.getOptionValue(OPTION_INSTALLER_CONFIG,
                "classpath:/hawkular-wildfly-agent-installer.properties");

        log.debug("Installer configuration file: " + installerConfig);

        if (installerConfig.startsWith("classpath:")) {
            installerConfig = installerConfig.substring(10);
            if (!installerConfig.startsWith("/")) {
                installerConfig = "/" + installerConfig;
            }
            properties.load(InstallerConfiguration.class.getResourceAsStream(installerConfig));
        } else if (installerConfig.matches("(http|https|file):.*")) {
            URL installerConfigUrl = new URL(installerConfig);
            try (InputStream is = installerConfigUrl.openStream()) {
                properties.load(is);
            }
        } else {
            File installerConfigFile = new File(installerConfig);
            try (FileInputStream fis = new FileInputStream(installerConfigFile)) {
                properties.load(fis);
            }
        }

        // now we override the defaults with options the user provided us
        setProperty(properties, commandLine, OPTION_ENABLED);
        setProperty(properties, commandLine, OPTION_TARGET_LOCATION);
        setProperty(properties, commandLine, OPTION_MODULE_DISTRIBUTION);
        setProperty(properties, commandLine, OPTION_TARGET_CONFIG);
        setProperty(properties, commandLine, OPTION_SUBSYSTEM_SNIPPET);
        setProperty(properties, commandLine, OPTION_MANAGED_SERVER_NAME);
        setProperty(properties, commandLine, OPTION_FEED_ID);
        setProperty(properties, commandLine, OPTION_TENANT_ID);
        setProperty(properties, commandLine, OPTION_METRICS_ONLY_MODE);
        setProperty(properties, commandLine, OPTION_SERVER_URL);
        setProperty(properties, commandLine, OPTION_KEYSTORE_PATH);
        setProperty(properties, commandLine, OPTION_KEYSTORE_PASSWORD);
        setProperty(properties, commandLine, OPTION_KEY_PASSWORD);
        setProperty(properties, commandLine, OPTION_KEY_ALIAS);
        setProperty(properties, commandLine, OPTION_USERNAME);
        setProperty(properties, commandLine, OPTION_PASSWORD);
        setProperty(properties, commandLine, OPTION_MANAGED_RESOURCE_TYPE_SETS);
    }

    private void setProperty(Properties props, CommandLine<?> commandLine, String option) {
        String value = commandLine.getOptionValue(option);
        if (value != null) {
            properties.setProperty(option, value);
        }
    }

    public void decodeProperties(String encryptionKey, byte[] salt) throws Exception {
        decodeProperty(properties, OPTION_KEYSTORE_PASSWORD, encryptionKey, salt);
        decodeProperty(properties, OPTION_KEY_PASSWORD, encryptionKey, salt);
        decodeProperty(properties, OPTION_PASSWORD, encryptionKey, salt);
    }

    private void decodeProperty(Properties prop, String option, String key, byte[] salt) throws Exception {
        String value = properties.getProperty(option, null);
        if (value != null) {
            value = EncoderDecoder.decode(value, key, salt);
            properties.setProperty(option, value);
        }
    }

    public String getInstallerConfig() {
        return properties.getProperty(OPTION_INSTALLER_CONFIG);
    }

    public boolean isEnabled() {
        return Boolean.parseBoolean(properties.getProperty(OPTION_ENABLED, "true"));
    }

    public String getTargetLocation() {
        return properties.getProperty(OPTION_TARGET_LOCATION);
    }

    public String getModuleDistribution() {
        return properties.getProperty(OPTION_MODULE_DISTRIBUTION);
    }

    public String getTargetConfig() {
        return properties.getProperty(OPTION_TARGET_CONFIG);
    }

    public String getSubsystemSnippet() {
        return properties.getProperty(OPTION_SUBSYSTEM_SNIPPET);
    }

    public String getServerUrl() {
        return properties.getProperty(OPTION_SERVER_URL);
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

    public String getUsername() {
        return properties.getProperty(OPTION_USERNAME);
    }

    public String getPassword() {
        return properties.getProperty(OPTION_PASSWORD);
    }

    public String getManagedServerName() {
        return properties.getProperty(OPTION_MANAGED_SERVER_NAME);
    }

    public String getFeedId() {
        return properties.getProperty(OPTION_FEED_ID);
    }

    public String getTenantId() {
        return properties.getProperty(OPTION_TENANT_ID);
    }

    public boolean isMetricsOnlyMode() {
        return Boolean.parseBoolean(properties.getProperty(OPTION_METRICS_ONLY_MODE, "false"));
    }

    public String getManagedResourceTypeSets() {
        return properties.getProperty(OPTION_MANAGED_RESOURCE_TYPE_SETS);
    }
}
