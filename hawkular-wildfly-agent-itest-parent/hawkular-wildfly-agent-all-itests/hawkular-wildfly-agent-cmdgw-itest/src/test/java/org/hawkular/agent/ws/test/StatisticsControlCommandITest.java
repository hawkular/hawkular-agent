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

import org.hawkular.agent.test.HawkularWildFlyAgentContextITest;
import org.hawkular.cmdgw.ws.test.TestWebSocketClient;
import org.hawkular.dmrclient.Address;
import org.hawkular.inventory.paths.CanonicalPath;
import org.jboss.as.controller.client.ModelControllerClient;
import org.junit.AfterClass;
import org.testng.annotations.Test;

public class StatisticsControlCommandITest extends AbstractCommandITest {
    public static final String GROUP = "StatisticsControlCommandITest";

    @AfterClass
    public void afterClass() throws Throwable {
        // WARNING: we want to reload to clear the reload-required state, but this puts the server in a bad state
        // reload();
    }

    @Test(groups = { GROUP }, dependsOnGroups = { HawkularWildFlyAgentContextITest.GROUP })
    public void testEnableStatistics() throws Throwable {
        waitForAccountsAndInventory();

        CanonicalPath wfPath = getHawkularWildFlyServerResourcePath();

        try (ModelControllerClient mcc = newHawkularModelControllerClient()) {

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
                    + "\"web\":\"ENABLED\","
                    + "\"transactions\":\"ENABLED\","
                    + "\"datasources\":\"ENABLED\","
                    + "\"infinispan\":\"ENABLED\","
                    + "\"ejb3\":\"ENABLED\","
                    + "\"messaging\":\"ENABLED\","
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
                    .expectText(response, TestWebSocketClient.Answer.CLOSE)
                    .expectClose()
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

        // test that we can see they were all enabled
        testReadOnlyStatistics(true);
    }

    @Test(groups = { GROUP }, dependsOnMethods = { "testEnableStatistics" })
    public void testDisableStatistics() throws Throwable {
        waitForAccountsAndInventory();

        CanonicalPath wfPath = getHawkularWildFlyServerResourcePath();

        try (ModelControllerClient mcc = newHawkularModelControllerClient()) {

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
                    + "\"web\":\"DISABLED\","
                    + "\"transactions\":\"DISABLED\","
                    + "\"datasources\":\"DISABLED\","
                    + "\"infinispan\":\"DISABLED\","
                    + "\"ejb3\":\"DISABLED\","
                    + "\"messaging\":\"DISABLED\","
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
                    .expectText(response, TestWebSocketClient.Answer.CLOSE)
                    .expectClose()
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

            // test that we can see they were all disabled
            testReadOnlyStatistics(false);
        }
    }

    private void testReadOnlyStatistics(boolean expectedEnabledFlag) throws Throwable {
        waitForAccountsAndInventory();

        CanonicalPath wfPath = getHawkularWildFlyServerResourcePath();

        try (ModelControllerClient mcc = newHawkularModelControllerClient()) {

            // sanity check - make sure they really are what we expect
            assertNodeAttributeEquals(mcc,
                    Address.parse("/subsystem=undertow").getAddressNode(),
                    "statistics-enabled",
                    String.valueOf(expectedEnabledFlag));
            assertNodeAttributeEquals(mcc,
                    Address.parse("/subsystem=transactions").getAddressNode(),
                    "enable-statistics",
                    String.valueOf(expectedEnabledFlag));
            assertNodeAttributeEquals(mcc,
                    Address.parse("/subsystem=ejb3").getAddressNode(),
                    "enable-statistics",
                    String.valueOf(expectedEnabledFlag));

            String flagString = (expectedEnabledFlag) ? "ENABLED" : "DISABLED";

            // just ask for statistics settings only - we aren't turning on or off any
            String req = "StatisticsControlRequest={\"authentication\":" + authentication + ", "
                    + "\"resourcePath\":\"" + wfPath.toString() + "\""
                    + "}";

            String response = "StatisticsControlResponse={"
                    + "\"web\":\"" + flagString + "\","
                    + "\"transactions\":\"" + flagString + "\","
                    + "\"datasources\":\"" + flagString + "\","
                    + "\"infinispan\":\"" + flagString + "\","
                    + "\"ejb3\":\"" + flagString + "\","
                    + "\"messaging\":\"" + flagString + "\","
                    + "\"resourcePath\":\"" + wfPath + "\","
                    + "\"destinationSessionId\":\"{{sessionId}}\","
                    + "\"status\":\"OK\","
                    + "\"message\":\"Statistics for server [" + wfPath.toString()
                    + "] have been enabled for subsystems [], disabled for subsystems []"
                    + ", and left as-is for subsystems "
                    + "[datasources, EJB3, infinispan, messaging, transactions, web]\""
                    + "}";

            try (TestWebSocketClient testClient = TestWebSocketClient.builder()
                    .url(baseGwUri + "/ui/ws")
                    .expectWelcome(req)
                    .expectGenericSuccess(wfPath.ids().getFeedId())
                    .expectText(response, TestWebSocketClient.Answer.CLOSE)
                    .expectClose()
                    .build()) {
                testClient.validate(10000);
            }
        }
    }

    @Test(groups = { GROUP }, dependsOnMethods = { "testDisableStatistics" })
    public void testEnableStatisticsSubset() throws Throwable {
        waitForAccountsAndInventory();

        CanonicalPath wfPath = getHawkularWildFlyServerResourcePath();

        try (ModelControllerClient mcc = newHawkularModelControllerClient()) {

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
                    + "\"web\":\"DISABLED\","
                    + "\"transactions\":\"ENABLED\","
                    + "\"datasources\":\"DISABLED\","
                    + "\"infinispan\":\"DISABLED\","
                    + "\"ejb3\":\"DISABLED\","
                    + "\"messaging\":\"DISABLED\","
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
                    .expectText(response, TestWebSocketClient.Answer.CLOSE)
                    .expectClose()
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
