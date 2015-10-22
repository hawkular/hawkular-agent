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
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

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
    private static final String OPTION_WILDFLY_HOME="wildfly-home";
    private static final String OPTION_MODULE = "module";
    private static final String OPTION_SERVER_CONFIG = "server-config";
    private static final String OPTION_HAWKULAR_SERVER_URL = "hawkular-server-url";
    private static final String OPTION_KEYSTORE_PATH = "keystore-path";
    private static final String OPTION_KEYSTORE_PASSWORD="keystore-password";
    private static final String OPTION_KEY_ALIAS="key-alias";
    private static final String OPTION_KEY_PASSWORD="key-password";

    private static Options OPTIONS;
    private static InstallerDefaults defaults;

    public static void main(String[] args) throws Exception {
        try {
            defaults = new InstallerDefaults();
            OPTIONS = commandLineOptions(defaults);
            CommandLine commandLine = new DefaultParser().parse(OPTIONS, args);

            String jbossHome = commandLine.getOptionValue(OPTION_WILDFLY_HOME);
            String moduleZip = commandLine.getOptionValue(OPTION_MODULE, defaults.getModule());
            String hawkularServerUrl = commandLine.getOptionValue(OPTION_HAWKULAR_SERVER_URL,
                    defaults.getHawkularServerUrl());

            URL moduleUrl = null;
            // if --module is not supplied try to download agent module from server
            if (moduleZip == null) {
                moduleUrl = downloadModule(hawkularServerUrl);
                if (moduleUrl == null) {
                    throw new IOException("Failed to retrieve agent module from server, option [module]"
                            + " is now required but it was not supplied");
                }
            } else if (moduleZip.startsWith("classpath:/")) {
                // this special protocol tells us to read module as resource from classpath
                String resourceUrl = moduleZip.substring(10);
                moduleUrl = AgentInstaller.class.getResource(resourceUrl);
                if (moduleUrl == null) {
                    throw new IOException("Unable to load module.zip from classpath as resource "+resourceUrl);
                }
            }
            else {
                moduleUrl = new File(moduleZip).toURI().toURL();
            }
            URL serverUrl = new URL(hawkularServerUrl);
            URL socketBinding = createSocketBindingSnippet(serverUrl);
            // deploy given module into given wfHome and
            // set it up the way it talks to hawkular server on hawkularServerUrl
            // TODO support domain mode
            Builder configuration = DeploymentConfiguration.builder()
                    .jbossHome(new File(jbossHome))
                    .module(moduleUrl)
                    .socketBinding(socketBinding);

            String serverConfig = commandLine.getOptionValue(OPTION_SERVER_CONFIG);
            if (serverConfig != null) {
                configuration.serverConfig(serverConfig);
            } else {
                serverConfig = DeploymentConfiguration.DEFAULT_SERVER_CONFIG;
                // we'll use this in case of https to resolve server configuration directory
            }

            if (serverUrl.getProtocol().equals("https")) {
                String keystorePath = commandLine.getOptionValue(OPTION_KEYSTORE_PATH);
                String keystorePass = commandLine.getOptionValue(OPTION_KEYSTORE_PASSWORD);
                String keyPass = commandLine.getOptionValue(OPTION_KEY_PASSWORD);
                String keyAlias = commandLine.getOptionValue(OPTION_KEY_ALIAS);
                if (keystorePath == null || keyAlias == null) {
                    StringBuilder sbOptions = new StringBuilder(OPTION_KEYSTORE_PATH)
                        .append(","+OPTION_KEY_ALIAS);

                    throw new ParseException("When using https protocol, following keystore"
                            + " command-line options are required: "+sbOptions.toString());
                }

                // password fields are not required, but if not supplied we'll ask user
                if (keystorePass == null) {
                    // try to read it from STDIN
                    keystorePass = readPaswordFromStdin("Keystore password:");
                    if (keystorePass == null || keystorePass.isEmpty()) {
                        keystorePass = ""; // set to empty string
                        log.warn("Option --"+OPTION_KEYSTORE_PASSWORD + " was not passed using empty password");
                    }
                }
                if (keyPass == null) {
                    // try to read it from STDIN
                    keyPass = readPaswordFromStdin("Key password:");
                    if (keyPass == null || keyPass.isEmpty()) {
                        keyPass = ""; // set to empty string
                        log.warn("Option --"+OPTION_KEY_PASSWORD + " was not passed using empty password");
                    }
                }

                // in case given keystore path is not already present within server-config
                // directory, copy it
                File keystoreSrcFile = new File(keystorePath);
                if (!(keystoreSrcFile.isFile() && keystoreSrcFile.canRead())) {
                    throw new FileNotFoundException("Cannot read "+keystoreSrcFile.getAbsolutePath());
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
                    log.info("Copy ["+keystoreSrcFile.getAbsolutePath()+"] to ["+keystoreDst.toString()+"]");
                    Files.copy(Paths.get(keystoreSrcFile.getAbsolutePath()), keystoreDst);
                }
                // setup security-realm and storage-adapter (within hawkular-wildfly-monitor subsystem)
                String securityRealm = createSecurityRealm(keystoreSrcFile.getName(), keystorePass, keyPass, keyAlias);
                configuration.addXmlEdit(new XmlEdit("/server/management/security-realms", securityRealm));
                configuration.addXmlEdit(createStorageAdapter(true));
            }
            else {
                configuration.addXmlEdit(createStorageAdapter(false));
            }
            configuration.addXmlEdit(createManagedServers());
            new ExtensionDeployer().install(configuration.build());
        } catch (ParseException pe) {
            log.warn(pe);
            help();
        } catch (Exception ex) {
            log.warn(ex);
        }
    }

    /**
     * reads password from user
     * @param message to present before reading
     * @return password or null if console is not available
     */
    private static String readPaswordFromStdin(String message) {
        Console console = System.console();
        if (console == null) {
            return null;
        }
        console.writer().write(message);
        console.writer().flush();
        return String.valueOf(console.readPassword());
    }

    /**
     * creates xml snippet which sets up security-realm
     * @param keystoreFile
     * @param keystorePass
     * @param keyPass
     * @param keyAlias
     * @return xml snippet
     */
    private static String createSecurityRealm(String keystoreFile, String keystorePass,
            String keyPass, String keyAlias) {
        return new StringBuilder("<security-realm name=\""+SECURITY_REALM_NAME+"\">")
            .append("<server-identities><ssl>")
            .append("<keystore path=\""+keystoreFile+"\"")
            .append(" relative-to=\"jboss.server.config.dir\"")
            .append(" keystore-password=\""+keystorePass+"\"")
            .append(" key-password=\""+keyPass+"\"")
            .append(" alias=\""+keyAlias+"\"")
            .append(" /></ssl></server-identities></security-realm>").toString();
    }

    /**
     * creates XML edit which sets up hawkular.agent.monitor storage-adapter, creates reference
     * to security-realm and enables SSL.
     * @return
     * @param withHttps
     */
    private static XmlEdit createStorageAdapter(boolean withHttps) {
        String select = "/server/profile/"
                + "*[namespace-uri()='urn:org.hawkular.agent.monitor:monitor:1.0']/";
        StringBuilder xml = new StringBuilder("<storage-adapter")
                .append(" type=\"HAWKULAR\"");
        if (withHttps) {
            xml.append(" securityRealm=\"" + SECURITY_REALM_NAME + "\"")
                    .append(" useSSL=\"true\"");
        }
        xml
            .append(" username=\"" + defaults.getHawkularUsername() + "\"")
            .append(" password=\"" + defaults.getHawkularPassword() + "\"")
            .append(" serverOutboundSocketBindingRef=\"hawkular\"")
            .append("/>");
        // this will replace <storage-adapter type="HAWKULAR" under urn:org.hawkular.agent.monitor:monitor:1.0 subsystem
        // with above content
        return new XmlEdit(select, xml.toString()).withAttribute("type");
    }

    /**
     * creates a (outbound) socket-binding snippet XML file
     * @param serverUrl
     * @return URL (path) to socket binding snippet file
     * @throws IOException
     */
    private static URL createSocketBindingSnippet(URL serverUrl) throws IOException {
        String host = serverUrl.getHost();
        String port = String.valueOf(serverUrl.getPort());
        StringBuilder xml = new StringBuilder("<outbound-socket-binding name=\"hawkular\">\n")
            .append("  <remote-destination host=\""+host+"\" port=\""+port+"\" />\n")
            .append("</outbound-socket-binding>");
        Path tempFile = Files.createTempFile("hk-wf-module-installer", ".xml");
        // TODO cleanup temp file
        Files.write(tempFile, xml.toString().getBytes());
        return tempFile.toUri().toURL();
    }


    private static XmlEdit createManagedServers() {
        String select = "/server/profile/"
                + "*[namespace-uri()='urn:org.hawkular.agent.monitor:monitor:1.0']/";
        StringBuilder xml = new StringBuilder("<managed-servers>")
                .append("<local-dmr name=\"Local\" enabled=\"true\" "
                        + "resourceTypeSets=\"Main,Deployment,Web Component,EJB,Datasource,"
                        + "XA Datasource,JDBC Driver,Transaction Manager,Hawkular\" />")
                .append("</managed-servers>");
        // this will replace <managed-servers> under urn:org.hawkular.agent.monitor:monitor:1.0 subsystem
        // with above content
        return new XmlEdit(select, xml.toString());
    }

    /**
     * downloads hawkular agent module ZIP from hawkular-server
     * @param hawkularServerUrl
     * @return absolute path to module downloaded locally or null if it could not be retrieved
     */
    private static URL downloadModule(String hawkularServerUrl) {
        FileOutputStream fos = null;
        try {
            // TODO fix agent module location
            // TODO do we need to authorize to get module.zip?
            URL fileUrl = new URL(hawkularServerUrl+"/hawkular/hawkular-agent-module.zip");
            File tempFile = File.createTempFile("agent-installer", ".zip");
            fos = new FileOutputStream(tempFile);
            IOUtils.copyLarge(fileUrl.openStream(), fos);
            return tempFile.toURI().toURL();
        } catch (Exception e) {
            log.warn("Unable to download hawkular-agent module ZIP from "+hawkularServerUrl);
        }finally {
            if (fos != null) {
                try {
                    fos.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }
        return null;
    }

    /**
     * print command-line help
     */
    private static void help() {
        HelpFormatter formatter = new HelpFormatter();
        formatter.setWidth(120);
        formatter.setOptionComparator(null);
        formatter.printHelp("hawkular-wildfly-monitor-installer", OPTIONS);
    }

    /**
     * build options we understand from command-line
     * @param defaults default values from bundled property file. Based on them, several command-line options
     * might no longer be required
     * @return
     */
    private static Options commandLineOptions(InstallerDefaults defaults) {
        Options options = new Options();
        options.addOption(Option.builder()
                .required()
                .argName("wildflyHome")
                .desc("Target wildfly home")
                .hasArg()
                .numberOfArgs(1)
                .longOpt(OPTION_WILDFLY_HOME)
                .build()
                );
        options.addOption(Option.builder()
                .argName("moduleZip")
                .longOpt(OPTION_MODULE)
                .desc("Extension Module")
                .numberOfArgs(1)
                .build()
                );
        Option serverUrl = Option.builder()
                .argName("serverUrl")
                .desc("Hawkular Server URL")
                .numberOfArgs(1)
                .longOpt(OPTION_HAWKULAR_SERVER_URL)
                .build();
        serverUrl.setRequired(defaults.getHawkularServerUrl() == null);
        options.addOption(serverUrl);

        options.addOption(Option.builder()
                .argName("serverConfig")
                .desc("Server config to write to. Can be either absolute path or relative to <wildflyHome>")
                .numberOfArgs(1)
                .longOpt(OPTION_SERVER_CONFIG)
                .build());
        // SSL related config options
        options.addOption(Option.builder()
                .argName("keyStore")
                .desc("Keystore file. Required when <serverUrl> protocol is https")
                .numberOfArgs(1)
                .longOpt(OPTION_KEYSTORE_PATH)
                .build());
        options.addOption(Option.builder()
                .argName("keystorePassword")
                .desc("Keystore password. When <serverUrl> protocol is https and this option is not passed,"
                        + " installer will ask for password")
                .numberOfArgs(1)
                .longOpt(OPTION_KEYSTORE_PASSWORD)
                .build());
        options.addOption(Option.builder()
                .argName("keyPassword")
                .desc("Key password. When <serverUrl> protocol is https and this option is not passed,"
                        + " installer will ask for password")
                .numberOfArgs(1)
                .longOpt(OPTION_KEY_PASSWORD)
                .build());
        options.addOption(Option.builder()
                .argName("alias")
                .desc("Key alias. Required when <serverUrl> protocol is https")
                .numberOfArgs(1)
                .longOpt(OPTION_KEY_ALIAS)
                .build());
        return options;
    }

}
