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
import java.io.FileOutputStream;
import java.io.InputStream;
import java.net.URL;

import org.apache.commons.io.IOUtils;
import org.jboss.aesh.cl.CommandLine;
import org.jboss.aesh.cl.internal.ProcessedCommand;
import org.jboss.aesh.cl.parser.CommandLineParser;
import org.jboss.aesh.cl.parser.CommandLineParserBuilder;
import org.junit.Assert;
import org.junit.Test;

public class InstallerConfigurationTest {
    @Test
    public void testSysPropArguments() throws Exception {
        ProcessedCommand<?> options = InstallerConfiguration.buildCommandLineOptions();
        CommandLineParser<?> parser = new CommandLineParserBuilder().processedCommand(options).create();
        CommandLine<?> commandLine = parser
                .parse(args(
                        "--enabled", "true",
                        "-DInstallerConfigurationTest.one=111",
                        "--feed-id", "myfeed",
                        "-DInstallerConfigurationTest.two=222"));
        new InstallerConfiguration(commandLine);
        Assert.assertEquals("111", System.getProperty("InstallerConfigurationTest.one"));
        Assert.assertEquals("222", System.getProperty("InstallerConfigurationTest.two"));
    }

    @Test
    public void testLoadPropertiesFileFromClasspath() throws Exception {
        ProcessedCommand<?> options = InstallerConfiguration.buildCommandLineOptions();
        CommandLineParser<?> parser = new CommandLineParserBuilder().processedCommand(options).create();
        CommandLine<?> commandLine = parser.parse(args("--installer-config", "classpath:test-installer.properties"));
        InstallerConfiguration installerConfig = new InstallerConfiguration(commandLine);
        assertTestProperties(installerConfig);
    }

    @Test
    public void testLoadPropertiesFileFromUrl() throws Exception {
        URL url = InstallerConfigurationTest.class.getResource("/test-installer.properties");
        ProcessedCommand<?> options = InstallerConfiguration.buildCommandLineOptions();
        CommandLineParser<?> parser = new CommandLineParserBuilder().processedCommand(options).create();
        CommandLine<?> commandLine = parser.parse(args("--installer-config", url.toString()));
        InstallerConfiguration installerConfig = new InstallerConfiguration(commandLine);
        assertTestProperties(installerConfig);
    }

    @Test
    public void testLoadPropertiesFileFromFile() throws Exception {
        URL url = InstallerConfigurationTest.class.getResource("/test-installer.properties");
        File tempFile = File.createTempFile("InstallerConfigurationTest", ".properties");

        try (FileOutputStream fos = new FileOutputStream(tempFile);
                InputStream ios = url.openStream()) {
            IOUtils.copyLarge(ios, fos); // make a copy of the test-installer.properties in our filesystem
        }

        try {
            ProcessedCommand<?> options = InstallerConfiguration.buildCommandLineOptions();
            CommandLineParser<?> parser = new CommandLineParserBuilder().processedCommand(options).create();
            CommandLine<?> commandLine = parser.parse(args("--installer-config", tempFile.getAbsolutePath()));
            InstallerConfiguration installerConfig = new InstallerConfiguration(commandLine);
            assertTestProperties(installerConfig);
        } finally {
            tempFile.delete();
        }
    }

    @Test
    public void testOverridePropertiesFromArgs() throws Exception {
        ProcessedCommand<?> options = InstallerConfiguration.buildCommandLineOptions();
        CommandLineParser<?> parser = new CommandLineParserBuilder().processedCommand(options).create();
        CommandLine<?> commandLine = parser.parse(
                args("--installer-config", "classpath:test-installer.properties",
                        "--enabled", "false",
                        "--feed-id", "OVERRIDE-feed-id",
                        "--tenant-id", "OVERRIDE-tenant-id",
                        "--metrics-only", "true",
                        "--target-location", "/opt/wildfly/OVERRIDE",
                        "--target-config", "standalone/configuration/OVERRIDE.xml",
                        "--subsystem-snippet", "subdir/subsystem-snippetOVERRIDE.xml",
                        "--server-url", "http://OVERRIDE:8080",
                        "--managed-server-name", "MyLocalNameOVERRIDE",
                        "--keystore-path", "/tmp/OVERRIDE/path",
                        "--keystore-password", "OVERRIDE-keystore-password",
                        "--key-password", "OVERRIDE-key-password",
                        "--key-alias", "OVERRIDE-alias",
                        "--username", "OVERRIDE-username",
                        "--password", "OVERRIDE-password",
                        "--module-dist", "/OVERRIDE/dist.zip",
                        "--managed-server-resource-type-sets", "\"OVERRIDE First Type,Second Type\""
                ));
        InstallerConfiguration installerConfig = new InstallerConfiguration(commandLine);
        Assert.assertFalse(installerConfig.isEnabled());
        Assert.assertEquals("/opt/wildfly/OVERRIDE", installerConfig.getTargetLocation());
        Assert.assertEquals("standalone/configuration/OVERRIDE.xml", installerConfig.getTargetConfig());
        Assert.assertEquals("subdir/subsystem-snippetOVERRIDE.xml", installerConfig.getSubsystemSnippet());
        Assert.assertEquals("http://OVERRIDE:8080", installerConfig.getServerUrl());
        Assert.assertEquals("MyLocalNameOVERRIDE", installerConfig.getManagedServerName());
        Assert.assertEquals("/tmp/OVERRIDE/path", installerConfig.getKeystorePath());
        Assert.assertEquals("OVERRIDE-keystore-password", installerConfig.getKeystorePassword());
        Assert.assertEquals("OVERRIDE-key-password", installerConfig.getKeyPassword());
        Assert.assertEquals("OVERRIDE-alias", installerConfig.getKeyAlias());
        Assert.assertEquals("OVERRIDE-username", installerConfig.getUsername());
        Assert.assertEquals("OVERRIDE-password", installerConfig.getPassword());
        Assert.assertEquals("/OVERRIDE/dist.zip", installerConfig.getModuleDistribution());
        Assert.assertEquals("OVERRIDE-feed-id", installerConfig.getFeedId());
        Assert.assertEquals("OVERRIDE-tenant-id", installerConfig.getTenantId());
        Assert.assertEquals("OVERRIDE First Type,Second Type", installerConfig.getManagedResourceTypeSets());
        Assert.assertTrue(installerConfig.isMetricsOnlyMode());
    }

    @Test
    public void testEncryptionKeyArg() throws Exception {
        ProcessedCommand<?> options = InstallerConfiguration.buildCommandLineOptions();
        CommandLineParser<?> parser = new CommandLineParserBuilder().processedCommand(options).create();
        CommandLine<?> commandLine;
        InstallerConfiguration installerConfig;

        // specify the option with the value
        commandLine = parser.parse(args("--encryption-key", "abc", "--target-location", "/opt"));
        installerConfig = new InstallerConfiguration(commandLine);
        Assert.assertTrue(commandLine.hasOption(InstallerConfiguration.OPTION_ENCRYPTION_KEY));
        Assert.assertEquals("abc", commandLine.getOptionValue(InstallerConfiguration.OPTION_ENCRYPTION_KEY));
        Assert.assertNull(commandLine.getOptionValue(InstallerConfiguration.OPTION_ENCRYPTION_SALT));
        Assert.assertEquals("/opt", installerConfig.getTargetLocation());

        // specify also the salt
        commandLine = parser
                .parse(args("--encryption-key", "abc", "--encryption-salt", "abcd1234", "--target-location", "/opt"));
        installerConfig = new InstallerConfiguration(commandLine);
        Assert.assertTrue(commandLine.hasOption(InstallerConfiguration.OPTION_ENCRYPTION_KEY));
        Assert.assertEquals("abc", commandLine.getOptionValue(InstallerConfiguration.OPTION_ENCRYPTION_KEY));
        Assert.assertEquals("abcd1234", commandLine.getOptionValue(InstallerConfiguration.OPTION_ENCRYPTION_SALT));
        Assert.assertEquals("/opt", installerConfig.getTargetLocation());

        // specify just the option without a value
        commandLine = parser.parse(args("--encryption-key", "--target-location", "/opt"));
        installerConfig = new InstallerConfiguration(commandLine);
        Assert.assertTrue(commandLine.hasOption(InstallerConfiguration.OPTION_ENCRYPTION_KEY));
        Assert.assertEquals("", commandLine.getOptionValue(InstallerConfiguration.OPTION_ENCRYPTION_KEY));
        Assert.assertNull(commandLine.getOptionValue(InstallerConfiguration.OPTION_ENCRYPTION_SALT));
        Assert.assertEquals("/opt", installerConfig.getTargetLocation());

        // specify just the option without a value, but put that option at the end (test fix to AESH-348)
        commandLine = parser.parse(args("--target-location", "/opt", "--encryption-key"));
        installerConfig = new InstallerConfiguration(commandLine);
        Assert.assertTrue(commandLine.hasOption(InstallerConfiguration.OPTION_ENCRYPTION_KEY));
        Assert.assertEquals("", commandLine.getOptionValue(InstallerConfiguration.OPTION_ENCRYPTION_KEY));
        Assert.assertNull(commandLine.getOptionValue(InstallerConfiguration.OPTION_ENCRYPTION_SALT));
        Assert.assertEquals("/opt", installerConfig.getTargetLocation());
    }

    @Test
    public void testBadProps() throws Exception {
        ProcessedCommand<?> options = InstallerConfiguration.buildCommandLineOptions();
        CommandLineParser<?> parser = new CommandLineParserBuilder().processedCommand(options).create();

        try {
            CommandLine<?> results = parser.parse(args("--bad", "bad"));
            new InstallerConfiguration(results);
            Assert.fail("Should have failed on bad argument: " + results);
        } catch (Exception ok) {
            //ok.printStackTrace();
            //System.out.println(parser.printHelp());
        }

        try {
            CommandLine<?> results = parser.parse(args("--target-location", ".", "--bad"));
            new InstallerConfiguration(results);
            Assert.fail("Should have failed on bad argument: " + results);
        } catch (Exception ok) {
            //ok.printStackTrace();
            //System.out.println(parser.printHelp());
        }
    }

    private String args(String... a) {
        StringBuilder line = new StringBuilder(InstallerConfiguration.COMMAND_NAME);
        for (String str : a) {
            line.append(' ').append(str);
        }
        return line.toString();
    }

    private void assertTestProperties(InstallerConfiguration installerConfig) {
        Assert.assertTrue(installerConfig.isEnabled());
        Assert.assertEquals("/opt/wildfly/test", installerConfig.getTargetLocation());
        Assert.assertEquals("standalone/configuration/test.xml", installerConfig.getTargetConfig());
        Assert.assertEquals("subdir/subsystem-snippet.xml", installerConfig.getSubsystemSnippet());
        Assert.assertEquals("http://test:8080", installerConfig.getServerUrl());
        Assert.assertEquals("MyLocalName", installerConfig.getManagedServerName());
        Assert.assertEquals("/tmp/test/path", installerConfig.getKeystorePath());
        Assert.assertEquals("test-keystore-password", installerConfig.getKeystorePassword());
        Assert.assertEquals("test-key-password", installerConfig.getKeyPassword());
        Assert.assertEquals("test-alias", installerConfig.getKeyAlias());
        Assert.assertEquals("test-username", installerConfig.getUsername());
        Assert.assertEquals("test-password", installerConfig.getPassword());
        Assert.assertEquals("/test/dist.zip", installerConfig.getModuleDistribution());
        Assert.assertEquals("test-feed-id", installerConfig.getFeedId());
        Assert.assertEquals("test-tenant-id", installerConfig.getTenantId());
        Assert.assertEquals("First Type,Second Type", installerConfig.getManagedResourceTypeSets());
        Assert.assertFalse("Default metrics-only should have been false", installerConfig.isMetricsOnlyMode());
    }
}
