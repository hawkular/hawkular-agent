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

import org.hawkular.cmdgw.ws.test.TestWebSocketClient;
import org.hawkular.inventory.paths.CanonicalPath;
import org.jboss.as.controller.client.ModelControllerClient;
import org.testng.annotations.Test;

/**
 * @author Juraci Paixão Kröhling
 */
public class ExportJdrCommandITest extends AbstractCommandITest {
    public static final String GROUP = "ExportJdrCommandITest";

    @Test(groups = { GROUP }, dependsOnGroups = { DeployApplicationITest.GROUP })
    public void exportJdrCommand() throws Throwable {
        waitForAccountsAndInventory();

        CanonicalPath wfPath = getHawkularWildFlyServerResourcePath();

        try (ModelControllerClient ignored = newHawkularModelControllerClient()) {
            String req = "ExportJdrRequest={\"authentication\":" + authentication + ", "
                    + "\"resourcePath\":\"" + wfPath.toString() + "\""
                    + "}";
            String responsePattern = "\\QExportJdrResponse={\\E.*";
            try (TestWebSocketClient testClient = TestWebSocketClient.builder()
                    .url(baseGwUri + "/ui/ws")
                    .readTimeout(240)//seconds
                    .expectWelcome(req)
                    .expectGenericSuccess(wfPath.ids().getFeedId())
                    .expectBinary(responsePattern, new TestWebSocketClient.ZipWithOneEntryMatcher(), TestWebSocketClient.Answer.CLOSE)
                    .expectClose()
                    .build()) {
                /* 240 seconds, as JDR takes long to execute */
                testClient.validate(240_000);
            }
        }
    }
}
