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

import java.io.InputStream;
import java.util.Collections;
import java.util.Set;

import org.jboss.as.controller.client.ModelControllerClient;
import org.wildfly.plugin.core.Deployment;
import org.wildfly.plugin.core.DeploymentManager;
import org.wildfly.plugin.core.DeploymentResult;
import org.wildfly.plugin.core.SimpleDeploymentDescription;
import org.wildfly.plugin.core.UndeployDescription;

/**
 * Provides convenience methods associated with deployments.
 *
 * @author John Mazzitelli
 */
public class DeploymentJBossASClient extends JBossASClient {

    public DeploymentJBossASClient(ModelControllerClient client) {
        super(client);
    }

    public void enableDeployment(String name, Set<String> serverGroups) throws Exception {
        enableDisableDeployment(name, true, serverGroups);
    }

    public void disableDeployment(String name, Set<String> serverGroups) throws Exception {
        enableDisableDeployment(name, false, serverGroups);
    }

    private void enableDisableDeployment(String name, boolean enable, Set<String> serverGroups) throws Exception {
        if (serverGroups == null) {
            serverGroups = Collections.emptySet();
        }
        DeploymentManager dm = DeploymentManager.Factory.create(getModelControllerClient());
        DeploymentResult result;

        if (enable) {
            SimpleDeploymentDescription sdd = SimpleDeploymentDescription.of(name);
            sdd.addServerGroups(serverGroups);
            result = dm.deployToRuntime(sdd);
        } else {
            UndeployDescription ud = UndeployDescription.of(name);
            ud.addServerGroups(serverGroups);
            ud.setRemoveContent(false);
            result = dm.undeploy(ud);
        }

        if (!result.successful()) {
            throw new FailureException(result.getFailureMessage());
        }

        return; // everything is OK
    }

    public void restartDeployment(String name, Set<String> serverGroups) throws Exception {
        if (serverGroups == null) {
            serverGroups = Collections.emptySet();
        }
        DeploymentManager dm = DeploymentManager.Factory.create(getModelControllerClient());
        DeploymentResult result;

        SimpleDeploymentDescription sdd = SimpleDeploymentDescription.of(name);
        sdd.addServerGroups(serverGroups);
        result = dm.redeployToRuntime(sdd);

        if (!result.successful()) {
            throw new FailureException(result.getFailureMessage());
        }

        return; // everything is OK
    }

    /**
     * Uploads the content to the app server's content repository and then deploys the content.
     * If this is to be used for app servers in "domain" mode you have to pass in one or more
     * server groups. If this is to be used to deploy an app in a standalone server, the
     * server groups should be empty.
     *
     * @param deploymentName name that the content will be known as
     * @param content stream containing the actual content data
     * @param enabled if true, the content will be uploaded and actually deployed;
     *                if false, content will be uploaded to the server, but it won't be deployed in the server runtime
     * @param serverGroups the server groups where the application will be deployed if in domain mode
     * @param forceDeploy if true the deployment content is uploaded even if that deployment name already has content
     *                    (in other words, the new content will overwrite the old). If false, an error will occur if
     *                    there is already content associated with the deployment name.
     */
    public void deploy(String deploymentName, InputStream content, boolean enabled, Set<String> serverGroups,
            boolean forceDeploy) {
        if (serverGroups == null) {
            serverGroups = Collections.emptySet();
        }

        DeploymentResult result = null;

        try {
            DeploymentManager dm = DeploymentManager.Factory.create(getModelControllerClient());
            Deployment deployment = Deployment.of(content, deploymentName)
                    .addServerGroups(serverGroups)
                    .setEnabled(enabled);
            if (forceDeploy) {
                result = dm.forceDeploy(deployment);
            } else {
                result = dm.deploy(deployment);
            }
        } catch (Exception e) {
            String errMsg;
            if (serverGroups.isEmpty()) {
                errMsg = String.format("Failed to deploy [%s] (standalone mode)", deploymentName);
            } else {
                errMsg = String.format("Failed to deploy [%s] to server groups: %s", deploymentName, serverGroups);
            }
            throw new FailureException(errMsg, e);
        }

        if (!result.successful()) {
            String errMsg;
            if (serverGroups.isEmpty()) {
                errMsg = String.format("Failed to deploy [%s] (standalone mode)", deploymentName);
            } else {
                errMsg = String.format("Failed to deploy [%s] to server groups [%s]", deploymentName, serverGroups);
            }
            throw new FailureException(errMsg + ": " + result.getFailureMessage());
        }

        return; // everything is OK
    }

    /**
     * Undeploys an app. If an empty set of server groups is passed in, this will assume we are operating on a
     * standalone server.
     *
     * @param deploymentName name that the app is known as
     * @param serverGroups the server groups where the application may already be deployed. If empty,
     *                     this will assume the app server is in STANDALONE mode.
     * @param removeContent if true, the content will be removed from the repository; false means the content stays.
     */
    public void undeploy(String deploymentName, Set<String> serverGroups, boolean removeContent) {
        if (serverGroups == null) {
            serverGroups = Collections.emptySet();
        }

        DeploymentResult result = null;

        try {
            DeploymentManager dm = DeploymentManager.Factory.create(getModelControllerClient());
            UndeployDescription undeployDescription = UndeployDescription.of(deploymentName)
                    .addServerGroups(serverGroups)
                    .setFailOnMissing(false)
                    .setRemoveContent(removeContent);
            result = dm.undeploy(undeployDescription);
        } catch (Exception e) {
            String errMsg;
            if (serverGroups.isEmpty()) {
                errMsg = String.format("Failed to undeploy [%s] (standalone mode)", deploymentName);
            } else {
                errMsg = String.format("Failed to undeploy [%s] from server groups: %s", deploymentName, serverGroups);
            }
            throw new FailureException(errMsg, e);
        }

        if (!result.successful()) {
            String errMsg;
            if (serverGroups.isEmpty()) {
                errMsg = String.format("Failed to undeploy [%s] (standalone mode)", deploymentName);
            } else {
                errMsg = String.format("Failed to undeploy [%s] from server groups [%s]", deploymentName,
                        serverGroups);
            }
            throw new FailureException(errMsg + ": " + result.getFailureMessage());
        }

        return; // everything is OK
    }
}
