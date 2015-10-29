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
                        "--wildfly-home", "/opt/wildfly/OVERRIDE",
                        "--server-config", "standalone/configuration/OVERRIDE.xml",
                        "--hawkular-server-url", "http://OVERRIDE:8080",
                        "--keystore-path", "/tmp/OVERRIDE/path",
                        "--keystore-password", "OVERRIDE-keystore-password",
                        "--key-password", "OVERRIDE-key-password",
                        "--key-alias", "OVERRIDE-alias",
                        "--hawkular-username", "OVERRIDE-username",
                        "--hawkular-password", "OVERRIDE-password",
                        "--hawkular-token", "OVERRIDE-token",
                        "--module-dist", "/OVERRIDE/dist.zip"
                ));
        InstallerConfiguration installerConfig = new InstallerConfiguration(commandLine);
        Assert.assertEquals("/opt/wildfly/OVERRIDE", installerConfig.getWildFlyHome());
        Assert.assertEquals("standalone/configuration/OVERRIDE.xml", installerConfig.getServerConfig());
        Assert.assertEquals("http://OVERRIDE:8080", installerConfig.getHawkularServerUrl());
        Assert.assertEquals("/tmp/OVERRIDE/path", installerConfig.getKeystorePath());
        Assert.assertEquals("OVERRIDE-keystore-password", installerConfig.getKeystorePassword());
        Assert.assertEquals("OVERRIDE-key-password", installerConfig.getKeyPassword());
        Assert.assertEquals("OVERRIDE-alias", installerConfig.getKeyAlias());
        Assert.assertEquals("OVERRIDE-username", installerConfig.getHawkularUsername());
        Assert.assertEquals("OVERRIDE-password", installerConfig.getHawkularPassword());
        Assert.assertEquals("OVERRIDE-token", installerConfig.getHawkularToken());
        Assert.assertEquals("/OVERRIDE/dist.zip", installerConfig.getModuleDistribution());
    }

    private String[] args(String... a) {
        return a;
    }

    private void assertTestProperties(InstallerConfiguration installerConfig) {
        Assert.assertEquals("/opt/wildfly/test", installerConfig.getWildFlyHome());
        Assert.assertEquals("standalone/configuration/test.xml", installerConfig.getServerConfig());
        Assert.assertEquals("http://test:8080", installerConfig.getHawkularServerUrl());
        Assert.assertEquals("/tmp/test/path", installerConfig.getKeystorePath());
        Assert.assertEquals("test-keystore-password", installerConfig.getKeystorePassword());
        Assert.assertEquals("test-key-password", installerConfig.getKeyPassword());
        Assert.assertEquals("test-alias", installerConfig.getKeyAlias());
        Assert.assertEquals("test-username", installerConfig.getHawkularUsername());
        Assert.assertEquals("test-password", installerConfig.getHawkularPassword());
        Assert.assertEquals("test-token", installerConfig.getHawkularToken());
        Assert.assertEquals("/test/dist.zip", installerConfig.getModuleDistribution());
    }
}