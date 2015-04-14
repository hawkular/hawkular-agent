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

import org.jboss.logging.Logger;
import org.jboss.logging.Logger.Level;
import org.jboss.logging.annotations.Cause;
import org.jboss.logging.annotations.LogMessage;
import org.jboss.logging.annotations.Message;
import org.jboss.logging.annotations.MessageLogger;
import org.jboss.logging.annotations.ValidIdRange;

@MessageLogger(projectCode = "HAWKMONITOR")
@ValidIdRange(min = 10000, max = 19999)
public interface MsgLogger {
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
    @Message(id = 10016, value = "The managed resource [%s] wants to use an unknown metric set [%s]")
    void warnMetricSetDoesNotExist(String resourceName, String metricSetName);

    @LogMessage(level = Level.WARN)
    @Message(id = 10017, value = "The managed resource [%s] wants to use an unknown avail set [%s]")
    void warnAvailSetDoesNotExist(String resourceName, String availSetName);
}
