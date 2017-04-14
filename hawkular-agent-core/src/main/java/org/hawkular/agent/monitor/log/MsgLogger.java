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
package org.hawkular.agent.monitor.log;

import java.util.Collection;
import java.util.List;

import org.hawkular.agent.monitor.inventory.MonitoredEndpoint;
import org.hawkular.agent.monitor.inventory.Name;
import org.hawkular.agent.monitor.protocol.EndpointService;
import org.hawkular.agent.monitor.protocol.ProtocolException;
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
    @Message(id = 10000, value = "Starting Hawkular Agent")
    void infoStarting();

    @LogMessage(level = Level.INFO)
    @Message(id = 10001, value = "Stopping Hawkular Agent")
    void infoStopping();

    @LogMessage(level = Level.INFO)
    @Message(id = 10002, value = "Hawkular Agent is disabled - it will not be started")
    void infoAgentDisabled();

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
    @Message(id = 10016, value = "The resource type [%s] wants to use an unknown metric set [%s]")
    void warnMetricSetDoesNotExist(String resourceTypeName, String metricSetName);

    @LogMessage(level = Level.WARN)
    @Message(id = 10017, value = "The resource type [%s] wants to use an unknown avail set [%s]")
    void warnAvailSetDoesNotExist(String resourceTypeName, String availSetName);

    @LogMessage(level = Level.INFO)
    @Message(id = 10019, value = "Managed server [%s] is disabled. It will not be monitored.")
    void infoManagedServerDisabled(String name);

    @LogMessage(level = Level.WARN)
    @Message(id = 10020, value = "The managed server [%s] wants to use an unknown resource type set [%s]")
    void warnResourceTypeSetDoesNotExist(String managedServerName, String resourceTypeSetName);

    @LogMessage(level = Level.INFO)
    @Message(id = 10022, value = "Resource type [%s] is disabled - all if its child types will also be disabled: %s")
    void infoDisablingResourceTypes(Object disabledType, List<?> toBeDisabled);

    @LogMessage(level = Level.ERROR)
    @Message(id = 10024, value = "Failed to store inventory data")
    void errorFailedToStoreInventoryData(@Cause Throwable t);

    @LogMessage(level = Level.ERROR)
    @Message(id = 10028, value = "Cannot start storage adapter; aborting startup")
    void errorCannotStartStorageAdapter(@Cause Throwable t);

    @LogMessage(level = Level.ERROR)
    @Message(id = 10029, value = "Scheduler failed to initialize; aborting startup")
    void errorCannotInitializeScheduler(@Cause Throwable t);

    @LogMessage(level = Level.INFO)
    @Message(id = 10030, value = "Using keystore at [%s]")
    void infoUseKeystore(String keystorePath);

    @LogMessage(level = Logger.Level.ERROR)
    @Message(id = 10032, value = "Server provided an invalid command request: [%s]")
    void errorInvalidCommandRequestFeed(String requestClassName);

    @LogMessage(level = Logger.Level.ERROR)
    @Message(id = 10033, value = "Failed to execute command [%s] for server")
    void errorCommandExecutionFailureFeed(String requestClassName, @Cause Throwable t);

    @LogMessage(level = Logger.Level.INFO)
    @Message(id = 10034, value = "Opened feed WebSocket connection to endpoint [%s]")
    void infoOpenedFeedComm(String endpoint);

    @LogMessage(level = Logger.Level.INFO)
    @Message(id = 10035, value = "Closed feed WebSocket connection to endpoint [%s]. Code=[%d], Reason=[%s]")
    void infoClosedFeedComm(String endpoint, int reasonCode, String reason);

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
    @Message(id = 10039, value = "The command-gateway URL is [%s]")
    void infoFeedCommUrl(String feedcommUrl);

    @LogMessage(level = Logger.Level.ERROR)
    @Message(id = 10040, value = "Cannot re-establish websocket connection")
    void errorCannotReconnectToWebSocket(@Cause Exception e);

    @LogMessage(level = Logger.Level.ERROR)
    @Message(id = 10041, value = "Cannot close command-gateway websocket")
    void errorCannotCloseWebSocketCall(@Cause Exception e);

    @LogMessage(level = Logger.Level.ERROR)
    @Message(id = 10042, value = "Cannot connect to the server over the feed communications channel.")
    void errorCannotEstablishFeedComm(@Cause Exception e);

    @LogMessage(level = Logger.Level.WARN)
    @Message(id = 10043, value = "Received the following error message and stack trace from server: %s\n%s")
    void warnReceivedGenericErrorResponse(String errorMessage, String stackTrace);

    @LogMessage(level = Level.WARN)
    @Message(id = 10047, value = "Failed to locate [%s] at location [%s] relative to [%s]")
    void warnFailedToLocate(@Cause ProtocolException e, String typeName, String location, String parentLocation);

    @LogMessage(level = Level.ERROR)
    @Message(id = 10049, value = "Could not access resources of endpoint [%s]")
    void errorCouldNotAccess(EndpointService<?, ?> endpoint, @Cause Throwable e);

    @LogMessage(level = Level.ERROR)
    @Message(id = 10051, value = "Missing tenant ID")
    void errorNoTenantIdSpecified();

    @LogMessage(level = Level.ERROR)
    @Message(id = 10052, value = "Could not store metrics for monitored endpoint [%s]")
    void errorFailedToStoreMetrics(String endpoint, @Cause Throwable t);

    @LogMessage(level = Level.ERROR)
    @Message(id = 10053, value = "Could not store availability data for monitored endpoint [%s]")
    void errorFailedToStoreAvails(String endpoint, @Cause Throwable t);

    @LogMessage(level = Level.ERROR)
    @Message(id = 10054, value = "Agent encountered errors during start up and will be stopped.")
    void errorFailedToStartAgent(@Cause Throwable t);

    @LogMessage(level = Level.WARN)
    @Message(id = 10055, value = "Agent encountered errors during shutdown")
    void warnFailedToStopAgent(@Cause Throwable t);

    @LogMessage(level = Level.INFO)
    @Message(id = 10056, value = "Periodic auto-discovery scans have been disabled")
    void infoAutoDiscoveryDisabled();

    @LogMessage(level = Level.INFO)
    @Message(id = 10057, value = "Auto-discovery scans will be performed every [%d] seconds")
    void infoAutoDiscoveryEnabled(int periodSeconds);

    @LogMessage(level = Level.WARN)
    @Message(id = 10058, value = "Auto-discovery scan failed")
    void errorAutoDiscoveryFailed(@Cause Throwable t);

    @LogMessage(level = Level.ERROR)
    @Message(id = 10060, value = "Could not close [%s]")
    void errorCannotClose(@Cause Throwable t, String name);

    @LogMessage(level = Level.ERROR)
    @Message(id = 10061, value = "Failed to discover resources in [%s]")
    void errorFailedToDiscoverResources(@Cause Throwable t, MonitoredEndpoint<?> endpoint);

    @LogMessage(level = Level.INFO)
    @Message(id = 10065, value = "Received request to perform [%s] on a [%s] given by inventory path [%s]")
    void infoReceivedResourcePathCommand(String operationName, String entityType, String resourcePath);

    @LogMessage(level = Level.DEBUG) // making DEBUG as this gets noisy if you run discovery often enough
    @Message(id = 10066, value = "Being asked to discover all resources for endpoint [%s]")
    void infoDiscoveryRequested(MonitoredEndpoint<?> monitoredEndpoint);

    @LogMessage(level = Level.INFO)
    @Message(id = 10068, value = "Agent is already stopped.")
    void infoStoppedAlready();

    @LogMessage(level = Level.INFO)
    @Message(id = 10071, value = "No longer monitoring the endpoint [%s]")
    void infoRemovedEndpointService(String id);

    @LogMessage(level = Level.INFO)
    @Message(id = 10073, value = "Now monitoring the new endpoint [%s]")
    void infoAddedEndpointService(String string);

    @LogMessage(level = Level.ERROR)
    @Message(id = 10074, value = "The resource type [%s] is missing a parent. "
            + "Make sure at least one of these resource types are defined and enabled: %s")
    void errorInvalidRootResourceType(String idString, Collection<Name> parents);

    @LogMessage(level = Level.ERROR)
    @Message(id = 10077, value = "Failed to store metric tags: %s")
    void errorFailedToStoreMetricTags(@Cause Throwable t, String data);

    @LogMessage(level = Level.WARN)
    @Message(id = 10078, value = "Tried %d times to reach the server %s endpoint at %s. Is it up?")
    void warnConnectionDelayed(int count, String what, String url);

    @LogMessage(level = Level.INFO)
    @Message(id = 10079, value = "Agent being asked to start when it is already starting up. Will wait.")
    void infoAlreadyStarting();

    @LogMessage(level = Level.WARN)
    @Message(id = 10081, value = "Cannot get Hawkular Server status - does the agent have proper credentials? (%d/%s)")
    void warnBadHawkularCredentials(int code, String message);

    @LogMessage(level = Level.INFO)
    @Message(id = 10082, value = "Agent being asked to start but is currently stopping. Will wait and then restart.")
    void infoAgentWillStartAfterStopping();
}