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

import org.hawkular.dmrclient.Address;
import org.hawkular.inventory.api.model.CanonicalPath;
import org.jboss.as.controller.client.ModelControllerClient;
import org.testng.annotations.Test;

public class StatisticsControlCommandITest extends AbstractCommandITest {

    @Test(groups = { "no-dependencies" })
    public void testEnableStatistics() throws Throwable {
        waitForAccountsAndInventory();

        CanonicalPath wfPath = getCurrentASPath();

        try (ModelControllerClient mcc = newModelControllerClient()) {

            String req = "StatisticsControlRequest={\"authentication\":" + authentication + ", "
                    + "\"resourcePath\":\"" + wfPath.toString() + "\","
                    + "\"web\":\"ENABLED\","
                    + "\"transactions\":\"ENABLED\","
                    + "\"datasources\":\"ENABLED\","
                    + "\"infinispan\":\"ENABLED\","
                    + "\"ejb3\":\"ENABLED\","
                    + "\"messaging\":\"ENABLED\""
                    + "}";

            String response = "StatisticsControlResponse={"
                    + "\"resourcePath\":\"" + wfPath + "\","
                    + "\"destinationSessionId\":\"{{sessionId}}\","
                    + "\"status\":\"OK\","
                    + "\"message\":\"Statistics for server [" + wfPath.toString()
                    + "] have been enabled for subsystems "
                    + "[datasources, EJB3, infinispan, messaging, transactions, web]"
                    + ", disabled for subsystems []"
                    + ", and left as-is for subsystems []\","
                    + "\"serverRefreshIndicator\":\"RELOAD-REQUIRED\""
                    + "}";

            try (TestWebSocketClient testClient = TestWebSocketClient.builder()
                    .url(baseGwUri + "/ui/ws")
                    .expectWelcome(req)
                    .expectGenericSuccess(wfPath.ids().getFeedId())
                    .expectText(response)
                    .build()) {
                testClient.validate(10000);
            }

            assertNodeAttributeEquals(mcc,
                    Address.parse("/subsystem=undertow").getAddressNode(),
                    "statistics-enabled",
                    "true");
            assertNodeAttributeEquals(mcc,
                    Address.parse("/subsystem=transactions").getAddressNode(),
                    "enable-statistics",
                    "true");
            assertNodeAttributeEquals(mcc,
                    Address.parse("/subsystem=ejb3").getAddressNode(),
                    "enable-statistics",
                    "true");
            assertNodeAttributeEquals(mcc,
                    Address.parse("/subsystem=messaging-activemq/server=default").getAddressNode(),
                    "statistics-enabled",
                    "true");
            assertNodeAttributeEquals(mcc,
                    Address.parse("/subsystem=infinispan/cache-container=ejb").getAddressNode(),
                    "statistics-enabled",
                    "true");

        }
    }

    @Test(groups = { "no-dependencies" }, dependsOnMethods = { "testEnableStatistics" })
    public void testDisableStatistics() throws Throwable {
        waitForAccountsAndInventory();

        CanonicalPath wfPath = getCurrentASPath();

        try (ModelControllerClient mcc = newModelControllerClient()) {

            String req = "StatisticsControlRequest={\"authentication\":" + authentication + ", "
                    + "\"resourcePath\":\"" + wfPath.toString() + "\","
                    + "\"web\":\"DISABLED\","
                    + "\"transactions\":\"DISABLED\","
                    + "\"datasources\":\"DISABLED\","
                    + "\"infinispan\":\"DISABLED\","
                    + "\"ejb3\":\"DISABLED\","
                    + "\"messaging\":\"DISABLED\""
                    + "}";

            String response = "StatisticsControlResponse={"
                    + "\"resourcePath\":\"" + wfPath + "\","
                    + "\"destinationSessionId\":\"{{sessionId}}\","
                    + "\"status\":\"OK\","
                    + "\"message\":\"Statistics for server [" + wfPath.toString()
                    + "] have been enabled for subsystems [], disabled for subsystems "
                    + "[datasources, EJB3, infinispan, messaging, transactions, web]"
                    + ", and left as-is for subsystems []\","
                    + "\"serverRefreshIndicator\":\"RELOAD-REQUIRED\""
                    + "}";

            try (TestWebSocketClient testClient = TestWebSocketClient.builder()
                    .url(baseGwUri + "/ui/ws")
                    .expectWelcome(req)
                    .expectGenericSuccess(wfPath.ids().getFeedId())
                    .expectText(response)
                    .build()) {
                testClient.validate(10000);
            }

            assertNodeAttributeEquals(mcc,
                    Address.parse("/subsystem=undertow").getAddressNode(),
                    "statistics-enabled",
                    "false");
            assertNodeAttributeEquals(mcc,
                    Address.parse("/subsystem=transactions").getAddressNode(),
                    "enable-statistics",
                    "false");
            assertNodeAttributeEquals(mcc,
                    Address.parse("/subsystem=ejb3").getAddressNode(),
                    "enable-statistics",
                    "false");
            assertNodeAttributeEquals(mcc,
                    Address.parse("/subsystem=messaging-activemq/server=default").getAddressNode(),
                    "statistics-enabled",
                    "false");
            assertNodeAttributeEquals(mcc,
                    Address.parse("/subsystem=infinispan/cache-container=ejb").getAddressNode(),
                    "statistics-enabled",
                    "false");

        }
    }

    @Test(groups = { "no-dependencies" }, dependsOnMethods = { "testDisableStatistics" })
    public void testEnableStatisticsSubset() throws Throwable {
        waitForAccountsAndInventory();

        CanonicalPath wfPath = getCurrentASPath();

        try (ModelControllerClient mcc = newModelControllerClient()) {

            // sanity check - make sure statistics are turned off (we'll turn on tx stats next)
            assertNodeAttributeEquals(mcc,
                    Address.parse("/subsystem=undertow").getAddressNode(),
                    "statistics-enabled",
                    "false");
            assertNodeAttributeEquals(mcc,
                    Address.parse("/subsystem=transactions").getAddressNode(),
                    "enable-statistics",
                    "false");
            assertNodeAttributeEquals(mcc,
                    Address.parse("/subsystem=ejb3").getAddressNode(),
                    "enable-statistics",
                    "false");

            // turn on statistics for only one subsystem, leaving the rest disabled
            String req = "StatisticsControlRequest={\"authentication\":" + authentication + ", "
                    + "\"resourcePath\":\"" + wfPath.toString() + "\","
                    + "\"transactions\":\"ENABLED\""
                    + "}";

            String response = "StatisticsControlResponse={"
                    + "\"resourcePath\":\"" + wfPath + "\","
                    + "\"destinationSessionId\":\"{{sessionId}}\","
                    + "\"status\":\"OK\","
                    + "\"message\":\"Statistics for server [" + wfPath.toString()
                    + "] have been enabled for subsystems [transactions], disabled for subsystems []"
                    + ", and left as-is for subsystems [datasources, EJB3, infinispan, messaging, web]\","
                    + "\"serverRefreshIndicator\":\"RELOAD-REQUIRED\""
                    + "}";

            try (TestWebSocketClient testClient = TestWebSocketClient.builder()
                    .url(baseGwUri + "/ui/ws")
                    .expectWelcome(req)
                    .expectGenericSuccess(wfPath.ids().getFeedId())
                    .expectText(response)
                    .build()) {
                testClient.validate(10000);
            }

            assertNodeAttributeEquals(mcc,
                    Address.parse("/subsystem=undertow").getAddressNode(),
                    "statistics-enabled",
                    "false");
            assertNodeAttributeEquals(mcc,
                    Address.parse("/subsystem=transactions").getAddressNode(),
                    "enable-statistics",
                    "true");
            assertNodeAttributeEquals(mcc,
                    Address.parse("/subsystem=ejb3").getAddressNode(),
                    "enable-statistics",
                    "false");

        }
    }
}
