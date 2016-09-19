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

import java.io.File;
import java.net.URL;

import org.hawkular.agent.monitor.util.Util;
import org.hawkular.cmdgw.ws.test.TestWebSocketClient;
import org.hawkular.cmdgw.ws.test.TestWebSocketClient.MessageAnswer;
import org.hawkular.inventory.paths.CanonicalPath;
import org.jboss.as.controller.client.ModelControllerClient;
import org.jboss.as.controller.descriptions.ModelDescriptionConstants;
import org.jboss.dmr.ModelNode;
import org.testng.annotations.Test;

/**
 * @author <a href="https://github.com/ppalaga">Peter Palaga</a>
 */
public class JdbcDriverCommandITest extends AbstractCommandITest {
    public static final String GROUP = "JdbcDriverCommandITest";

    private static final String driverFileNameAfterAdd = "driver-after-add.node.txt";
    private static final String driverName = "mysql";

    private static ModelNode driverAddress() {
        return new ModelNode().add(ModelDescriptionConstants.SUBSYSTEM, "datasources").add("jdbc-driver", driverName);
    }

    @Test(groups = { GROUP }, dependsOnGroups = { ExportJdrCommandITest.GROUP })
    public void testAddJdbcDriver() throws Throwable {
        waitForAccountsAndInventory();

        CanonicalPath wfPath = getHawkularWildFlyServerResourcePath();
        final ModelNode driverAddress = driverAddress();

        try (ModelControllerClient mcc = newHawkularModelControllerClient()) {
            assertResourceExists(mcc, driverAddress, false);

            /* OK, h2 is there let's add a new MySQL Driver */
            final String driverJarRawUrl = "http://central.maven.org/maven2/mysql/mysql-connector-java/5.1.36/"
                    + "mysql-connector-java-5.1.36.jar";
            URL driverJarUrl = new URL(driverJarRawUrl);
            final String driverJarName = new File(driverJarUrl.getPath()).getName();

            String req = "AddJdbcDriverRequest={\"authentication\":" + authentication + ","
                    + "\"resourcePath\":\"" + wfPath.toString() + "\","
                    + "\"driverName\":\"" + driverName + "\","
                    + "\"driverClass\":\"com.mysql.jdbc.Driver\","
                    + "\"driverMajorVersion\":\"5\","
                    + "\"driverMinorVersion\":\"1\","
                    + "\"moduleName\":\"com.mysql\","
                    + "\"driverJarName\":\"" + driverJarName + "\""
                    + "}";
            String response = "AddJdbcDriverResponse={"
                    + "\"driverName\":\"" + driverName + "\","
                    + "\"resourcePath\":\"" + wfPath + "\","
                    + "\"destinationSessionId\":\"{{sessionId}}\","
                    + "\"status\":\"OK\","
                    + "\"message\":\"Added JDBC Driver: " + driverName + "\""
                    + "}";
            try (TestWebSocketClient testClient = TestWebSocketClient.builder()
                    .url(baseGwUri + "/ui/ws")
                    .expectWelcome(new MessageAnswer(req, driverJarUrl, 0))
                    .expectGenericSuccess(wfPath.ids().getFeedId())
                    .expectText(response, TestWebSocketClient.Answer.CLOSE)
                    .expectClose()
                    .build()) {
                testClient.validate(10000);
            }

            assertNodeEquals(mcc, driverAddress, getClass(), driverFileNameAfterAdd, false);
        }
    }

    @Test(groups = { GROUP }, dependsOnMethods = { "testAddJdbcDriver" })
    public void testRemoveJdbcDriver() throws Throwable {
        waitForAccountsAndInventory();

        CanonicalPath wfPath = getHawkularWildFlyServerResourcePath();
        final ModelNode driverAddress = driverAddress();

        String removePath = wfPath.toString().replaceFirst("\\~+$", "")
                + Util.urlEncode("~/subsystem=datasources/jdbc-driver=" + driverName);

        try (ModelControllerClient mcc = newHawkularModelControllerClient()) {
            ModelNode datasourcesPath = new ModelNode().add(ModelDescriptionConstants.SUBSYSTEM, "datasources");
            assertResourceCount(mcc, datasourcesPath, "jdbc-driver", 4);
            assertResourceExists(mcc, driverAddress, true);

            String req = "RemoveJdbcDriverRequest={\"authentication\":" + authentication + ", "
                    + "\"resourcePath\":\"" + removePath + "\""
                    + "}";
            String response = "RemoveJdbcDriverResponse={"
                    + "\"resourcePath\":\"" + removePath.toString() + "\","
                    + "\"destinationSessionId\":\"{{sessionId}}\","
                    + "\"status\":\"OK\","
                    + "\"message\":\"Performed [Remove] on a [JDBC Driver] given by Inventory path [" + removePath
                    + "]\""
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

            assertResourceExists(mcc, driverAddress, false);

        }
    }
}
