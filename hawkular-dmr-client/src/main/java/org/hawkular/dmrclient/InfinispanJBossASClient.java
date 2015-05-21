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
 * Provides convenience methods associated with Infinispan cache management.
 *
 * @author Jay Shaughnessy
 * @author John Mazzitelli
 */
public class InfinispanJBossASClient extends JBossASClient {

    public static final String SUBSYSTEM_INFINISPAN = "infinispan";
    public static final String CACHE_CONTAINER = "cache-container";
    public static final String LOCAL_CACHE = "local-cache";

    public InfinispanJBossASClient(ModelControllerClient client) {
        super(client);
    }

    /**
     * Checks to see if there is already a cache container with the given name.
     *
     * @param cacheContainerName the name to check
     * @return true if there is a cache container with the given name already in existence
     */
    public boolean isCacheContainer(String cacheContainerName) throws Exception {
        Address addr = Address.root().add(SUBSYSTEM, SUBSYSTEM_INFINISPAN);
        String haystack = CACHE_CONTAINER;
        return null != findNodeInList(addr, haystack, cacheContainerName);
    }

    /**
     * Checks to see if there is already a local cache with the given name.
     *
     * @param cacheContainerName the parent container
     * @param localCacheName the name to check
     * @return true if there is a local cache with the given name already in existence
     */
    public boolean isLocalCache(String cacheContainerName, String localCacheName) throws Exception {
        if (!isCacheContainer(cacheContainerName)) {
            return false;
        }

        Address addr = Address.root().add(SUBSYSTEM, SUBSYSTEM_INFINISPAN, CACHE_CONTAINER, cacheContainerName);
        String haystack = LOCAL_CACHE;
        return null != findNodeInList(addr, haystack, localCacheName);
    }

    /**
     * Returns a ModelNode that can be used to create a cache container configuration for
     * subsequent cache configuration. Callers are free to tweak the request that is returned,
     * if they so choose, before asking the client to execute the request.
     * <p>
     * The JNDI name will be java:jboss/infinispan/<cacheContainerName>
     * </p>
     *
     * @param name the name of the cache container
     * @param defaultCacheName the name of the default cache.  The referenced cache must be
     * subsequently created.
     *
     * @return the request to create the cache container configuration.
     */
    public ModelNode createNewCacheContainerRequest(String name, String defaultCacheName) {
        String dmrTemplate = "" //
                + "{" //
                + "\"default-cache-name\" => \"%s\" ," //
                + "\"jndi-name\" => \"%s\" " //
                + "}";

        String jndiName = "java:jboss/infinispan/" + name;
        String dmr = String.format(dmrTemplate, defaultCacheName, jndiName);

        Address addr = Address.root().add(SUBSYSTEM, SUBSYSTEM_INFINISPAN, CACHE_CONTAINER, name);
        final ModelNode result = ModelNode.fromString(dmr);
        result.get(OPERATION).set(ADD);
        result.get(ADDRESS).set(addr.getAddressNode());

        return result;
    }

    /**
     * Returns a ModelNode that can be used to create a local cache. Callers are free to tweak the
     * request that is returned, if they so choose, before asking the client to execute the request.
     *
     * @param cacheContainerName cache container name
     * @param localCacheName cache name
     * @param transactionMode if null, defaults to "NONE"
     * @param evictionStrategy if null, defaults to "LRU"
     * @param evictionMaxEntries if null, defaults to 50000
     * @param expirationLifeSpan if null, defaults to -1
     * @param expirationMaxIdle if null, defaults to 100000
     * @param isolationLevel if null, defaults to REPEATABLE_READ (currently ignored)
     *
     * @return the request that can be used to create the local cache
     * @throws IllegalArgumentException if cacheContainerName does not correspond to a defined container
     */
    public ModelNode createNewLocalCacheRequest(String cacheContainerName, String localCacheName,
            String transactionMode, String evictionStrategy, Long evictionMaxEntries, Long expirationLifeSpan,
            Long expirationMaxIdle, String isolationLevel) throws Exception {

        if (!isCacheContainer(cacheContainerName)) {
            throw new IllegalArgumentException("cache-container does not exist [" + cacheContainerName + "]");
        }

        ModelNode[] result = new ModelNode[4];

        // The cache
        Address addr = Address.root().add(SUBSYSTEM, SUBSYSTEM_INFINISPAN, CACHE_CONTAINER, cacheContainerName,
                LOCAL_CACHE, localCacheName);
        String dmrTemplate = "" //
                + "{" //
                + "\"isolation-level\" =>  \"%s\" " // TODO (jshaughn): ignored, why?
                + "}";
        String dmr = String.format(dmrTemplate, //
                ((null == isolationLevel) ? "REPEATABLE_READ" : isolationLevel));

        result[0] = ModelNode.fromString(dmr);
        result[0].get(OPERATION).set(ADD);
        result[0].get(ADDRESS).set(addr.getAddressNode());

        // The cache eviction
        addr = Address.root().add(SUBSYSTEM, SUBSYSTEM_INFINISPAN, CACHE_CONTAINER, cacheContainerName, LOCAL_CACHE,
                localCacheName, "eviction", "EVICTION");
        dmrTemplate = "" //
                + "{" //
                + "\"strategy\" =>  \"%s\" ," //
                + "\"max-entries\" =>  %dL " //
                + "}";
        dmr = String.format(dmrTemplate, //
                ((null == evictionStrategy) ? "LRU" : evictionStrategy), //
                ((null == evictionMaxEntries) ? 50000L : evictionMaxEntries));

        result[1] = ModelNode.fromString(dmr);
        result[1].get(OPERATION).set(ADD);
        result[1].get(ADDRESS).set(addr.getAddressNode());

        // The cache expiration
        addr = Address.root().add(SUBSYSTEM, SUBSYSTEM_INFINISPAN, CACHE_CONTAINER, cacheContainerName, LOCAL_CACHE,
                localCacheName, "expiration", "EXPIRATION");
        dmrTemplate = "" //
                + "{" //
                + "\"max-idle\" =>  %dL ," //
                + "\"lifespan\" =>  %dL "
                + "}";
        dmr = String.format(dmrTemplate, //
                ((null == expirationMaxIdle) ? 100000L : expirationMaxIdle),
                ((null == expirationLifeSpan) ? -1L : expirationLifeSpan));

        result[2] = ModelNode.fromString(dmr);
        result[2].get(OPERATION).set(ADD);
        result[2].get(ADDRESS).set(addr.getAddressNode());

        // The cache transaction
        addr = Address.root().add(SUBSYSTEM, SUBSYSTEM_INFINISPAN, CACHE_CONTAINER, cacheContainerName, LOCAL_CACHE,
                localCacheName, "transaction", "TRANSACTION");
        dmrTemplate = "" //
                + "{" //
                + "\"mode\" => \"%s\" " //
                + "}";
        dmr = String.format(dmrTemplate, //
                ((null == transactionMode) ? "NONE" : transactionMode));

        result[3] = ModelNode.fromString(dmr);
        result[3].get(OPERATION).set(ADD);
        result[3].get(ADDRESS).set(addr.getAddressNode());

        return createBatchRequest(result);
    }
}
