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
package org.hawkular.cmdgw.ws.test;

import static org.mockito.Mockito.verify;

import java.net.URLEncoder;
import java.util.List;

import org.hawkular.inventory.api.model.CanonicalPath;
import org.jboss.as.controller.client.ModelControllerClient;
import org.jboss.as.controller.descriptions.ModelDescriptionConstants;
import org.jboss.dmr.ModelNode;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;
import org.testng.AssertJUnit;
import org.testng.annotations.Test;

import com.squareup.okhttp.Request;
import com.squareup.okhttp.Response;
import com.squareup.okhttp.ws.WebSocket;
import com.squareup.okhttp.ws.WebSocket.PayloadType;
import com.squareup.okhttp.ws.WebSocketCall;
import com.squareup.okhttp.ws.WebSocketListener;

import okio.BufferedSource;

/**
 * @author <a href="https://github.com/ppalaga">Peter Palaga</a>
 */
public class DatasourceCommandITest extends AbstractCommandITest {
    private static final String datasourceConnectionUrl = "jdbc:h2:mem:test;DB_CLOSE_DELAY=-1";
    private static final String datasourceJndiName = "java:/testH2Ds";
    private static final String datasourceName = "testH2Ds";
    private static final String driverClass = "org.h2.Driver";
    private static final String driverName = "h2";
    private static final String password = "sa";
    private static final String userName = "sa";
    private static final String xaDataSourceClass = "org.h2.jdbcx.JdbcDataSource";
    private static final String xaDatasourceJndiName = "java:/testXaDs";
    private static final String xaDatasourceName = "testXaDs";
    private static final String xaDataSourceUrl = "jdbc:h2:mem:test";

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

            Request request = new Request.Builder().url(baseGwUri + "/ui/ws").build();
            WebSocketListener mockListener = Mockito.mock(WebSocketListener.class);
            WebSocketListener openingListener = new TestListener(mockListener, writeExecutor) {

                @Override
                public void onOpen(WebSocket webSocket, Response response) {
                    send(webSocket,
                            "AddDatasourceRequest={\"authentication\":" + authentication + ", " //
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
                                    + "}");
                    super.onOpen(webSocket, response);
                }
            };

            WebSocketCall.create(client, request).enqueue(openingListener);

            verify(mockListener, Mockito.timeout(10000).times(1)).onOpen(Mockito.any(), Mockito.any());
            ArgumentCaptor<BufferedSource> bufferedSourceCaptor = ArgumentCaptor.forClass(BufferedSource.class);
            verify(mockListener, Mockito.timeout(10000).times(3)).onMessage(bufferedSourceCaptor.capture(),
                    Mockito.same(PayloadType.TEXT));

            List<BufferedSource> receivedMessages = bufferedSourceCaptor.getAllValues();
            int i = 0;

            String sessionId = assertWelcomeResponse(receivedMessages.get(i++).readUtf8());

            String expectedRe = "\\QGenericSuccessResponse={\"message\":" + "\"The request has been forwarded to feed ["
                    + wfPath.ids().getFeedId() + "] (\\E.*";

            String msg = receivedMessages.get(i++).readUtf8();
            AssertJUnit.assertTrue("[" + msg + "] does not match [" + expectedRe + "]", msg.matches(expectedRe));

            AssertJUnit.assertEquals("AddDatasourceResponse={"//
                    + "\"xaDatasource\":false,"//
                    + "\"datasourceName\":\"" + datasourceName + "\","//
                    + "\"resourcePath\":\"" + wfPath + "\","//
                    + "\"destinationSessionId\":\"" + sessionId + "\","//
                    + "\"status\":\"OK\","//
                    + "\"message\":\"Added Datasource: " + datasourceName + "\""//
                    + "}", receivedMessages.get(i++).readUtf8());

            assertResourceExists(mcc, dsAddress, true);

        }
    }

    @Test(dependsOnGroups = { "exclusive-inventory-access" })
    public void testAddXaDatasource() throws Throwable {
        waitForAccountsAndInventory();
        ModelNode dsAddress = datasourceAddess(xaDatasourceName, true);
        CanonicalPath wfPath = getCurrentASPath();

        try (ModelControllerClient mcc = newModelControllerClient()) {
            assertResourceExists(mcc, dsAddress, false);

            Request request = new Request.Builder().url(baseGwUri + "/ui/ws").build();
            WebSocketListener mockListener = Mockito.mock(WebSocketListener.class);
            WebSocketListener openingListener = new TestListener(mockListener, writeExecutor) {

                @Override
                public void onOpen(WebSocket webSocket, Response response) {
                    send(webSocket,
                            "AddDatasourceRequest={\"authentication\":" + authentication + ", " //
                                    + "\"resourcePath\":\"" + wfPath.toString() + "\"," //
                                    + "\"xaDatasource\":\"true\"," //
                                    + "\"datasourceName\":\"" + xaDatasourceName + "\"," //
                                    + "\"jndiName\":\"" + xaDatasourceJndiName + "\"," //
                                    + "\"driverName\":\"" + driverName + "\"," //
                                    + "\"xaDataSourceClass\":\"" + xaDataSourceClass + "\"," //
                                    + "\"datasourceProperties\":{\"URL\":\"" + xaDataSourceUrl + "\"}," //
                                    + "\"userName\":\"" + userName + "\"," //
                                    + "\"password\":\"" + password + "\"" //
                                    + "}");
                    super.onOpen(webSocket, response);
                }
            };

            WebSocketCall.create(client, request).enqueue(openingListener);

            verify(mockListener, Mockito.timeout(10000).times(1)).onOpen(Mockito.any(), Mockito.any());
            ArgumentCaptor<BufferedSource> bufferedSourceCaptor = ArgumentCaptor.forClass(BufferedSource.class);
            verify(mockListener, Mockito.timeout(10000).times(3)).onMessage(bufferedSourceCaptor.capture(),
                    Mockito.same(PayloadType.TEXT));

            List<BufferedSource> receivedMessages = bufferedSourceCaptor.getAllValues();
            int i = 0;

            String sessionId = assertWelcomeResponse(receivedMessages.get(i++).readUtf8());

            String expectedRe = "\\QGenericSuccessResponse={\"message\":" + "\"The request has been forwarded to feed ["
                    + wfPath.ids().getFeedId() + "] (\\E.*";

            String msg = receivedMessages.get(i++).readUtf8();
            AssertJUnit.assertTrue("[" + msg + "] does not match [" + expectedRe + "]", msg.matches(expectedRe));

            AssertJUnit.assertEquals("AddDatasourceResponse={"//
                    + "\"xaDatasource\":true,"//
                    + "\"datasourceName\":\"" + xaDatasourceName + "\","//
                    + "\"resourcePath\":\"" + wfPath + "\","//
                    + "\"destinationSessionId\":\"" + sessionId + "\","//
                    + "\"status\":\"OK\","//
                    + "\"message\":\"Added Datasource: " + xaDatasourceName + "\""//
                    + "}", receivedMessages.get(i++).readUtf8());

            assertResourceExists(mcc, dsAddress, true);

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

            Request request = new Request.Builder().url(baseGwUri + "/ui/ws").build();
            WebSocketListener mockListener = Mockito.mock(WebSocketListener.class);
            WebSocketListener openingListener = new TestListener(mockListener, writeExecutor) {

                @Override
                public void onOpen(WebSocket webSocket, Response response) {
                    send(webSocket,
                            "RemoveDatasourceRequest={\"authentication\":" + authentication + ", " //
                                    + "\"resourcePath\":\"" + removePath + "\"" //
                                    + "}");
                    super.onOpen(webSocket, response);
                }
            };

            WebSocketCall.create(client, request).enqueue(openingListener);

            verify(mockListener, Mockito.timeout(10000).times(1)).onOpen(Mockito.any(), Mockito.any());
            ArgumentCaptor<BufferedSource> bufferedSourceCaptor = ArgumentCaptor.forClass(BufferedSource.class);
            verify(mockListener, Mockito.timeout(10000).times(3)).onMessage(bufferedSourceCaptor.capture(),
                    Mockito.same(PayloadType.TEXT));

            List<BufferedSource> receivedMessages = bufferedSourceCaptor.getAllValues();
            int i = 0;

            String sessionId = assertWelcomeResponse(receivedMessages.get(i++).readUtf8());

            String expectedRe = "\\QGenericSuccessResponse={\"message\":" + "\"The request has been forwarded to feed ["
                    + wfPath.ids().getFeedId() + "] (\\E.*";

            String msg = receivedMessages.get(i++).readUtf8();
            AssertJUnit.assertTrue("[" + msg + "] does not match [" + expectedRe + "]", msg.matches(expectedRe));

            AssertJUnit.assertEquals("RemoveDatasourceResponse={"//
                    + "\"resourcePath\":\"" + removePath.toString() + "\"," //
                    + "\"destinationSessionId\":\"" + sessionId + "\"," //
                    + "\"status\":\"OK\","//
                    + "\"message\":\"Performed [Remove] on a [Datasource] given by Inventory path [" + removePath
                    + "]\""//
                    + "}", receivedMessages.get(i++).readUtf8());

            assertResourceExists(mcc, dsAddress, false);

        }
    }

    @Test(dependsOnMethods = { "testAddXaDatasource" })
    public void testRemoveXaDatasource() throws Throwable {
        waitForAccountsAndInventory();

        CanonicalPath wfPath = getCurrentASPath();
        ModelNode dsAddress = datasourceAddess(xaDatasourceName, true);

        String removePath = wfPath.toString().replaceFirst("\\~+$", "")
                + URLEncoder.encode("~/subsystem=datasources/xa-data-source=" + xaDatasourceName, "UTF-8");

        try (ModelControllerClient mcc = newModelControllerClient()) {
            assertResourceExists(mcc, dsAddress, true);

            Request request = new Request.Builder().url(baseGwUri + "/ui/ws").build();
            WebSocketListener mockListener = Mockito.mock(WebSocketListener.class);
            WebSocketListener openingListener = new TestListener(mockListener, writeExecutor) {

                @Override
                public void onOpen(WebSocket webSocket, Response response) {
                    send(webSocket,
                            "RemoveDatasourceRequest={\"authentication\":" + authentication + ", " //
                                    + "\"resourcePath\":\"" + removePath + "\"" //
                                    + "}");
                    super.onOpen(webSocket, response);
                }
            };

            WebSocketCall.create(client, request).enqueue(openingListener);

            verify(mockListener, Mockito.timeout(10000).times(1)).onOpen(Mockito.any(), Mockito.any());
            ArgumentCaptor<BufferedSource> bufferedSourceCaptor = ArgumentCaptor.forClass(BufferedSource.class);
            verify(mockListener, Mockito.timeout(10000).times(3)).onMessage(bufferedSourceCaptor.capture(),
                    Mockito.same(PayloadType.TEXT));

            List<BufferedSource> receivedMessages = bufferedSourceCaptor.getAllValues();
            int i = 0;

            String sessionId = assertWelcomeResponse(receivedMessages.get(i++).readUtf8());

            String expectedRe = "\\QGenericSuccessResponse={\"message\":" + "\"The request has been forwarded to feed ["
                    + wfPath.ids().getFeedId() + "] (\\E.*";

            String msg = receivedMessages.get(i++).readUtf8();
            AssertJUnit.assertTrue("[" + msg + "] does not match [" + expectedRe + "]", msg.matches(expectedRe));

            AssertJUnit.assertEquals("RemoveDatasourceResponse={"//
                    + "\"resourcePath\":\"" + removePath.toString() + "\"," //
                    + "\"destinationSessionId\":\"" + sessionId + "\"," //
                    + "\"status\":\"OK\","//
                    + "\"message\":\"Performed [Remove] on a [Datasource] given by Inventory path [" + removePath
                    + "]\""//
                    + "}", receivedMessages.get(i++).readUtf8());

            assertResourceExists(mcc, dsAddress, false);

        }
    }

}
