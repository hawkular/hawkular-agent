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

import java.io.IOException;
import java.util.List;

import org.junit.Assert;
import org.junit.Ignore;
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
public class EchoCommandITest extends AbstractCommandITest {
    @Test
    public void testEcho() throws InterruptedException, IOException {

        Request request = new Request.Builder().url(baseGwUri + "/ui/ws").build();
        WebSocketListener mockListener = Mockito.mock(WebSocketListener.class);

        WebSocketListener openingListener = new TestListener(mockListener, writeExecutor) {
            @Override
            public void onOpen(WebSocket webSocket, Response response) {
                send(webSocket, "EchoRequest={\"authentication\": " + authentication
                        + ", \"echoMessage\": \"Yodel Ay EEE Oooo\"}");
                super.onOpen(webSocket, response);
            }
        };

        WebSocketCall.create(client, request).enqueue(openingListener);

        verify(mockListener, Mockito.timeout(10000).times(1)).onOpen(Mockito.any(), Mockito.any());
        ArgumentCaptor<BufferedSource> bufferedSourceCaptor = ArgumentCaptor.forClass(BufferedSource.class);
        verify(mockListener, Mockito.timeout(10000).times(1)).onMessage(bufferedSourceCaptor.capture(),
                Mockito.same(PayloadType.TEXT));

        List<BufferedSource> receivedMessages = bufferedSourceCaptor.getAllValues();
        int i = 0;
        Assert.assertEquals("EchoResponse={\"reply\":\"ECHO [Yodel Ay EEE Oooo]\"}",
                receivedMessages.get(i++).readUtf8());

        Assert.assertEquals(1, receivedMessages.size());

    }

    @Test
    @Ignore // created as a proof of concept of a ws test. Can be removed later
    public void testWsOrg() throws InterruptedException, IOException {

        Request request = new Request.Builder().url("ws://echo.websocket.org").build();
        WebSocketListener mockListener = Mockito.mock(WebSocketListener.class);

        WebSocketListener openingListener = new TestListener(mockListener, writeExecutor) {
            @Override
            public void onOpen(WebSocket webSocket, Response response) {
                send(webSocket, "whatever");
                super.onOpen(webSocket, response);
            }
        };

        WebSocketCall.create(client, request).enqueue(openingListener);

        verify(mockListener, Mockito.timeout(10000).times(1)).onOpen(Mockito.any(), Mockito.any());
        ArgumentCaptor<BufferedSource> bufferedSourceCaptor = ArgumentCaptor.forClass(BufferedSource.class);
        verify(mockListener, Mockito.timeout(10000).times(1)).onMessage(bufferedSourceCaptor.capture(),
                Mockito.same(PayloadType.TEXT));
        Assert.assertEquals("whatever", bufferedSourceCaptor.getValue().readUtf8());

    }
}
