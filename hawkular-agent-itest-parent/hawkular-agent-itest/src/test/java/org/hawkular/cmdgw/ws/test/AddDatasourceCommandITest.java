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

import java.util.List;

import org.hawkular.inventory.api.model.CanonicalPath;
import org.hawkular.inventory.api.model.Resource;
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
public class AddDatasourceCommandITest extends AbstractCommandITest {

    @Test(dependsOnGroups = { "exclusive-inventory-access" })
    public void testAddXaDatasource() throws Throwable {
        waitForAccountsAndInventory();

        List<Resource> wfs = getResources("/test/resources", 1);
        AssertJUnit.assertEquals(1, wfs.size());
        CanonicalPath wfPath = wfs.get(0).getPath();

        try (ModelControllerClient mcc = newModelControllerClient()) {
            ModelNode datasourcesPath = new ModelNode().add(ModelDescriptionConstants.SUBSYSTEM, "datasources");
            /* There should be zero XA datasources there */
            assertResourceCount(mcc, datasourcesPath, "xa-data-source", 0);

            final String datasourceName = "testXaDs";
            final String jndiName = "java:/testXaDs";
            final String driverName = "h2";
            final String xaDataSourceClass = "org.h2.jdbcx.JdbcDataSource";
            final String xaDataSourceUrl = "jdbc:h2:mem:test";
            final String userName = "sa";
            final String password = "sa";

            Request request = new Request.Builder().url(baseGwUri + "/ui/ws").build();
            WebSocketListener mockListener = Mockito.mock(WebSocketListener.class);
            WebSocketListener openingListener = new TestListener(mockListener, writeExecutor) {

                @Override
                public void onOpen(WebSocket webSocket, Response response) {
                    send(webSocket,
                            "AddDatasourceRequest={\"authentication\":" + authentication + ", " //
                                    + "\"resourcePath\":\"" + wfPath.toString() + "\"," //
                                    + "\"xaDatasource\":\"true\"," //
                                    + "\"datasourceName\":\"" + datasourceName + "\"," //
                                    + "\"jndiName\":\"" + jndiName + "\"," //
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
            verify(mockListener, Mockito.timeout(10000).times(2)).onMessage(bufferedSourceCaptor.capture(),
                    Mockito.same(PayloadType.TEXT));

            List<BufferedSource> receivedMessages = bufferedSourceCaptor.getAllValues();
            int i = 0;

            String expectedRe = "\\QGenericSuccessResponse={\"message\":"
                    + "\"The execution request has been forwarded to feed [" + wfPath.ids().getFeedId() + "] (\\E.*";

            String msg = receivedMessages.get(i++).readUtf8();
            AssertJUnit.assertTrue("[" + msg + "] does not match [" + expectedRe + "]", msg.matches(expectedRe));

            AssertJUnit.assertEquals("AddDatasourceResponse={" + "\"resourcePath\":\"" + wfPath + "\"," //
                    + "\"status\":\"OK\"," //
                    + "\"message\":\"Added Datasource: " + datasourceName + "\"" //
                    + "}", receivedMessages.get(i++).readUtf8());

            AssertJUnit.assertEquals(2, receivedMessages.size());

            ModelNode address = new ModelNode().add(ModelDescriptionConstants.SUBSYSTEM, "datasources")
                    .add("xa-data-source", datasourceName);
            assertResourceExists(mcc, address,
                    "XA Datasource " + datasourceName + " cannot be found after it was added: %s");
            assertResourceCount(mcc, datasourcesPath, "xa-data-source", 1);

        }

    }

    @Test(dependsOnGroups = { "exclusive-inventory-access" })
    public void testAddDatasource() throws Throwable {
        waitForAccountsAndInventory();

        List<Resource> wfs = getResources("/test/resources", 1);
        AssertJUnit.assertEquals(1, wfs.size());
        CanonicalPath wfPath = wfs.get(0).getPath();

        try (ModelControllerClient mcc = newModelControllerClient()) {
            ModelNode datasourcesPath = new ModelNode().add(ModelDescriptionConstants.SUBSYSTEM, "datasources");
            assertResourceCount(mcc, datasourcesPath, "data-source", 2);

            final String datasourceName = "testH2Ds";
            final String jndiName = "java:/testH2Ds";
            final String driverName = "h2";
            final String driverClass = "org.h2.Driver";
            final String connectionUrl = "jdbc:h2:mem:test;DB_CLOSE_DELAY=-1";
            final String userName = "sa";
            final String password = "sa";

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
                                    + "\"jndiName\":\"" + jndiName + "\"," //
                                    + "\"driverName\":\"" + driverName + "\"," //
                                    + "\"driverClass\":\"" + driverClass + "\"," //
                                    + "\"connectionUrl\":\"" + connectionUrl + "\"," //
                                    + "\"userName\":\"" + userName + "\"," //
                                    + "\"password\":\"" + password + "\"" //
                                    + "}");
                    super.onOpen(webSocket, response);
                }
            };

            WebSocketCall.create(client, request).enqueue(openingListener);

            verify(mockListener, Mockito.timeout(10000).times(1)).onOpen(Mockito.any(), Mockito.any());
            ArgumentCaptor<BufferedSource> bufferedSourceCaptor = ArgumentCaptor.forClass(BufferedSource.class);
            verify(mockListener, Mockito.timeout(10000).times(2)).onMessage(bufferedSourceCaptor.capture(),
                    Mockito.same(PayloadType.TEXT));

            List<BufferedSource> receivedMessages = bufferedSourceCaptor.getAllValues();
            int i = 0;

            String expectedRe = "\\QGenericSuccessResponse={\"message\":"
                    + "\"The execution request has been forwarded to feed [" + wfPath.ids().getFeedId() + "] (\\E.*";

            String msg = receivedMessages.get(i++).readUtf8();
            AssertJUnit.assertTrue("[" + msg + "] does not match [" + expectedRe + "]", msg.matches(expectedRe));

            AssertJUnit.assertEquals("AddDatasourceResponse={" + "\"resourcePath\":\"" + wfPath + "\"," //
                    + "\"status\":\"OK\"," //
                    + "\"message\":\"Added Datasource: " + datasourceName + "\"" //
                    + "}", receivedMessages.get(i++).readUtf8());

            AssertJUnit.assertEquals(2, receivedMessages.size());

            ModelNode address = new ModelNode().add(ModelDescriptionConstants.SUBSYSTEM, "datasources")
                    .add("data-source", datasourceName);

            assertResourceExists(mcc, address,
                    "Datasource " + datasourceName + " cannot be found after it was added: %s");
            assertResourceCount(mcc, datasourcesPath, "data-source", 3);

        }
    }
}
