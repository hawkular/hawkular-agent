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
import java.nio.file.Paths;
import java.util.List;

import org.jboss.logging.Logger;

/**
 * Goal which deploys JBoss module to JBoss AS7/WildFly server
 */
public class ExtensionDeployer {
    private final Logger log = Logger.getLogger(this.getClass());

    private File targetServerConfigAbsolute;
    private File sourceServerConfigBackupAbsolute;
    private File modulesHomeAbsolute;

    public void install(DeploymentConfiguration configuration) throws ExtensionDeploymentException {
        log.debug("Validating configuration");
        validConfiguration(configuration);

        JBossModule module = null;
        RegisterModuleConfiguration resolvedOptions = new RegisterModuleConfiguration();
        if (configuration.getModule() != null) {
            try {
                log.debugf("Reading module from [%s]", configuration.getModule());
                module = JBossModule.readFromURL(configuration.getModule());
            } catch (Exception e) {
                throw new ExtensionDeploymentException("Failed to read module", e);
            }

            try {
                List<File> installedFiles = module.installTo(modulesHomeAbsolute);
                resolvedOptions = resolveBundledXmlSnippets(installedFiles, configuration);

            } catch (Exception e) {
                throw new ExtensionDeploymentException("Failed to install module", e);
            }
        }

        try {
            RegisterModuleConfiguration options = new RegisterModuleConfiguration();
            if (module != null) {
                options.withExtension(module.getModuleId());
            }

            if (configuration.getConfigType() != null) {
                options.configType(configuration.getConfigType());
            }
            options
                .targetServerConfig(targetServerConfigAbsolute)
                .sourceServerConfig(sourceServerConfigBackupAbsolute)
                .subsystem(configuration.getSubsystem())
                .socketBinding(configuration.getSocketBinding())
                .socketBindingGroups(configuration.getSocketBindingGroups())
                .xmlEdits(configuration.getEdit())
                .profiles(configuration.getProfiles())
                .failNoMatch(configuration.isFailNoMatch());

            resolvedOptions.extend(options);
            log.debugf("Proceeding with \n%s", resolvedOptions);
            register(resolvedOptions);

        } catch (Exception e) {
            log.error(e);
            throw new ExtensionDeploymentException("Failed to update server configuration file", e);
        }
    }

    private RegisterModuleConfiguration resolveBundledXmlSnippets(List<File> installedFiles,
            DeploymentConfiguration configuration) throws Exception {

        RegisterModuleConfiguration options = new RegisterModuleConfiguration();

        // if the user provided a subsystem and socketBinding snippet XML, use that
        // rather than what's bundled with the module. This let's the user override
        // his own configuration with that of the default config that comes with the
        // module itself.

        boolean overrideSubsystem = false;
        boolean overrideSocketBinding = false;

        if (configuration.getSubsystem() != null) {
            options.subsystem(configuration.getSubsystem());
            overrideSubsystem = true;
            log.debugf("Using user-provided subsystem snippet: [%s]", options.getSubsystem());
        }
        if (configuration.getSocketBinding() != null) {
            options.socketBinding(configuration.getSocketBinding());
            overrideSocketBinding = true;
            log.debugf("Using user-provided socketBinding snippet: [%s]", options.getSocketBinding());
        }

        if (!overrideSubsystem || !overrideSocketBinding) {
            for (File file : installedFiles) {
                if (!overrideSubsystem) {
                    if ("subsystem-snippet.xml".equals(file.getName())) {
                        log.debugf("Found packaged subsystem snippet: [%s]", file.getAbsolutePath());
                        options.subsystem(file.toURI().toURL());
                    }
                }
                if (!overrideSocketBinding) {
                    if ("socket-binding-snippet.xml".equals(file.getName())) {
                        log.debugf("Found packaged socket-binding snippet: [%s]", file.getAbsolutePath());
                        options.socketBinding(file.toURI().toURL());
                    }
                }
            }
        }

        return options;
    }

    public void register(RegisterModuleConfiguration options) throws Exception {
        new RegisterExtension().register(options);
    }

    private void validConfiguration(DeploymentConfiguration configuration) throws ExtensionDeploymentException {
        File jbossHome = configuration.getJbossHome();

        if (!(jbossHome.exists() && jbossHome.isDirectory() && jbossHome.canRead())) {
            throw new ExtensionDeploymentException("wildflyHome = " + jbossHome.getAbsolutePath()
                    + " is not readable and existing directory");
        }
        if (!new File(jbossHome, "modules").isDirectory()) {
            throw new ExtensionDeploymentException("wildflyHome = " + jbossHome.getAbsolutePath()
                    + " does not seem to point to AS7/WildFly installation dir");
        }

        if (new File(configuration.getTargetServerConfig()).isAbsolute()) {
            targetServerConfigAbsolute = new File(configuration.getTargetServerConfig());
        } else {
            targetServerConfigAbsolute = new File(jbossHome, configuration.getTargetServerConfig());
        }
        if (!(targetServerConfigAbsolute.exists() && targetServerConfigAbsolute.isFile() && targetServerConfigAbsolute
                .canWrite())) {
            throw new ExtensionDeploymentException(
                    "targetServerConfig = "
                            + configuration.getTargetServerConfig()
                            + " is not writable and existing file. [targetserverConfig]"
                            + "must be either absolute path or relative to [jbossHome]");
        }

        if (new File(configuration.getSourceServerConfig()).isAbsolute()) {
            sourceServerConfigBackupAbsolute = new File(configuration.getSourceServerConfig());
        } else {
            sourceServerConfigBackupAbsolute = new File(jbossHome, configuration.getSourceServerConfig());
        }
        if (!(sourceServerConfigBackupAbsolute.getParentFile().exists()
                && targetServerConfigAbsolute.getParentFile().isDirectory() && targetServerConfigAbsolute
                .getParentFile().canWrite())) {
            throw new ExtensionDeploymentException(
                    "sourceServerConfig = "
                            + configuration.getSourceServerConfig()
                            + " 's parent directory does not exist or is writable."
                            + "[sourceServerConfig] must be either absolute path or relative to [jbossHome]");
        }
        String modulesHome = configuration.getModulesHome();

        if (modulesHome == null) {
            // auto-detect modulesHome
            File wfHome = Paths.get(jbossHome.getAbsolutePath(), "modules", "system", "layers", "base").toFile();
            if (!wfHome.exists()) {
                wfHome = Paths.get(jbossHome.getAbsolutePath(),"modules").toFile();
            }
            modulesHomeAbsolute = wfHome;
        } else {
            File modulesHomeFile = Paths.get(modulesHome).toFile();
            if (modulesHomeFile.isAbsolute()) {
                modulesHomeAbsolute = modulesHomeFile;
            } else {
                modulesHomeAbsolute = Paths.get(jbossHome.getAbsolutePath(), modulesHome).toFile();
            }
        }

        if (!(modulesHomeAbsolute.exists() && modulesHomeAbsolute.isDirectory() && modulesHomeAbsolute.canWrite())) {
            throw new ExtensionDeploymentException(
                    "modulesHome = "
                            + modulesHome
                            + " is not writable and existing directory. [modulesHome]"
                            + "must be either absolute path or relative to [jbossHome]");
        }
    }
}
