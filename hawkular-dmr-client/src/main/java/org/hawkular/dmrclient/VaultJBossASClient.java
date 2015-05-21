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
 * Provides convenience methods associated with Vault management.
 *
 * @author Stefan Negrea
 */
public class VaultJBossASClient extends JBossASClient {

    public static final String CORE_SERVICE = "core-service";
    public static final String VAULT = "vault";

    public VaultJBossASClient(ModelControllerClient client) {
        super(client);
    }

    /**
     * Checks to see if there is already a vault configured.
     *
     * @return true if the vault is already configured
     * @throws Exception any error
     */
    public boolean isVault() throws Exception {
        Address addr = Address.root().add(CORE_SERVICE, VAULT);
        final ModelNode queryNode = createRequest(READ_RESOURCE, addr);
        final ModelNode results = execute(queryNode);
        if (isSuccess(results)) {
            return true;
        }

        return false;
    }

    /**
     * Attempts to retrieve the configured class for the vault. This method will
     * return null if no vault is configured or if the vault does not have a custom
     * vault class.
     *
     * @return vault configured class
     * @throws Exception any error
     */
    public String getVaultClass() throws Exception {
        Address addr = Address.root().add(CORE_SERVICE, VAULT);

        ModelNode vaultNode = readResource(addr);
        if (vaultNode == null) {
            return null;
        }

        ModelNode codeNode = vaultNode.get("code");
        if (codeNode == null) {
            return null;
        }

        return codeNode.asString();
    }

    /**
     * Returns a ModelNode that can be used to create the vault.
     * Callers are free to tweak the queue request that is returned,
     * if they so choose, before asking the client to execute the request.
     *
     * @param className class name for the custom vault
     *
     * @return the request that can be used to create the vault
     */
    public ModelNode createNewVaultRequest(String className) {
        String dmrTemplate = "" //
            + "{" //
            + "\"code\" => \"%s\""
            + "}";

        String dmr = String.format(dmrTemplate, className);

        Address addr = Address.root().add(CORE_SERVICE, VAULT);
        final ModelNode request = ModelNode.fromString(dmr);
        request.get(OPERATION).set(ADD);
        request.get(ADDRESS).set(addr.getAddressNode());

        return request;
    }
}
