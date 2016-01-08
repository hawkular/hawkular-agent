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
package org.hawkular.cmdgw.ws.test;

import java.net.URLEncoder;

import org.hawkular.inventory.api.model.CanonicalPath;
import org.jboss.as.controller.client.ModelControllerClient;
import org.jboss.as.controller.descriptions.ModelDescriptionConstants;
import org.jboss.dmr.ModelNode;
import org.testng.annotations.Test;

/**
 * @author <a href="https://github.com/ppalaga">Peter Palaga</a>
 */
public class DatasourceCommandITest extends AbstractCommandITest {
    private static final String datasourceConnectionUrl = "jdbc:h2:mem:test;DB_CLOSE_DELAY=-1";
    private static final String datasourceJndiName = "java:/testH2Ds";
    private static final String datasourceName = "testH2Ds";
    private static final String driverClass = "org.h2.Driver";
    private static final String driverName = "h2";
    private static final String dsFileNameAfterAdd;
    private static final String dsFileNameAfterUpdate;
    private static final String password = "sa";
    private static final String userName = "sa";
    private static final String xaDataSourceClass = "org.h2.jdbcx.JdbcDataSource";
    private static final String xaDatasourceJndiName = "java:/testXaDs";

    private static final String xaDatasourceName = "testXaDs";
    private static final String xaDataSourceUrl = "jdbc:h2:mem:test";
    private static final String xaDsFileNameAfterAdd;
    private static final String xaDsFileNameAfterUpdate;

    static {
        dsFileNameAfterAdd = datasourceName + "-after-add.node.txt";
        dsFileNameAfterUpdate = datasourceName + "-after-update.node.txt";
        xaDsFileNameAfterAdd = xaDatasourceName + "-after-add.node.txt";
        xaDsFileNameAfterUpdate = xaDatasourceName + "-after-update.node.txt";
    }

    private static ModelNode datasourceAddess(String dsName, boolean isXaDatasource) {
        return new ModelNode().add(ModelDescriptionConstants.SUBSYSTEM, "datasources")
                .add(isXaDatasource ? "xa-data-source" : "data-source", dsName);
    }

    @Test(dependsOnGroups = { "exclusive-inventory-access" })
    public void testAddDatasource() throws Throwable {
        waitForAccountsAndInventory();

        CanonicalPath wfPath = getCurrentASPath();
        ModelNode dsAddress = datasourceAddess(datasourceName, false);

        try (ModelControllerClient mcc = newModelControllerClient()) {
            assertResourceExists(mcc, dsAddress, false);

            /* define the mock and its behavior */
            String req = "AddDatasourceRequest={\"authentication\":" + authentication + ", " //
                    + "\"resourcePath\":\"" + wfPath.toString() + "\"," //
                    + "\"xaDatasource\":\"false\"," //
                    + "\"datasourceName\":\"" + datasourceName + "\"," //
                    + "\"jndiName\":\"" + datasourceJndiName + "\"," //
                    + "\"datasourceProperties\":{\"prop1\":\"val1\",\"prop2\":\"val2\"}," //
                    + "\"driverName\":\"" + driverName + "\"," //
                    + "\"driverClass\":\"" + driverClass + "\"," //
                    + "\"connectionUrl\":\"" + datasourceConnectionUrl + "\"," //
                    + "\"userName\":\"" + userName + "\"," //
                    + "\"password\":\"" + password + "\"" //
                    + "}";

            String response = "AddDatasourceResponse={"//
                    + "\"xaDatasource\":false,"//
                    + "\"datasourceName\":\"" + datasourceName + "\","//
                    + "\"resourcePath\":\"" + wfPath + "\","//
                    + "\"destinationSessionId\":\"{{sessionId}}\","//
                    + "\"status\":\"OK\","//
                    + "\"message\":\"Added Datasource: " + datasourceName + "\""//
                    + "}";

            try (TestWebSocketClient testClient =
                    TestWebSocketClient.builder() //
                            .url(baseGwUri + "/ui/ws") //
                            .expectWelcome(req) //
                            .expectGenericSuccess(wfPath.ids().getFeedId())//
                            .expectText(response)
                            .build()) {
                testClient.validate(10000);
            }

            assertNodeEquals(mcc, dsAddress, getClass(), dsFileNameAfterAdd);

        }
    }

    @Test(dependsOnGroups = { "exclusive-inventory-access" })
    public void testAddXaDatasource() throws Throwable {
        waitForAccountsAndInventory();
        ModelNode dsAddress = datasourceAddess(xaDatasourceName, true);
        CanonicalPath wfPath = getCurrentASPath();

        try (ModelControllerClient mcc = newModelControllerClient()) {
            assertResourceExists(mcc, dsAddress, false);

            String req = "AddDatasourceRequest={\"authentication\":" + authentication + ", " //
                    + "\"resourcePath\":\"" + wfPath.toString() + "\"," //
                    + "\"xaDatasource\":\"true\"," //
                    + "\"datasourceName\":\"" + xaDatasourceName + "\"," //
                    + "\"jndiName\":\"" + xaDatasourceJndiName + "\"," //
                    + "\"driverName\":\"" + driverName + "\"," //
                    + "\"xaDataSourceClass\":\"" + xaDataSourceClass + "\"," //
                    + "\"datasourceProperties\":{\"URL\":\"" + xaDataSourceUrl
                    + "\",\"loginTimeout\":\"2\"}," + "\"userName\":\"" + userName + "\"," //
                    + "\"password\":\"" + password + "\"" //
                    + "}";
            String response = "AddDatasourceResponse={"//
                    + "\"xaDatasource\":true,"//
                    + "\"datasourceName\":\"" + xaDatasourceName + "\","//
                    + "\"resourcePath\":\"" + wfPath + "\","//
                    + "\"destinationSessionId\":\"{{sessionId}}\","//
                    + "\"status\":\"OK\","//
                    + "\"message\":\"Added Datasource: " + xaDatasourceName + "\""//
                    + "}";
            try (TestWebSocketClient testClient =
                    TestWebSocketClient.builder() //
                            .url(baseGwUri + "/ui/ws") //
                            .expectWelcome(req) //
                            .expectGenericSuccess(wfPath.ids().getFeedId())//
                            .expectText(response)
                            .build()) {
                testClient.validate(10000);
            }

            assertNodeEquals(mcc, dsAddress, getClass(), xaDsFileNameAfterAdd);

        }

    }

    @Test(dependsOnMethods = { "testUpdateDatasource" })
    public void testRemoveDatasource() throws Throwable {
        waitForAccountsAndInventory();

        CanonicalPath wfPath = getCurrentASPath();
        ModelNode dsAddress = datasourceAddess(datasourceName, false);

        String removePath = wfPath.toString().replaceFirst("\\~+$", "")
                + URLEncoder.encode("~/subsystem=datasources/data-source=" + datasourceName, "UTF-8");

        try (ModelControllerClient mcc = newModelControllerClient()) {
            assertResourceExists(mcc, dsAddress, true);

            // see that the resource has been persisted to hawkular-inventory
            getResource("/feeds/" + feedId + "/resourceTypes/Datasource/resources",
                    (r -> r.getId().contains(datasourceName)));

            String req = "RemoveDatasourceRequest={\"authentication\":" + authentication + ", " //
                    + "\"resourcePath\":\"" + removePath + "\"" //
                    + "}";
            String response = "RemoveDatasourceResponse={"//
                    + "\"resourcePath\":\"" + removePath.toString() + "\"," //
                    + "\"destinationSessionId\":\"{{sessionId}}\"," //
                    + "\"status\":\"OK\","//
                    + "\"message\":\"Performed [Remove] on a [Datasource] given by Inventory path [" + removePath
                    + "]\""//
                    + "}";
            try (TestWebSocketClient testClient =
                    TestWebSocketClient.builder() //
                            .url(baseGwUri + "/ui/ws") //
                            .expectWelcome(req) //
                            .expectGenericSuccess(wfPath.ids().getFeedId())//
                            .expectText(response)
                            .build()) {
                testClient.validate(10000);
            }

            assertResourceExists(mcc, dsAddress, false);

            // this should be gone now, let's make sure it does get deleted from h-inventory
            assertResourceNotInInventory("/feeds/" + feedId + "/resourceTypes/Datasource/resources",
                    (r -> r.getId().contains(datasourceName)), 10, 5000);

        }
    }

    @Test(dependsOnMethods = { "testUpdateXaDatasource" })
    public void testRemoveXaDatasource() throws Throwable {
        waitForAccountsAndInventory();

        CanonicalPath wfPath = getCurrentASPath();
        ModelNode dsAddress = datasourceAddess(xaDatasourceName, true);

        String removePath = wfPath.toString().replaceFirst("\\~+$", "")
                + URLEncoder.encode("~/subsystem=datasources/xa-data-source=" + xaDatasourceName, "UTF-8");

        try (ModelControllerClient mcc = newModelControllerClient()) {
            assertResourceExists(mcc, dsAddress, true);

            // see that the resource has been persisted to hawkular-inventory
            getResource("/feeds/" + feedId + "/resourceTypes/XA%20Datasource/resources",
                    (r -> r.getId().contains(xaDatasourceName)));

            String req = "RemoveDatasourceRequest={\"authentication\":" + authentication + ", " //
                    + "\"resourcePath\":\"" + removePath + "\"" //
                    + "}";
            String response = "RemoveDatasourceResponse={"//
                    + "\"resourcePath\":\"" + removePath.toString() + "\"," //
                    + "\"destinationSessionId\":\"{{sessionId}}\"," //
                    + "\"status\":\"OK\","//
                    + "\"message\":\"Performed [Remove] on a [Datasource] given by Inventory path [" + removePath
                    + "]\""//
                    + "}";
            try (TestWebSocketClient testClient =
                    TestWebSocketClient.builder() //
                            .url(baseGwUri + "/ui/ws") //
                            .expectWelcome(req) //
                            .expectGenericSuccess(wfPath.ids().getFeedId())//
                            .expectText(response)
                            .build()) {
                testClient.validate(10000);
            }

            assertResourceExists(mcc, dsAddress, false);

            // this should be gone now, let's make sure it does get deleted from h-inventory
            assertResourceNotInInventory("/feeds/" + feedId + "/resourceTypes/XA%20Datasource/resources",
                    (r -> r.getId().contains(xaDatasourceName)), 10, 5000);
        }
    }

    @Test(dependsOnMethods = { "testAddDatasource" })
    public void testUpdateDatasource() throws Throwable {
        waitForAccountsAndInventory();

        CanonicalPath wfPath = getCurrentASPath();
        ModelNode dsAddress = datasourceAddess(datasourceName, false);

        String dsPath = wfPath.toString().replaceFirst("\\~+$", "")
                + URLEncoder.encode("~/subsystem=datasources/data-source=" + datasourceName, "UTF-8");

        try (ModelControllerClient mcc = newModelControllerClient()) {
            assertNodeEquals(mcc, dsAddress, getClass(), dsFileNameAfterAdd);

            final String changedConnectionUrl = "jdbc:h2:mem:test;DB_CLOSE_DELAY=5000";

            String req = "UpdateDatasourceRequest={\"authentication\":" + authentication + ", " //
                    + "\"resourcePath\":\"" + dsPath + "\"," //
                    + "\"datasourceName\":\"" + datasourceName + "\"," //
                    + "\"jndiName\":\"" + datasourceJndiName + "\"," //
                    + "\"datasourceProperties\":{\"prop1\":\"val1.1\",\"prop3\":\"val3\"}," //
                    + "\"driverName\":\"" + driverName + "\"," //
                    + "\"driverClass\":\"" + driverClass + "\"," //
                    + "\"connectionUrl\":\"" + changedConnectionUrl + "\"," //
                    + "\"userName\":\"" + userName + "\"," //
                    + "\"password\":\"" + password + "\"" //
                    + "}";

            String response = "UpdateDatasourceResponse={"//
                    + "\"resourcePath\":\"" + dsPath.toString() + "\"," //
                    + "\"destinationSessionId\":\"{{sessionId}}\"," //
                    + "\"status\":\"OK\","//
                    + "\"message\":\"Performed [Update] on a [Datasource] given by Inventory path [" + dsPath + "]\""
                    + "}";

            try (TestWebSocketClient testClient =
                    TestWebSocketClient.builder() //
                            .url(baseGwUri + "/ui/ws") //
                            .expectWelcome(req) //
                            .expectGenericSuccess(wfPath.ids().getFeedId())//
                            .expectText(response)
                            .build()) {
                testClient.validate(10000);
            }

            assertNodeEquals(mcc, dsAddress, getClass(), dsFileNameAfterUpdate);

            // reload causes that shut down hook of WF plugin shuts down the server
            // reload();

        }
    }

    @Test(dependsOnMethods = { "testAddXaDatasource" })
    public void testUpdateXaDatasource() throws Throwable {
        waitForAccountsAndInventory();

        CanonicalPath wfPath = getCurrentASPath();
        ModelNode dsAddress = datasourceAddess(xaDatasourceName, true);

        String dsPath = wfPath.toString().replaceFirst("\\~+$", "")
                + URLEncoder.encode("~/subsystem=datasources/xa-data-source=" + xaDatasourceName, "UTF-8");

        try (ModelControllerClient mcc = newModelControllerClient()) {
            assertNodeEquals(mcc, dsAddress, getClass(), xaDsFileNameAfterAdd);

            final String changedXaDatasourceJndiName = xaDatasourceJndiName + "_changed";
            final String changedXaDsUrl = "jdbc:h2:mem:test;DB_CLOSE_DELAY=5000";

            String req = "UpdateDatasourceRequest={\"authentication\":" + authentication + ", " //
                    + "\"resourcePath\":\"" + dsPath + "\"," //
                    + "\"datasourceName\":\"" + xaDatasourceName + "\"," //
                    + "\"jndiName\":\"" + changedXaDatasourceJndiName + "\"," //
            // changing or removing of props seems to be broken
                    + "\"datasourceProperties\":{\"URL\":\"" + changedXaDsUrl
                    + "\",\"loginTimeout\":\"3\"}," //
                    + "\"driverName\":\"" + driverName + "\"," //
                    + "\"xaDataSourceClass\":\"" + xaDataSourceClass + "\"," //
                    + "\"userName\":\"" + userName + "\"," //
                    + "\"password\":\"" + password + "\"" //
                    + "}";
            String response = "UpdateDatasourceResponse={"//
                    + "\"resourcePath\":\"" + dsPath.toString() + "\"," //
                    + "\"destinationSessionId\":\"{{sessionId}}\"," //
                    + "\"status\":\"OK\","//
                    + "\"message\":\"Performed [Update] on a [Datasource] given by Inventory path [" + dsPath + "]\""
                    + "}";

            try (TestWebSocketClient testClient =
                    TestWebSocketClient.builder() //
                            .url(baseGwUri + "/ui/ws") //
                            .expectWelcome(req) //
                            .expectGenericSuccess(wfPath.ids().getFeedId()) //
                            .expectText(response) //
                            .build()) {
                testClient.validate(10000);
            }

            assertNodeEquals(mcc, dsAddress, getClass(), xaDsFileNameAfterUpdate, false);

            // reload causes that shut down hook of WF plugin shuts down the server
            // reload();

        }
    }

}
