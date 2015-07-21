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
package org.hawkular.agent.monitor.log;

import java.util.List;

import org.hawkular.agent.monitor.scheduler.config.MonitoredEndpoint;
import org.jboss.logging.BasicLogger;
import org.jboss.logging.Logger;
import org.jboss.logging.Logger.Level;
import org.jboss.logging.annotations.Cause;
import org.jboss.logging.annotations.LogMessage;
import org.jboss.logging.annotations.Message;
import org.jboss.logging.annotations.MessageLogger;
import org.jboss.logging.annotations.ValidIdRange;

@MessageLogger(projectCode = "HAWKMONITOR")
@ValidIdRange(min = 10000, max = 19999)
public interface MsgLogger extends BasicLogger {
    MsgLogger LOG = Logger.getMessageLogger(MsgLogger.class, "org.hawkular.agent.monitor");

    @LogMessage(level = Level.INFO)
    @Message(id = 10000, value = "Starting Hawkular Monitor service")
    void infoStarting();

    @LogMessage(level = Level.INFO)
    @Message(id = 10001, value = "Stopping Hawkular Monitor service")
    void infoStopping();

    @LogMessage(level = Level.INFO)
    @Message(id = 10002, value = "Hawkular Monitor subsystem is disabled; service will not be started")
    void infoSubsystemDisabled();

    @LogMessage(level = Level.INFO)
    @Message(id = 10003, value = "JNDI binding [%s]: bound to object of type [%s]")
    void infoBindJndiResource(String jndiName, String objectTypeName);

    @LogMessage(level = Level.INFO)
    @Message(id = 10004, value = "JNDI binding [%s]: unbound")
    void infoUnbindJndiResource(String jndiName);

    @LogMessage(level = Level.INFO)
    @Message(id = 10005, value = "No diagnostics configuration - diagnostics will be disabled")
    void infoNoDiagnosticsConfig();

    @LogMessage(level = Level.INFO)
    @Message(id = 10006, value = "There are no enabled metric sets")
    void infoNoEnabledMetricsConfigured();

    @LogMessage(level = Level.INFO)
    @Message(id = 10007, value = "There are no enabled availability check sets")
    void infoNoEnabledAvailsConfigured();

    @LogMessage(level = Level.ERROR)
    @Message(id = 10008, value = "A metric collection failed")
    void errorMetricCollectionFailed(@Cause Throwable t);

    @LogMessage(level = Level.ERROR)
    @Message(id = 10009, value = "An availability check failed")
    void errorAvailCheckFailed(@Cause Throwable t);

    @LogMessage(level = Level.ERROR)
    @Message(id = 10010, value = "Failed to store metric data: %s")
    void errorFailedToStoreMetricData(@Cause Throwable t, String data);

    @LogMessage(level = Level.ERROR)
    @Message(id = 10011, value = "Failed to store avail data: %s")
    void errorFailedToStoreAvailData(@Cause Throwable t, String data);

    @LogMessage(level = Level.INFO)
    @Message(id = 10012, value = "Starting scheduler")
    void infoStartingScheduler();

    @LogMessage(level = Level.INFO)
    @Message(id = 10013, value = "Stopping scheduler")
    void infoStoppingScheduler();

    @LogMessage(level = Level.WARN)
    @Message(id = 10014, value = "Batch operation requested [%d] values but received [%d]")
    void warnBatchResultsDoNotMatchRequests(int expectedCound, int actualCount);

    @LogMessage(level = Level.WARN)
    @Message(id = 10015, value = "Comma in name! This will interfere with comma-separators in lists. [%s]")
    void warnCommaInName(String name);

    @LogMessage(level = Level.WARN)
    @Message(id = 10016, value = "The resource type [%s] wants to use an unknown metric set [%s]")
    void warnMetricSetDoesNotExist(String resourceTypeName, String metricSetName);

    @LogMessage(level = Level.WARN)
    @Message(id = 10017, value = "The resource type [%s] wants to use an unknown avail set [%s]")
    void warnAvailSetDoesNotExist(String resourceTypeName, String availSetName);

    @LogMessage(level = Level.WARN)
    @Message(id = 10018, value = "Cannot obtain server identifiers for [%s]: %s")
    void warnCannotObtainServerIdentifiersForDMREndpoint(String endpoint, String errorString);

    @LogMessage(level = Level.INFO)
    @Message(id = 10019, value = "Managed server [%s] is disabled. It will not be monitored.")
    void infoManagedServerDisabled(String name);

    @LogMessage(level = Level.WARN)
    @Message(id = 10020, value = "The managed server [%s] wants to use an unknown resource type set [%s]")
    void warnResourceTypeSetDoesNotExist(String managedServerName, String resourceTypeSetName);

    @LogMessage(level = Level.INFO)
    @Message(id = 10021, value = "There are no enabled resource type sets")
    void infoNoEnabledResourceTypesConfigured();

    @LogMessage(level = Level.INFO)
    @Message(id = 10022, value = "Resource type [%s] is disabled - all if its child types will also be disabled: %s")
    void infoDisablingResourceTypes(Object disabledType, List<?> toBeDisabled);

    @LogMessage(level = Level.ERROR)
    @Message(id = 10023, value = "Discovery failed while probing endpoint [%s]")
    void errorDiscoveryFailed(@Cause Exception e, MonitoredEndpoint endpoint);

    @LogMessage(level = Level.ERROR)
    @Message(id = 10024, value = "Failed to store inventory data")
    void errorFailedToStoreInventoryData(@Cause Throwable t);

    @LogMessage(level = Level.INFO)
    @Message(id = 10025, value = "Will talk to Hawkular at URL [%s]")
    void infoUsingServerSideUrl(String url);

    @LogMessage(level = Level.ERROR)
    @Message(id = 10026, value = "Can't do anything without a feed; aborting startup")
    void errorCannotDoAnythingWithoutFeed(@Cause Throwable t);

    @LogMessage(level = Level.ERROR)
    @Message(id = 10027, value = "To use standalone Hawkular Metrics, you must configure a tenant ID")
    void errorMustHaveTenantIdConfigured();

    @LogMessage(level = Level.ERROR)
    @Message(id = 10028, value = "Cannot start storage adapter; aborting startup")
    void errorCannotStartStorageAdapter(@Cause Throwable t);

    @LogMessage(level = Level.ERROR)
    @Message(id = 10029, value = "Scheduler failed to initialize; aborting startup")
    void errorCannotInitializeScheduler(@Cause Throwable t);

    @LogMessage(level = Level.INFO)
    @Message(id = 10030, value = "Using keystore at [%s]")
    void infoUseKeystore(String keystorePath);

    @LogMessage(level = Level.INFO)
    @Message(id = 10031, value = "The storage adapter URL is explicitly specified [%s], so useSSL will be set to [%s]")
    void infoUsingSSL(String url, boolean useSSL);

    @LogMessage(level = Logger.Level.ERROR)
    @Message(id = 10032, value = "Server provided an invalid command request: [%s]")
    void errorInvalidCommandRequestFeed(String requestClassName);

    @LogMessage(level = Logger.Level.ERROR)
    @Message(id = 10033, value = "Failed to execute command [%s] for server")
    void errorCommandExecutionFailureFeed(String requestClassName, @Cause Throwable t);

    @LogMessage(level = Logger.Level.INFO)
    @Message(id = 10034, value = "Feed communications channel to the server has been opened")
    void infoOpenedFeedComm();

    @LogMessage(level = Logger.Level.INFO)
    @Message(id = 10035, value = "Feed communications channel to the server has been closed. Code=[%d], Reason=[%s]")
    void infoClosedFeedComm(int reasonCode, String reason);

    @LogMessage(level = Logger.Level.WARN)
    @Message(id = 10036, value = "Feed communications channel encountered a failure. Response=[%s]")
    void warnFeedCommFailure(String response, @Cause Exception e);

    @LogMessage(level = Logger.Level.ERROR)
    @Message(id = 10037, value = "Failed to send message [%s] over the feed communications channel")
    void errorFailedToSendOverFeedComm(String command, @Cause Throwable t);

    @LogMessage(level = Logger.Level.WARN)
    @Message(id = 10038, value = "Failed to close web socket with code=[%d] and reason=[%s]")
    void warnFailedToCloseWebSocket(int code, String reason, @Cause Exception e);

    @LogMessage(level = Logger.Level.INFO)
    @Message(id = 10039, value = "The feed-comm URL is [%s]")
    void infoFeedCommUrl(String feedcommUrl);

    @LogMessage(level = Logger.Level.ERROR)
    @Message(id = 10040, value = "Cannot close feed-comm processor")
    void errorCannotCloseCommProcessor(@Cause Exception e);

    @LogMessage(level = Logger.Level.ERROR)
    @Message(id = 10041, value = "Cannot close feed-comm websocket")
    void errorCannotCloseWebSocketCall(@Cause Exception e);

    @LogMessage(level = Logger.Level.ERROR)
    @Message(id = 10042, value = "Cannot connect to the server over the feed communications channel.")
    void errorCannotEstablishFeedComm(@Cause Exception e);

}
