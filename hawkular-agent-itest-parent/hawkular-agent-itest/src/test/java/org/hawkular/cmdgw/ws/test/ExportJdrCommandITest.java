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
import static org.testng.Assert.assertNotNull;
import static org.testng.Assert.fail;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.Charset;
import java.util.Arrays;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import org.hawkular.inventory.api.model.CanonicalPath;
import org.jboss.as.controller.client.ModelControllerClient;
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
 * @author Juraci Paixão Kröhling
 */
public class ExportJdrCommandITest extends AbstractCommandITest {

    @Test(dependsOnGroups = { "no-dependencies" }, groups = "export-jdr")
    public void exportJdrCommand() throws Throwable {
        waitForAccountsAndInventory();

        CanonicalPath wfPath = getCurrentASPath();

        try (ModelControllerClient ignored = newModelControllerClient()) {
            Request request = new Request.Builder().url(baseGwUri + "/ui/ws").build();
            WebSocketListener mockListener = Mockito.mock(WebSocketListener.class);
            WebSocketListener openingListener = new TestListener(mockListener, writeExecutor) {

                @Override
                public void onOpen(WebSocket webSocket, Response response) {
                    send(webSocket,
                            "ExportJdrRequest={\"authentication\":" + authentication + ", " //
                                    + "\"resourcePath\":\"" + wfPath.toString() + "\"" //
                                    + "}");
                    super.onOpen(webSocket, response);
                }
            };

            WebSocketCall.create(client, request).enqueue(openingListener);

            // we expect to have sent 1 message to the server
            verify(mockListener, Mockito.timeout(10_000).times(1)).onOpen(Mockito.any(), Mockito.any());
            ArgumentCaptor<BufferedSource> bufferedSourceCaptor = ArgumentCaptor.forClass(BufferedSource.class);

            // we expect to have received 2 text messages in the first 10 seconds, ACKs of our original message
            verify(mockListener, Mockito.timeout(10_000).times(2)).onMessage(bufferedSourceCaptor.capture(),
                    Mockito.same(PayloadType.TEXT));

            // we expect to have received 1 binary message in the first 120 seconds, as JDR takes long to execute
            // this is the message that includes a string part and a binary part
            // but at this point, it's enough to know that the socket's message itself is binary
            verify(mockListener, Mockito.timeout(120_000).times(1)).onMessage(bufferedSourceCaptor.capture(),
                    Mockito.same(PayloadType.BINARY));

            List<BufferedSource> receivedMessages = bufferedSourceCaptor.getAllValues();
            assertWelcomeResponse(receivedMessages.get(0).readUtf8());

            String expectedRe = "\\QGenericSuccessResponse={\"message\":" + "\"The request has been forwarded to feed ["
                    + wfPath.ids().getFeedId() + "] (\\E.*";

            String msg = receivedMessages.get(1).readUtf8();
            AssertJUnit.assertTrue("[" + msg + "] does not match [" + expectedRe + "]", msg.matches(expectedRe));

            // our last message is a binary message, so, we read it as a byte array
            byte[] executeJdrResponsePayload = receivedMessages.get(2).readByteArray();

            // here, we get the first 2k bytes and try to extract the message from it
            // our text part is usually around 500 bytes, so, 2k gives a good room for the message...
            String messagePartOfPayload = extractTextPartFromBinaryMessage(executeJdrResponsePayload, 2048);

            // then, we get the size of the text part
            int binLastCloseBrackets = messagePartOfPayload.length();

            // and use it as the starting point on the original payload to get the binary part
            // for instance: ExportJdrResponse={}BINARY
            // we get the size of "ExportJdrResponse={}", and get only BINARY on the next byte array
            byte[] binaryPartOfPayload = Arrays.copyOfRange(executeJdrResponsePayload, binLastCloseBrackets,
                    executeJdrResponsePayload.length);

            // at this point, we should have a valid ZIP file in our byte array
            InputStream binaryInputStream = new ByteArrayInputStream(binaryPartOfPayload);
            ZipInputStream zipInputStream = new ZipInputStream(binaryInputStream);

            // if we have a valid ZIP, we should be able to get at least one entry of it
            ZipEntry entry = zipInputStream.getNextEntry();

            // it should be enough to assert that it's a valid ZIP file
            // when the ZIP is not valid, the entry will be null
            assertNotNull(entry);
            assertNotNull(entry.getName());
        }
    }

    private String extractTextPartFromBinaryMessage(byte[] message, int maxTextPayloadSize) {
        // first, we create a string based on the first bytes of the message
        String payloadAsString = new String(Arrays.copyOf(message, maxTextPayloadSize), Charset.forName("UTF-8"));

        // we track the number of JSON objects that were opened and track which is the last bracked
        int indexLastCloseBrackets = -1;
        int numberOfOpenedObjects = 0;

        // then we go char by char, determining what's our last char on the text part of the message
        char[] charArray = payloadAsString.toCharArray();
        for (int i = 0 ; i < charArray.length ; i++) {
            char c = charArray[i];

            // a new json object!
            if (c == '{') {
                numberOfOpenedObjects++;
            }

            // closing the previously opened json object
            if (c == '}') {
                numberOfOpenedObjects--;
                if (numberOfOpenedObjects == 0) {
                    // at this point, we closed all json objects that we have opened, so, we are at the end of the
                    // text part of the message... as we are 0-based, we need to add 1 to get the last } char
                    indexLastCloseBrackets = i+1;
                    break;
                }
            }

            if (i > maxTextPayloadSize) {
                // if we reached this point, it means that we could not find the last closing bracket...
                // this can have two reasons:
                // 1) we never found an opening bracket in the first place, perhaps due to an encoding issue?
                // 2) the message is indeed bigger than what we expected and the closing bracket is further away
                // on the second case, it would be acceptable to get the next chunk of bytes from the message,
                // but for this case, we are quite certain that the payload will not exceed the given parameter...
                // if so, it means we added more stuff to the payload, so, it would be a one-time change to bump the
                // max-size.
                String parsed = payloadAsString.substring(0, i); // to include the last }
                fail("Maximum text payload size exceeded. Parsed so far: " + parsed);
            }
        }

        return payloadAsString.substring(0, indexLastCloseBrackets);
    }

}
