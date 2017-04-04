/*
 * Copyright 2015-2017 Red Hat, Inc. and/or its affiliates
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

import java.io.Console;
import java.io.File;
import java.io.FileInputStream;
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
import java.util.jar.Manifest;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.commons.io.IOUtils;
import org.hawkular.wildfly.module.installer.DeploymentConfiguration;
import org.hawkular.wildfly.module.installer.DeploymentConfiguration.Builder;
import org.hawkular.wildfly.module.installer.ExtensionDeployer;
import org.hawkular.wildfly.module.installer.XmlEdit;
import org.jboss.aesh.cl.CommandLine;
import org.jboss.aesh.cl.internal.ProcessedCommand;
import org.jboss.aesh.cl.parser.CommandLineParser;
import org.jboss.aesh.cl.parser.CommandLineParserBuilder;
import org.jboss.aesh.cl.parser.CommandLineParserException;
import org.jboss.logging.Logger;

public class AgentInstaller {

    private static final Logger log = Logger.getLogger(AgentInstaller.class);
    private static final String SECURITY_REALM_NAME = "HawkularRealm";

    public static void main(String[] args) throws Exception {
        ProcessedCommand<?> options = null;

        ArrayList<File> filesToDelete = new ArrayList<>();

        try {
            options = InstallerConfiguration.buildCommandLineOptions();
            CommandLineParser<?> parser = new CommandLineParserBuilder().processedCommand(options).create();

            StringBuilder argLine = new StringBuilder(InstallerConfiguration.COMMAND_NAME);
            for (String str : args) {
                argLine.append(' ').append(str);
            }

            CommandLine<?> commandLine = parser.parse(argLine.toString());
            InstallerConfiguration installerConfig = new InstallerConfiguration(commandLine);

            // IF we were told the passwords were encrypted THEN
            //   IF we were given the key on the command line THEN
            //      Use the key given on the command line for decoding
            //   ELSE
            //      Use the key the user gives us over stdin for decoding
            //
            //   IF we were given the salt on the command line THEN
            //      Use the salt given on the command line for decoding
            //   ELSE
            //      Use the salt the user gives us over stdin for decoding
            //
            // Decode using the key and salt.
            boolean passwordsEncrypted = commandLine.hasOption(InstallerConfiguration.OPTION_ENCRYPTION_KEY);
            if (passwordsEncrypted) {
                String key = commandLine.getOptionValue(InstallerConfiguration.OPTION_ENCRYPTION_KEY, null);
                String saltAsString = commandLine.getOptionValue(InstallerConfiguration.OPTION_ENCRYPTION_SALT, null);
                if (key == null || key.isEmpty()) {
                    key = readPasswordFromStdin("Encryption key:");
                }

                boolean saltSpecified = commandLine.hasOption(InstallerConfiguration.OPTION_ENCRYPTION_SALT);
                if (!saltSpecified) {
                    saltAsString = key;
                }

                if (saltAsString == null || saltAsString.isEmpty()) {
                    saltAsString = readPasswordFromStdin("Salt:");
                }

                assert saltAsString != null;
                assert key != null;

                byte[] salt = saltAsString.getBytes("UTF-8");
                installerConfig.decodeProperties(key, salt);
            }

            String jbossHome = installerConfig.getTargetLocation();
            if (jbossHome == null) {
                // user did not provide us with a wildfly home - let's see if we are sitting in a wildfly home already
                File jbossHomeFile = new File(".").getCanonicalFile();
                if (!(jbossHomeFile.exists() &&
                        jbossHomeFile.isDirectory() &&
                        jbossHomeFile.canRead() &&
                        new File(jbossHomeFile, "modules").isDirectory())) {
                    throw new Exception(InstallerConfiguration.OPTION_TARGET_LOCATION + " must be specified");
                }
                // looks like our current working directory is a WildFly home - use that
                jbossHome = jbossHomeFile.getCanonicalPath();
            }

            if (installerConfig.getUsername() == null || installerConfig.getPassword() == null) {
                throw new Exception(
                        "You must provide credentials (username/password) in installer configuration");
            }

            String hawkularServerProtocol;
            String hawkularServerHost;
            String hawkularServerPort;

            if (installerConfig.getServerUrl() == null) {
                throw new Exception("You must provide the Hawkular Server URL");
            }

            try {
                URL hawkularServerUrl = new URL(installerConfig.getServerUrl());
                hawkularServerProtocol = hawkularServerUrl.getProtocol();
                hawkularServerHost = hawkularServerUrl.getHost();
                hawkularServerPort = String.valueOf(hawkularServerUrl.getPort());
                if ("-1".equals(hawkularServerPort)) {
                    hawkularServerPort = "80";
                }
            } catch (MalformedURLException mue) {
                // It is possible the user passed a URL with a WildFly expression like:
                // "http://${jboss.bind.address:localhost}:8080" or "${protocol}://${addr:localhost}:${port:8080}"
                // Here we try to parse something like that.
                Matcher m = Pattern.compile("(https?|\\$\\{.+\\})://(.+):(\\d+|\\$\\{.+\\})")
                        .matcher(installerConfig.getServerUrl());
                if (m.matches() && m.groupCount() == 3) {
                    hawkularServerProtocol = m.group(1);
                    hawkularServerHost = m.group(2);
                    hawkularServerPort = m.group(3);
                } else {
                    throw new Exception("Server URL cannot be parsed.", mue);
                }
            }

            String moduleZip = installerConfig.getModuleDistribution();

            URL moduleZipUrl;

            if (installerConfig.isConfigOnly()) {
                moduleZipUrl = null;
            } else if (moduleZip == null) {
                // --module-dist is not supplied so try to download agent module from server
                File moduleTempFile = downloadModuleZip(getHawkularServerAgentDownloadUrl(installerConfig), jbossHome);
                if (moduleTempFile == null) {
                    throw new IOException("Failed to retrieve module dist from server, You can use option ["
                            + InstallerConfiguration.OPTION_MODULE_DISTRIBUTION
                            + "] to supply your own");
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

                // if the user didn't specify a name, assume the typical name
                if (resourceUrl.equals("/")) {
                    if (isEAP6(jbossHome)) {
                        resourceUrl = "/hawkular-wildfly-agent-wf-extension-eap6.zip";
                    } else {
                        resourceUrl = "/hawkular-wildfly-agent-wf-extension.zip";
                    }
                }

                moduleZipUrl = AgentInstaller.class.getResource(resourceUrl);
                if (moduleZipUrl == null) {
                    throw new IOException("Unable to load module.zip from classpath [" + resourceUrl + "]");
                }
            } else if (moduleZip.matches("(http|https|file):.*")) {
                // the module is specified as a URL - we'll download it
                File moduleTempFile = downloadModuleZip(new URL(moduleZip), jbossHome);
                if (moduleTempFile == null) {
                    throw new IOException("Failed to retrieve agent module from server, option ["
                            + InstallerConfiguration.OPTION_MODULE_DISTRIBUTION
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
            File socketBindingSnippetFile = createSocketBindingSnippet(hawkularServerHost, hawkularServerPort);
            filesToDelete.add(socketBindingSnippetFile);
            Builder configurationBldr = DeploymentConfiguration.builder()
                    .jbossHome(new File(jbossHome))
                    .module(moduleZipUrl)
                    .defaultModuleId("org.hawkular.agent")
                    .defaultModuleRelativePath("/system/add-ons/hawkular-agent/")
                    .socketBinding(socketBindingSnippetFile.toURI().toURL());

            // let the user override the default subsystem snippet that is found in the module zip
            // can be specified as a URL or a file path
            if (installerConfig.getSubsystemSnippet() != null) {
                try {
                    configurationBldr.subsystem(new URL(installerConfig.getSubsystemSnippet()));
                } catch (MalformedURLException mue) {
                    File file = new File(installerConfig.getSubsystemSnippet());
                    if (file.exists()) {
                        configurationBldr.subsystem(file.getAbsoluteFile().toURI().toURL());
                    } else {
                        throw new FileNotFoundException("Subsystem snippet not found at ["
                                + installerConfig.getSubsystemSnippet() + "]");
                    }
                }
            }

            String targetConfig = installerConfig.getTargetConfig();
            if (targetConfig != null) {
                configurationBldr.serverConfig(targetConfig);
            } else {
                targetConfig = DeploymentConfiguration.DEFAULT_SERVER_CONFIG;
                // we'll use this in case of https to resolve server configuration directory
            }

            // some xpaths are different depending on which config we are installing in (standalone, domain, host)
            TargetConfigInfo targetConfigInfo;
            if (targetConfig.matches(".*standalone[^/]*.xml")) {
                targetConfigInfo = new StandaloneTargetConfigInfo();
            } else if (targetConfig.matches(".*host[^/]*.xml")) {
                targetConfigInfo = new HostTargetConfigInfo();
            } else if (targetConfig.matches(".*domain[^/]*.xml")) {
                targetConfigInfo = new DomainTargetConfigInfo();
            } else {
                log.warnf("Don't know the kind of config this is, will assume standalone: [%s]", targetConfig);
                targetConfigInfo = new StandaloneTargetConfigInfo();
            }

            // If we are to talk to the Hawkular Server over HTTPS, we need to set up some additional things.
            // We may not know the protocol if the user supplied the URL in the form ${some-env}://host:port.
            // In this case, do our best and assume the user knows what they are doing (that is, if no keystore
            // info is supplied, the user must know he isn't going to be using https so allow that to happen).
            String keystorePath = installerConfig.getKeystorePath();
            String keystorePass = installerConfig.getKeystorePassword();

            // If protocol is explicitly defined as https, this if-stmt merely performs some helpful things
            // like abort to remind the user to provide required keystore info.
            // If we cannot tell if https is to be used, we keep going but these helpful things
            // are not performed and the user must ensure they provide this information if they expect to use https.
            if (hawkularServerProtocol.equals("https")) {
                if (keystorePath == null) {
                    throw new Exception(String.format("When using https protocol, the following keystore "
                            + "command line option is required: %s",
                            InstallerConfiguration.OPTION_KEYSTORE_PATH));
                }
            }

            // if keystore path is specified, it means the user expects to use https, so do some extra things
            if (keystorePath != null) {
                // password fields are not required, but if not supplied we'll ask user
                if (keystorePass == null) {
                    keystorePass = readPasswordFromStdin("Keystore password:");
                    if (keystorePass == null || keystorePass.isEmpty()) {
                        keystorePass = "";
                        log.warn(InstallerConfiguration.OPTION_KEYSTORE_PASSWORD
                                + " was not provided; using empty password");
                    }
                }

                File keystoreSrcFile = new File(keystorePath);
                if (!(keystoreSrcFile.isFile() && keystoreSrcFile.canRead())) {
                    throw new FileNotFoundException("Cannot read " + keystoreSrcFile.getAbsolutePath());
                }

                File targetConfigDir;
                if (new File(targetConfig).isAbsolute()) {
                    targetConfigDir = new File(targetConfig).getParentFile();
                } else {
                    targetConfigDir = new File(jbossHome, targetConfig).getParentFile();
                }

                Path keystoreDst = Paths.get(targetConfigDir.getAbsolutePath()).resolve(keystoreSrcFile.getName());

                // never overwrite target keystore
                if (!keystoreDst.toFile().exists()) {
                    log.info("Copy [" + keystoreSrcFile.getAbsolutePath() + "] to [" + keystoreDst.toString() + "]");
                    Files.copy(Paths.get(keystoreSrcFile.getAbsolutePath()), keystoreDst);
                }

                String securityRealm = createSecurityRealm(keystoreSrcFile.getName(), keystorePass);
                configurationBldr.addXmlEdit(new XmlEdit(targetConfigInfo.getSecurityRealmsXPath(), securityRealm));
            }

            // setup storage-adapter (within hawkular-wildfly-agent subsystem)
            configurationBldr
                    .addXmlEdit(createStorageAdapter(targetConfigInfo, (keystorePath != null), installerConfig));

            // create managed servers and other things
            configurationBldr.addXmlEdit(createManagedServers(targetConfigInfo, installerConfig));
            configurationBldr.addXmlEdit(setEnableFlag(targetConfigInfo, installerConfig));

            configurationBldr.modulesHome("modules");

            // It is possible the user wants to define the protocol via WildFly env. var. in server URL,
            // e.g. --server-url=${protocol-to-use}://${host:localhost}:8080
            // In that case,we have to use the url attribute, not the outbound socket binding.
            // TODO due to WFCORE-1505 - domain mode can't use outbound bindings
            boolean useOutboundSocketBinding = (hawkularServerProtocol.startsWith("http")) &&
                    (targetConfigInfo instanceof StandaloneTargetConfigInfo);
            if (!useOutboundSocketBinding) {
                configurationBldr.socketBinding(null);
            }

            new ExtensionDeployer().install(configurationBldr.build());

        } catch (CommandLineParserException pe) {
            log.error(pe);
            printHelp(options);
            if (Boolean.getBoolean("org.hawkular.wildfly.agent.installer.throw-exception-on-error")) {
                throw pe;
            }
        } catch (Exception ex) {
            log.error(ex);
            if (Boolean.getBoolean("org.hawkular.wildfly.agent.installer.throw-exception-on-error")) {
                throw ex;
            }
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
     * @return XML snippet
     */
    private static String createSecurityRealm(String keystoreFile, String keystorePass) {
        return new StringBuilder("<security-realm name=\"" + SECURITY_REALM_NAME + "\">")
                .append("<authentication>")
                .append("<truststore path=\"" + keystoreFile + "\"")
                .append(" relative-to=\"jboss.server.config.dir\"")
                .append(" keystore-password=\"" + keystorePass + "\"")
                .append(" /></authentication></security-realm>").toString();
    }

    /**
     * Creates XML edit which sets up storage-adapter configuration, creates a reference
     * to the security-realm and enables SSL, if appropriate.
     *
     * @param targetConfigInfo info on the xml config file being edited
     * @param withHttps if the storage adapter will be accessed via HTTPS
     * @return object that can be used to edit some xml content
     */
    private static XmlEdit createStorageAdapter(TargetConfigInfo targetConfigInfo, boolean withHttps,
            InstallerConfiguration installerConfig) {
        String select = targetConfigInfo.getProfileXPath() + "/*[namespace-uri()='urn:org.hawkular.agent:agent:1.0']/";
        StringBuilder xml = new StringBuilder("<storage-adapter");

        String tenantId = installerConfig.getTenantId();
        if (installerConfig.isMetricsOnlyMode()) {
            xml.append(" type=\"METRICS\"");
            if (tenantId == null || tenantId.isEmpty()) {
                throw new IllegalArgumentException("You must specify tenant-id when in metrics-only mode");
            }
        } else {
            xml.append(" type=\"HAWKULAR\"");
        }

        if (tenantId != null && !tenantId.isEmpty()) {
            xml.append(" tenant-id=\"" + tenantId + "\"");
        }

        if (withHttps) {
            xml.append(" security-realm=\"" + SECURITY_REALM_NAME + "\"")
                    .append(" use-ssl=\"true\"");
        }

        if (installerConfig.getFeedId() != null && !installerConfig.getFeedId().isEmpty()) {
            xml.append(" feed-id=\"" + installerConfig.getFeedId() + "\"");
        }

        if (installerConfig.getUsername() != null && !installerConfig.getUsername().isEmpty()) {
            xml.append(" username=\"" + installerConfig.getUsername() + "\"");
        }
        if (installerConfig.getPassword() != null && !installerConfig.getPassword().isEmpty()) {
            xml.append(" password=\"" + installerConfig.getPassword() + "\"");
        }

        // It is possible the user wants to define the protocol via WildFly env. var. in server URL,
        // e.g. --server-url=${protocol-to-use}://${host:localhost}:8080
        // In that case,we have to use the url attribute, not the outbound socket binding.
        // TODO due to WFCORE-1505 - domain mode can't use outbound bindings
        String serverUrl = installerConfig.getServerUrl();
        boolean useOutboundSocketBinding = (serverUrl.startsWith("http")) &&
                (targetConfigInfo instanceof StandaloneTargetConfigInfo);
        if (useOutboundSocketBinding) {
            xml.append(" server-outbound-socket-binding-ref=\"hawkular\"");
        } else {
            xml.append(" url=\"").append(serverUrl).append("\"");
        }

        xml.append("/>");

        // replaces <storage-adapter> under urn:org.hawkular.agent:agent:1.0 subsystem with above content
        // we ignore whether the original storage-adapter has type of HAWKULAR or METRICS
        return new XmlEdit(select, xml.toString()).withAttribute("type").withIsIgnoreAttributeValue(true);
    }

    /**
     * Creates a (outbound) socket-binding snippet XML file
     *
     * @param host the host where the hawkular server is running
     * @param port the port where the hawkular server is listening
     * @return file to the temporary socket binding snippet file (this should be cleaned up by the caller)
     * @throws IOException on error
     */
    private static File createSocketBindingSnippet(String host, String port) throws IOException {
        StringBuilder xml = new StringBuilder("<outbound-socket-binding name=\"hawkular\">\n")
                .append("  <remote-destination host=\"" + host + "\" port=\"" + port + "\" />\n")
                .append("</outbound-socket-binding>");
        Path tempFile = Files.createTempFile("hawkular-wildfly-module-installer-outbound-socket-binding", ".xml");
        Files.write(tempFile, xml.toString().getBytes());
        return tempFile.toFile();
    }

    private static XmlEdit createManagedServers(TargetConfigInfo targetConfigInfo, InstallerConfiguration config) {
        String select = targetConfigInfo.getProfileXPath()
                + "/*[namespace-uri()='urn:org.hawkular.agent:agent:1.0']/";
        String managedServerName = config.getManagedServerName();
        if (managedServerName == null || managedServerName.trim().isEmpty()) {
            managedServerName = "Local"; // just make sure its something
        }
        String managedServerResourceTypeSets = config.getManagedResourceTypeSets();
        if (managedServerResourceTypeSets == null || managedServerResourceTypeSets.trim().isEmpty()) {
            managedServerResourceTypeSets = targetConfigInfo.getManagedServerResourceTypeSets();
        }

        StringBuilder xml = new StringBuilder("<managed-servers>")
                .append("<local-dmr name=\"" + managedServerName + "\" enabled=\"true\" "
                        + "resource-type-sets=\""
                        + managedServerResourceTypeSets
                        + "\">")
                .append("<wait-for name=\"/\" />")
                .append("</local-dmr>")
                .append("</managed-servers>");

        // replaces <managed-servers> under urn:org.hawkular.agent:agent:1.0 subsystem with above content
        return new XmlEdit(select, xml.toString());
    }

    private static XmlEdit setEnableFlag(TargetConfigInfo targetConfigInfo, InstallerConfiguration config) {
        String select = targetConfigInfo.getProfileXPath()
                + "/*[namespace-uri()='urn:org.hawkular.agent:agent:1.0'][@enabled]";
        String isEnabled = String.valueOf(config.isEnabled());
        return new XmlEdit(select, isEnabled).withIsAttributeContent(true).withAttribute("enabled");
    }

    private static URL getHawkularServerAgentDownloadUrl(InstallerConfiguration config)
            throws Exception {
        try {
            String serverUrl = String.format("%s/hawkular/wildfly-agent/download", config.getDownloadServerUrl());
            return new URL(serverUrl);
        } catch (MalformedURLException e) {
            throw new Exception(
                    "Invalid download URL. Use --" + InstallerConfiguration.OPTION_DOWNLOAD_SERVER_URL
                            + " to specify where the installer can download the module distribution. "
                            + "Or provide a module distribution to the installer via --"
                            + InstallerConfiguration.OPTION_MODULE_DISTRIBUTION,
                    e);
        }
    }

    /**
     * Downloads the Hawkular WildFly Agent ZIP file from a URL
     *
     * @param url where the agent zip is
     * @param jbossHome used to determine what kind of agent we need
     * @return absolute path to module downloaded locally or null if it could not be retrieved;
     *         this is a temporary file that should be cleaned once it is used
     */
    private static File downloadModuleZip(URL url, String jbossHome) {
        File tempFile;

        try {
            tempFile = File.createTempFile("hawkular-wildfly-agent", ".zip");
        } catch (Exception e) {
            throw new RuntimeException("Cannot create temp file to hold module zip", e);
        }

        if (isEAP6(jbossHome) && url.getProtocol().startsWith("http")) {
            String param = "appserver=eap6";
            if (url.getQuery() == null || !url.getQuery().contains(param)) {
                try {
                    url = new URL(url.getProtocol(), url.getHost(), url.getPort(),
                            url.getFile() + (url.getQuery() == null ? "?" : "&") + param);
                } catch (MalformedURLException e) {
                    throw new RuntimeException("Cannot append param [" + param + "] to url [" + url + "]", e);
                }
            }
        }

        log.info("Downloading agent module extension from: " + url);

        try (FileOutputStream fos = new FileOutputStream(tempFile);
                InputStream ios = url.openStream()) {
            IOUtils.copyLarge(ios, fos);
            return tempFile;
        } catch (Exception e) {
            log.warn("Unable to download hawkular wildfly agent module extension: " + url, e);
            tempFile.delete();
        }
        return null;
    }

    private static void printHelp(ProcessedCommand<?> options) {
        if (options == null) {
            throw new RuntimeException("Cannot print help - options is null");
        }
        System.out.println(options.printHelp());
    }

    // see if the app server the agent is being installed into is EAP 6.x
    private static boolean isEAP6(String jbossHome) {
        try {
            File manifestFile = new File(jbossHome,
                    "modules/system/layers/base/org/jboss/as/product/eap/dir/META-INF/MANIFEST.MF");
            if (manifestFile.canRead()) {
                try (InputStream ios = new FileInputStream(manifestFile)) {
                    Manifest manifest = new Manifest(ios);
                    String version = manifest.getMainAttributes().getValue("JBoss-Product-Release-Version");
                    return version != null && version.startsWith("6.");
                }
            } else {
                log.debugf("No readable manifest file at [%s], assuming the app server is not EAP 6.x.",
                        manifestFile.getAbsolutePath());
                return false; // no manifest file - can't tell what it is
            }
        } catch (Exception e) {
            log.debug("Unable to determine if the app server is EAP 6.x - assuming it is not. Cause: " + e);
            return false;
        }
    }
}
