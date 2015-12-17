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
package org.hawkular.wildfly.agent.installer;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.net.URL;

import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.DefaultParser;
import org.apache.commons.cli.Options;
import org.apache.commons.io.IOUtils;
import org.junit.Assert;
import org.junit.Test;

public class InstallerConfigurationTest {
    @Test
    public void testLoadPropertiesFileFromClasspath() throws Exception {
        Options options = InstallerConfiguration.buildCommandLineOptions();
        CommandLine commandLine = new DefaultParser().parse(options,
                args("--installer-config", "classpath:test-installer.properties"));
        InstallerConfiguration installerConfig = new InstallerConfiguration(commandLine);
        assertTestProperties(installerConfig);
    }

    @Test
    public void testLoadPropertiesFileFromUrl() throws Exception {
        URL url = InstallerConfigurationTest.class.getResource("/test-installer.properties");
        Options options = InstallerConfiguration.buildCommandLineOptions();
        CommandLine commandLine = new DefaultParser().parse(options,
                args("--installer-config", url.toString()));
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
            Options options = InstallerConfiguration.buildCommandLineOptions();
            CommandLine commandLine = new DefaultParser().parse(options,
                    args("--installer-config", tempFile.getAbsolutePath()));
            InstallerConfiguration installerConfig = new InstallerConfiguration(commandLine);
            assertTestProperties(installerConfig);
        } finally {
            tempFile.delete();
        }
    }

    @Test
    public void testOverridePropertiesFromArgs() throws Exception {
        Options options = InstallerConfiguration.buildCommandLineOptions();
        CommandLine commandLine = new DefaultParser().parse(options,
                args("--installer-config", "classpath:test-installer.properties",
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
                        "--security-key", "OVERRIDE-key",
                        "--security-secret", "OVERRIDE-secret",
                        "--module-dist", "/OVERRIDE/dist.zip"
                ));
        InstallerConfiguration installerConfig = new InstallerConfiguration(commandLine);
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
        Assert.assertEquals("OVERRIDE-key", installerConfig.getSecurityKey());
        Assert.assertEquals("OVERRIDE-secret", installerConfig.getSecuritySecret());
        Assert.assertEquals("/OVERRIDE/dist.zip", installerConfig.getModuleDistribution());
    }

    @Test
    public void testEncryptionKeyArg() throws Exception {
        Options options = InstallerConfiguration.buildCommandLineOptions();

        // specify just the option without a value
        CommandLine commandLine = new DefaultParser().parse(options,
                args("--encryption-key", "--target-location", "/opt"));
        InstallerConfiguration installerConfig = new InstallerConfiguration(commandLine);
        Assert.assertEquals("/opt", installerConfig.getTargetLocation());
        Assert.assertTrue(commandLine.hasOption(InstallerConfiguration.OPTION_ENCRYPTION_KEY));
        Assert.assertNull(commandLine.getOptionValue(InstallerConfiguration.OPTION_ENCRYPTION_KEY));
        Assert.assertNull(commandLine.getOptionValue(InstallerConfiguration.OPTION_ENCRYPTION_SALT));

        // specify the option with the value
        commandLine = new DefaultParser().parse(options,
                args("--encryption-key", "abc", "--target-location", "/opt"));
        installerConfig = new InstallerConfiguration(commandLine);
        Assert.assertEquals("/opt", installerConfig.getTargetLocation());
        Assert.assertTrue(commandLine.hasOption(InstallerConfiguration.OPTION_ENCRYPTION_KEY));
        Assert.assertEquals("abc", commandLine.getOptionValue(InstallerConfiguration.OPTION_ENCRYPTION_KEY));
        Assert.assertNull(commandLine.getOptionValue(InstallerConfiguration.OPTION_ENCRYPTION_SALT));

        // specify also the salt
        commandLine = new DefaultParser().parse(options,
                args("--encryption-key", "abc", "--encryption-salt", "abcd1234", "--target-location", "/opt"));
        installerConfig = new InstallerConfiguration(commandLine);
        Assert.assertEquals("/opt", installerConfig.getTargetLocation());
        Assert.assertTrue(commandLine.hasOption(InstallerConfiguration.OPTION_ENCRYPTION_KEY));
        Assert.assertEquals("abc", commandLine.getOptionValue(InstallerConfiguration.OPTION_ENCRYPTION_KEY));
        Assert.assertEquals("abcd1234", commandLine.getOptionValue(InstallerConfiguration.OPTION_ENCRYPTION_SALT));
    }

    @Test
    public void testBadProps() throws Exception {
        Options options = InstallerConfiguration.buildCommandLineOptions();

        try {
            new DefaultParser().parse(options, args("--bad", "bad"));
            Assert.fail("Should have failed on bad argument");
        } catch (Exception ok) {
        }

        try {
            new DefaultParser().parse(options, args("--target-location", ".", "--bad"));
            Assert.fail("Should have failed on bad argument");
        } catch (Exception ok) {
        }
    }

    private String[] args(String... a) {
        return a;
    }

    private void assertTestProperties(InstallerConfiguration installerConfig) {
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
        Assert.assertEquals("test-key", installerConfig.getSecurityKey());
        Assert.assertEquals("test-secret", installerConfig.getSecuritySecret());
        Assert.assertEquals("/test/dist.zip", installerConfig.getModuleDistribution());
    }
}
