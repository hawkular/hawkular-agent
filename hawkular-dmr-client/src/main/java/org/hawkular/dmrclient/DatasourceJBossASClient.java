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

import java.util.Map;
import java.util.Map.Entry;

import org.jboss.as.controller.client.ModelControllerClient;
import org.jboss.dmr.ModelNode;
import org.jboss.dmr.ValueExpression;

/**
 * Provides convenience methods associated with datasource management.
 *
 * @author John Mazzitelli
 */
public class DatasourceJBossASClient extends JBossASClient {

    public static final String SUBSYSTEM_DATASOURCES = "datasources";
    public static final String DATA_SOURCE = "data-source";
    public static final String XA_DATA_SOURCE = "xa-data-source";
    public static final String JDBC_DRIVER = "jdbc-driver";
    public static final String CONNECTION_PROPERTIES = "connection-properties";
    public static final String XA_DATASOURCE_PROPERTIES = "xa-datasource-properties";
    public static final String OP_ENABLE = "enable";

    public DatasourceJBossASClient(ModelControllerClient client) {
        super(client);
    }

    /**
     * Completely removes the named datasource. If the datasource does not exist, this returns silently (in other words,
     * no exception is thrown).
     *
     * Note that no distinguishing between XA and non-XA datasource is needed - if any datasource (XA or non-XA) exists
     * with the given name, it will be removed.
     *
     * @param name the name of the datasource to remove
     * @throws Exception any error
     */
    public void removeDatasource(String name) throws Exception {
        Address addr = Address.root().add(SUBSYSTEM, SUBSYSTEM_DATASOURCES);
        if (isDatasource(name)) {
            addr.add(DATA_SOURCE, name);
        } else if (isXADatasource(name)) {
            addr.add(XA_DATA_SOURCE, name);
        } else {
            return; // there is no datasource (XA or non-XA) with the given name, just return silently
        }

        remove(addr);
        return;
    }

    public boolean isDatasourceEnabled(String name) throws Exception {
        return isDatasourceEnabled(false, name);
    }

    public boolean isXADatasourceEnabled(String name) throws Exception {
        return isDatasourceEnabled(true, name);
    }

    private boolean isDatasourceEnabled(boolean isXA, String name) throws Exception {
        Address addr = Address.root().add(SUBSYSTEM, SUBSYSTEM_DATASOURCES);
        addr.add((isXA) ? XA_DATA_SOURCE : DATA_SOURCE, name);
        ModelNode results = readResource(addr);
        boolean enabledFlag = false;
        if (results.hasDefined("enabled")) {
            ModelNode enabled = results.get("enabled");
            enabledFlag = enabled.asBoolean(false);
        }
        return enabledFlag;
    }

    public void enableDatasource(String name) throws Exception {
        enableDatasource(false, name);
    }

    public void enableXADatasource(String name) throws Exception {
        enableDatasource(true, name);
    }

    private void enableDatasource(boolean isXA, String name) throws Exception {
        if (isDatasourceEnabled(isXA, name)) {
            return; // nothing to do - its already enabled
        }

        Address addr = Address.root().add(SUBSYSTEM, SUBSYSTEM_DATASOURCES);
        addr.add((isXA) ? XA_DATA_SOURCE : DATA_SOURCE, name);
        ModelNode request = createRequest(OP_ENABLE, addr);
        request.get(PERSISTENT).set(true);
        ModelNode results = execute(request);
        if (!isSuccess(results)) {
            throw new FailureException(results);
        }
        return; // everything is OK
    }

    /**
     * Checks to see if there is already a JDBC driver with the given name.
     *
     * @param jdbcDriverName the name to check
     * @return true if there is a JDBC driver with the given name already in existence
     * @throws Exception any error
     */
    public boolean isJDBCDriver(String jdbcDriverName) throws Exception {
        Address addr = Address.root().add(SUBSYSTEM, SUBSYSTEM_DATASOURCES);
        String haystack = JDBC_DRIVER;
        return null != findNodeInList(addr, haystack, jdbcDriverName);
    }

    /**
     * Checks to see if there is already a datasource with the given name.
     *
     * @param datasourceName the name to check
     * @return true if there is a datasource with the given name already in existence
     * @throws Exception any error
     */
    public boolean isDatasource(String datasourceName) throws Exception {
        Address addr = Address.root().add(SUBSYSTEM, SUBSYSTEM_DATASOURCES);
        String haystack = DATA_SOURCE;
        return null != findNodeInList(addr, haystack, datasourceName);
    }

    /**
     * Checks to see if there is already a XA datasource with the given name.
     *
     * @param datasourceName the name to check
     * @return true if there is a XA datasource with the given name already in existence
     * @throws Exception any error
     */
    public boolean isXADatasource(String datasourceName) throws Exception {
        Address addr = Address.root().add(SUBSYSTEM, SUBSYSTEM_DATASOURCES);
        String haystack = XA_DATA_SOURCE;
        return null != findNodeInList(addr, haystack, datasourceName);
    }

    /**
     * @param driverName the name of the JDBC driver to create, e.g. {@code "h2"}
     * @param moduleName the name of the WIldFly module that contains the driver jar file
     * @throws Exception
     */
    public void addJdbcDriver(String driverName, String moduleName) throws Exception {
        Address addr = Address.root().add(SUBSYSTEM, SUBSYSTEM_DATASOURCES, JDBC_DRIVER, driverName);
        final ModelNode driverNode = new ModelNode();

        driverNode.get("driver-name").set(driverName);
        driverNode.get("driver-module-name").set(moduleName);
        driverNode.get(OPERATION).set(ADD);
        driverNode.get(ADDRESS).set(addr.getAddressNode());

        execute(driverNode);

    }

    public void addXaDatasource(String datasourceName, String jndiName, String driverName, String xaDataSourceClass,
            Map<String, String> xaDatasourceProperties, String userName, String password, String securityDomain)
                    throws Exception {
        Address addr = Address.root().add(SUBSYSTEM, SUBSYSTEM_DATASOURCES, XA_DATA_SOURCE, datasourceName);
        final ModelNode dsNode = new ModelNode();
        dsNode.get("name").set(datasourceName);
        dsNode.get("jndi-name").set(jndiName);
        dsNode.get("driver-name").set(driverName);
        dsNode.get("jndi-name").set(xaDataSourceClass);

        if (userName != null) {
            dsNode.get("user-name ").set(userName);
        }
        if (password != null) {
            dsNode.get("password").set(password);
        }
        if (securityDomain != null) {
            dsNode.get("security-domain").set(securityDomain);
        }

        dsNode.get(OPERATION).set(ADD);
        dsNode.get(ADDRESS).set(addr.getAddressNode());

        final ModelNode batch;
        if (xaDatasourceProperties == null || xaDatasourceProperties.isEmpty()) {
            batch = dsNode;
        } else {
            batch = new ModelNode();
            batch.get(OPERATION).set(BATCH);
            batch.get(ADDRESS).setEmptyList();
            final ModelNode stepsNode = batch.get(BATCH_STEPS);
            stepsNode.add(dsNode);
            for (Entry<String, String> prop : xaDatasourceProperties.entrySet()) {
                addr = Address.root().add(SUBSYSTEM, SUBSYSTEM_DATASOURCES, XA_DATA_SOURCE, datasourceName,
                        XA_DATASOURCE_PROPERTIES, prop.getKey());
                final ModelNode propNode = new ModelNode();
                propNode.get(OPERATION).set(ADD);
                propNode.get(ADDRESS).set(addr.getAddressNode());
                setPossibleExpression(propNode, VALUE, prop.getValue());
                stepsNode.add(propNode);
            }
        }

        execute(batch);

    }

    public void addDatasource(String datasourceName, String jndiName, String driverName, String driverClass,
            String connectionUrl, Map<String, String> xaDatasourceProperties, String userName, String password)
                    throws Exception {
        Address addr = Address.root().add(SUBSYSTEM, SUBSYSTEM_DATASOURCES, XA_DATA_SOURCE, datasourceName);
        final ModelNode dsNode = new ModelNode();
        dsNode.get("name").set(datasourceName);
        dsNode.get("jndi-name").set(jndiName);
        dsNode.get("driver-name").set(driverName);
        dsNode.get("driver-class").set(driverClass);
        dsNode.get("connection-url").set(connectionUrl);

        if (userName != null) {
            dsNode.get("user-name ").set(userName);
        }
        if (password != null) {
            dsNode.get("password").set(password);
        }

        dsNode.get(OPERATION).set(ADD);
        dsNode.get(ADDRESS).set(addr.getAddressNode());

        final ModelNode batch;
        if (xaDatasourceProperties == null || xaDatasourceProperties.isEmpty()) {
            batch = dsNode;
        } else {
            batch = new ModelNode();
            batch.get(OPERATION).set(BATCH);
            batch.get(ADDRESS).setEmptyList();
            final ModelNode stepsNode = batch.get(BATCH_STEPS);
            stepsNode.add(dsNode);
            for (Entry<String, String> prop : xaDatasourceProperties.entrySet()) {
                addr = Address.root().add(SUBSYSTEM, SUBSYSTEM_DATASOURCES, DATA_SOURCE, datasourceName,
                        CONNECTION_PROPERTIES, prop.getKey());
                final ModelNode propNode = new ModelNode();
                propNode.get(OPERATION).set(ADD);
                propNode.get(ADDRESS).set(addr.getAddressNode());
                setPossibleExpression(propNode, VALUE, prop.getValue());
                stepsNode.add(propNode);
            }
        }

        execute(batch);

    }

    /**
     * Returns a ModelNode that can be used to create a datasource. Callers are free to tweak the datasource request
     * that is returned, if they so choose, before asking the client to execute the request.
     *
     * @param name the name of the datasource
     * @param blockingTimeoutWaitMillis see datasource documentation for meaning of this setting
     * @param connectionUrlExpression see datasource documentation for meaning of this setting
     * @param driverName see datasource documentation for meaning of this setting
     * @param exceptionSorterClassName see datasource documentation for meaning of this setting
     * @param idleTimeoutMinutes see datasource documentation for meaning of this setting
     * @param jta true if this DS should support transactions; false if not
     * @param minPoolSize see datasource documentation for meaning of this setting
     * @param maxPoolSize see datasource documentation for meaning of this setting
     * @param preparedStatementCacheSize see datasource documentation for meaning of this setting
     * @param securityDomain see datasource documentation for meaning of this setting
     * @param staleConnectionCheckerClassName see datasource documentation for meaning of this setting
     * @param transactionIsolation see datasource documentation for meaning of this setting
     * @param validConnectionCheckerClassName see datasource documentation for meaning of this setting
     * @param validateOnMatch see datasource documentation for meaning of this setting
     * @param connectionProperties see datasource documentation for meaning of this setting
     *
     * @return the request that can be used to create the datasource
     */
    public ModelNode createNewDatasourceRequest(String name, int blockingTimeoutWaitMillis,
            String connectionUrlExpression, String driverName, String exceptionSorterClassName, int idleTimeoutMinutes,
            boolean jta, int minPoolSize, int maxPoolSize, int preparedStatementCacheSize, String securityDomain,
            String staleConnectionCheckerClassName, String transactionIsolation, String validConnectionCheckerClassName,
            boolean validateOnMatch, Map<String, String> connectionProperties) {

        String jndiName = "java:jboss/datasources/" + name;

        String dmrTemplate = "" //
                + "{" //
                + "\"blocking-timeout-wait-millis\" => %dL " //
                + ", \"connection-url\" => expression \"%s\" " //
                + ", \"driver-name\" => \"%s\" " //
                + ", \"exception-sorter-class-name\" => \"%s\" " //
                + ", \"idle-timeout-minutes\" => %dL " //
                + ", \"jndi-name\" => \"%s\" " //
                + ", \"jta\" => %s " //
                + ", \"min-pool-size\" => %d " //
                + ", \"max-pool-size\" => %d " //
                + ", \"prepared-statements-cache-size\" => %dL " //
                + ", \"security-domain\" => \"%s\" " //
                + ", \"stale-connection-checker-class-name\" => \"%s\" " //
                + ", \"transaction-isolation\" => \"%s\" " //
                + ", \"use-java-context\" => true " //
                + ", \"valid-connection-checker-class-name\" => \"%s\" " //
                + ", \"validate-on-match\" => %s " //
                + "}";

        String dmr = String.format(dmrTemplate, blockingTimeoutWaitMillis, connectionUrlExpression, driverName,
                exceptionSorterClassName, idleTimeoutMinutes, jndiName, jta, minPoolSize, maxPoolSize,
                preparedStatementCacheSize, securityDomain, staleConnectionCheckerClassName, transactionIsolation,
                validConnectionCheckerClassName, validateOnMatch);

        Address addr = Address.root().add(SUBSYSTEM, SUBSYSTEM_DATASOURCES, DATA_SOURCE, name);
        final ModelNode request1 = ModelNode.fromString(dmr);
        request1.get(OPERATION).set(ADD);
        request1.get(ADDRESS).set(addr.getAddressNode());

        // if there are no conn properties, no need to create a batch request, there is only one ADD request to make
        if (connectionProperties == null || connectionProperties.size() == 0) {
            return request1;
        }

        // create a batch of requests - the first is the main one, the rest create each conn property
        ModelNode[] batch = new ModelNode[1 + connectionProperties.size()];
        batch[0] = request1;
        int n = 1;
        for (Map.Entry<String, String> entry : connectionProperties.entrySet()) {
            addr = Address.root().add(SUBSYSTEM, SUBSYSTEM_DATASOURCES, DATA_SOURCE, name, CONNECTION_PROPERTIES,
                    entry.getKey());
            final ModelNode requestN = new ModelNode();
            requestN.get(OPERATION).set(ADD);
            requestN.get(ADDRESS).set(addr.getAddressNode());
            setPossibleExpression(requestN, VALUE, entry.getValue());
            batch[n++] = requestN;
        }

        return createBatchRequest(batch);
    }

    public ModelNode createNewDatasourceRequest(String name, String connectionUrlExpression, String driverName,
            boolean jta, Map<String, String> connectionProperties) {

        String jndiName = "java:jboss/datasources/" + name;

        String dmrTemplate = "" //
                + "{" //
                + "\"connection-url\" => expression \"%s\" " //
                + ", \"driver-name\" => \"%s\" " //
                + ", \"jndi-name\" => \"%s\" " //
                + ", \"jta\" => %s " //
                + ", \"use-java-context\" => true " //
                + "}";

        String dmr = String.format(dmrTemplate, connectionUrlExpression, driverName, jndiName, jta);

        Address addr = Address.root().add(SUBSYSTEM, SUBSYSTEM_DATASOURCES, DATA_SOURCE, name);
        final ModelNode request1 = ModelNode.fromString(dmr);
        request1.get(OPERATION).set(ADD);
        request1.get(ADDRESS).set(addr.getAddressNode());

        // if there are no conn properties, no need to create a batch request, there is only one ADD request to make
        if (connectionProperties == null || connectionProperties.size() == 0) {
            return request1;
        }

        // create a batch of requests - the first is the main one, the rest create each conn property
        ModelNode[] batch = new ModelNode[1 + connectionProperties.size()];
        batch[0] = request1;
        int n = 1;
        for (Map.Entry<String, String> entry : connectionProperties.entrySet()) {
            addr = Address.root().add(SUBSYSTEM, SUBSYSTEM_DATASOURCES, DATA_SOURCE, name, CONNECTION_PROPERTIES,
                    entry.getKey());
            final ModelNode requestN = new ModelNode();
            requestN.get(OPERATION).set(ADD);
            requestN.get(ADDRESS).set(addr.getAddressNode());
            if (entry.getValue().indexOf("${") > -1) {
                requestN.get(VALUE).set(new ValueExpression(entry.getValue()));
            } else {
                requestN.get(VALUE).set(entry.getValue());
            }
            batch[n++] = requestN;
        }

        return createBatchRequest(batch);
    }

    /**
     * Returns a ModelNode that can be used to create an XA datasource. Callers are free to tweek the datasource request
     * that is returned, if they so choose, before asking the client to execute the request.
     *
     * @param name name of the XA datasource
     * @param blockingTimeoutWaitMillis see datasource documentation for meaning of this setting
     * @param driverName see datasource documentation for meaning of this setting
     * @param xaDataSourceClass see datasource documentation for meaning of this setting
     * @param exceptionSorterClassName see datasource documentation for meaning of this setting
     * @param idleTimeoutMinutes see datasource documentation for meaning of this setting
     * @param minPoolSize see datasource documentation for meaning of this setting
     * @param maxPoolSize see datasource documentation for meaning of this setting
     * @param noRecovery optional, left unset if null
     * @param noTxSeparatePool optional, left unset if null
     * @param preparedStatementCacheSize see datasource documentation for meaning of this setting
     * @param recoveryPluginClassName optional, left unset if null
     * @param securityDomain see datasource documentation for meaning of this setting
     * @param staleConnectionCheckerClassName optional, left unset if null
     * @param transactionIsolation see datasource documentation for meaning of this setting
     * @param validConnectionCheckerClassName see datasource documentation for meaning of this setting
     * @param xaDatasourceProperties see datasource documentation for meaning of this setting
     *
     * @return the request that can be used to create the XA datasource
     */
    public ModelNode createNewXADatasourceRequest(String name, int blockingTimeoutWaitMillis, String driverName,
            String xaDataSourceClass, String exceptionSorterClassName, int idleTimeoutMinutes, int minPoolSize,
            int maxPoolSize, Boolean noRecovery, Boolean noTxSeparatePool, int preparedStatementCacheSize,
            String recoveryPluginClassName, String securityDomain, String staleConnectionCheckerClassName,
            String transactionIsolation, String validConnectionCheckerClassName,
            Map<String, String> xaDatasourceProperties) {

        String jndiName = "java:jboss/datasources/" + name;

        String dmrTemplate = "" //
                + "{" //
                + "\"xa-datasource-class\" => \"%s\"" + ", \"blocking-timeout-wait-millis\" => %dL " //
                + ", \"driver-name\" => \"%s\" " //
                + ", \"exception-sorter-class-name\" => \"%s\" " //
                + ", \"idle-timeout-minutes\" => %dL " //
                + ", \"jndi-name\" => \"%s\" " //
                + ", \"jta\" => true " //
                + ", \"min-pool-size\" => %d " //
                + ", \"max-pool-size\" => %d " //
                + ", \"no-recovery\" => %b " //
                + ", \"no-tx-separate-pool\" => %b " //
                + ", \"prepared-statements-cache-size\" => %dL " //
                + ", \"recovery-plugin-class-name\" => \"%s\" " //
                + ", \"security-domain\" => \"%s\" " //
                + ", \"stale-connection-checker-class-name\" => \"%s\" " //
                + ", \"transaction-isolation\" => \"%s\" " //
                + ", \"use-java-context\" => true " //
                + ", \"valid-connection-checker-class-name\" => \"%s\" " //
                + "}";

        String dmr = String.format(dmrTemplate, xaDataSourceClass, blockingTimeoutWaitMillis, driverName,
                exceptionSorterClassName, idleTimeoutMinutes, jndiName, minPoolSize, maxPoolSize, noRecovery,
                noTxSeparatePool, preparedStatementCacheSize, recoveryPluginClassName, securityDomain,
                staleConnectionCheckerClassName, transactionIsolation, validConnectionCheckerClassName);

        Address addr = Address.root().add(SUBSYSTEM, SUBSYSTEM_DATASOURCES, XA_DATA_SOURCE, name);
        final ModelNode request1 = ModelNode.fromString(dmr);
        request1.get(OPERATION).set(ADD);
        request1.get(ADDRESS).set(addr.getAddressNode());

        // if no xa datasource properties, no need to create a batch request, there is only one ADD request to make
        if (xaDatasourceProperties == null || xaDatasourceProperties.size() == 0) {
            return request1;
        }

        // create a batch of requests - the first is the main one, the rest create each conn property
        ModelNode[] batch = new ModelNode[1 + xaDatasourceProperties.size()];
        batch[0] = request1;
        int n = 1;
        for (Map.Entry<String, String> entry : xaDatasourceProperties.entrySet()) {
            addr = Address.root().add(SUBSYSTEM, SUBSYSTEM_DATASOURCES, XA_DATA_SOURCE, name, XA_DATASOURCE_PROPERTIES,
                    entry.getKey());
            final ModelNode requestN = new ModelNode();
            requestN.get(OPERATION).set(ADD);
            requestN.get(ADDRESS).set(addr.getAddressNode());
            setPossibleExpression(requestN, VALUE, entry.getValue());
            batch[n++] = requestN;
        }

        ModelNode result = createBatchRequest(batch);

        // remove unset args
        if (null == noRecovery) {
            result.get("steps").get(0).remove("no-recovery");
        }
        if (null == noTxSeparatePool) {
            result.get("steps").get(0).remove("no-tx-separate-pool");
        }
        if (null == recoveryPluginClassName) {
            result.get("steps").get(0).remove("recovery-plugin-class-name");
        }
        if (null == staleConnectionCheckerClassName) {
            result.get("steps").get(0).remove("stale-connection-checker-class-name");
        }

        return result;
    }
}
