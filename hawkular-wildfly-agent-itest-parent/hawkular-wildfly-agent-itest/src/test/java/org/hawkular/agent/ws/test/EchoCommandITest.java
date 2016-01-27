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
package org.hawkular.agent.ws.test;

import org.testng.annotations.Test;

/**
 * @author <a href="https://github.com/ppalaga">Peter Palaga</a>
 */
public class EchoCommandITest extends AbstractCommandITest {
    public static final String GROUP = "EchoCommandITest";

    @Test(groups = { GROUP })
    public void testEcho() throws Throwable {

        String req = "EchoRequest={\"authentication\": " + authentication
                + ", \"echoMessage\": \"Yodel Ay EEE Oooo\"}";
        String response = "EchoResponse={\"reply\":\"ECHO [Yodel Ay EEE Oooo]\"}";
        try (TestWebSocketClient testClient = TestWebSocketClient.builder()
                .url(baseGwUri + "/ui/ws")
                .expectWelcome(req)
                .expectText(response)
                .build()) {
            testClient.validate(10000);
        }
    }
}
