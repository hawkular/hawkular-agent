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
package org.hawkular.dmrclient.deployment;

import static org.jboss.as.controller.client.helpers.ClientConstants.CONTENT;
import static org.jboss.as.controller.client.helpers.ClientConstants.DEPLOYMENT;
import static org.jboss.as.controller.client.helpers.ClientConstants.DEPLOYMENT_FULL_REPLACE_OPERATION;
import static org.jboss.as.controller.client.helpers.ClientConstants.DEPLOYMENT_REDEPLOY_OPERATION;
import static org.jboss.as.controller.client.helpers.ClientConstants.DEPLOYMENT_UNDEPLOY_OPERATION;
import static org.jboss.as.controller.client.helpers.ClientConstants.NAME;
import static org.jboss.as.controller.client.helpers.ClientConstants.RUNTIME_NAME;
import static org.jboss.as.controller.client.helpers.ClientConstants.SERVER_GROUP;
import static org.jboss.as.controller.client.helpers.Operations.createAddOperation;
import static org.jboss.as.controller.client.helpers.Operations.createOperation;

import java.io.IOException;
import java.io.InputStream;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.CancellationException;

import org.jboss.as.controller.client.ModelControllerClient;
import org.jboss.as.controller.client.Operation;
import org.jboss.as.controller.client.OperationBuilder;
import org.jboss.as.controller.client.helpers.ClientConstants;
import org.jboss.as.controller.client.helpers.Operations;
import org.jboss.as.controller.client.helpers.Operations.CompositeOperationBuilder;
import org.jboss.dmr.ModelNode;

/**
 * A deployment for standalone servers.
 *
 * @author <a href="mailto:jperkins@redhat.com">James R. Perkins</a>
 */
public class Deployment {

    public enum Type {
        DEPLOY("deploying"),
        FORCE_DEPLOY("deploying"),
        UNDEPLOY("undeploying"),
        UNDEPLOY_IGNORE_MISSING("undeploying"),
        REDEPLOY("redeploying");

        final String verb;

        Type(final String verb) {
            this.verb = verb;
        }
    }

    private final InputStream content;
    private final Set<String> serverGroups;
    private final ModelControllerClient client;
    private final String name;
    private final String runtimeName;
    private final Type type;
    private final Deployments deployments;
    private final boolean enabled;

    /**
     * Creates a new deployment.
     *
     * @param client               the client that is connected.
     * @param serverGroups         the server groups to deploy to
     * @param content              the content for the deployment.
     * @param name                 the name of the deployment, if {@code null} the name of the content file is used.
     * @param runtimeName          he runtime name of the deployment
     * @param type                 the deployment type.
     * @param enabled              enable the deployments
     */
    public Deployment(final ModelControllerClient client, final Set<String> serverGroups, final InputStream content,
            final String name, final String runtimeName, final Type type, final boolean enabled) {
        this.content = content;
        this.client = client;
        this.serverGroups = serverGroups;
        this.name = name;
        this.runtimeName = runtimeName;
        this.type = type;
        this.deployments = Deployments.create(client, serverGroups);
        this.enabled = enabled;
    }

    /**
     * Executes the deployment
     *
     * @throws Exception if the deployment fails
     */
    public void execute() throws Exception {
        try {
            final Operation operation;
            switch (type) {
                case DEPLOY: {
                    operation = createDeployOperation();
                    break;
                }
                case FORCE_DEPLOY: {
                    if (deployments.hasDeployment(name)) {
                        operation = createReplaceOperation(true);
                    } else {
                        operation = createDeployOperation();
                    }
                    break;
                }
                case REDEPLOY: {
                    if (!deployments.hasDeployment(name)) {
                        throw new Exception(String.format("Deployment '%s' not found, cannot redeploy", name));
                    }
                    operation = createRedeployOperation();
                    break;
                }
                case UNDEPLOY: {
                    operation = createUndeployOperation(true);
                    break;
                }
                case UNDEPLOY_IGNORE_MISSING: {
                    operation = createUndeployOperation(false);
                    // This may be null if there was nothing to deploy
                    if (operation == null) {
                        return;
                    }
                    break;
                }
                default:
                    throw new IllegalArgumentException("Invalid type: " + type);
            }
            final ModelNode result = client.execute(operation);
            if (!ServerOperations.isSuccessfulOutcome(result)) {
                throw new Exception(String.format("Deployment failed: %s",
                        ServerOperations.getFailureDescriptionAsString(result)));
            }
        } catch (CancellationException e) {
            throw new Exception(String.format(
                    "Error %s %s. The operation was cancelled. This may be caused by the client being closed.",
                    type.verb, name), e);
        } catch (Exception e) {
            throw new Exception(String.format("Error %s %s", type.verb, name), e);
        }
    }

    /**
     * The type of the deployment.
     *
     * @return the type of the deployment.
     */
    public Type getType() {
        return type;
    }

    private void addContent(final OperationBuilder builder, final ModelNode op) {
        final ModelNode contentNode = op.get(CONTENT);
        final ModelNode contentItem = contentNode.get(0);
        contentItem.get(ServerOperations.INPUT_STREAM_INDEX).set(0);
        builder.addInputStream(content);
    }

    private Operation createDeployOperation() throws IOException {
        final Operations.CompositeOperationBuilder builder = Operations.CompositeOperationBuilder.create(true);
        final ModelNode address = ServerOperations.createAddress(DEPLOYMENT, name);
        final ModelNode addOperation = createAddOperation(address);
        if (runtimeName != null) {
            addOperation.get(RUNTIME_NAME).set(runtimeName);
        }
        addContent(builder, addOperation);
        builder.addStep(addOperation);

        // If the server groups are empty this is a standalone deployment
        if (serverGroups.isEmpty()) {
            builder.addStep(createOperation(ClientConstants.DEPLOYMENT_DEPLOY_OPERATION, address));
        } else {
            for (String serverGroup : serverGroups) {
                final ModelNode sgAddress = ServerOperations.createAddress(SERVER_GROUP, serverGroup, DEPLOYMENT, name);
                final ModelNode op = ServerOperations.createAddOperation(sgAddress);
                op.get(ServerOperations.ENABLED).set(this.enabled);
                if (runtimeName != null) {
                    op.get(RUNTIME_NAME).set(runtimeName);
                }
                builder.addStep(op);
            }
        }
        return builder.build();
    }

    private Operation createReplaceOperation(final boolean allowAddIfMissing) throws IOException {
        final CompositeOperationBuilder builder = CompositeOperationBuilder.create();
        // Adds need to happen first on server-groups otherwise the full-replace-deployment will fail currently
        if (allowAddIfMissing) {
            // If deployment is not on the server group, add it but don't yet enable it. The full-replace-deployment
            // should handle that part.
            for (String serverGroup : serverGroups) {
                if (!deployments.hasDeployment(serverGroup, name)) {
                    final ModelNode sgAddress = ServerOperations.createAddress(SERVER_GROUP, serverGroup, DEPLOYMENT, name);
                    final ModelNode addOp = ServerOperations.createAddOperation(sgAddress);
                    addOp.get(ServerOperations.ENABLED).set(false);
                    if (runtimeName != null) {
                        addOp.get(RUNTIME_NAME).set(runtimeName);
                    }
                    builder.addStep(addOp);
                }
            }
        }
        final ModelNode op = createOperation(DEPLOYMENT_FULL_REPLACE_OPERATION);
        op.get(NAME).set(name);
        if (runtimeName != null) {
            op.get(RUNTIME_NAME).set(runtimeName);
        }
        addContent(builder, op);
        op.get(ServerOperations.ENABLE).set(this.enabled);
        builder.addStep(op);
        return builder.build();
    }

    private Operation createRedeployOperation() throws IOException {
        if (serverGroups.isEmpty()) {
            return OperationBuilder.create(
                    createOperation(DEPLOYMENT_REDEPLOY_OPERATION, ServerOperations.createAddress(DEPLOYMENT, name)))
                    .build();
        }
        return createReplaceOperation(false);
    }

    private Operation createUndeployOperation(final boolean failOnMissing) throws Exception {
        final CompositeOperationBuilder builder = CompositeOperationBuilder.create();
        boolean stepAdded = false;
        // Being empty we'll assume this is a standalone server
        if (serverGroups.isEmpty()) {
            final Set<String> deploymentNames = deployments.filter(name, null);
            if (deploymentNames.isEmpty() && failOnMissing) {
                throw new Exception(String.format("No deployment matching %s found to undeploy.", name));
            }
            // Undeploy each deployment required
            for (String deploymentName : deploymentNames) {
                stepAdded = true;
                final ModelNode address = ServerOperations.createAddress(DEPLOYMENT, deploymentName);
                builder.addStep(createOperation(DEPLOYMENT_UNDEPLOY_OPERATION, address))
                        .addStep(ServerOperations.createRemoveOperation(address));
            }
        } else {
            final Set<String> toRemove = new HashSet<>();
            // Process each server group separately
            for (String serverGroup : serverGroups) {
                final Set<String> deploymentNames = deployments.filter(name, null, serverGroup);
                if (deploymentNames.isEmpty() && failOnMissing) {
                    throw new Exception(String.format(
                            "No deployment matching %s found to undeploy on server group %s.", name, serverGroup));
                }
                // Undeploy each deployment required
                for (String deploymentName : deploymentNames) {
                    // If the deployment is present on the server group add the undeploy and remove operation
                    if (deployments.hasDeployment(serverGroup, deploymentName)) {
                        stepAdded = true;
                        final ModelNode address = ServerOperations.createAddress(SERVER_GROUP, serverGroup, DEPLOYMENT,
                                deploymentName);
                        builder.addStep(createOperation(DEPLOYMENT_UNDEPLOY_OPERATION, address))
                                .addStep(ServerOperations.createRemoveOperation(address));
                    } else if (failOnMissing) {
                        throw new Exception(
                                String.format("Could not undeploy %s. Deployment was not found on server group %s.",
                                        deploymentName, serverGroup));
                    }
                    // Add the deployment to the list of deployments to be removed from the domain content repository
                    toRemove.add(deploymentName);
                }
            }
            for (String deploymentName : toRemove) {
                stepAdded = true;
                builder.addStep(ServerOperations
                        .createRemoveOperation(ServerOperations.createAddress(DEPLOYMENT, deploymentName)));
            }
        }
        // If no steps were added return null
        return (stepAdded ? builder.build() : null);
    }
}
