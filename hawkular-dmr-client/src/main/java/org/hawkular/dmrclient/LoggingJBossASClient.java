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
package org.hawkular.dmrclient;

import org.jboss.as.controller.client.ModelControllerClient;
import org.jboss.dmr.ModelNode;

/**
 * Provides management of the logging subsystem.
 *
 * @author John Mazzitelli
 */
public class LoggingJBossASClient extends JBossASClient {

    public static final String LOGGING = "logging";
    public static final String LOGGER = "logger";
    public static final String FILE_HANDLER = "periodic-rotating-file-handler";

    public LoggingJBossASClient(ModelControllerClient client) {
        super(client);
    }

    /**
     * Checks to see if there is already a logger with the given name.
     *
     * @param loggerName the name to check (this is also known as the category name)
     * @return true if there is a logger/category with the given name already in existence
     */
    public boolean isLogger(String loggerName) throws Exception {
        Address addr = Address.root().add(SUBSYSTEM, LOGGING, LOGGER, loggerName);
        return null != readResource(addr);
    }

    /**
     * Returns the level of the given logger.
     *
     * @param loggerName the name of the logger (this is also known as the category name)
     * @return level of the logger
     * @throws Exception if the log level could not be obtained (typically because the logger doesn't exist)
     */
    public String getLoggerLevel(String loggerName) throws Exception {
        Address addr = Address.root().add(SUBSYSTEM, LOGGING, LOGGER, loggerName);
        return getStringAttribute("level", addr);
    }

    /**
     * Sets the logger to the given level.
     * If the logger does not exist yet, it will be created.
     *
     * @param loggerName the logger name (this is also known as the category name)
     * @param level the new level of the logger (e.g. DEBUG, INFO, ERROR, etc.)
     * @throws Exception
     */
    public void setLoggerLevel(String loggerName, String level) throws Exception {

        final Address addr = Address.root().add(SUBSYSTEM, LOGGING, LOGGER, loggerName);
        final ModelNode request;

        if (isLogger(loggerName)) {
            request = createWriteAttributeRequest("level", level, addr);
        } else {
            final String dmrTemplate = "" //
                + "{" //
                + "\"category\" => \"%s\" " //
                + ", \"level\" => \"%s\" " //
                + ", \"use-parent-handlers\" => \"true\" " //
                + "}";
            final String dmr = String.format(dmrTemplate, loggerName, level);

            request = ModelNode.fromString(dmr);
            request.get(OPERATION).set(ADD);
            request.get(ADDRESS).set(addr.getAddressNode());
        }

        final ModelNode response = execute(request);
        if (!isSuccess(response)) {
            throw new FailureException(response);
        }
        return;
    }

    public void setFilterSpec(String filterSpec) throws Exception {

        final Address addr = Address.root().add(SUBSYSTEM, LOGGING, FILE_HANDLER, "FILE");
        final ModelNode request;

        request = createWriteAttributeRequest("filter-spec", filterSpec, addr);

        final ModelNode response = execute(request);
        if (!isSuccess(response)) {
            throw new FailureException(response);
        }
        return;
    }

}
