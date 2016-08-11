/*
 * Copyright 2015-2016 Red Hat, Inc. and/or its affiliates
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

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.jboss.as.controller.client.ModelControllerClient;
import org.jboss.as.controller.client.OperationMessageHandler;
import org.jboss.dmr.ModelNode;
import org.jboss.dmr.ModelType;
import org.jboss.dmr.ValueExpression;
import org.jboss.logging.Logger;

/**
 * A client that can be used to talk to a JBossAS/Wildfly server via the DMR/ModelControllerClient API.
 *
 * @author John Mazzitelli
 */
public class JBossASClient implements AutoCloseable {

    // protected to allow subclasses to have a logger, too, without explicitly declaring one themselves
    protected final Logger log = Logger.getLogger(this.getClass());

    public static final String BATCH = "composite";
    public static final String BATCH_STEPS = "steps";
    public static final String OPERATION = "operation";
    public static final String ADDRESS = "address";
    public static final String RESULT = "result";
    public static final String OUTCOME = "outcome";
    public static final String OUTCOME_SUCCESS = "success";
    public static final String SUBSYSTEM = "subsystem";
    public static final String FAILURE_DESCRIPTION = "failure-description";
    public static final String NAME = "name";
    public static final String VALUE = "value";
    public static final String READ_ATTRIBUTE = "read-attribute";
    public static final String READ_RESOURCE = "read-resource";
    public static final String WRITE_ATTRIBUTE = "write-attribute";
    public static final String ADD = "add";
    public static final String REMOVE = "remove";
    public static final String SYSTEM_PROPERTY = "system-property";
    public static final String PERSISTENT = "persistent"; // used by some operations to persist their effects

    private final ModelControllerClient client;

    /**
     * Constructs a new JBoss AS Client that talks to the model through the provided client.
     * Note that the caller is responsible to correctly close the client instance!!!
     *
     * @param client the client to use
     */
    public JBossASClient(ModelControllerClient client) {
        this.client = client;
    }

    /////////////////////////////////////////////////////////////////
    // Some static methods useful for convenience

    /**
     * Convienence method that allows you to create request that reads a single attribute
     * value to a resource.
     *
     * @param attributeName the name of the attribute whose value is to be read
     * @param address identifies the resource
     * @return the request
     */
    public static ModelNode createReadAttributeRequest(String attributeName, Address address) {
        return createReadAttributeRequest(false, attributeName, address);
    }

    /**
     * Convienence method that allows you to create request that reads a single attribute
     * value to a resource.
     *
     * @param runtime if <code>true</code>, the attribute is a runtime attribute
     * @param attributeName the name of the attribute whose value is to be read
     * @param address identifies the resource
     * @return the request
     */
    public static ModelNode createReadAttributeRequest(boolean runtime, String attributeName, Address address) {
        final ModelNode op = createRequest(READ_ATTRIBUTE, address);
        op.get("include-runtime").set(runtime);
        op.get(NAME).set(attributeName);
        return op;
    }

    /**
     * Convienence method that allows you to create request that writes a single attribute's
     * string value to a resource.
     *
     * @param attributeName the name of the attribute whose value is to be written
     * @param attributeValue the attribute value that is to be written
     * @param address identifies the resource
     * @return the request
     */
    public static ModelNode createWriteAttributeRequest(String attributeName, String attributeValue, Address address) {
        final ModelNode op = createRequest(WRITE_ATTRIBUTE, address);
        op.get(NAME).set(attributeName);
        setPossibleExpression(op, VALUE, attributeValue);
        return op;
    }

    /**
     * Convienence method that builds a partial operation request node.
     *
     * @param operation the operation to be requested
     * @param address identifies the target resource
     * @return the partial operation request node - caller should fill this in further to complete the node
     */
    public static ModelNode createRequest(String operation, Address address) {
        return createRequest(operation, address, null);
    }

    /**
     * Convienence method that builds a partial operation request node, with additional
     * node properties supplied by the given node.
     *
     * @param operation the operation to be requested
     * @param address identifies the target resource
     * @param extra provides additional properties to add to the returned request node.
     * @return the partial operation request node - caller can fill this in further to complete the node
     */
    public static ModelNode createRequest(String operation, Address address, ModelNode extra) {
        final ModelNode request = (extra != null) ? extra.clone() : new ModelNode();
        request.get(OPERATION).set(operation);
        request.get(ADDRESS).set(address.getAddressNode());
        return request;
    }

    /**
     * Creates a batch of operations that can be atomically invoked.
     *
     * @param steps the different operation steps of the batch
     *
     * @return the batch operation node
     */
    public static ModelNode createBatchRequest(ModelNode... steps) {
        final ModelNode composite = new ModelNode();
        composite.get(OPERATION).set(BATCH);
        composite.get(ADDRESS).setEmptyList();
        final ModelNode stepsNode = composite.get(BATCH_STEPS);
        for (ModelNode step : steps) {
            if (step != null) {
                stepsNode.add(step);
            }
        }
        return composite;
    }

    /**
     * If the given node has a result list, that list will be returned
     * with the values as Strings. Otherwise, an empty list is returned.
     *
     * @param operationResult the node to examine
     * @return the result list as Strings if there is a list, empty otherwise
     */
    public static List<String> getResultListAsStrings(ModelNode operationResult) {
        if (!operationResult.hasDefined(RESULT)) {
            return Collections.emptyList();
        }

        final List<ModelNode> nodeList = operationResult.get(RESULT).asList();
        if (nodeList.isEmpty()) {
            return Collections.emptyList();
        }

        final List<String> list = new ArrayList<String>(nodeList.size());
        for (ModelNode node : nodeList) {
            list.add(node.asString());
        }

        return list;
    }

    /**
     * If the given node has results, those results are returned in a ModelNode.
     * Otherwise, an empty node is returned.
     *
     * @param operationResult the node to examine
     * @return the results as a ModelNode
     */
    public static ModelNode getResults(ModelNode operationResult) {
        if (!operationResult.hasDefined(RESULT)) {
            return new ModelNode();
        }

        return operationResult.get(RESULT);
    }

    /**
     * Returns <code>true</code> if the operation was a success; <code>false</code> otherwise.
     *
     * @param operationResult the operation result to test
     * @return the success or failure flag of the result
     */
    public static boolean isSuccess(ModelNode operationResult) {
        if (operationResult != null) {
            return operationResult.hasDefined(OUTCOME)
                && operationResult.get(OUTCOME).asString().equals(OUTCOME_SUCCESS);
        }
        return false;
    }

    /**
     * If the operation result was a failure, this returns the failure description if there is one.
     * A generic failure message will be returned if the operation was a failure but has no failure
     * description. A <code>null</code> is returned if the operation was a success.
     *
     * @param operationResult the operation whose failure description is to be returned
     * @return the failure description of <code>null</code> if the operation was a success
     */
    public static String getFailureDescription(ModelNode operationResult) {
        if (isSuccess(operationResult)) {
            return null;
        }
        if (operationResult != null) {
            final ModelNode descr = operationResult.get(FAILURE_DESCRIPTION);
            if (descr != null) {
                return descr.asString();
            }
        }
        return "Unknown failure";
    }

    /**
     * This sets the given node's named attribute to the given value. If the value
     * appears to be an expression (that is, contains "${" somewhere in it), this will
     * set the value as an expression on the node.
     *
     * @param node the node whose attribute is to be set
     * @param name the name of the attribute whose value is to be set
     * @param value the value, possibly an expression
     *
     * @return returns the node
     */
    public static ModelNode setPossibleExpression(ModelNode node, String name, String value) {
        if (value != null) {
            if (value.contains("${")) {
                return node.get(name).set(new ValueExpression(value));
            } else {
                return node.get(name).set(value);
            }
        } else {
            return node.get(name).clear();
        }
    }

    /////////////////////////////////////////////////////////////////
    // Non-static methods that need the client

    public ModelControllerClient getModelControllerClient() {
        return client;
    }

    @Override
    public void close() throws Exception {
        getModelControllerClient().close();
    }

    /**
     * Convienence method that executes the request.
     *
     * @param request request to execute
     * @return results results of execution
     * @throws Exception any error
     */
    public ModelNode execute(ModelNode request) throws Exception {
        ModelControllerClient mcc = getModelControllerClient();
        return mcc.execute(request, OperationMessageHandler.logging);
    }

    /**
     * This returns information on the resource at the given address.
     * This will not return an exception if the address points to a non-existent resource, rather,
     * it will just return null. You can use this as a test for resource existence.
     *
     * @param addr the address of the resource
     * @return the found item or null if not found
     * @throws Exception if some error prevented the lookup from even happening
     */
    public ModelNode readResource(Address addr) throws Exception {
        return readResource(addr, false);
    }

    /**
     * This returns information on the resource at the given address, recursively
     * returning child nodes with the result if recursive argument is set to <code>true</code>.
     * This will not return an exception if the address points to a non-existent resource, rather,
     * it will just return null. You can use this as a test for resource existence.
     *
     * @param addr the address of the resource
     * @param recursive if true, return all child data within the resource node
     * @return the found item or null if not found
     * @throws Exception if some error prevented the lookup from even happening
     */
    public ModelNode readResource(Address addr, boolean recursive) throws Exception {
        final ModelNode request = createRequest(READ_RESOURCE, addr);
        request.get("recursive").set(recursive);
        final ModelNode results = getModelControllerClient().execute(request, OperationMessageHandler.logging);
        if (isSuccess(results)) {
            final ModelNode resource = getResults(results);
            return resource;
        } else {
            return null;
        }
    }

    /**
     * Removes the resource at the given address.
     *
     * @param doomedAddr the address of the resource to remove
     * @throws Exception any error
     */
    public void remove(Address doomedAddr) throws Exception {
        final ModelNode request = createRequest(REMOVE, doomedAddr);
        final ModelNode response = execute(request);
        if (!isSuccess(response)) {
            throw new FailureException(response, "Failed to remove resource at address [" + doomedAddr + "]");
        }
        return;
    }

    /**
     * Convienence method that allows you to obtain a single attribute's value from a resource.
     *
     * @param attributeName the attribute whose value is to be returned
     * @param address identifies the resource
     * @return the attribute value
     *
     * @throws Exception if failed to obtain the attribute value
     */
    public ModelNode getAttribute(String attributeName, Address address) throws Exception {
        return getAttribute(false, attributeName, address);
    }

    /**
     * Convienence method that allows you to obtain a single attribute's value from a resource.
     *
     * @param runtime if <code>true</code>, the attribute to be retrieved is a runtime attribute
     * @param attributeName the attribute whose value is to be returned
     * @param address identifies the resource
     * @return the attribute value
     *
     * @throws Exception if failed to obtain the attribute value
     */
    public ModelNode getAttribute(boolean runtime, String attributeName, Address address) throws Exception {
        final ModelNode op = createReadAttributeRequest(runtime, attributeName, address);
        final ModelNode results = execute(op);
        if (isSuccess(results)) {
            return getResults(results);
        } else {
            throw new FailureException(results, "Failed to get attribute [" + attributeName + "] from [" + address
                    + "]");
        }
    }

    /**
     * Convienence method that allows you to obtain a single attribute's string value from
     * a resource.
     *
     * @param attributeName the attribute whose value is to be returned
     * @param address identifies the resource
     * @return the attribute value
     *
     * @throws Exception if failed to obtain the attribute value
     */
    public String getStringAttribute(String attributeName, Address address) throws Exception {
        return getStringAttribute(false, attributeName, address);
    }

    /**
     * Convienence method that allows you to obtain a single attribute's string value from
     * a resource.
     *
     * @param runtime if <code>true</code>, the attribute to be retrieved is a runtime attribute
     * @param attributeName the attribute whose value is to be returned
     * @param address identifies the resource
     * @return the attribute value
     *
     * @throws Exception if failed to obtain the attribute value
     */
    public String getStringAttribute(boolean runtime, String attributeName, Address address) throws Exception {
        return getAttribute(runtime, attributeName, address).asString();
    }

    /**
     * This tries to find specific node within a list of nodes. Given an address and a named node
     * at that address (the "haystack"), it is assumed that haystack is actually a list of other
     * nodes. This method looks in the haystack and tries to find the named needle. If it finds it,
     * that list item is returned. If it does not find the needle in the haystack, it returns null.
     *
     * For example, if you want to find a specific datasource in the list of datasources, you
     * can pass in the address for the datasource subsystem, and ask to look in the data-source
     * node list (the haystack) and return the named datasource (the needle).
     *
     * @param addr resource address
     * @param haystack the collection
     * @param needle the item to find in the collection
     * @return the found item or null if not found
     * @throws Exception if the lookup fails for some reason
     */
    public ModelNode findNodeInList(Address addr, String haystack, String needle) throws Exception {
        final ModelNode queryNode = createRequest(READ_RESOURCE, addr);
        final ModelNode results = execute(queryNode);
        if (isSuccess(results)) {
            final ModelNode haystackNode = getResults(results).get(haystack);
            if (haystackNode.getType() != ModelType.UNDEFINED) {
                final List<ModelNode> haystackList = haystackNode.asList();
                for (ModelNode needleNode : haystackList) {
                    if (needleNode.has(needle)) {
                        return needleNode;
                    }
                }
            }
            return null;
        } else {
            throw new FailureException(results, "Failed to get data for [" + addr + "]");
        }
    }
}
