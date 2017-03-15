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
package org.hawkular.agent.wildfly.log;

import javax.management.MalformedObjectNameException;

import org.hawkular.agent.monitor.config.AgentCoreEngineConfiguration.StorageReportTo;
import org.jboss.logging.BasicLogger;
import org.jboss.logging.Logger;
import org.jboss.logging.Logger.Level;
import org.jboss.logging.annotations.Cause;
import org.jboss.logging.annotations.LogMessage;
import org.jboss.logging.annotations.Message;
import org.jboss.logging.annotations.MessageLogger;
import org.jboss.logging.annotations.ValidIdRange;

@MessageLogger(projectCode = "HAWKMONITOR")
@ValidIdRange(min = 20000, max = 29999)
public interface MsgLogger extends BasicLogger {
    MsgLogger LOG = Logger.getMessageLogger(MsgLogger.class, "org.hawkular.agent.monitor");

    @LogMessage(level = Level.INFO)
    @Message(id = 20002, value = "Hawkular WildFly Agent subsystem is disabled; service will not be started")
    void infoSubsystemDisabled();

    @LogMessage(level = Level.INFO)
    @Message(id = 20003, value = "JNDI binding [%s]: bound to object of type [%s]")
    void infoBindJndiResource(String jndiName, String objectTypeName);

    @LogMessage(level = Level.INFO)
    @Message(id = 20004, value = "JNDI binding [%s]: unbound")
    void infoUnbindJndiResource(String jndiName);

    @LogMessage(level = Level.INFO)
    @Message(id = 20005, value = "No diagnostics configuration - diagnostics will be disabled")
    void infoNoDiagnosticsConfig();

    @LogMessage(level = Level.INFO)
    @Message(id = 20006, value = "There are no enabled %s metric sets")
    void infoNoEnabledMetricsConfigured(String type);

    @LogMessage(level = Level.INFO)
    @Message(id = 20007, value = "There are no enabled %s availability check sets")
    void infoNoEnabledAvailsConfigured(String type);

    @LogMessage(level = Level.WARN)
    @Message(id = 20015, value = "Comma in name! This will interfere with comma-separators in lists. [%s]")
    void warnCommaInName(String name);

    @LogMessage(level = Level.INFO)
    @Message(id = 20021, value = "There are no enabled %s resource type sets")
    void infoNoEnabledResourceTypesConfigured(String type);

    @LogMessage(level = Level.INFO)
    @Message(id = 20025, value = "Will talk to Hawkular at URL [%s]")
    void infoUsingServerSideUrl(String url);

    @LogMessage(level = Level.INFO)
    @Message(id = 20031, value = "The storage adapter URL is explicitly specified [%s], so useSSL will be set to [%s]")
    void infoUsingSSL(String url, boolean useSSL);

    @LogMessage(level = Level.INFO)
    @Message(id = 20045, value = "No platform configuration - platform metrics will be disabled")
    void infoNoPlatformConfig();

    @LogMessage(level = Level.WARN)
    @Message(id = 20048, value = "Malformed JMX object name: [%s]")
    void warnMalformedJMXObjectName(String objectName, @Cause MalformedObjectNameException e);

    @LogMessage(level = Level.INFO)
    @Message(id = 20050, value = "Tenant ID [%s]")
    void infoTenantId(String tenantId);

    @LogMessage(level = Level.INFO)
    @Message(id = 20059, value = "Agent is using storage adapter mode [%s]")
    void infoStorageAdapterMode(StorageReportTo type);

    @LogMessage(level = Level.WARN)
    @Message(id = 20076, value = "Cannot register feed under tenant ID [%s] for new managed server [%s]: %s")
    void warnCannotRegisterFeedForNewManagedServer(String tenantId, String managedServerName, String error);

    @LogMessage(level = Level.INFO)
    @Message(id = 20080, value = "Agent has been configured to be immutable")
    void infoAgentIsImmutable();
}