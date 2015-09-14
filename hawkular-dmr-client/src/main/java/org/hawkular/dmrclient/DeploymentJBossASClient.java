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

import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Future;

import org.jboss.as.controller.client.ModelControllerClient;
import org.jboss.as.controller.client.helpers.domain.DeploymentPlanResult;
import org.jboss.as.controller.client.helpers.domain.DomainDeploymentManager;
import org.jboss.as.controller.client.helpers.domain.impl.DomainClientImpl;
import org.jboss.as.controller.client.helpers.standalone.DeploymentAction;
import org.jboss.as.controller.client.helpers.standalone.DeploymentPlan;
import org.jboss.as.controller.client.helpers.standalone.ServerDeploymentActionResult;
import org.jboss.as.controller.client.helpers.standalone.ServerDeploymentPlanResult;
import org.jboss.as.controller.client.helpers.standalone.impl.ModelControllerClientServerDeploymentManager;
import org.jboss.dmr.ModelNode;

/**
 * Provides convenience methods associated with deployments.
 *
 * @author John Mazzitelli
 */
public class DeploymentJBossASClient extends JBossASClient {

    public static final String SUBSYSTEM_DEPLOYMENT = "deployment";
    public static final String ENABLED = "enabled";
    public static final String CONTENT = "content";
    public static final String PATH = "path";

    public DeploymentJBossASClient(ModelControllerClient client) {
        super(client);
    }

    /**
     * Checks to see if there is already a deployment with the given name.
     *
     * @param name the deployment name to check
     * @return true if there is a deployment with the given name already in existence
     * @throws Exception any error
     */
    public boolean isDeployment(String name) throws Exception {
        Address addr = Address.root().add(SUBSYSTEM_DEPLOYMENT, name);
        return null != readResource(addr);
    }

    public boolean isDeploymentEnabled(String name) throws Exception {
        Address addr = Address.root().add(SUBSYSTEM_DEPLOYMENT, name);
        ModelNode results = readResource(addr);
        if (results == null) {
            throw new IllegalArgumentException("There is no deployment with the name: " + name);
        }
        boolean enabledFlag = false;
        if (results.hasDefined(ENABLED)) {
            ModelNode enabled = results.get(ENABLED);
            enabledFlag = enabled.asBoolean(false);
        }
        return enabledFlag;
    }

    public void enableDeployment(String name) throws Exception {
        enableDisableDeployment(name, true);
    }

    public void disableDeployment(String name) throws Exception {
        enableDisableDeployment(name, false);
    }

    private void enableDisableDeployment(String name, boolean enable) throws Exception {
        if (isDeploymentEnabled(name) == enable) {
            return; // nothing to do - its already in the state we want
        }

        Address addr = Address.root().add(SUBSYSTEM_DEPLOYMENT, name);
        ModelNode request = createWriteAttributeRequest(ENABLED, Boolean.toString(enable), addr);
        ModelNode results = execute(request);
        if (!isSuccess(results)) {
            throw new FailureException(results);
        }
        return; // everything is OK
    }

    /**
     * Given the name of a deployment, this returns where the deployment is (specifically,
     * it returns the PATH of the deployment).
     *
     * @param name the name of the deployment
     * @return the path where the deployment is found
     *
     * @throws Exception any error
     */
    public String getDeploymentPath(String name) throws Exception {
        Address addr = Address.root().add(SUBSYSTEM_DEPLOYMENT, name);
        ModelNode op = createReadAttributeRequest(CONTENT, addr);
        final ModelNode results = execute(op);
        if (isSuccess(results)) {
            ModelNode path;
            try {
                path = getResults(results).asList().get(0).asObject().get(PATH);
            } catch (Exception e) {
                throw new Exception("Cannot get path associated with deployment [" + name + "]");
            }
            if (path != null) {
                return path.asString();
            } else {
                throw new Exception("No path associated with deployment [" + name + "]");
            }
        } else {
            throw new FailureException(results, "Cannot get the deployment path");
        }
    }

    /**
     * Uploads the content to the app server's content repository and then deploys the content.
     * This is to be used for app servers in "standalone" mode.
     *
     * @param deploymentName name that the content will be known as
     * @param content stream containing the actual content data
     * @param enabled if true, the content will be uploaded and actually deployed;
     *                if false, content will be uploaded to the server, but it won't be deployed in the server runtime
     */
    public void deployStandalone(String deploymentName, InputStream content, boolean enabled) {
        ModelControllerClientServerDeploymentManager deployMgr;
        deployMgr = new ModelControllerClientServerDeploymentManager(getModelControllerClient(), false);

        DeploymentPlan plan;
        if (enabled) {
            plan = deployMgr.newDeploymentPlan().add(deploymentName, content).andDeploy().build();
        } else {
            plan = deployMgr.newDeploymentPlan().add(deploymentName, content).build();
        }

        Future<ServerDeploymentPlanResult> future = deployMgr.execute(plan);
        ServerDeploymentPlanResult results;
        try {
            results = future.get();
        } catch (Exception e) {
            throw new FailureException("Failed to execute standalone deployment plan for [" + deploymentName + "]", e);
        }

        boolean success = true;
        ArrayList<Throwable> exceptions = new ArrayList<>();
        List<DeploymentAction> actions = plan.getDeploymentActions();
        for (DeploymentAction action : actions) {
            ServerDeploymentActionResult result = results.getDeploymentActionResult(action.getId());
            switch (result.getResult()) {
                case EXECUTED:
                case CONFIGURATION_MODIFIED_REQUIRES_RESTART: {
                    success &= true; // if it is already false, we want to keep it as false
                    break;
                }
                case FAILED:
                case NOT_EXECUTED:
                case ROLLED_BACK: {
                    success = false;
                    Throwable error = result.getDeploymentException();
                    if (error != null) {
                        exceptions.add(error);
                    }
                    break;
                }
            }
        }

        if (!success) {
            StringBuilder errorMsg = new StringBuilder();
            errorMsg.append("Failed to deploy [").append(deploymentName).append("]");
            int errorNumber = 1;
            for (Throwable exception : exceptions) {
                errorMsg.append('\n').append(errorNumber++).append(": ").append(exception);
            }
            throw new FailureException(errorMsg.toString());
        }

        return; // success
    }

    /**
     * Uploads the content to the app server's content repository and then deploys the content.
     * This is to be used for app servers in "domain" mode.
     *
     * NOTE: This has not been tested. It also will not throw exceptions if the deployment failed.
     *       This method needs some TLC to finish it.
     *
     * @param deploymentName name that the content will be known as
     * @param content stream containing the actual content data
     * @param enabled if true, the content will be uploaded and actually deployed;
     *                if false, content will be uploaded to the server, but it won't be deployed in the server runtime
     */
    public void deployDomain(String deploymentName, InputStream content, boolean enabled) {
        DomainClientImpl domainClient = new DomainClientImpl(getModelControllerClient());
        DomainDeploymentManager deployMgr = domainClient.getDeploymentManager();
        org.jboss.as.controller.client.helpers.domain.DeploymentPlan plan;
        try {
            if (enabled) {
                plan = deployMgr.newDeploymentPlan().add(deploymentName, content).andDeploy().build();
            } else {
                plan = deployMgr.newDeploymentPlan().add(deploymentName, content).build();
            }
        } catch (Exception e) {
            throw new FailureException("Cannot build domain deployment plan for [" + deploymentName + "]", e);
        }

        Future<DeploymentPlanResult> future = deployMgr.execute(plan);
        DeploymentPlanResult results;
        try {
            results = future.get();
        } catch (Exception e) {
            throw new FailureException("Failed to execute domain deployment plan for [" + deploymentName + "]", e);
        }

        // TODO parse "results" to know if it worked; if failed, throw exception with appropriate error message
        return;
    }
}
