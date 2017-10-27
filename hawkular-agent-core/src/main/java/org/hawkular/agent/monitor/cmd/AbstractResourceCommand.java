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
package org.hawkular.agent.monitor.cmd;

import org.hawkular.agent.monitor.config.AgentCoreEngineConfiguration.AbstractEndpointConfiguration;
import org.hawkular.agent.monitor.inventory.MonitoredEndpoint;
import org.hawkular.bus.common.BasicMessageWithExtraData;
import org.hawkular.cmdgw.api.ResourceRequest;
import org.hawkular.cmdgw.api.ResourceResponse;
import org.hawkular.cmdgw.api.ResponseStatus;

public abstract class AbstractResourceCommand<REQ extends ResourceRequest, RESP extends ResourceResponse>
        implements Command<REQ, RESP> {

    /**
     * A natural language name of the operation the present command is performing, such as {@code Updade}, {@code Add}
     * or {@code Remove}. {@link #operationName} is supposed to be used in log and exception messages primarily.
     */
    private final String operationName;

    /**
     * A natural language name of entity the present command is creating, removing or updating. {@link #entityType} is
     * supposed to be used in log and exception messages primarily. This field should have values like
     * {@code "Datasource"}, {@code "JDBC Driver"} or similar.
     */
    private final String entityType;

    public AbstractResourceCommand(String operationName, String entityType) {
        this.operationName = operationName;
        this.entityType = entityType;
    }

    protected String getOperationName(BasicMessageWithExtraData<REQ> envelope) {
        return this.operationName;
    }

    protected String getEntityType(BasicMessageWithExtraData<REQ> envelope) {
        return entityType;
    }

    /**
     * Checks if the {@code request} has {@code resourcePath} field set. Subclasses may want to add more {@code request}
     * validations in their overrides.
     *
     * @param envelope the request to validate
     */
    protected void validate(BasicMessageWithExtraData<REQ> envelope) {
        if (envelope.getBasicMessage().getResourceId() == null) {
            throw new IllegalArgumentException(
                    String.format("resourceId of a [%s] cannot be null", envelope.getClass().getName()));
        }
    }

    /**
     * Validation for subclasses.
     *
     * @param envelope message to check
     * @param endpoint the request the {@code modelNodePath} comes from
     */
    protected abstract void validate(BasicMessageWithExtraData<REQ> envelope,
            MonitoredEndpoint<? extends AbstractEndpointConfiguration> endpoint);

    /**
     * @return a new instance of the appropriate {@link ResourceResponse} subclass
     */
    protected abstract RESP createResponse();

    protected void success(BasicMessageWithExtraData<REQ> envelope, RESP response) {
        response.setStatus(ResponseStatus.OK);
        String msg = String.format("Performed [%s] on a [%s] given by Feed Id [%s] Resource Id [%s]",
                this.getOperationName(envelope), entityType, envelope.getBasicMessage().getFeedId(),
                envelope.getBasicMessage().getResourceId());

        String innerMessage = response.getMessage();
        if (innerMessage != null) {
            msg = msg + ": " + innerMessage;
        }
        response.setMessage(msg);

    }

    protected void assertLocalDMRServer(MonitoredEndpoint<? extends AbstractEndpointConfiguration> endpoint) {
        if (!endpoint.isLocal()) {

            // TODO: Until the java agent can actually talk to a local DMR client,
            // we have to allow this if we think we are running in a WildFly/EAP container
            // and we guess at this if there is a env var or system property that WildFly defines.

            String envVar = "JBOSS_HOME_ENV_VAR";
            String sysProp = "jboss.home.dir";

            // check the env var which is set by the WildFly boot scripts
            String envVarValue = System.getenv(envVar);
            if (envVarValue != null) {
                return;
            }

            // no env var but check system property, it may be set from a client using the CLI API to execute commands
            String sysPropValue = System.getProperty(sysProp, null);
            if (sysPropValue != null) {
                return;
            }

            throw new IllegalStateException(String.format(
                    "Cannot perform [%s] on a [%s] on a non local instance of [%s].", operationName,
                    entityType, endpoint.getName()));
        }
    }

    /**
     * If the command may permanently modify the managed resource, this method should return true which means the command
     * will be aborted if the agent is configured to be immutable. This method implementation always returns true.
     * Subclasses are free to override this and return false if the command never modifies the managed resource.
     *
     * @return true if the operation may modify something on the managed resource permanently
     */
    protected boolean modifiesResource() {
        return true;
    }

}