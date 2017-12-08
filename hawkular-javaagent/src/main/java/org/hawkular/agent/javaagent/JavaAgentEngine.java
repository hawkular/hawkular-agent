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
package org.hawkular.agent.javaagent;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.lang.management.ManagementFactory;
import java.net.InetAddress;
import java.security.KeyManagementException;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.UnrecoverableKeyException;
import java.security.cert.CertificateException;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import javax.management.MBeanServer;
import javax.management.ObjectName;
import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;
import javax.net.ssl.TrustManagerFactory;

import org.hawkular.agent.javaagent.cmd.EchoCommand;
import org.hawkular.agent.javaagent.config.ConfigConverter;
import org.hawkular.agent.javaagent.config.ConfigManager;
import org.hawkular.agent.javaagent.config.Configuration;
import org.hawkular.agent.javaagent.config.SecurityRealm;
import org.hawkular.agent.javaagent.log.JavaAgentLoggers;
import org.hawkular.agent.javaagent.log.MsgLogger;
import org.hawkular.agent.monitor.cmd.Command;
import org.hawkular.agent.monitor.config.AgentCoreEngineConfiguration;
import org.hawkular.agent.monitor.config.AgentCoreEngineConfiguration.MetricsExporterConfiguration;
import org.hawkular.agent.monitor.protocol.dmr.ModelControllerClientFactory;
import org.hawkular.agent.monitor.service.AgentCoreEngine;
import org.hawkular.agent.monitor.service.ServiceStatus;
import org.hawkular.agent.monitor.service.Version;
import org.hawkular.bus.common.BasicMessage;

/**
 * The Hawkular Agent running as a standalone Java Agent.
 */
public class JavaAgentEngine extends AgentCoreEngine implements JavaAgentMXBean {
    private static final MsgLogger log = JavaAgentLoggers.getLogger(JavaAgentEngine.class);
    private static final String MBEAN_OBJECT_NAME = "org.hawkular.agent:type=hawkular-javaagent";

    private final ConfigManager configurationManager;
    private final Map<String, TrustManager[]> trustOnlyTrustManagers = new HashMap<>();
    private final Map<String, SSLContext> trustOnlySslContexts = new HashMap<>();

    public JavaAgentEngine(File configFile) throws Exception {
        this(new ConfigManager(configFile));
    }

    private JavaAgentEngine(ConfigManager configMgr) throws Exception {
        super(new ConfigConverter(configMgr.getConfiguration(true)).convert());
        log.infoLoadedConfigurationFile(configMgr.getConfigFile().getAbsolutePath());
        this.configurationManager = configMgr;
        loadSecurityRealms(configMgr.getConfiguration(), trustOnlyTrustManagers, trustOnlySslContexts);

        MBeanServer mbs = ManagementFactory.getPlatformMBeanServer();
        mbs.registerMBean(this, new ObjectName(MBEAN_OBJECT_NAME));
    }

    private static void loadSecurityRealms(
            Configuration config,
            Map<String, TrustManager[]> trustOnlyTrustManagers,
            Map<String, SSLContext> trustOnlySslContexts) {

        SecurityRealm[] securityRealms = config.getSecurityRealms();
        if (securityRealms == null) {
            return;
        }

        for (SecurityRealm securityRealm : securityRealms) {
            try {
                String keyStoreType = securityRealm.getKeystoreType();
                String trustManagerAlgorithm = securityRealm.getTrustManagerAlgorithm();
                String keyManagerAlgorithm = securityRealm.getKeyManagerAlgorithm();
                String sslProtocol = securityRealm.getSslProtocol();
                String keyPassword = (securityRealm.getKeyPassword() != null) ? securityRealm.getKeyPassword()
                        : securityRealm.getKeystorePassword();

                KeyStore keystore = KeyStore.getInstance(keyStoreType);
                try (InputStream is = new FileInputStream(securityRealm.getKeystorePath())) {
                    keystore.load(is, securityRealm.getKeystorePassword().toCharArray());
                }
                KeyManagerFactory kmf = KeyManagerFactory.getInstance(keyManagerAlgorithm);
                kmf.init(keystore, keyPassword.toCharArray());
                TrustManagerFactory tmf = TrustManagerFactory.getInstance(trustManagerAlgorithm);
                tmf.init(keystore);

                trustOnlyTrustManagers.put(securityRealm.getName(), tmf.getTrustManagers());

                SSLContext sc = SSLContext.getInstance(sslProtocol);
                sc.init(kmf.getKeyManagers(), tmf.getTrustManagers(), new java.security.SecureRandom());

                trustOnlySslContexts.put(securityRealm.getName(), sc);

            } catch (NoSuchAlgorithmException
                    | KeyStoreException
                    | CertificateException
                    | IOException
                    | UnrecoverableKeyException
                    | KeyManagementException e) {
                log.errorBuildingSecurityRealm(securityRealm.getName(), e);
            }
        }
    }

    /**
     * This method allows you to start the agent using a Java Agent Engine Configuration ({@link Configuration})
     * rather than a Agent Core Engine configuration ({@link AgentCoreEngineConfiguration}).
     *
     * If the original agent configuration indicated the agent should be immutable, this ignores
     * the given configuration and restarts the agent using the old configuration.
     *
     * The new configuration will be persisted if the agent was mutable and allowed to change.
     *
     * @param newConfig the new configuration to use (may be null which means use the previous configuration).
     */
    public void startHawkularAgent(Configuration newConfig) {
        if (newConfig == null) {
            super.startHawkularAgent();
        } else {
            Configuration oldConfig = getConfigurationManager().getConfiguration();
            boolean doNotChangeConfig = (oldConfig != null && oldConfig.getSubsystem().getImmutable());

            AgentCoreEngineConfiguration agentConfig;
            try {
                agentConfig = new ConfigConverter(doNotChangeConfig ? oldConfig : newConfig).convert();
            } catch (Exception e) {
                throw new RuntimeException("Cannot start agent - config is invalid", e);
            }

            try {
                if (!doNotChangeConfig) {
                    this.configurationManager.updateConfiguration(newConfig, true);
                }
                super.startHawkularAgent(agentConfig);
            } catch (RuntimeException re) {
                throw re;
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }
    }

    /**
     * Returns the object responsible for managing the Java Agent's configuration.
     * This contains the current configuration and a way to persist new configurations.
     *
     * @return configuration manager
     */
    public ConfigManager getConfigurationManager() {
        return this.configurationManager;
    }

    @Override
    protected Map<String, SSLContext> buildTrustOnlySSLContextValues(AgentCoreEngineConfiguration config) {
        return this.trustOnlySslContexts;
    }

    @Override
    protected Map<String, TrustManager[]> buildTrustOnlyTrustManagersValues(AgentCoreEngineConfiguration config) {
        return this.trustOnlyTrustManagers;
    }

    @Override
    protected ModelControllerClientFactory buildLocalModelControllerClientFactory() {
        return null;
    }

    @Override
    protected AgentCoreEngineConfiguration overlayConfiguration(InputStream newConfig) {
        try {
            this.configurationManager.overlayConfiguration(newConfig, false, false);
            AgentCoreEngineConfiguration agentConfig;
            agentConfig = new ConfigConverter(this.configurationManager.getConfiguration()).convert();
            return agentConfig;
        } catch (Exception e) {
            throw new RuntimeException("Cannot convert overlaid configuration - config is invalid", e);
        }
    }

    @Override
    protected void cleanupDuringStop() {
        return; // no-op
    }

    @Override
    protected String autoGenerateFeedId() throws Exception {
        // Try to figure out a good feed ID to use.
        // If we are attached to WildFly/EAP, certain system properties may be set to uniquely
        // identify the server - we'll use that identification for our feed ID.
        // Otherwise, try to figure out a feed ID using the hostname.
        // If nothing works, use the local host name as known via Java API.
        final class FeedIdLocation {
            private static final String SYSPROP = "System Property";
            private static final String ENVVAR = "Environment Variable";
            private static final String JAVAAPI = "Java API";
            private String where;
            private String name;

            private FeedIdLocation(String where, String name) {
                this.where = where;
                this.name = name;
            }

            private String getFeedId() throws Exception {
                String feedId;
                if (SYSPROP.equals(this.where)) {
                    feedId = System.getProperty(this.name);
                } else if (ENVVAR.equals(this.where)) {
                    feedId = System.getenv(this.name);
                } else {
                    feedId = InetAddress.getLocalHost().getCanonicalHostName();
                }

                if (feedId != null) {
                    // log it so we know where we are getting it from
                    log.debugf("Got feed ID from [%s '%s']=[%s]", this.where, this.name, feedId);
                }
                return feedId;
            }
        }

        FeedIdLocation[] feedIdLocations = new FeedIdLocation[] {
                new FeedIdLocation(FeedIdLocation.SYSPROP, "jboss.server.management.uuid"),
                new FeedIdLocation(FeedIdLocation.SYSPROP, "jboss.node.name"),
                new FeedIdLocation(FeedIdLocation.SYSPROP, "jboss.host.name"),
                new FeedIdLocation(FeedIdLocation.SYSPROP, "jboss.server.name"),
                new FeedIdLocation(FeedIdLocation.SYSPROP, "jboss.qualified.host.name"),
                new FeedIdLocation(FeedIdLocation.ENVVAR, "HOSTNAME"),
                new FeedIdLocation(FeedIdLocation.ENVVAR, "COMPUTERNAME"),
                new FeedIdLocation(FeedIdLocation.JAVAAPI, "InetAddress.getLocalHost().getCanonicalHostName()")
        };

        for (FeedIdLocation fil : feedIdLocations) {
            String feedId = fil.getFeedId();
            if (feedId != null) {
                return feedId;
            }
        }
        throw new IllegalStateException("Cannot autogenerate feed ID");
    }

    @Override
    protected Map<String, Class<? extends Command<? extends BasicMessage, ? extends BasicMessage>>> //
            buildAdditionalCommands() {
        return Collections.singletonMap(EchoCommand.REQUEST_CLASS.getName(), EchoCommand.class);
    }

    // JMX Interface

    @Override
    public String getVersion() {
        return Version.getVersionString();
    }

    @Override
    public boolean getImmutable() {
        return getConfiguration().getGlobalConfiguration().isImmutable();
    }

    @Override
    public boolean getInContainer() {
        return getConfiguration().getGlobalConfiguration().isInContainer();
    }

    @Override
    public String getMetricsEndpoints() {
        MetricsExporterConfiguration mec = getConfiguration().getMetricsExporterConfiguration();
        StringBuilder endpoints = new StringBuilder(String.format("%s:%d", mec.getHost(), mec.getPort()));
        Set<String> proxiedEndpoints = getMetricsExportersThatAreProxied();
        for (String proxiedEndpoint : proxiedEndpoints) {
            endpoints.append("|").append(proxiedEndpoint);
        }
        return endpoints.toString();
    }

    @Override
    public void start() {
        // force the agent configuration file to be reloaded
        Configuration reloadedConfig;
        try {
            reloadedConfig = this.configurationManager.getConfiguration(true);
        } catch (Exception e) {
            throw new RuntimeException("Cannot start the agent", e);
        }
        this.startHawkularAgent(reloadedConfig);
    }

    @Override
    public void stop() {
        stopHawkularAgent();
    }

    @Override
    public String status() {
        return getStatus().name();
    }

    @Override
    public String fullDiscoveryScan() {
        try {
            ServiceStatus status = getStatus();
            if (status == ServiceStatus.RUNNING) {
                if (!isMetricsOnlyMode(getConfiguration())) {
                    long start = System.currentTimeMillis();
                    getProtocolServices().discoverAll();
                    long duration = System.currentTimeMillis() - start;
                    return String.format("Full inventory discovery scan completed in [%d] milliseconds", duration);
                } else {
                    return String.format("Cannot run discovery scan because the agent is in metrics-only mode");
                }
            } else {
                return String.format("Cannot run discovery scan because the agent is not running. Status is [%s]",
                        status);
            }
        } catch (Exception e) {
            return String.format("Error occurred while attempting discovery scan. err=%s", e);
        }
    }

    @Override
    public String inventoryReport() {
        if (isMetricsOnlyMode(getConfiguration())) {
            return "Cannot obtain inventory report because the agent is in metrics-only mode";
        }

        try {
            return InventoryReport.getInventoryReport(this);
        } catch (Exception e) {
            return "Cannot obtain inventory report: " + e;
        }
    }
}
