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

import java.util.ArrayList;
import java.util.List;

import org.hawkular.agent.monitor.config.AgentCoreEngineConfiguration;
import org.hawkular.agent.monitor.service.AgentCoreEngine;
import org.hawkular.bus.common.BasicMessage;
import org.hawkular.bus.common.BasicMessageWithExtraData;

public class CommandContext {

    /**
     * This listener is called after the command's response has been sent.
     */
    public interface ResponseSentListener {
        void onSend(BasicMessageWithExtraData<? extends BasicMessage> response, Exception sendError);
    }

    private final FeedCommProcessor feedComm;
    private final AgentCoreEngine agentCoreEngine;
    private final List<ResponseSentListener> sentListeners;

    public CommandContext(
            FeedCommProcessor feedCommProcessor,
            AgentCoreEngine agentCoreEngine) {
        this.feedComm = feedCommProcessor;
        this.agentCoreEngine = agentCoreEngine;
        this.sentListeners = new ArrayList<>(0);
    }

    /**
     * @return the object that can be used to send data back to the server
     */
    public FeedCommProcessor getFeedCommProcessor() {
        return feedComm;
    }

    public AgentCoreEngineConfiguration getAgentCoreEngineConfiguration() {
        return agentCoreEngine.getConfiguration();
    }

    public AgentCoreEngine getAgentCoreEngine() {
        return agentCoreEngine;
    }

    public void addResponseSentListener(ResponseSentListener listener) {
        this.sentListeners.add(listener);
    }

    List<ResponseSentListener> getResponseSentListeners() {
        return this.sentListeners;
    }
}
