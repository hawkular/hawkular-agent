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

import java.io.File;
import java.net.URL;
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
public class AddJdbcDriverCommandITest extends AbstractCommandITest {

    @Test
    public void testAddJdbcDriver() throws Throwable {
        waitForAccountsAndInventory();

        List<Resource> wfs = getResources("/test/resources", 1);
        Assert.assertEquals(1, wfs.size());
        CanonicalPath wfPath = wfs.get(0).getPath();
        String feedId = wfPath.ids().getFeedId();
        // http://localhost:8080/hawkular/inventory/test/slama/resourceTypes/JDBC%20Driver/resources
        /* Here we wait for Agent to write the built-in h2 driver to inventory */
        List<Resource> drivers = getResources("/test/" + feedId + "/resourceTypes/JDBC%20Driver/resources", 1);
        Assert.assertEquals(1, drivers.size());
        final String driverName = "mysql";

        /* OK, h2 is there let's add a new MySQL Driver */
        final String driverJarRawUrl =
                "http://central.maven.org/maven2/mysql/mysql-connector-java/5.1.36/mysql-connector-java-5.1.36.jar";
        URL driverJarUrl = new URL(driverJarRawUrl);
        final String driverJarName = new File(driverJarUrl.getPath()).getName();

        Request request = new Request.Builder().url(baseGwUri + "/ui/ws").build();
        WebSocketListener mockListener = Mockito.mock(WebSocketListener.class);
        WebSocketListener openingListener = new TestListener(mockListener, writeExecutor) {

            @Override
            public void onOpen(WebSocket webSocket, Response response) {
                send(webSocket,
                        "AddJdbcDriverRequest={\"authentication\":" + authentication + ", " //
                                + "\"resourcePath\":\"" + wfPath.toString() + "\"," //
                                + "\"driverName\":\"" + driverName + "\"," //
                                + "\"moduleName\":\"com.mysql\"," //
                                + "\"driverJarName\":\"" + driverJarName + "\"" //
                                + "}",
                        driverJarUrl);
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
        Assert.assertTrue("[" + msg + "] does not match [" + expectedRe + "]", msg.matches(expectedRe));

        Assert.assertEquals("AddJdbcDriverResponse={" + "\"resourcePath\":\"" + wfPath + "\"," //
                + "\"status\":\"OK\"," //
                + "\"message\":\"Added JDBC Driver: " + driverName + "\"," //
        // FIXME HAWKULAR-603 the server should not forward the authentication to UI
                + "\"authentication\":" + authentication //
                + "}", receivedMessages.get(i++).readUtf8());

        Assert.assertEquals(2, receivedMessages.size());

        // there is a good hope that https://issues.jboss.org/browse/HWKAGENT-7
        // brings a way to sync with the inventory, so that we can validate that we really added it.
        // List<Resource> driversAfter = getResources("/test/" + feedId + "/resourceTypes/JDBC%20Driver/resources", 2);
        // Assert.assertEquals(2, driversAfter.size());
        // Assert.assertTrue("The [" + driverName + "] could not be found after it was added",
        // driversAfter.stream().filter(r -> r.getId().endsWith("=" + driverName)).findFirst().isPresent());

    }

}
