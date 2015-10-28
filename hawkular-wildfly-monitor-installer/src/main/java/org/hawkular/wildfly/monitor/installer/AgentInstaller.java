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

import java.io.Console;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.MalformedURLException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;

import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.DefaultParser;
import org.apache.commons.cli.HelpFormatter;
import org.apache.commons.cli.Option;
import org.apache.commons.cli.Options;
import org.apache.commons.cli.ParseException;
import org.apache.commons.io.IOUtils;
import org.hawkular.wildfly.module.installer.DeploymentConfiguration;
import org.hawkular.wildfly.module.installer.DeploymentConfiguration.Builder;
import org.hawkular.wildfly.module.installer.ExtensionDeployer;
import org.hawkular.wildfly.module.installer.XmlEdit;
import org.jboss.logging.Logger;

public class AgentInstaller {

    private static final Logger log = Logger.getLogger(AgentInstaller.class);
    private static final String SECURITY_REALM_NAME = "HawkularRealm";

    public static void main(String[] args) throws Exception {
        Options options = null;

        ArrayList<File> filesToDelete = new ArrayList<>();

        try {
            options = buildCommandLineOptions();
            CommandLine commandLine = new DefaultParser().parse(options, args);
            InstallerConfiguration installerConfig = new InstallerConfiguration(commandLine);

            URL hawkularServerUrl = new URL(installerConfig.getHawkularServerUrl());
            String jbossHome = installerConfig.getWildFlyHome();
            String moduleZip = installerConfig.getModule();

            URL moduleZipUrl;

            if (moduleZip == null) {
                // --module is not supplied so try to download agent module from server
                File moduleTempFile = downloadModuleZip(getHawkularServerAgentDownloadUrl(installerConfig));
                if (moduleTempFile == null) {
                    throw new IOException("Failed to retrieve agent module from server, option ["
                            + InstallerConfiguration.OPTION_MODULE
                            + "] is now required but it was not supplied");
                }
                filesToDelete.add(moduleTempFile);
                moduleZipUrl = moduleTempFile.toURI().toURL();
            } else if (moduleZip.startsWith("classpath:")) {
                // This special protocol tells us to read module zip as resource from classpath.
                // This is in case the module zip is bundled directly in the installer.
                String resourceUrl = moduleZip.substring(10);
                if (!resourceUrl.startsWith("/")) {
                    resourceUrl = "/" + resourceUrl;
                }
                moduleZipUrl = AgentInstaller.class.getResource(resourceUrl);
                if (moduleZipUrl == null) {
                    throw new IOException("Unable to load module.zip from classpath [" + resourceUrl + "]");
                }
            } else if (moduleZip.matches("(http|https|file):.*")){
                // the module is specified as a URL - we'll download it
                File moduleTempFile = downloadModuleZip(new URL(moduleZip));
                if (moduleTempFile == null) {
                    throw new IOException("Failed to retrieve agent module from server, option ["
                            + InstallerConfiguration.OPTION_MODULE
                            + "] is now required but it was not supplied");
                }
                filesToDelete.add(moduleTempFile);
                moduleZipUrl = moduleTempFile.toURI().toURL();
            } else {
                // the module is specified as a file path
                moduleZipUrl = new File(moduleZip).toURI().toURL();
            }

            // deploy given module into given app server home directory and
            // set it up the way it talks to hawkular server on hawkularServerUrl
            // TODO support domain mode
            File socketBindingSnippetFile = createSocketBindingSnippet(hawkularServerUrl);
            filesToDelete.add(socketBindingSnippetFile);
            Builder configuration = DeploymentConfiguration.builder()
                    .jbossHome(new File(jbossHome))
                    .module(moduleZipUrl)
                    .socketBinding(socketBindingSnippetFile.toURI().toURL());

            String serverConfig = installerConfig.getServerConfig();
            if (serverConfig != null) {
                configuration.serverConfig(serverConfig);
            } else {
                serverConfig = DeploymentConfiguration.DEFAULT_SERVER_CONFIG;
                // we'll use this in case of https to resolve server configuration directory
            }

            // If we are to talk to the Hawkular Server over HTTPS, we need to set up some additional things
            if (hawkularServerUrl.getProtocol().equals("https")) {
                String keystorePath = installerConfig.getKeystorePath();
                String keystorePass = installerConfig.getKeystorePassword();
                String keyPass = installerConfig.getKeyPassword();
                String keyAlias = installerConfig.getKeyAlias();
                if (keystorePath == null || keyAlias == null) {
                    throw new ParseException(String.format("When using https protocol, the following keystore "
                            + "command-line options are required: %s, %s",
                            InstallerConfiguration.OPTION_KEYSTORE_PATH, InstallerConfiguration.OPTION_KEY_ALIAS));
                }

                // password fields are not required, but if not supplied we'll ask user
                if (keystorePass == null) {
                    keystorePass = readPasswordFromStdin("Keystore password:");
                    if (keystorePass == null || keystorePass.isEmpty()) {
                        keystorePass = "";
                        log.warn(InstallerConfiguration.OPTION_KEYSTORE_PASSWORD
                                + " was not provided; using empty password");
                    }
                }
                if (keyPass == null) {
                    keyPass = readPasswordFromStdin("Key password:");
                    if (keyPass == null || keyPass.isEmpty()) {
                        keyPass = "";
                        log.warn(InstallerConfiguration.OPTION_KEY_PASSWORD
                                + " was not provided; using empty password");
                    }
                }

                // if given keystore path is not already present within server-config directory, copy it
                File keystoreSrcFile = new File(keystorePath);
                if (!(keystoreSrcFile.isFile() && keystoreSrcFile.canRead())) {
                    throw new FileNotFoundException("Cannot read " + keystoreSrcFile.getAbsolutePath());
                }
                File serverConfigDir;
                if (new File(serverConfig).isAbsolute()) {
                    serverConfigDir = new File(serverConfig).getParentFile();
                } else {
                    serverConfigDir = new File(jbossHome, serverConfig).getParentFile();
                }
                Path keystoreDst = Paths.get(serverConfigDir.getAbsolutePath()).resolve(keystoreSrcFile.getName());
                // never overwrite target keystore
                if (!keystoreDst.toFile().exists()) {
                    log.info("Copy [" + keystoreSrcFile.getAbsolutePath() + "] to [" + keystoreDst.toString() + "]");
                    Files.copy(Paths.get(keystoreSrcFile.getAbsolutePath()), keystoreDst);
                }

                // setup security-realm and storage-adapter (within hawkular-wildfly-monitor subsystem)
                String securityRealm = createSecurityRealm(keystoreSrcFile.getName(), keystorePass, keyPass, keyAlias);
                configuration.addXmlEdit(new XmlEdit("/server/management/security-realms", securityRealm));
                configuration.addXmlEdit(createStorageAdapter(true, installerConfig));
            }
            else {
                // just going over non-secure HTTP
                configuration.addXmlEdit(createStorageAdapter(false, installerConfig));
            }

            configuration.addXmlEdit(createManagedServers());

            new ExtensionDeployer().install(configuration.build());

        } catch (ParseException pe) {
            log.warn(pe);
            printHelp(options);
        } catch (Exception ex) {
            log.warn(ex);
        } finally {
            for (File fileToDelete : filesToDelete) {
                if (!fileToDelete.delete()) {
                    log.warn("Failed to delete temporary file: " + fileToDelete);
                }
            }
        }
    }

    /**
     * Reads password from the console (stdin).
     *
     * @param message to present before reading
     * @return password or null if console is not available
     */
    private static String readPasswordFromStdin(String message) {
        Console console = System.console();
        if (console == null) {
            return null;
        }
        console.writer().write(message);
        console.writer().flush();
        return String.valueOf(console.readPassword());
    }

    /**
     * Creates xml snippet which sets up security-realm.
     *
     * @param keystoreFile location of the keystore file
     * @param keystorePass the password to access the keystore file
     * @param keyPass the password to access the data for the given alias
     * @param keyAlias the alias specifying the identifying security information
     * @return XML snippet
     */
    private static String createSecurityRealm(String keystoreFile, String keystorePass,
            String keyPass, String keyAlias) {
        return new StringBuilder("<security-realm name=\"" + SECURITY_REALM_NAME + "\">")
                .append("<server-identities><ssl>")
                .append("<keystore path=\"" + keystoreFile + "\"")
                .append(" relative-to=\"jboss.server.config.dir\"")
                .append(" keystore-password=\"" + keystorePass + "\"")
                .append(" key-password=\"" + keyPass + "\"")
                .append(" alias=\"" + keyAlias + "\"")
                .append(" /></ssl></server-identities></security-realm>").toString();
    }

    /**
     * Creates XML edit which sets up storage-adapter configuration, creates a reference
     * to the security-realm and enables SSL, if appropriate.
     *
     * @param withHttps if the storage adapter will be accessed via HTTPS
     * @return object that can be used to edit some xml content
     */
    private static XmlEdit createStorageAdapter(boolean withHttps, InstallerConfiguration installerConfig) {
        String select = "/server/profile/*[namespace-uri()='urn:org.hawkular.agent.monitor:monitor:1.0']/";
        StringBuilder xml = new StringBuilder("<storage-adapter")
                .append(" type=\"HAWKULAR\"");

        if (withHttps) {
            xml.append(" securityRealm=\"" + SECURITY_REALM_NAME + "\"")
                    .append(" useSSL=\"true\"");
        }

        xml.append(" username=\"" + installerConfig.getHawkularUsername() + "\"")
                .append(" password=\"" + installerConfig.getHawkularPassword() + "\"")
                .append(" serverOutboundSocketBindingRef=\"hawkular\"")
                .append("/>");

        // replaces <storage-adapter> under urn:org.hawkular.agent.monitor:monitor:1.0 subsystem with above content
        // but only if it has type="HAWKULAR"
        return new XmlEdit(select, xml.toString()).withAttribute("type");
    }

    /**
     * Creates a (outbound) socket-binding snippet XML file
     *
     * @param serverUrl the hawkular server URL
     * @return file to the temporary socket binding snippet file (this should be cleaned up by the caller)
     * @throws IOException on error
     */
    private static File createSocketBindingSnippet(URL serverUrl) throws IOException {
        String host = serverUrl.getHost();
        String port = String.valueOf(serverUrl.getPort());
        StringBuilder xml = new StringBuilder("<outbound-socket-binding name=\"hawkular\">\n")
            .append("  <remote-destination host=\""+host+"\" port=\""+port+"\" />\n")
            .append("</outbound-socket-binding>");
        Path tempFile = Files.createTempFile("hawkular-wildfly-module-installer-outbound-socket-binding", ".xml");
        Files.write(tempFile, xml.toString().getBytes());
        return tempFile.toFile();
    }


    private static XmlEdit createManagedServers() {
        String select = "/server/profile/"
                + "*[namespace-uri()='urn:org.hawkular.agent.monitor:monitor:1.0']/";
        StringBuilder xml = new StringBuilder("<managed-servers>")
                .append("<local-dmr name=\"Local\" enabled=\"true\" "
                        + "resourceTypeSets=\"Main,Deployment,Web Component,EJB,Datasource,"
                        + "XA Datasource,JDBC Driver,Transaction Manager,Hawkular\" />")
                .append("</managed-servers>");

        // replaces <managed-servers> under urn:org.hawkular.agent.monitor:monitor:1.0 subsystem with above content
        return new XmlEdit(select, xml.toString());
    }

    private static URL getHawkularServerAgentDownloadUrl(InstallerConfiguration config) throws MalformedURLException {
        // TODO fix agent module location
        String serverUrl = String.format("%s/hawkular-wildfly-monitor/module.zip", config.getHawkularServerUrl());
        return new URL(serverUrl);
    }

    /**
     * Downloads the Hawkular WildFly Monitor agent module ZIP file from a URL
     *
     * @param url where the agent module zip is
     * @return absolute path to module downloaded locally or null if it could not be retrieved;
     *         this is a temporary file that should be cleaned once it is used
     */
    private static File downloadModuleZip(URL url) {
        File tempFile;

        try {
            tempFile = File.createTempFile("hawkular-wildfly-monitor-module", ".zip");
        } catch (Exception e) {
            throw new RuntimeException("Cannot create temp file to hold module zip", e);
        }

        try (FileOutputStream fos = new FileOutputStream(tempFile);
                InputStream ios = url.openStream()) {
            IOUtils.copyLarge(ios, fos);
            return tempFile;
        } catch (Exception e) {
            log.warn("Unable to download hawkular wildfly monitor module ZIP: " + url, e);
            tempFile.delete();
        }
        return null;
    }

    private static void printHelp(Options options) {
        if (options == null) {
            throw new RuntimeException("Cannot print help - options is null");
        }

        HelpFormatter formatter = new HelpFormatter();
        formatter.setWidth(120);
        formatter.setOptionComparator(null);
        formatter.printHelp("hawkular-wildfly-monitor-installer", options);
    }

    private static Options buildCommandLineOptions() {
        Options options = new Options();

        options.addOption(Option.builder("D")
                .hasArgs()
                .valueSeparator('=')
                .build());
        options.addOption(Option.builder()
                .argName(InstallerConfiguration.OPTION_INSTALLER_CONFIG)
                .longOpt(InstallerConfiguration.OPTION_INSTALLER_CONFIG)
                .desc("Installer .properties configuration file")
                .numberOfArgs(1)
                .build());
        options.addOption(Option.builder()
                .required()
                .argName(InstallerConfiguration.OPTION_WILDFLY_HOME)
                .longOpt(InstallerConfiguration.OPTION_WILDFLY_HOME)
                .desc("Target WildFly home directory")
                .numberOfArgs(1)
                .build());
        options.addOption(Option.builder()
                .argName(InstallerConfiguration.OPTION_MODULE)
                .longOpt(InstallerConfiguration.OPTION_MODULE)
                .desc("Extension Module zip file")
                .numberOfArgs(1)
                .build());
        options.addOption(Option.builder()
                .argName(InstallerConfiguration.OPTION_HAWKULAR_SERVER_URL)
                .longOpt(InstallerConfiguration.OPTION_HAWKULAR_SERVER_URL)
                .desc("Hawkular Server URL")
                .numberOfArgs(1)
                .build());
        options.addOption(Option.builder()
                .argName(InstallerConfiguration.OPTION_SERVER_CONFIG)
                .longOpt(InstallerConfiguration.OPTION_SERVER_CONFIG)
                .desc("Server config to write to. Can be either absolute path or relative to "
                        + InstallerConfiguration.OPTION_WILDFLY_HOME)
                .numberOfArgs(1)
                .build());

        // SSL related config options
        options.addOption(Option.builder()
                .argName(InstallerConfiguration.OPTION_KEYSTORE_PATH)
                .longOpt(InstallerConfiguration.OPTION_KEYSTORE_PATH)
                .desc("Keystore file. Required when " + InstallerConfiguration.OPTION_HAWKULAR_SERVER_URL
                        + " protocol is https")
                .numberOfArgs(1)
                .build());
        options.addOption(Option.builder()
                .argName(InstallerConfiguration.OPTION_KEYSTORE_PASSWORD)
                .longOpt(InstallerConfiguration.OPTION_KEYSTORE_PASSWORD)
                .desc("Keystore password. When " + InstallerConfiguration.OPTION_HAWKULAR_SERVER_URL
                        + " protocol is https and this option is not passed, installer will ask for password")
                .numberOfArgs(1)
                .build());
        options.addOption(Option.builder()
                .argName(InstallerConfiguration.OPTION_KEY_PASSWORD)
                .longOpt(InstallerConfiguration.OPTION_KEY_PASSWORD)
                .desc("Key password. When " + InstallerConfiguration.OPTION_HAWKULAR_SERVER_URL
                        + " protocol is https and this option is not passed, installer will ask for password")
                .numberOfArgs(1)
                .build());
        options.addOption(Option.builder()
                .argName(InstallerConfiguration.OPTION_KEY_ALIAS)
                .longOpt(InstallerConfiguration.OPTION_KEY_ALIAS)
                .desc("Key alias. Required when " + InstallerConfiguration.OPTION_HAWKULAR_SERVER_URL
                        + " protocol is https")
                .numberOfArgs(1)
                .build());

        return options;
    }

}
