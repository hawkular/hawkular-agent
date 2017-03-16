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
package org.hawkular.agent.monitor.service;

import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;

import org.hawkular.agent.monitor.api.HawkularAgentContext;
import org.hawkular.agent.monitor.cmd.Command;
import org.hawkular.agent.monitor.config.AgentCoreEngineConfiguration;
import org.hawkular.agent.monitor.config.AgentCoreEngineConfiguration.EndpointConfiguration;
import org.hawkular.agent.monitor.config.AgentCoreEngineConfiguration.StorageAdapterConfiguration;
import org.hawkular.agent.monitor.protocol.dmr.DMREndpointService;
import org.hawkular.agent.monitor.protocol.dmr.ModelControllerClientFactory;
import org.hawkular.agent.wildfly.cmd.UpdateCollectionIntervalsCommand;
import org.hawkular.agent.wildfly.log.AgentLoggers;
import org.hawkular.agent.wildfly.log.MsgLogger;
import org.hawkular.agent.wildfly.util.WildflyCompatibilityUtils;
import org.hawkular.bus.common.BasicMessage;
import org.jboss.as.controller.ControlledProcessState;
import org.jboss.as.controller.ControlledProcessState.State;
import org.jboss.as.controller.ControlledProcessStateService;
import org.jboss.as.controller.ModelController;
import org.jboss.as.controller.OperationContext;
import org.jboss.as.controller.ProcessType;
import org.jboss.as.controller.client.ModelControllerClient;
import org.jboss.as.domain.management.SecurityRealm;
import org.jboss.as.domain.management.security.SSLContextService;
import org.jboss.as.domain.management.security.SecurityRealmService;
import org.jboss.as.host.controller.DomainModelControllerService;
import org.jboss.as.naming.ManagedReferenceFactory;
import org.jboss.as.naming.ServiceBasedNamingStore;
import org.jboss.as.naming.deployment.ContextNames;
import org.jboss.as.naming.service.BinderService;
import org.jboss.as.network.OutboundSocketBinding;
import org.jboss.as.network.SocketBinding;
import org.jboss.as.server.ServerEnvironment;
import org.jboss.as.server.ServerEnvironmentService;
import org.jboss.as.server.Services;
import org.jboss.msc.inject.Injector;
import org.jboss.msc.service.AbstractServiceListener;
import org.jboss.msc.service.Service;
import org.jboss.msc.service.ServiceBuilder;
import org.jboss.msc.service.ServiceContainer;
import org.jboss.msc.service.ServiceController;
import org.jboss.msc.service.ServiceName;
import org.jboss.msc.service.ServiceTarget;
import org.jboss.msc.service.StartContext;
import org.jboss.msc.service.StartException;
import org.jboss.msc.service.StopContext;
import org.jboss.msc.value.InjectedValue;

/**
 * The main Agent service.
 *
 * @author John Mazzitelli
 * @author <a href="https://github.com/ppalaga">Peter Palaga</a>
 */
public class MonitorService extends AgentCoreEngine implements Service<MonitorService> {
    private static final MsgLogger log = AgentLoggers.getLogger(MonitorService.class);

    /**
     * Builds the runtime configuration, typically out of the boot configuration. It is static so that always stays
     * clear what data it relies on.
     *
     * On certain circumstances, this method may return the {@code bootConfiguration} instance without any modification.
     *
     * @param bootConfiguration the boot configuration
     * @param httpSocketBindingValue the httpSocketBindingValue (not available if agent is inside host controller)
     * @param httpsSocketBindingValue the httpsSocketBindingValue (not available if agent is inside host controller)
     * @param serverOutboundSocketBindingValue the serverOutboundSocketBindingValue
     * @return the runtime configuration
     */
    private static AgentCoreEngineConfiguration buildRuntimeConfiguration(
            AgentCoreEngineConfiguration bootConfiguration,
            InjectedValue<SocketBinding> httpSocketBindingValue,
            InjectedValue<SocketBinding> httpsSocketBindingValue,
            InjectedValue<OutboundSocketBinding> serverOutboundSocketBindingValue) {

        final AgentCoreEngineConfiguration.StorageAdapterConfiguration bootStorageAdapter = bootConfiguration
                .getStorageAdapter();

        log.infoStorageAdapterMode(bootStorageAdapter.getType());
        log.infoTenantId(bootStorageAdapter.getTenantId());
        if (bootConfiguration.getGlobalConfiguration().isImmutable()) {
            log.infoAgentIsImmutable();
        }

        if (bootStorageAdapter.getUrl() != null) {
            return bootConfiguration;
        } else {

            // determine where our Hawkular server is
            // If the user gave us a URL explicitly, that overrides everything and we use it.
            // If no URL is configured, but we are given a server outbound socket binding name,
            // we use that to determine the remote Hawkular URL.
            // If neither URL nor output socket binding name is provided, we assume we are running
            // co-located with the Hawkular server and we use local bindings.
            String useUrl = bootStorageAdapter.getUrl();
            if (useUrl == null) {
                try {
                    String address;
                    int port;

                    if (bootStorageAdapter.getServerOutboundSocketBindingRef() == null) {
                        // no URL or output socket binding - assume we are running co-located with server
                        SocketBinding socketBinding;
                        if (bootStorageAdapter.isUseSSL()) {
                            socketBinding = httpsSocketBindingValue.getValue();
                        } else {
                            socketBinding = httpSocketBindingValue.getValue();
                        }
                        address = socketBinding.getAddress().getHostName();
                        if (address.equals("0.0.0.0") || address.equals("::/128")) {
                            address = InetAddress.getLocalHost().getCanonicalHostName();
                        }
                        port = socketBinding.getAbsolutePort();
                    } else {
                        OutboundSocketBinding serverBinding = serverOutboundSocketBindingValue.getValue();
                        address = WildflyCompatibilityUtils
                                .outboundSocketBindingGetResolvedDestinationAddress(serverBinding).getHostName();
                        port = serverBinding.getDestinationPort();
                    }
                    String protocol = (bootStorageAdapter.isUseSSL()) ? "https" : "http";
                    useUrl = String.format("%s://%s:%d", protocol, address, port);
                } catch (UnknownHostException uhe) {
                    throw new IllegalArgumentException("Cannot determine Hawkular server host", uhe);
                }
            }

            log.infoUsingServerSideUrl(useUrl);

            AgentCoreEngineConfiguration.StorageAdapterConfiguration runtimeStorageAdapter = //
                    new AgentCoreEngineConfiguration.StorageAdapterConfiguration(
                            bootStorageAdapter.getType(),
                            bootStorageAdapter.getUsername(),
                            bootStorageAdapter.getPassword(),
                            bootStorageAdapter.getTenantId(),
                            bootStorageAdapter.getFeedId(),
                            useUrl,
                            bootStorageAdapter.isUseSSL(),
                            bootStorageAdapter.getServerOutboundSocketBindingRef(),
                            bootStorageAdapter.getMetricsContext(),
                            bootStorageAdapter.getFeedcommContext(),
                            bootStorageAdapter.getKeystorePath(),
                            bootStorageAdapter.getKeystorePassword(),
                            bootStorageAdapter.getSecurityRealm(),
                            bootStorageAdapter.getConnectTimeoutSeconds(),
                            bootStorageAdapter.getReadTimeoutSeconds());

            return bootConfiguration.cloneWith(runtimeStorageAdapter);
        }

    }

    private final InjectedValue<ModelController> modelControllerValue = new InjectedValue<>();
    private final InjectedValue<ServerEnvironment> serverEnvironmentValue = new InjectedValue<>();
    private final InjectedValue<ControlledProcessStateService> processStateValue = new InjectedValue<>();
    private final InjectedValue<SocketBinding> httpSocketBindingValue = new InjectedValue<>();
    private final InjectedValue<SocketBinding> httpsSocketBindingValue = new InjectedValue<>();
    private final InjectedValue<OutboundSocketBinding> serverOutboundSocketBindingValue = new InjectedValue<>();
    // key=securityRealm name as a String
    private final Map<String, InjectedValue<SSLContext>> trustOnlySSLContextInjectedValues = new HashMap<>();
    private final Map<String, InjectedValue<TrustManager[]>> trustOnlyTrustManagersInjectedValue = new HashMap<>();

    private PropertyChangeListener serverStateListener;

    // Declared config found in standalone.xml. Only used to build the runtime configuration.
    private AgentCoreEngineConfiguration bootConfiguration;

    // Indicates if we are running in a standalone server or in a host controller (or something similar)
    private final ProcessType processType;

    public MonitorService(AgentCoreEngineConfiguration bootConfiguration, ProcessType processType) {
        super(bootConfiguration);
        this.bootConfiguration = bootConfiguration;
        this.processType = processType;
    }

    @Override
    public MonitorService getValue() {
        return this;
    }

    /**
     * When this service is being built, this method is called to allow this service
     * to add whatever dependencies it needs.
     *
     * @param target the service target
     * @param bldr the service builder used to add dependencies
     */
    public void addDependencies(ServiceTarget target, ServiceBuilder<MonitorService> bldr) {
        if (processType.isManagedDomain()) {
            // we are in the host controller
            // NOTE: host controller does not yet have an equivalent for ServerEnvironment, we workaround this later
            bldr.addDependency(DomainModelControllerService.SERVICE_NAME, ModelController.class, modelControllerValue);
        } else {
            // we are in standalone mode
            bldr.addDependency(ServerEnvironmentService.SERVICE_NAME, ServerEnvironment.class, serverEnvironmentValue);
            bldr.addDependency(Services.JBOSS_SERVER_CONTROLLER, ModelController.class, modelControllerValue);
        }
        bldr.addDependency(ControlledProcessStateService.SERVICE_NAME, ControlledProcessStateService.class,
                processStateValue);

        StorageAdapterConfiguration storageAdapterConfig = this.bootConfiguration.getStorageAdapter();

        // if the URL is not explicitly defined, we need some dependencies to help us build it
        if (storageAdapterConfig.getUrl() == null || storageAdapterConfig.getUrl().isEmpty()) {
            if (storageAdapterConfig.getServerOutboundSocketBindingRef() == null ||
                    storageAdapterConfig.getServerOutboundSocketBindingRef().isEmpty()) {
                // The outbound binding isn't given, so we'll assume we are co-located with the server.
                // In this case, we need our own http/https binding so we know what our local server is bound to.
                // Note that this is an invalid configuration if we are in host controller, so error out in that case
                if (processType.isManagedDomain()) {
                    throw new IllegalStateException("Do not know where the external Hawkular server is. Aborting.");
                }
                bldr.addDependency(SocketBinding.JBOSS_BINDING_NAME.append("http"), SocketBinding.class,
                        httpSocketBindingValue);
                bldr.addDependency(SocketBinding.JBOSS_BINDING_NAME.append("https"), SocketBinding.class,
                        httpsSocketBindingValue);
            } else {
                // TODO: broken when deployed in host controller. see https://issues.jboss.org/browse/WFCORE-1505
                // When that is fixed, remove this if-statement entirely.
                if (processType.isManagedDomain()) {
                    throw new IllegalStateException("When deployed in host controller, you must use the URL attribute"
                            + " and not the outbound socket binding. "
                            + "See bug https://issues.jboss.org/browse/WFCORE-1505 for more.");
                }
                bldr.addDependency(OutboundSocketBinding.OUTBOUND_SOCKET_BINDING_BASE_SERVICE_NAME
                        .append(storageAdapterConfig.getServerOutboundSocketBindingRef()),
                        OutboundSocketBinding.class, serverOutboundSocketBindingValue);
            }
        }

        // get the security realm ssl context for the storage adapter
        if (storageAdapterConfig.getSecurityRealm() != null) {
            InjectedValue<SSLContext> iv = new InjectedValue<>();
            trustOnlySSLContextInjectedValues.put(storageAdapterConfig.getSecurityRealm(), iv);

            // if we ever need our own private key, we can add another dependency with trustStoreOnly=false
            boolean trustStoreOnly = true;
            SSLContextService.ServiceUtil.addDependency(
                    bldr,
                    iv,
                    SecurityRealm.ServiceUtil.createServiceName(storageAdapterConfig.getSecurityRealm()),
                    trustStoreOnly);
        }

        // get the security realms for any configured remote servers that require ssl
        for (EndpointConfiguration endpoint : bootConfiguration.getDmrConfiguration().getEndpoints().values()) {
            String securityRealm = endpoint.getSecurityRealm();
            if (securityRealm != null) {
                addSslContext(securityRealm, bldr);
            }
        }
        for (EndpointConfiguration endpoint : bootConfiguration.getJmxConfiguration().getEndpoints().values()) {
            String securityRealm = endpoint.getSecurityRealm();
            if (securityRealm != null) {
                addSslContext(securityRealm, bldr);
            }
        }

        // bind the API to JNDI so other apps can use it, and prepare to build the binder service
        // Note that if we are running in host controller or similiar, JNDI binding is not available.
        String jndiName = bootConfiguration.getGlobalConfiguration().getApiJndi();
        boolean bindJndi = (jndiName == null || jndiName.isEmpty() || processType.isManagedDomain()) ? false : true;
        if (bindJndi) {
            class JndiBindListener extends AbstractServiceListener<Object> {
                private final String jndiName;
                private final String jndiObjectClassName;

                public JndiBindListener(String jndiName, String jndiObjectClassName) {
                    this.jndiName = jndiName;
                    this.jndiObjectClassName = jndiObjectClassName;
                }

                @Override
                public void transition(final ServiceController<? extends Object> controller,
                        final ServiceController.Transition transition) {
                    switch (transition) {
                        case STARTING_to_UP: {
                            log.infoBindJndiResource(jndiName, jndiObjectClassName);
                            break;
                        }
                        case START_REQUESTED_to_DOWN: {
                            log.infoUnbindJndiResource(jndiName);
                            break;
                        }
                        case REMOVING_to_REMOVED: {
                            log.infoUnbindJndiResource(jndiName);
                            break;
                        }
                        default:
                            break;
                    }
                }
            }
            Object jndiObject = getHawkularAgentContext();
            ContextNames.BindInfo bindInfo = ContextNames.bindInfoFor(jndiName);
            BinderService binderService = new BinderService(bindInfo.getBindName());
            Injector<ManagedReferenceFactory> managedObjectInjector = WildflyCompatibilityUtils
                    .getManagedObjectInjectorFromBinderService(binderService);
            Injector<ServiceBasedNamingStore> namingStoreInjector = WildflyCompatibilityUtils
                    .getNamingStoreInjectorFromBinderService(binderService);
            ManagedReferenceFactory valueMRF = WildflyCompatibilityUtils
                    .getImmediateManagedReferenceFactory(jndiObject);
            String jndiObjectClassName = HawkularAgentContext.class.getName();
            ServiceName binderServiceName = bindInfo.getBinderServiceName();
            ServiceBuilder<?> binderBuilder = target
                    .addService(binderServiceName, binderService)
                    .addInjection(managedObjectInjector, valueMRF)
                    .setInitialMode(ServiceController.Mode.ACTIVE)
                    .addDependency(bindInfo.getParentContextServiceName(),
                            ServiceBasedNamingStore.class,
                            namingStoreInjector)
                    .addListener(new JndiBindListener(jndiName, jndiObjectClassName));
            // our monitor service will depend on the binder service
            bldr.addDependency(binderServiceName);

            // install the binder service
            binderBuilder.install();
        }

        return; // deps added
    }

    public void removeInstalledServices(OperationContext context) {
        String jndiName = bootConfiguration.getGlobalConfiguration().getApiJndi();
        boolean bindJndi = (jndiName == null || jndiName.isEmpty() || processType.isManagedDomain()) ? false : true;
        if (bindJndi) {
            ContextNames.BindInfo bindInfo = ContextNames.bindInfoFor(jndiName);
            context.removeService(bindInfo.getBinderServiceName());
        }

    }

    private void addSslContext(String securityRealm, ServiceBuilder<MonitorService> bldr) {
        if (securityRealm != null && !this.trustOnlySSLContextInjectedValues.containsKey(securityRealm)) {
            // if we haven't added a dependency on the security realm yet, add it now
            InjectedValue<SSLContext> iv = new InjectedValue<>();
            this.trustOnlySSLContextInjectedValues.put(securityRealm, iv);

            boolean trustStoreOnly = true;
            SSLContextService.ServiceUtil.addDependency(
                    bldr,
                    iv,
                    SecurityRealm.ServiceUtil.createServiceName(securityRealm),
                    trustStoreOnly);
        }
    }

    @Override
    public void start(final StartContext startContext) throws StartException {
        final AtomicReference<Thread> startThread = new AtomicReference<Thread>();

        class CustomPropertyChangeListener implements PropertyChangeListener {
            @Override
            public void propertyChange(PropertyChangeEvent evt) {
                if (isRunning(evt.getNewValue())) {
                    startNow();
                } else if (ControlledProcessState.State.STOPPING.equals(evt.getNewValue())) {
                    Thread oldThread = startThread.get();
                    if (oldThread != null) {
                        oldThread.interrupt();
                    }
                }
            }

            private void startNow() {
                // see HWKAGENT-74 for why we need to do this in a separate thread
                Thread newThread = new Thread(new Runnable() {
                    @Override
                    public void run() {
                        try {
                            startHawkularAgent();
                        } catch (Throwable t) {
                            log.debug("Agent start thread aborted within start method", t);
                        }
                    }
                }, "Hawkular WildFly Agent Startup Thread");
                newThread.setDaemon(true);

                Thread oldThread = startThread.getAndSet(newThread);
                if (oldThread != null) {
                    oldThread.interrupt();
                }

                newThread.start();
            }

            private boolean isRunning(Object state) {
                return state == State.RUNNING || state == State.RELOAD_REQUIRED || state == State.RESTART_REQUIRED;
            }

        }

        ServiceContainer serviceContainer = startContext.getController().getServiceContainer();
        for (String realmName : trustOnlySSLContextInjectedValues.keySet()) {
            ServiceName sn = SSLContextService.ServiceUtil.createServiceName(
                    SecurityRealmService.ServiceUtil.createServiceName(realmName),
                    true);
            SSLContextService sslContextService = (SSLContextService) (serviceContainer.getRequiredService(sn)
                    .getService());
            trustOnlyTrustManagersInjectedValue.put(realmName, sslContextService.getTrustManagerInjector());
        }

        // deferred startup: must wait for server to be running before we can monitor the subsystems
        ControlledProcessStateService stateService = processStateValue.getValue();
        CustomPropertyChangeListener listener = new CustomPropertyChangeListener();
        serverStateListener = listener;
        stateService.addPropertyChangeListener(serverStateListener);
        // if the server is already started, we need to restart now. Otherwise, we'll start when the
        // server tells us it is running in our change listener above.
        if (listener.isRunning(stateService.getCurrentState())) {
            listener.startNow();
        }
    }

    @Override
    public void stop(StopContext stopContext) {
        stopHawkularAgent();
    }

    @Override
    protected AgentCoreEngineConfiguration loadRuntimeConfiguration(AgentCoreEngineConfiguration config) {
        return buildRuntimeConfiguration(
                config,
                this.httpSocketBindingValue,
                this.httpsSocketBindingValue,
                this.serverOutboundSocketBindingValue);
    }

    @Override
    protected ModelControllerClientFactory buildLocalModelControllerClientFactory() {
        return ModelControllerClientFactory.createLocal(modelControllerValue.getValue());
    }

    @Override
    protected Map<String, SSLContext> buildTrustOnlySSLContextValues(AgentCoreEngineConfiguration config) {
        Map<String, SSLContext> map = new HashMap<>(this.trustOnlySSLContextInjectedValues.size());
        for (Map.Entry<String, InjectedValue<SSLContext>> entry : this.trustOnlySSLContextInjectedValues.entrySet()) {
            map.put(entry.getKey(), entry.getValue().getOptionalValue());
        }
        return map;
    }

    @Override
    protected Map<String, TrustManager[]> buildTrustOnlyTrustManagersValues(AgentCoreEngineConfiguration config) {
        Map<String, TrustManager[]> map = new HashMap<>(this.trustOnlyTrustManagersInjectedValue.size());
        for (Map.Entry<String, InjectedValue<TrustManager[]>> entry : this.trustOnlyTrustManagersInjectedValue
                .entrySet()) {
            map.put(entry.getKey(), entry.getValue().getOptionalValue());
        }
        return map;
    }

    @Override
    protected void cleanupDuringStop() {
        // cleanup the state listener
        if (serverStateListener != null) {
            processStateValue.getValue().removePropertyChangeListener(serverStateListener);
            serverStateListener = null;
        }
    }

    @Override
    protected String autoGenerateFeedId() throws Exception {
        ModelControllerClientFactory clientFactory = buildLocalModelControllerClientFactory();
        if (clientFactory != null) {
            try (ModelControllerClient c = clientFactory.createClient()) {
                return DMREndpointService.lookupServerIdentifier(c);
            } catch (Exception e) {
                throw new Exception("Could not obtain local feed ID", e);
            }
        } else {
            throw new IllegalStateException("Not running in a container where feed ID can be determined - "
                    + "you must set the feed ID explicitly in the configuration");
        }
    }

    @Override
    protected Map<String, Class<? extends Command<? extends BasicMessage, ? extends BasicMessage>>> //
            buildAdditionalCommands() {
        return Collections.singletonMap(UpdateCollectionIntervalsCommand.REQUEST_CLASS.getName(),
                UpdateCollectionIntervalsCommand.class);
    }

}
