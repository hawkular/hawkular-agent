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

import java.util.List;

import org.jboss.as.controller.client.ModelControllerClient;
import org.jboss.dmr.ModelNode;

/**
 * Provides convenience methods associated with JMS management.
 *
 * @author Jay Shaughnessy
 * @author John Mazzitelli
 */
public class MessagingJBossASClient extends JBossASClient {

    public static final String SUBSYSTEM_MESSAGING = "messaging";
    public static final String HORNETQ_SERVER = "hornetq-server";
    public static final String JMS_QUEUE = "jms-queue";

    public MessagingJBossASClient(ModelControllerClient client) {
        super(client);
    }

    /**
     * Checks to see if there is already a queue with the given name.
     *
     * @param queueName the name to check
     * @return true if there is a queue with the given name already in existence
     * @throws Exception any error
     */
    public boolean isQueue(String queueName) throws Exception {
        Address addr = Address.root().add(SUBSYSTEM, SUBSYSTEM_MESSAGING, HORNETQ_SERVER, "default");
        String haystack = JMS_QUEUE;
        return null != findNodeInList(addr, haystack, queueName);
    }

    /**
     * Returns a ModelNode that can be used to create a queue.
     * Callers are free to tweak the queue request that is returned,
     * if they so choose, before asking the client to execute the request.
     *
     * @param name the queue name
     * @param durable if null, default is "true"
     * @param entryNames the jndiNames, each is prefixed with 'java:/'.  Only supports one entry currently.
     *
     * @return the request that can be used to create the queue
     */
    public ModelNode createNewQueueRequest(String name, Boolean durable, List<String> entryNames) {

        String dmrTemplate = "" //
            + "{" //
            + "\"durable\" => \"%s\", " //
            + "\"entries\" => [\"%s\"] " //
            + "}";

        String dmr = String.format(dmrTemplate, ((null == durable) ? "true" : durable.toString()), entryNames.get(0));

        Address addr = Address.root().add(SUBSYSTEM, SUBSYSTEM_MESSAGING, HORNETQ_SERVER, "default", JMS_QUEUE, name);
        final ModelNode request = ModelNode.fromString(dmr);
        request.get(OPERATION).set(ADD);
        request.get(ADDRESS).set(addr.getAddressNode());

        return request;
    }

}
