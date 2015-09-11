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
import org.junit.Assert;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;

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

    @Test
    public void testAddXaDatasource() throws Throwable {
        waitForAccountsAndInventory();

        List<Resource> wfs = getResources("/test/resources", 1);
        Assert.assertEquals(1, wfs.size());
        CanonicalPath wfPath = wfs.get(0).getPath();
        String feedId = wfPath.ids().getFeedId();
        // http://localhost:8080/hawkular/inventory/test/slama/resourceTypes/JDBC%20Driver/resources
        /* Here we wait for Agent to write the built-in h2 driver to inventory */
        List<Resource> datasources = getResources("/test/" + feedId + "/resourceTypes/Datasource/resources", 2);
        Assert.assertEquals(2, datasources.size());

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
                                + "\"xaDatasourceProperties\":{\"URL\":\"" + xaDataSourceUrl + "\"}," //
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
        System.out.println("msg = "+ msg);
        Assert.assertTrue("[" + msg + "] does not match [" + expectedRe + "]", msg.matches(expectedRe));

        Assert.assertEquals("AddDatasourceResponse={" + "\"resourcePath\":\"" + wfPath + "\"," //
                + "\"status\":\"OK\"," //
                + "\"message\":\"Added Datasource: " + datasourceName + "\"," //
        // FIXME HAWKULAR-603 the server should not forward the authentication to UI
                + "\"authentication\":" + authentication //
                + "}", receivedMessages.get(i++).readUtf8());

        Assert.assertEquals(2, receivedMessages.size());

    }

    @Test
    public void testAddDatasource() throws Throwable {
        waitForAccountsAndInventory();

        List<Resource> wfs = getResources("/test/resources", 1);
        Assert.assertEquals(1, wfs.size());
        CanonicalPath wfPath = wfs.get(0).getPath();
        String feedId = wfPath.ids().getFeedId();
        // http://localhost:8080/hawkular/inventory/test/slama/resourceTypes/JDBC%20Driver/resources
        /* Here we wait for Agent to write the built-in h2 driver to inventory */
        List<Resource> datasources = getResources("/test/" + feedId + "/resourceTypes/Datasource/resources", 2);
        Assert.assertEquals(2, datasources.size());

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
        System.out.println("msg = "+ msg);
        Assert.assertTrue("[" + msg + "] does not match [" + expectedRe + "]", msg.matches(expectedRe));

        Assert.assertEquals("AddDatasourceResponse={" + "\"resourcePath\":\"" + wfPath + "\"," //
                + "\"status\":\"OK\"," //
                + "\"message\":\"Added Datasource: " + datasourceName + "\"," //
        // FIXME HAWKULAR-603 the server should not forward the authentication to UI
                + "\"authentication\":" + authentication //
                + "}", receivedMessages.get(i++).readUtf8());

        Assert.assertEquals(2, receivedMessages.size());

    }
}
